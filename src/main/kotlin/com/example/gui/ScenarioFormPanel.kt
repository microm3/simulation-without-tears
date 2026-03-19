package com.example.gui

import com.example.scenarios.ParamValue
import com.example.scenarios.ScenarioDefinition
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.UIManager

/** Panel for selecting a scenario and filling in its parameters. */
internal class ScenarioFormPanel(
    private val availableScenarios: List<ScenarioDefinition>,
    title: String,
    private val rendering: ParameterisationForm,
    private val onChange: () -> Unit,
) : JPanel(MigLayout("fill, wrap 1, ins 0", "[grow,fill]")) {
    private val categoryCombo = JComboBox<ComboItem>()
    private val scenarioCombo = JComboBox<ComboItem>()
    private val descriptionLabel =
        JTextArea(" ").apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = UIManager.getColor("Panel.background")
            foreground = Color.DARK_GRAY
            font = font.deriveFont(Font.ITALIC)
            border = BorderFactory.createEmptyBorder()
        }
    private val paramPanel = JPanel(MigLayout("fillx, wrap 2, ins 6", "[right][grow,fill]"))
    private val rootSlots = mutableListOf<ParamSlot>()

    init {
        availableScenarios
            .map { it.category }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .forEach { categoryCombo.addItem(ComboItem(it, it)) }
        populateScenarios()
        rebuildParams()
        updateDescription()

        categoryCombo.onSelect { populateScenarios(); rebuildParams(); updateDescription(); onChange() }
        scenarioCombo.onSelect { rebuildParams(); updateDescription(); onChange() }

        border = BorderFactory.createTitledBorder(title)
        add(buildSelectionPanel(), "growx")
        add(buildParamSection(), "growx")
    }

    fun selectedScenario(): ScenarioDefinition? {
        val key = (scenarioCombo.selectedItem as? ComboItem)?.key ?: return null
        return availableScenarios.firstOrNull { it.id == key }
    }

    fun collectBindings(): Map<String, ParamValue> = rendering.collectBindings(rootSlots)

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        categoryCombo.isEnabled = enabled
        scenarioCombo.isEnabled = enabled
        paramPanel.setEnabledRecursively(enabled)
    }

    private fun populateScenarios() {
        val catKey = (categoryCombo.selectedItem as? ComboItem)?.key ?: return
        scenarioCombo.removeAllItems()
        availableScenarios
            .filter { it.category == catKey }
            .forEach { scenarioCombo.addItem(ComboItem(it.id, it.name)) }
    }

    private fun updateDescription() {
        descriptionLabel.text = selectedScenario()?.displayDescription ?: " "
    }

    private fun rebuildParams() {
        paramPanel.removeAll()
        rootSlots.clear()
        val scenario = selectedScenario() ?: return
        val params = scenario.parameters
        if (params.isEmpty()) {
            paramPanel.add(JLabel("(no parameters for this scenario)"), "span 2, growx")
        } else {
            rendering.renderInto(params, paramPanel, rootSlots)
        }
        paramPanel.revalidate()
        paramPanel.repaint()
    }

    private fun buildSelectionPanel(): JPanel =
        JPanel(MigLayout("fillx, wrap 2", "[right][grow,fill]")).apply {
            add(JLabel("Category:"))
            add(categoryCombo, "growx")
            add(JLabel("Scenario:"))
            add(scenarioCombo, "growx")
            add(descriptionLabel, "span 2, growx")
        }

    private fun buildParamSection(): JPanel =
        JPanel(MigLayout("fill, ins 0")).apply {
            border = BorderFactory.createTitledBorder("Parameters")
            add(JScrollPane(paramPanel).apply { border = BorderFactory.createEmptyBorder() }, "grow, h 100:200:")
        }
}
