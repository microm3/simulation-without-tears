package com.example.gui

import com.example.model.ModelMetadata
import com.example.scenarios.CombinationPolicy
import com.example.scenarios.EDITED_BANNER
import com.example.scenarios.RenderedScenario
import com.example.scenarios.ScenarioBuildOutcome
import com.example.scenarios.ScenarioDefinition
import com.example.scenarios.ParameterisedScenario
import com.example.scenarios.SimulationScenariosLoader
import com.example.scenarios.buildScenarioConstraint
import com.example.scenarios.combinedBanner
import com.example.scenarios.injectEditedMarker
import net.miginfocom.swing.MigLayout
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

data class RunCommandOption(val label: String, val displayText: String)

sealed class ScenarioOutcome {
    data class Apply(
        val result: RenderedScenario,
    ) : ScenarioOutcome()

    data class UseExisting(
        val commandLabel: String,
    ) : ScenarioOutcome()
}

/**
 * Modal dialog: pick (and optionally combine up to [com.example.scenarios.MAX_COMBINED_SCENARIOS]) scenarios,
 * fill parameters, see a live NL preview, and return a [ScenarioOutcome]. 
 * [parseCheck] runs only on Apply when the user manually edited the Alloy code.
 */
class ScenarioDialog private constructor(
    private val modelMetadata: ModelMetadata,
    private val existingRunCommands: List<RunCommandOption>,
    private val parseCheck: ((String) -> String?)?,
) {
    companion object {
        fun show(
            modelMetadata: ModelMetadata,
            existingRunCommands: List<RunCommandOption> = emptyList(),
            parseCheck: ((String) -> String?)? = null,
        ): ScenarioOutcome? = ScenarioDialog(modelMetadata, existingRunCommands, parseCheck).run()
    }

    private val scenarios = SimulationScenariosLoader.scenarios
    private val rendering =
        ParameterisationForm(
            classNames = modelMetadata.domainClassNames(),
            relations = modelMetadata.navigableRelations(),
            onParamChanged = { refreshPreview() },
        )

    private val previewPanel = ConstraintPreviewPanel(editable = true, onUserEdit = { onAlloyUserEdit() })

    private val panels = mutableListOf<ScenarioFormPanel>()
    private val panelsContainer = JPanel(MigLayout("fillx, wrap 1, ins 0", "[grow,fill]"))
    private val combineRow = JPanel(MigLayout("fillx, ins 4 6 4 6", "[grow,fill][]"))

    private val useExistingBtn =
        JButton("Use previous constraint").apply {
            toolTipText = "Pick from the run commands already in user_constraints.als"
        }
    private val revertBtn =
        JButton("Revert edits").apply {
            isVisible = false
            toolTipText = "Discard hand edits and re-enable the form"
        }

    private var outcome: ScenarioOutcome? = null
    private var editing = false
    private var lastGeneratedNl = ""

    private fun run(): ScenarioOutcome? {
        val applyBtn = JButton("Run")
        val cancelBtn = JButton("Cancel")
        val canPickExisting = existingRunCommands.size > 1

        addPrimaryPanel()
        refreshCombineRow()
        refreshPreview()

        val inner =
            ViewportTrackingPanel(MigLayout("fillx, wrap 1, ins 0", "[grow,fill]")).apply {
                if (canPickExisting) add(buildExistingBanner(), "growx")
                add(panelsContainer, "growx")
                add(combineRow, "growx")
                add(previewPanel, "growx")
            }
        val content =
            JScrollPane(inner).apply {
                border = BorderFactory.createEmptyBorder()
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBar.unitIncrement = 16
            }

        val dialog =
            DialogShell.modal(
                title = "Simulation Scenario Selector",
                content = content,
                defaultButton = applyBtn,
                onCancel = { outcome = null },
                leadingButtons = { listOf(revertBtn) },
            ) { close ->
                applyBtn.addActionListener { onApply(applyBtn, close) }
                revertBtn.addActionListener { exitEditMode() }
                useExistingBtn.addActionListener {
                    pickRunCommand()?.let {
                        outcome = ScenarioOutcome.UseExisting(it.label)
                        close()
                    }
                }
                cancelBtn.addActionListener {
                    outcome = null
                    close()
                }
                listOf(cancelBtn, applyBtn)
            }
        dialog.isVisible = true
        return outcome
    }

    private fun onApply(
        parent: JComponent,
        close: () -> Unit,
    ) {
        if (editing) {
            val edited = buildEditedResult()
            val error = parseCheck?.invoke(edited.predicateText)
            if (error != null) {
                JOptionPane.showMessageDialog(parent, error, "Edited Alloy did not parse", JOptionPane.ERROR_MESSAGE)
                return
            }
            outcome = ScenarioOutcome.Apply(edited)
            close()
            return
        }
        val parameterised = collectParameterisedScenarios() ?: return
        when (val build = buildScenarioConstraint(parameterised, modelMetadata)) {
            is ScenarioBuildOutcome.Invalid -> {
                JOptionPane.showMessageDialog(parent, build.message, "Invalid parameter binding", JOptionPane.PLAIN_MESSAGE)
            }

            is ScenarioBuildOutcome.Ok -> {
                outcome = ScenarioOutcome.Apply(build.result)
                close()
            }
        }
    }

    private fun onAlloyUserEdit() {
        if (editing) return
        editing = true
        setFormEnabled(false)
        revertBtn.isVisible = true
        previewPanel.setNaturalLanguage("$EDITED_BANNER\n\n$lastGeneratedNl")
    }

    private fun exitEditMode() {
        editing = false
        setFormEnabled(true)
        revertBtn.isVisible = false
        refreshPreview()
    }

    private fun setFormEnabled(enabled: Boolean) {
        useExistingBtn.isEnabled = enabled
        combineRow.setEnabledRecursively(enabled)
        if (enabled) {
            refreshPanelEnablement()
        } else {
            panels.forEach { it.setEnabled(false) }
        }
    }

    private fun buildEditedResult(): RenderedScenario {
        val edited = previewPanel.currentAlloyText()
        val combinedId =
            panels
                .mapNotNull { it.selectedScenario()?.id }
                .joinToString("_")
                .ifEmpty { "edited" }
        return RenderedScenario(
            predicateText = injectEditedMarker(edited),
            naturalLanguage = "$EDITED_BANNER\n\n$lastGeneratedNl",
            alloyExpression = edited,
            scenarioId = combinedId,
        )
    }

    private fun buildExistingBanner(): JPanel =
        JPanel(MigLayout("fillx, ins 6 8 6 8", "[grow,fill][]")).apply {
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY)
            add(JLabel("Already added a constraint?"), "growx")
            add(useExistingBtn)
        }

    private fun addPrimaryPanel() {
        addPanel(ScenarioFormPanel(scenarios, titleFor(0), rendering, ::refreshPreview))
    }

    private fun addCombinedPanel() {
        val additions = CombinationPolicy.compatibleAdditions(selectedScenarios(), scenarios)
        addPanel(ScenarioFormPanel(additions, titleFor(panels.size), rendering, ::refreshPreview))
        refreshAfterPanelChange()
    }

    private fun removeLastCombinedPanel() {
        panels.removeAt(panels.lastIndex).also { panelsContainer.remove(it) }
        refreshAfterPanelChange()
        panelsContainer.revalidate()
        panelsContainer.repaint()
    }

    private fun refreshAfterPanelChange() {
        refreshPanelEnablement()
        refreshCombineRow()
        refreshPreview()
    }

    private fun addPanel(panel: ScenarioFormPanel) {
        panels += panel
        panelsContainer.add(panel, "growx")
        panelsContainer.revalidate()
        panelsContainer.repaint()
    }

    // only the last panel is editable
    private fun refreshPanelEnablement() {
        if (editing) {
            panels.forEach { it.setEnabled(false) }
            return
        }
        panels.forEachIndexed { idx, panel -> panel.setEnabled(idx == panels.lastIndex) }
    }

    private fun refreshCombineRow() {
        combineRow.removeAll()
        val selected = selectedScenarios()
        val canAdd =
            CombinationPolicy.canCombineMore(selected) &&
                CombinationPolicy.compatibleAdditions(selected, scenarios).isNotEmpty()
        if (canAdd) {
            combineRow.add(
                JButton("Combine with another constraint").apply {
                    toolTipText = "Add a second scenario whose target/property differs from the ones above; " +
                        "their bodies will be merged into a single Alloy predicate."
                    addActionListener { addCombinedPanel() }
                },
                "growx",
            )
        } else {
            combineRow.add(JLabel(""), "growx")
        }
        if (panels.size > 1) {
            combineRow.add(
                JButton("Remove last combination").apply {
                    toolTipText = "Drop the most recently added scenario from this combined constraint."
                    addActionListener { removeLastCombinedPanel() }
                },
            )
        }
        if (editing) combineRow.setEnabledRecursively(false)
        combineRow.revalidate()
        combineRow.repaint()
    }

    private fun selectedScenarios(): List<ScenarioDefinition> = panels.mapNotNull { it.selectedScenario() }

    private fun titleFor(index: Int): String = if (index == 0) "Scenario" else "Combine with: scenario ${index + 1}"

    private fun pickRunCommand(): RunCommandOption? {
        val commands = existingRunCommands
        if (commands.isEmpty()) return null
        val list =
            JList(commands.map { it.displayText }.toTypedArray()).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                selectedIndex = commands.size - 1
                ensureIndexIsVisible(commands.size - 1)
                visibleRowCount = minOf(commands.size, 10)
            }
        var chosen: RunCommandOption? = null
        val select = JButton("Select")
        val dialog =
            DialogShell.modal(
                title = "Select run command",
                content = JScrollPane(list),
                defaultButton = select,
            ) { close ->
                select.addActionListener {
                    chosen = list.selectedIndex.takeIf { it in commands.indices }?.let { commands[it] }
                    close()
                }
                listOf(select, JButton("Cancel").apply { addActionListener { close() } })
            }
        dialog.isVisible = true
        return chosen
    }

    private fun collectParameterisedScenarios(): List<ParameterisedScenario>? =
        panels.map { panel ->
            val scenario = panel.selectedScenario() ?: return null
            ParameterisedScenario(scenario.id, panel.collectBindings())
        }

    private fun refreshPreview() {
        if (editing) return
        val parameterised = collectParameterisedScenarios()
        val (nl, alloy) =
            if (parameterised.isNullOrEmpty()) {
                "(no scenario selected)" to "(no constraint)"
            } else {
                when (val build = buildScenarioConstraint(parameterised, modelMetadata)) {
                    is ScenarioBuildOutcome.Invalid -> {
                        "ERROR: ${build.message}" to "-- ERROR: ${build.message}"
                    }

                    is ScenarioBuildOutcome.Ok -> {
                        val selectedNames = selectedScenarios().map { it.name }
                        val displayedNl =
                            if (selectedNames.size > 1) {
                                combinedBanner(selectedNames) + "\n" + build.result.naturalLanguage
                            } else {
                                build.result.naturalLanguage
                            }
                        displayedNl to build.result.predicateText
                    }
                }
            }
        lastGeneratedNl = nl
        previewPanel.update(nl, alloy)
    }
}
