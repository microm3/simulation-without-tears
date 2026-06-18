package com.example.gui

import com.example.alloy.SolveOutcome
import com.example.alloy.nextInstance
import com.example.alloy.solveCommand
import com.example.scenarios.RenderedScenario
import com.example.scenarios.UNSAT_BANNER
import edu.mit.csail.sdg.alloy4viz.VizGUI
import edu.mit.csail.sdg.ast.Command
import edu.mit.csail.sdg.ast.Module
import edu.mit.csail.sdg.translator.A4Solution
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * Swing layer: runs [solveCommand], then opens the Alloy visualizer on SAT or a small UNSAT dialog.
 */
fun launchVisualizer(
        module: Module,
        theme: Path,
        command: Command? = null,
        scenarioResult: RenderedScenario? = null,
        onAddAnotherConstraint: (() -> Unit)? = null,
) {
  val commandToRun =
          command
                  ?: module.allCommands.lastOrNull()
                          ?: throw IllegalArgumentException(
                          "The module does not contain any run commands."
                  )

  when (val outcome = solveCommand(module, commandToRun)) {
    is SolveOutcome.Unsat -> {
      println("UNSAT: ${outcome.command}")
      val unsatResult =
              scenarioResult?.copy(
                      naturalLanguage = "$UNSAT_BANNER\n\n${scenarioResult.naturalLanguage}",
              )
      SwingUtilities.invokeLater {
        showLauncherWindow("No instance found", unsatResult) { close ->
          listOf(
                  retryButton(close, onAddAnotherConstraint),
                  exitButton(close),
          )
        }
      }
    }
    is SolveOutcome.Sat -> {
      SwingUtilities.invokeLater {
        val visualizer = openVizGui(outcome.xmlPath, theme)
        val controller = SolutionController(outcome.xmlPath, visualizer, outcome.solution)

        showLauncherWindow("Instance Explorer", scenarioResult) { close ->
          listOf(
                  nextInstanceButton(controller),
                  allWorldsButton(outcome.xmlPath, theme),
                  retryButton(
                          close,
                          onAddAnotherConstraint,
                          beforeRetry = {
                            visualizer.frame?.also {
                              it.isVisible = false
                              it.dispose()
                            }
                          }
                  ),
                  exitButton(close),
          )
        }
      }
    }
  }
}

private fun showLauncherWindow(
        title: String,
        scenarioResult: RenderedScenario?,
        buttons: (close: () -> Unit) -> List<JButton>,
) {
  val preview = scenarioResult?.let { ConstraintPreviewPanel(it) }
  DialogShell.frame(
                  title = title,
                  content = preview,
                  alwaysOnTop = true,
                  buttons = buttons,
          )
          .apply {
            defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
            isVisible = true
          }
}

private fun nextInstanceButton(controller: SolutionController): JButton =
        JButton("Next instance").apply { addActionListener { isEnabled = controller.showNext() } }

private fun openVizGui(xmlPath: Path, theme: Path): VizGUI =
        VizGUI(
                        /* standalone = */ false,
                        /* xmlFileName = */ xmlPath.toString(),
                        /* windowmenu = */ null
                )
                .also {
                  it.loadThemeFile(theme.toString())
                  it.doShowViz()
                }

private fun allWorldsButton(xmlPath: Path, theme: Path): JButton =
        JButton("Open another window").apply { addActionListener { openVizGui(xmlPath, theme) } }

private fun retryButton(
        close: () -> Unit,
        onAddAnotherConstraint: (() -> Unit)?,
        beforeRetry: (() -> Unit)? = null,
): JButton =
        JButton("Run another scenario").apply {
          isEnabled = onAddAnotherConstraint != null
          addActionListener {
            close()
            beforeRetry?.invoke()
            onAddAnotherConstraint?.invoke()
          }
        }

private fun exitButton(close: () -> Unit): JButton =
        JButton("Exit").apply {
          addActionListener {
            close()
            System.exit(0)
          }
        }

private class SolutionController(
        private val xmlPath: Path,
        private val visualizer: VizGUI,
        initial: A4Solution,
) {
  private var current: A4Solution = initial

  fun showNext(): Boolean {
    val next = nextInstance(current, xmlPath) ?: return false
    current = next
    visualizer.loadXML(xmlPath.toString(), true)
    return current.satisfiable()
  }
}
