package com.example.gui

import com.example.model.RelationEndpoint
import com.example.scenarios.OptionChoice
import com.example.scenarios.ParamValue
import com.example.scenarios.Parameter
import net.miginfocom.swing.MigLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

data class ComboItem(
    val key: String,
    val label: String,
) {
    override fun toString(): String = label
}

/** One rendered parameter widget */
data class ParamSlot(
    val param: Parameter,
    val component: JComponent,
    val choices: List<OptionChoice>?,
    val childContainer: JPanel? = null,
    val childSlots: MutableList<ParamSlot> = mutableListOf(),
)

/** Renders [Parameter] definitions as widgets and retrieves them into a map of [ParamValue] */
class ParameterisationForm(
    private val classNames: List<String>,
    private val relations: List<RelationEndpoint>,
    private val onParamChanged: () -> Unit,
) {
    fun renderInto(
        params: List<Parameter>,
        container: JPanel,
        outSlots: MutableList<ParamSlot>,
    ) {
        for (param in params) {
            val widget = createWidget(param)
            container.add(JLabel("${param.name.ifEmpty { param.id }}:"))
            container.add(widget, "growx")

            if (param is Parameter.Option) {
                val childContainer = JPanel(MigLayout("fillx, wrap 2, ins 0 24 4 0", "[right][grow,fill]"))
                container.add(childContainer, "span 2, growx")
                val slot = ParamSlot(param, widget, param.choices, childContainer)
                outSlots.add(slot)
                rebuildOptionChildren(slot)

                @Suppress("UNCHECKED_CAST")
                (widget as JComboBox<ComboItem>).onSelect { rebuildOptionChildren(slot); onParamChanged() }
            } else {
                outSlots.add(ParamSlot(param, widget, choices = null))
                attachLivePreview(widget)
            }
        }
    }

    private fun rebuildOptionChildren(slot: ParamSlot) {
        val container = slot.childContainer ?: return
        container.removeAll()
        slot.childSlots.clear()
        @Suppress("UNCHECKED_CAST")
        val selectedKey = ((slot.component as JComboBox<ComboItem>).selectedItem as? ComboItem)?.key
        val childParams =
            slot.choices
                ?.firstOrNull { it.id == selectedKey }
                ?.parameters
                .orEmpty()
        if (childParams.isNotEmpty()) renderInto(childParams, container, slot.childSlots)
        container.revalidate()
        container.repaint()
    }

    private fun createWidget(param: Parameter): JComponent =
        when (param) {
            is Parameter.IntInput -> {
                JSpinner(
                    SpinnerNumberModel(
                        param.default ?: param.min ?: 1,
                        param.min ?: 0,
                        param.max ?: 100,
                        1,
                    ),
                )
            }

            is Parameter.Option -> {
                val items = param.choices.map { ComboItem(it.id, it.name.ifEmpty { it.id }) }
                JComboBox(items.toTypedArray())
            }

            is Parameter.ClassRef -> {
                if (classNames.isNotEmpty()) JComboBox(classNames.toTypedArray()) else JTextField(20)
            }

            is Parameter.Association -> {
                if (relations.isNotEmpty()) {
                    val items = relations.map { ComboItem(it.name, "${it.name} (${it.source} \u2192 ${it.target})") }
                    JComboBox(items.toTypedArray())
                } else {
                    JTextField(20)
                }
            }
        }

    private fun attachLivePreview(widget: JComponent) {
        when (widget) {
            is JComboBox<*> -> widget.addActionListener { onParamChanged() }
            is JSpinner -> widget.addChangeListener { onParamChanged() }
            is JTextField -> widget.onTextChange(onParamChanged)
        }
    }

    /** Collects current widget state into a flat map of [Parameter.id] to [ParamValue]. */
    fun collectBindings(slots: List<ParamSlot>): Map<String, ParamValue> {
        val out = LinkedHashMap<String, ParamValue>()
        fillBindings(slots, out)
        return out
    }

    private fun fillBindings(
        slots: List<ParamSlot>,
        out: MutableMap<String, ParamValue>,
    ) {
        for (slot in slots) {
            when (val param = slot.param) {
                is Parameter.IntInput -> {
                    val v = ((slot.component as JSpinner).value as Number).toInt()
                    out[param.id] = ParamValue.IntValue(v)
                }

                is Parameter.Option -> {
                    @Suppress("UNCHECKED_CAST")
                    val item = (slot.component as JComboBox<ComboItem>).selectedItem as? ComboItem ?: continue
                    out[param.id] = ParamValue.StringValue(item.key)
                    fillBindings(slot.childSlots, out)
                }

                is Parameter.ClassRef -> {
                    out[param.id] = ParamValue.StringValue(readValue(slot.component) { it.toString() })
                }

                is Parameter.Association -> {
                    out[param.id] =
                        ParamValue.StringValue(
                            readValue(slot.component) { (it as? ComboItem)?.key ?: it.toString() },
                        )
                }
            }
        }
    }

    private fun readValue(
        c: JComponent,
        fromCombo: (Any) -> String,
    ): String =
        when (c) {
            is JComboBox<*> -> c.selectedItem?.let(fromCombo).orEmpty()
            is JTextField -> c.text.trim()
            else -> ""
        }
}
