package com.example.batch

import com.example.scenarios.ParameterisedScenario
import com.example.scenarios.SimulationScenariosLoader
import com.example.scenarios.bindingsToPrimitives
import com.example.transformation.TransformationMetadata
import com.example.transformation.prepareAlloyModel
import java.nio.file.Files
import java.nio.file.Paths

object SingleScenarioRunner {
    private const val SAMPLES_FLAG = "--samples-per-scenario"
    private const val SEED_FLAG = "--seed"

    @JvmStatic
    fun main(rawArgs: Array<String>) {
        val samplesPerScenario = rawArgs.flag(SAMPLES_FLAG)!!.toInt()
        val timeoutSeconds = rawArgs.flag(TIMEOUT_SECONDS_FLAG)!!.toInt()
        val transformOutOverride = rawArgs.flag(TRANSFORM_OUT_FLAG)?.let { Paths.get(it).normalize() }
        val seed = rawArgs.flag(SEED_FLAG)!!.toLong()
        val inputArg = Paths.get(rawArgs.flag(MODEL_FLAG)!!).normalize()
        val modelPath =
            if (inputArg.fileName.toString().endsWith(".als")) {
                inputArg
            } else {
                val outDir =
                    requireNotNull(transformOutOverride) {
                        "--transform-out is required for .json input"
                    }
                Files.createDirectories(outDir)
                prepareAlloyModel(inputArg, outDir, ALLOW_ABSTRACT_LEAVES_FLAG in rawArgs).modelPath
            }
        val outPath =
            rawArgs.flag(OUT_FLAG)?.let { Paths.get(it).normalize() }
                ?: modelPath.resolveSibling("scenario_batch_results.jsonl")

        val metadata =
            TransformationMetadata.fromPath(
                modelPath.resolveSibling("transformation_metadata.json"),
            )
        val baseModel = Files.readString(modelPath)
        val relations = metadata.navigableRelations()
        val resolver = ParameterResolver(metadata.domainClassNames(), relations)
        val scenarios = SimulationScenariosLoader.scenarios

        val combinations =
            scenarios.map {
                it to
                    findAllArgumentCombinationsForScenario(
                        it,
                        resolver,
                        relations,
                        samplesPerScenario,
                        seed,
                    )
            }
        val totalRuns = combinations.sumOf { it.second.first.size }
        val fullyEnumeratedCount = combinations.count { it.second.third }
        println("n=$samplesPerScenario, seed=$seed, $totalRuns runs across ${scenarios.size} scenarios")
        combinations.forEach { (sc, result) ->
            val (combos, total, fullyEnumerated) = result
            val note = if (fullyEnumerated) "all" else "${combos.size}/$total sampled"
            println("  ${sc.id}: $total total ($note)")
        }

        var runIndex = 0
        val statusCounts = linkedMapOf<String, Int>()

        Files.createDirectories(outPath.parent)
        Files.newBufferedWriter(outPath).use { jsonl ->
            for ((scenario, result) in combinations) {
                val (combos, spaceSize, fullyEnumerated) = result
                combos.forEachIndexed { ci, combo ->
                    runIndex++
                    print("[$runIndex/$totalRuns] ${scenario.id} #${ci + 1}/${combos.size} ... ")
                    System.out.flush()
                    val start = System.currentTimeMillis()
                    val row =
                        runPredicate(
                            parameterised = listOf(ParameterisedScenario(scenario.id, combo.bindings)),
                            modelMetadata = metadata,
                            baseModel = baseModel,
                            modelPath = modelPath,
                            timeoutSeconds = timeoutSeconds,
                        )
                    val elapsed = System.currentTimeMillis() - start
                    val final = finalizeOutcome(row.outcome, elapsed, timeoutSeconds)
                    println("${final.status} (${elapsed}ms)${final.error}")

                    statusCounts[final.status] = (statusCounts[final.status] ?: 0) + 1
                    jsonl.appendLine(
                        gson.toJson(
                            mapOf(
                                "seed" to seed,
                                "scenario_id" to scenario.id,
                                "scenario_name" to scenario.name,
                                "combo_index" to ci + 1,
                                "combo_total" to combos.size,
                                "fully_enumerated" to fullyEnumerated,
                                "space_size" to spaceSize,
                                "status" to final.status,
                                "satisfiable" to final.satisfiable,
                                "duration_ms" to elapsed,
                                "worldScope" to row.worldScope,
                                "error" to final.error,
                                "parameters" to combo.alloy,
                                "bindings" to bindingsToPrimitives(combo.bindings),
                                "rendered_alloy" to row.renderedAlloy,
                                "rendered_nl" to row.renderedNl,
                            ),
                        ),
                    )
                    jsonl.flush()
                }
            }
            println("Status: ${statusCounts.toSortedMap()}")
        }
    }
}
