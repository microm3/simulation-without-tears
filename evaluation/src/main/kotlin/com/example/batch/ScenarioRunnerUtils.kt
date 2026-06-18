package com.example.batch

import com.example.alloy.parseAlloyModule
import com.example.model.ModelMetadata
import com.example.model.RelationEndpoint
import com.example.scenarios.ParamValue
import com.example.scenarios.Parameter
import com.example.scenarios.ParameterisedScenario
import com.example.scenarios.ScenarioBuildOutcome
import com.example.scenarios.ScenarioDefinition
import com.example.scenarios.bindingsFromPrimitives
import com.example.scenarios.buildScenarioConstraint
import com.example.scenarios.resolveBindings
import com.example.scenarios.validateAssociationBindings
import com.example.scenarios.validateScenarioParameterConstraints
import com.google.gson.GsonBuilder
import edu.mit.csail.sdg.alloy4.A4Reporter
import edu.mit.csail.sdg.translator.A4Options
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod
import kodkod.engine.satlab.SATFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.random.Random

internal const val MODEL_FLAG = "--model"
internal const val OUT_FLAG = "--out"
internal const val TIMEOUT_SECONDS_FLAG = "--timeout-seconds"
internal const val ALLOW_ABSTRACT_LEAVES_FLAG = "--allow-abstract-leaf-instances"
internal const val TRANSFORM_OUT_FLAG = "--transform-out"
internal val gson = GsonBuilder().disableHtmlEscaping().create()

/** Returns the value after the named flag, or null if the flag is absent. */
internal fun Array<String>.flag(name: String): String? = indexOf(name).takeIf { it >= 0 }?.let { this[it + 1] }

/**
 * One enumerated parameter binding for a scenario. [bindings] is the wire-shaped request body;
 * [alloy] / [nl] are the resolved maps (post-snippet/accessor lookup) kept around for batch output
 * formatting and pre-execution validation filtering.
 */
internal data class Combo(
    val bindings: Map<String, ParamValue>,
    val alloy: Map<String, Any>,
    val nl: Map<String, Any>,
)

internal data class PredicateOutcome(
    val status: String,
    val satisfiable: Boolean?,
    val error: String?,
)

internal data class RunOutcome(
    val outcome: PredicateOutcome,
    val worldScope: Int,
    val renderedAlloy: String,
    val renderedNl: String,
)

internal class ParameterResolver(
    private val classNames: List<String>,
    private val relations: List<RelationEndpoint>,
) {
    fun intValuesFor(p: Parameter.IntInput): List<Int> = ((p.min ?: 0)..(p.max ?: 5)).toList()

    fun classValues(): List<String> = classNames

    fun relValuesFor(
        src: String?,
        tgt: String?,
    ): List<RelationEndpoint> =
        relations.filter {
            (src == null || it.source == src) && (tgt == null || it.target == tgt)
        }
}

internal fun findAllArgumentCombinationsForScenario(
    scenario: ScenarioDefinition,
    resolver: ParameterResolver,
    relations: List<RelationEndpoint>,
    n: Int,
    seed: Long,
): Triple<List<Combo>, Int, Boolean> {
    // Builds the cartesian product of parameter values one parameter at a time, in the wire shape
    // (choice ids for Option, relation names for Association). Snippet/accessor lookup is left to
    // `resolveBindings` so this mirrors what an HTTP client would post.
    fun combos(params: List<Parameter>): List<Map<String, ParamValue>> {
        if (params.isEmpty()) return listOf(emptyMap())
        val root = params.first()
        val rest = params.drop(1)
        val thisValues: List<Map<String, ParamValue>> =
            when (root) {
                is Parameter.IntInput ->
                    resolver.intValuesFor(root).map { mapOf(root.id to ParamValue.IntValue(it)) }

                is Parameter.ClassRef ->
                    resolver.classValues().map { mapOf(root.id to ParamValue.StringValue(it)) }

                is Parameter.Association ->
                    resolver.relValuesFor(null, null).map { rel ->
                        mapOf(root.id to ParamValue.StringValue(rel.name))
                    }

                // recurse into each choice's sub-parameters, then prepend the choice id binding
                is Parameter.Option ->
                    root.choices.flatMap { choice ->
                        combos(choice.parameters).map { remaining ->
                            mapOf<String, ParamValue>(root.id to ParamValue.StringValue(choice.id)) + remaining
                        }
                    }
            }
        return thisValues.flatMap { current ->
            combos(rest).map { remaining -> current + remaining }
        }
    }

    val valid =
        combos(scenario.parameters).mapNotNull { bindings ->
            val resolved = resolveBindings(scenario, ParameterisedScenario(scenario.id, bindings), relations)
            if (validateAssociationBindings(resolved.activeParams, resolved.alloyParams, relations) != null) return@mapNotNull null
            if (validateScenarioParameterConstraints(scenario, resolved.alloyParams) != null) return@mapNotNull null
            Combo(bindings, resolved.alloyParams, resolved.nlParams)
        }
    // shuffle before sampling so that taking the first n gives a random spread (not dependent on the cartesian process)
    val shuffled = valid.shuffled(Random(seed))
    val fullyEnumerated = valid.size <= n
    return Triple(if (fullyEnumerated) shuffled else shuffled.take(n), valid.size, fullyEnumerated)
}

internal fun executePredicate(
    modelPath: Path,
    baseModel: String,
    predicateText: String,
    tempFileName: String,
    timeoutSeconds: Int,
): PredicateOutcome {
    val tempFile = modelPath.resolveSibling(tempFileName)
    Files.writeString(tempFile, "$baseModel\n$predicateText\n")
    return try {
        solveInIsolatedJvm(tempFile, timeoutSeconds)
    } finally {
        runCatching { Files.deleteIfExists(tempFile) }
    }
}

private fun effectiveClassPath(): String {
    val cl = Thread.currentThread().contextClassLoader
    if (cl is java.net.URLClassLoader) {
        val cp = cl.urLs.joinToString(java.io.File.pathSeparator) { Paths.get(it.toURI()).toString() }
        if (cp.isNotBlank()) return cp
    }
    return System.getProperty("java.class.path")
}

private fun solveInIsolatedJvm(
    tempFile: Path,
    timeoutSeconds: Int,
): PredicateOutcome {
    val javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
    val outcomeFile = tempFile.resolveSibling("${tempFile.fileName}.outcome.json")
    val stderrFile = tempFile.resolveSibling("${tempFile.fileName}.stderr.log")
    val process =
        ProcessBuilder(
            javaBin,
            "-cp",
            effectiveClassPath(),
            "com.example.batch.PredicateSolver",
            tempFile.toString(),
            outcomeFile.toString(),
            timeoutSeconds.toString(),
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.to(stderrFile.toFile()))
            .start()
    return try {
        val finished = process.waitFor(15L, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return PredicateOutcome("TIMEOUT", null, "Aborted after ${timeoutSeconds}s (parent hard-kill after 15s)")
        }
        val exitCode = process.exitValue()
        if (exitCode == WATCHDOG_EXIT_CODE) {
            return PredicateOutcome("TIMEOUT", null, "Aborted after ${timeoutSeconds}s")
        }
        if (Files.exists(outcomeFile)) {
            gson.fromJson(Files.readString(outcomeFile), PredicateOutcome::class.java)
        } else {
            val stderrTail =
                runCatching { Files.readAllLines(stderrFile).takeLast(20).joinToString(" | ") }
                    .getOrDefault("<unavailable>")
            PredicateOutcome("COMMAND_ERROR", null, "exit=$exitCode stderr=$stderrTail")
        }
    } finally {
        runCatching { Files.deleteIfExists(outcomeFile) }
        runCatching { Files.deleteIfExists(stderrFile) }
    }
}

internal fun finalizeOutcome(
    outcome: PredicateOutcome,
    durationMs: Long,
    timeoutSeconds: Int,
): PredicateOutcome =
    if (outcome.status == "SAT" && durationMs > timeoutSeconds * 1000L + 2000L) {
        PredicateOutcome("TIMEOUT", null, "Aborted after ${timeoutSeconds}s (late SAT beyond budget: ${durationMs}ms)")
    } else {
        outcome
    }

@Suppress("UNCHECKED_CAST")
internal fun parseSatCasesFromJsonl(path: Path): Map<String, List<Map<String, ParamValue>>> =
    Files
        .readAllLines(path)
        .filter { it.isNotBlank() }
        .map { gson.fromJson(it, Map::class.java) as Map<String, Any> }
        .filter { it["status"] == "SAT" }
        .groupBy { it["scenario_id"] as String }
        .mapValues { (_, rows) ->
            // gson deserialises numbers as Double, normalise to Int before lifting to ParamValue
            rows.map { row ->
                val raw = row["bindings"] as Map<String, Any>
                val normalised = raw.mapValues { (_, v) -> if (v is Number) v.toInt() else v }
                bindingsFromPrimitives(normalised)
            }
        }

internal fun runPredicate(
    parameterised: List<ParameterisedScenario>,
    modelMetadata: ModelMetadata,
    baseModel: String,
    modelPath: Path,
    timeoutSeconds: Int,
): RunOutcome {
    val rendered = (buildScenarioConstraint(parameterised, modelMetadata) as ScenarioBuildOutcome.Ok).result
    val outcome = executePredicate(modelPath, baseModel, rendered.predicateText, "batch_predicate.als", timeoutSeconds)
    return RunOutcome(outcome, rendered.worldScope, rendered.predicateText, rendered.naturalLanguage)
}

// exit code used by the child-JVM watchdog so the parent can distinguish a hard timeout from a normal crash
private const val WATCHDOG_EXIT_CODE = 42

object PredicateSolver {
    @JvmStatic
    fun main(args: Array<String>) {
        val tempFile = Paths.get(args[0]).normalize()
        val outcomeFile = Paths.get(args[1]).normalize()
        val timeoutSeconds = args.getOrNull(2)?.toLongOrNull() ?: -1L

        // if the SAT solver doesn't finish in time, kill the JVM
        if (timeoutSeconds > 0) {
            Thread {
                try {
                    Thread.sleep(timeoutSeconds * 1000L)
                } catch (_: InterruptedException) {
                }
                Runtime.getRuntime().halt(WATCHDOG_EXIT_CODE)
            }.apply {
                isDaemon = true
                start()
            }
        }

        var parsed = false
        val outcome =
            try {
                val module = parseAlloyModule(tempFile)
                parsed = true
                val solution =
                    TranslateAlloyToKodkod.execute_command(
                        A4Reporter.NOP,
                        module.allReachableSigs,
                        module.allCommands.last(),
                        A4Options().apply { solver = SATFactory.DEFAULT },
                    )
                val sat = solution.satisfiable()
                PredicateOutcome(if (sat) "SAT" else "UNSAT", sat, null)
            } catch (e: Throwable) {
                PredicateOutcome(
                    if (parsed) "COMMAND_ERROR" else "PARSE_ERROR",
                    null,
                    "${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()}",
                )
            }
        Files.writeString(outcomeFile, gson.toJson(outcome))
    }
}
