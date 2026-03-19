package com.example.batch

import com.example.alloy.SolveOutcome
import com.example.alloy.parseAlloyModule
import com.example.alloy.solveCommand
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Script to parse and run a command for each of the alloy-models in a batch manifest writes JSON
 * (`results` + `summary`) CLI: [0] path to manifest.json [1] output file/path [2] optional
 * comma-separated run slugs to skip
 */
object CatalogAlloyRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val manifestPath = Paths.get(args[0]).normalize()
        val outputPath =
            args.getOrNull(1)?.let { Paths.get(it).normalize() }
                ?: manifestPath.resolveSibling("validation_results.json")
        val excludeSlugs =
            args
                .getOrNull(2)
                ?.split(',')
                ?.map { it.trim() }
                ?.toSet()
                .orEmpty()

        val gson = GsonBuilder().setPrettyPrinting().create()
        val manifest = gson.fromJson(Files.readString(manifestPath), CatalogTransformationManifest::class.java)
        val alloyDir = Paths.get(manifest.alloyOutDir)

        // Skip failed transforms, empty outputs, and explicitly excluded slugs.
        val runs =
            manifest.runs.filter {
                it.ok && it.outputFiles.isNotEmpty() && it.slug !in excludeSlugs
            }

        println("Running ${runs.size} models\n")

        val results =
            runs.mapIndexed { i, run ->
                val alsFile = run.outputFiles.first()
                print("[${i + 1}/${runs.size}] $alsFile : ")
                System.out.flush()
                val start = System.currentTimeMillis()
                val (sat, error) = runModel(alloyDir.resolve(alsFile))
                val durationMs = System.currentTimeMillis() - start

                val status =
                    when {
                        error != null -> "ERROR"
                        sat == true -> "SAT"
                        sat == false -> "UNSAT"
                        else -> "UNKNOWN"
                    }

                println("$status (${durationMs}ms)")
                ModelRunResult(alsFile, run.modelName, run.slug, sat, error, durationMs)
            }

        val summary =
            mapOf(
                "total" to results.size,
                "sat" to results.count { it.satisfiable == true },
                "unsat" to results.count { it.satisfiable == false },
                "failed" to results.count { it.error != null },
            )
        outputPath.parent?.let { Files.createDirectories(it) }
        Files.writeString(outputPath, gson.toJson(mapOf("results" to results, "summary" to summary)))
        println(
            "total=${summary["total"]} sat=${summary["sat"]} unsat=${summary["unsat"]} failed=${summary["failed"]}",
        )
    }

    // Parses the module, runs its first command, returns (isSat?, error?)
    private fun runModel(modelPath: Path): Pair<Boolean?, String?> =
        try {
            val module = parseAlloyModule(modelPath)
            (solveCommand(module, module.allCommands.first()) is SolveOutcome.Sat) to null
        } catch (e: Exception) {
            null to "${e.javaClass.simpleName}: ${e.message}"
        }
}

private data class ModelRunResult(
    val file: String,
    val modelName: String,
    val slug: String,
    val satisfiable: Boolean?,
    val error: String?,
    val durationMs: Long,
)
