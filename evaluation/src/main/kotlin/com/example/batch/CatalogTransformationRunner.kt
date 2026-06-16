package com.example.batch

import com.example.common.MAIN_ALS_FILE
import com.example.transformation.OntoUml2AlloyTransformer
import com.example.transformation.prepareAlloyModel
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// used to run the earlier version of ontouml-js.ontouml2alloy, which did not output metadata
private const val NO_METADATA_FLAG = "--no-metadata"

object CatalogTransformationRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val noMetadata = NO_METADATA_FLAG in args
        val manifestPath = Paths.get(args[0]).normalize()
        val outDir =
            (
                args.getOrNull(1)?.let { Paths.get(it) }
                    ?: manifestPath.resolveSibling("batch-alloy-output")
            ).normalize()
        val excludeSlugs =
            args
                .getOrNull(2)
                ?.split(',')
                ?.map { it.trim() }
                ?.toSet()
                .orEmpty()

        Files.createDirectories(outDir)

        val gsonPretty = GsonBuilder().setPrettyPrinting().create()
        val manifest = gsonPretty.fromJson(Files.readString(manifestPath), CatalogManifest::class.java)
        val models = manifest.models.orEmpty().filter { it.slug !in excludeSlugs }

        val root =
            manifest.outRoot?.let { Paths.get(it) }?.takeIf { Files.exists(it) }
                ?: manifestPath.parent
        val seen = mutableSetOf<Path>()
        val runs = mutableListOf<ModelTransformationResult>()
        var ok = 0
        var failed = 0

        println("${models.size} models")

        for ((i, model) in models.withIndex()) {
            val slug = model.slug ?: "model"
            val modelDir = model.dir ?: continue
            val jsonFile = model.json ?: continue
            val jsonPath = root.resolve(modelDir).resolve(jsonFile).normalize()
            if (!seen.add(jsonPath)) continue

            val base = jsonFile.removeSuffix(".json").replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            val prefix = if (base == "ontology" || base == "main") slug else "${slug}__$base"
            val modelName = model.label ?: slug

            print("[${i + 1}/${models.size}] $prefix ... ")
            System.out.flush()

            try {
                if (noMetadata) {
                    // run transformation only, for old transformation code
                    transformOnly(jsonPath, outDir.resolve(prefix), false)
                } else {
                    // run transformation via pipeline, including theme generation
                    prepareAlloyModel(jsonPath, outDir.resolve(prefix), false)
                }
                runs +=
                    ModelTransformationResult(
                        modelName = modelName,
                        slug = slug,
                        jsonPath = "$modelDir/$jsonFile",
                        ok = true,
                        error = null,
                        outputFiles = listOf("$prefix/$MAIN_ALS_FILE"),
                    )
                ok++
                println("OK")
            } catch (e: Exception) {
                runs +=
                    ModelTransformationResult(
                        modelName = modelName,
                        slug = slug,
                        jsonPath = "$modelDir/$jsonFile",
                        ok = false,
                        error = e.message,
                        outputFiles = emptyList(),
                    )
                failed++
                println("FAILED: ${e.message?.lines()?.firstOrNull()}")
            }
        }

        val outManifest = outDir.resolve("alloy_batch_manifest.json")
        Files.writeString(
            outManifest,
            gsonPretty.toJson(
                CatalogTransformationManifest(
                    alloyOutDir = outDir.toString(),
                    runs = runs,
                    summary = CatalogTransformationSummary(ok = ok, failed = failed),
                ),
            ),
        )
        println("\nDone. ok=$ok failed=$failed")
    }
}

// run the transformation only, used to test old transformation
internal fun transformOnly(
    inputJson: Path,
    outDir: Path,
    allowAbstractLeafInstances: Boolean,
): Path {
    OntoUml2AlloyTransformer.transform(inputJson, outDir, allowAbstractLeafInstances)
    return outDir.resolve(MAIN_ALS_FILE)
}

private data class CatalogModelInfo(
    val slug: String?,
    val label: String?,
    val dir: String?,
    val json: String?,
)

private data class CatalogManifest(
    val outRoot: String?,
    val models: List<CatalogModelInfo>?,
)

data class CatalogTransformationManifest(
    val alloyOutDir: String,
    val runs: List<ModelTransformationResult>,
    val summary: CatalogTransformationSummary? = null,
)

data class CatalogTransformationSummary(
    val ok: Int,
    val failed: Int,
)

data class ModelTransformationResult(
    val modelName: String,
    val slug: String,
    val jsonPath: String? = null,
    val ok: Boolean,
    val error: String? = null,
    val outputFiles: List<String>,
)
