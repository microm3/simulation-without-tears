package com.example.batch

import com.example.model.ModelMetadata
import com.example.scenarios.CombinationPolicy
import com.example.scenarios.ParameterisedScenario
import com.example.scenarios.ScenarioDefinition
import com.example.scenarios.ScenarioExpansion
import com.example.scenarios.SimulationScenariosLoader
import com.example.scenarios.resolveBindings
import com.example.transformation.TransformationMetadata
import com.google.common.collect.Lists
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object PairwiseScenarioRunner {
  private const val COMBINE_SIZE_FLAG = "--combine-size"
  private const val POLICY_FLAG = "--policy"
  private const val SAT_CASES_FROM_FLAG = "--sat-cases-from"
  private const val CASES_PER_SCENARIO_FLAG = "--cases-per-scenario"
  private const val CLASS_SHARING_FLAG = "--class-sharing"
  private const val CLASS_SHARING_SHARED = "shared"
  private const val CLASS_SHARING_DISJOINT = "disjoint"
  private const val CLASS_SHARING_OVERLAP = "overlap"
  private const val COMBINATION_POLICY_FLAG = "--combination-policy"

  @JvmStatic
  fun main(rawArgs: Array<String>) {
    val timeoutSeconds = rawArgs.flag(TIMEOUT_SECONDS_FLAG)!!.toInt()
    val combineSize = rawArgs.flag(COMBINE_SIZE_FLAG)?.toInt() ?: 2
    val policy = rawArgs.flag(POLICY_FLAG) ?: "allowed"
    val casesPerScenario = rawArgs.flag(CASES_PER_SCENARIO_FLAG)?.toInt() ?: 1
    val classSharing = rawArgs.flag(CLASS_SHARING_FLAG) ?: CLASS_SHARING_SHARED
    val satCasesBySc =
            parseSatCasesFromJsonl(Paths.get(rawArgs.flag(SAT_CASES_FROM_FLAG)!!).normalize())
    val modelPath = Paths.get(rawArgs.flag(MODEL_FLAG)!!).normalize()
    val outPath =
            rawArgs.flag(OUT_FLAG)?.let { Paths.get(it).normalize() }
                    ?: modelPath.resolveSibling("scenario_batch_combined_results.jsonl")
    val metadata =
            TransformationMetadata.fromPath(
                    modelPath.resolveSibling("transformation_metadata.json"),
            )
    val classNames = metadata.domainClassNames()
    val baseModel = Files.readString(modelPath)
    val scenarios =
            rawArgs.flag(COMBINATION_POLICY_FLAG)?.let {
              applyTargetPropertyCombinationPolicy(
                      SimulationScenariosLoader.scenarios,
                      Paths.get(it)
              )
            }
                    ?: SimulationScenariosLoader.scenarios

    val relations = metadata.navigableRelations()
    val cases =
            scenarios
                    .map { sc ->
                      sc to
                              satCasesBySc[sc.id].orEmpty().take(casesPerScenario).map { bindings ->
                                val resolved =
                                        resolveBindings(
                                                sc,
                                                ParameterisedScenario(sc.id, bindings),
                                                relations
                                        )
                                Combo(bindings, resolved.alloyParams, resolved.nlParams)
                              }
                    }
                    .filter { it.second.isNotEmpty() }

    val tuples = enumerateTuples(cases, policy)
    println(
            "k=$combineSize policy=$policy sharing=$classSharing cases=$casesPerScenario, ${tuples.size} tuples across ${cases.size} scenarios",
    )
    cases.forEach { (sc, combos) ->
      val available = satCasesBySc[sc.id]?.size ?: 0
      val note =
              if (available <= casesPerScenario) "all"
              else "${combos.size}/$available sampled (limited by valid combinations)"
      println("  ${sc.id}: $available available ($note)")
    }

    var idx = 0
    var skippedBySharing = 0
    val statusCounts = linkedMapOf<String, Int>()

    Files.createDirectories(outPath.parent)
    Files.newBufferedWriter(outPath).use { jsonl ->
      for (tuple in tuples) {
        val overlap = tupleClassBindingsOverlap(tuple, classNames, modelMetadata = metadata)
        val skip =
                when (classSharing) {
                  CLASS_SHARING_DISJOINT -> overlap
                  CLASS_SHARING_OVERLAP -> !overlap
                  else -> false
                }
        if (skip) {
          skippedBySharing++
          continue
        }
        idx++

        val ids = tuple.joinToString("|") { it.first.id }
        val names = tuple.joinToString("|") { it.first.name }
        print("[$idx/${tuples.size}] $ids ... ")
        System.out.flush()
        val start = System.currentTimeMillis()
        val row =
                runPredicate(
                        parameterised =
                                tuple.map { (sc, cb) -> ParameterisedScenario(sc.id, cb.bindings) },
                        modelMetadata = metadata,
                        baseModel = baseModel,
                        modelPath = modelPath,
                        timeoutSeconds = timeoutSeconds,
                )
        val elapsed = System.currentTimeMillis() - start
        val final = finalizeOutcome(row.outcome, elapsed, timeoutSeconds)
        println("${final.status} (${elapsed}ms)${final.error?.let { " - ${it.take(120)}" } ?: ""}")

        statusCounts[final.status] = (statusCounts[final.status] ?: 0) + 1
        jsonl.appendLine(
                gson.toJson(
                        mapOf(
                                "policy" to policy,
                                "tuple_index" to idx,
                                "tuple_total" to tuples.size,
                                "tuple_size" to tuple.size,
                                "scenario_ids" to ids,
                                "scenario_names" to names,
                                "status" to final.status,
                                "satisfiable" to final.satisfiable,
                                "duration_ms" to elapsed,
                                "worldScope" to row.worldScope,
                                "error" to final.error,
                                "parameters" to tuple.map { it.second.alloy },
                                "rendered_alloy" to row.renderedAlloy,
                                "rendered_nl" to row.renderedNl,
                        ),
                ),
        )
        jsonl.flush()
      }
      println("Status: ${statusCounts.toSortedMap()}")
    }
    if (skippedBySharing > 0) {
      println("Skipped $skippedBySharing tuple(s) by $CLASS_SHARING_FLAG=$classSharing")
    }
  }

  private fun applyTargetPropertyCombinationPolicy(
          scenarios: List<ScenarioDefinition>,
          taxonomyPath: Path,
  ): List<ScenarioDefinition> {
    val groups =
            (gson.fromJson(Files.readString(taxonomyPath), Map::class.java)["groups"] as? Map<*, *>)
                    .orEmpty()
    return scenarios.map { s -> s.copy(conflictGroups = listOfNotNull(groups[s.id] as? String)) }
  }

  private fun tupleClassBindingsOverlap(
          tuple: List<Pair<ScenarioDefinition, Combo>>,
          classNames: List<String>,
          modelMetadata: ModelMetadata,
  ): Boolean {
    val pattern =
            Regex(
                    "\\b(" +
                            classNames.sortedByDescending { it.length }.joinToString("|") {
                              Regex.escape(it)
                            } +
                            ")\\d*\\b",
            )
    val sets =
            tuple.map { (sc, cb) ->
              val rendered =
                      ScenarioExpansion.renderScenarioTemplate(
                              sc.alloyTemplate,
                              cb.alloy,
                              modelMetadata
                      )
              pattern.findAll(rendered).map { it.groupValues[1] }.toSet()
            }
    return sets.indices.any { i ->
      sets.subList(i + 1, sets.size).any { it.intersect(sets[i]).isNotEmpty() }
    }
  }

  private fun enumerateTuples(
          scenarios: List<Pair<ScenarioDefinition, List<Combo>>>,
          policy: String,
  ): List<List<Pair<ScenarioDefinition, Combo>>> {
    fun policyOk(tuple: List<ScenarioDefinition>): Boolean =
            when (policy) {
              "allowed" -> {
                tuple.indices.all { i ->
                  CombinationPolicy.isCombinable(
                          tuple[i],
                          tuple.filterIndexed { j, _ -> j != i },
                  )
                }
              }
              "disallowed" -> {
                tuple.indices.all { i ->
                  !CombinationPolicy.isCombinable(
                          tuple[i],
                          tuple.filterIndexed { j, _ -> j != i },
                  )
                }
              }
              else -> {
                error("Unknown policy: $policy")
              }
            }
    return scenarios
            .indices
            .flatMap { i -> (i..scenarios.lastIndex).map { j -> listOf(i, j) } }
            .filter { idxList -> policyOk(idxList.map { scenarios[it].first }) }
            .flatMap { idxList ->
              val tuple = idxList.map { scenarios[it] }
              Lists.cartesianProduct(tuple.map { (sc, combos) -> combos.map { sc to it } })
            }
  }
}
