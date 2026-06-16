package com.example.gui

import com.example.scenarios.RenderedScenario
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.border.Border

/**
 * Panel with the substituted natural-language sentence above an opt-in toggle revealing the generated Alloy.
 * When [editable] is true the Alloy area is editable and [onUserEdit] fires after human changes only.
 */
class ConstraintPreviewPanel(
    initialResult: RenderedScenario? = null,
    editable: Boolean = false,
    private val onUserEdit: (() -> Unit)? = null,
) : JPanel(MigLayout("fillx, wrap 1, ins 0", "[grow,fill]")) {
    private val nlArea =
        JTextArea(6, 40).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = NL_BACKGROUND
            font = font.deriveFont(Font.PLAIN)
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
        }

    private val alloyArea =
        JTextArea(6, 40).apply {
            isEditable = editable
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        }

    private val alloyScroll = JScrollPane(alloyArea).apply { isVisible = false }

    private val toggle =
        JCheckBox("Show generated Alloy code", false).apply {
            addActionListener {
                alloyScroll.isVisible = isSelected
                revalidate()
                repaint()
            }
        }

    private var suppressEditEvents = false

    init {
        val nlScroll =
            JScrollPane(nlArea).apply {
                border = BorderFactory.createEmptyBorder()
                viewport.background = NL_BACKGROUND
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }
        add(
            JPanel(MigLayout("fill, ins 0")).apply {
                border = naturalLanguageBorder()
                background = NL_BACKGROUND
                add(nlScroll, "grow")
            },
            "growx",
        )
        add(
            JPanel(MigLayout("fillx, wrap 1, ins 4")).apply {
                border = BorderFactory.createTitledBorder("Generated Alloy")
                add(toggle, "growx")
                add(alloyScroll, "growx")
            },
            "growx",
        )

        if (editable && onUserEdit != null) {
            alloyArea.onTextChange { if (!suppressEditEvents) onUserEdit.invoke() }
        }

        if (initialResult != null) update(initialResult)
    }

    fun update(
        naturalLanguage: String,
        alloy: String,
    ) {
        suppressEditEvents = true
        try {
            nlArea.text = naturalLanguage
            nlArea.caretPosition = 0
            alloyArea.text = alloy
            alloyArea.caretPosition = 0
        } finally {
            suppressEditEvents = false
        }
    }

    fun update(result: RenderedScenario) = update(result.naturalLanguage, result.predicateText)

    fun setNaturalLanguage(text: String) {
        nlArea.text = text
        nlArea.caretPosition = 0
    }

    fun currentAlloyText(): String = alloyArea.text

    companion object {
        private val NL_BACKGROUND: Color = Color(0xE3, 0xF2, 0xFD)
        private val NL_BORDER: Color = Color(0x1E, 0x88, 0xE5)
        private const val NL_PANEL_TITLE: String = "Natural language constraint"

        fun naturalLanguageBorder(title: String = NL_PANEL_TITLE): Border =
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(NL_BORDER, 2), title)
    }
}
