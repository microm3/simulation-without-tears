package com.example.transformation

import com.example.common.GENERATED_THEME_FILE
import com.example.common.MAIN_ALS_FILE
import com.example.common.TRANSFORMATION_METADATA_FILE
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.writeText

data class PreparedModel(
    val modelPath: Path,
    val metadataPath: Path,
    val generatedThemePath: Path,
)

/**
 * Resolves the user-supplied input (.json or .als) into the model + metadata paths the downstream
 * pipeline needs. A theme is generated based on the metadata file.
 */
fun prepareAlloyModel(
    inputFile: Path,
    outputDirOverride: Path? = null,
    allowAbstractLeafInstances: Boolean = false,
): PreparedModel {
    val (modelPath, outputDir) = when (inputFile.extension.lowercase()) {
        "json" -> {
            val dir = OntoUml2AlloyTransformer.transform(
                inputFile,
                outputDirOverride,
                allowAbstractLeafInstances,
            )
            dir.resolve(MAIN_ALS_FILE) to dir
        }
        "als" -> inputFile to inputFile.parent
        else -> error("Input must be an OntoUML JSON file (.json) or a transformed Alloy model (.als).")
    }

    val metadataPath = outputDir.resolve(TRANSFORMATION_METADATA_FILE)
    val themePath = outputDir.resolve(GENERATED_THEME_FILE).apply {
        writeText(ThemeGenerator.generate(TransformationMetadata.fromPath(metadataPath)))
    }
    return PreparedModel(modelPath, metadataPath, themePath)
}
