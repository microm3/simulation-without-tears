package com.example.app

import com.example.alloy.appendUserConstraint
import com.example.alloy.lookupStoredUserConstraint
import com.example.alloy.parseAlloyModule
import com.example.alloy.parseCheckConstraint
import com.example.common.USER_CONSTRAINTS_FILE
import com.example.gui.RunCommandOption
import com.example.gui.ScenarioDialog
import com.example.gui.ScenarioOutcome
import com.example.gui.launchVisualizer
import com.example.gui.pickModelFile
import com.example.gui.promptAllowAbstractLeaves
import com.example.model.ModelMetadata
import com.example.scenarios.RenderedScenario
import com.example.transformation.TransformationMetadata
import com.example.transformation.prepareAlloyModel
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.system.exitProcess

object App {
    private const val ALLOW_ABSTRACT_LEAVES_FLAG = "--allow-abstract-leaf-instances"

    fun run(args: Array<String>) {
        val allowAbstractLeavesFromCli =
            args.isNotEmpty() && args.last() == ALLOW_ABSTRACT_LEAVES_FLAG
        val pathCount = if (allowAbstractLeavesFromCli) args.size - 1 else args.size
        val cliInput: Path? = if (pathCount >= 1) cliPath(args[0]) else null
        val cliOutputDir: Path? = if (pathCount >= 2) cliPath(args[1]) else null

        val inputFile: Path = cliInput ?: pickModelFile() ?: exitProcess(0)
        val isJsonInput = inputFile.toString().endsWith(".json")
        val allowAbstractLeafInstances: Boolean =
            when {
                !isJsonInput -> false
                cliInput != null -> allowAbstractLeavesFromCli
                else -> promptAllowAbstractLeaves()
            }

        val preparedModel = prepareAlloyModel(inputFile, cliOutputDir, allowAbstractLeafInstances)
        val modelMetadata = TransformationMetadata.fromPath(preparedModel.metadataPath)

        runScenarioCycle(modelMetadata, preparedModel.modelPath, preparedModel.generatedThemePath)
    }

    private fun runScenarioCycle(
        modelMetadata: ModelMetadata,
        modelPath: Path,
        themePath: Path,
    ) {
        val userConstraintsPath = modelPath.parent.resolve(USER_CONSTRAINTS_FILE)
        val existingModule =
            userConstraintsPath
                .takeIf { it.exists() }
                ?.let(::parseAlloyModule)
        val existingRunCommands: List<RunCommandOption> =
            existingModule
                ?.allCommands
                ?.map { RunCommandOption(label = it.label, displayText = it.toString()) }
                ?: emptyList()

        val parseChecker = { text: String -> parseCheckConstraint(modelPath.parent, text) }

        when (val outcome = ScenarioDialog.show(modelMetadata, existingRunCommands, parseChecker)) {
            null -> exitProcess(0)

            is ScenarioOutcome.Apply -> {
                val userPath = appendUserConstraint(modelPath.parent, outcome.result.predicateText)
                val moduleAfterAppend = parseAlloyModule(userPath)
                val appendedRun = moduleAfterAppend.allCommands.lastOrNull()

                launchVisualizer(
                    moduleAfterAppend,
                    theme = themePath,
                    command = appendedRun,
                    scenarioResult = outcome.result,
                    onAddAnotherConstraint = { runScenarioCycle(modelMetadata, modelPath, themePath) },
                )
            }

            is ScenarioOutcome.UseExisting -> {
                val module = existingModule ?: return
                val command =
                    module.allCommands.firstOrNull { it.label == outcome.commandLabel }
                        ?: return
                val stored = lookupStoredUserConstraint(userConstraintsPath, outcome.commandLabel)
                val naturalLanguage =
                    stored?.naturalLanguage?.takeIf { it.isNotBlank() }
                        ?: "Re-running previously added constraint (${outcome.commandLabel})."
                val alloy =
                    stored?.predicateBody?.takeIf { it.isNotBlank() }
                        ?: (command.formula?.toString() ?: command.toString())
                launchVisualizer(
                    module,
                    theme = themePath,
                    command = command,
                    scenarioResult =
                        RenderedScenario(
                            predicateText = alloy,
                            naturalLanguage = naturalLanguage,
                            alloyExpression = alloy,
                            scenarioId = outcome.commandLabel,
                        ),
                    onAddAnotherConstraint = { runScenarioCycle(modelMetadata, modelPath, themePath) },
                )
            }
        }
    }

    private fun cliPath(raw: String): Path = Paths.get(raw).normalize()
}
