package com.example.gui

import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.WindowConstants

/**
 * Shared window builder for every top-level surface in the app (ScenarioDialog, VisualizerFlow)
 */
object DialogShell {
    fun modal(
        title: String,
        content: JComponent? = null,
        defaultButton: JButton? = null,
        minimumSize: Dimension? = null,
        onCancel: (() -> Unit)? = null,
        leadingButtons: (close: () -> Unit) -> List<JButton> = { emptyList() },
        buttons: (close: () -> Unit) -> List<JButton>,
    ): JDialog {
        val dialog =
            JDialog(null as Frame?, title, true).apply {
                defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            }
        val close: () -> Unit = { dialog.dispose() }
        dialog.contentPane = wrap(content, leadingButtons(close), buttons(close))
        defaultButton?.let { dialog.rootPane.defaultButton = it }
        dialog.rootPane.registerKeyboardAction(
            {
                onCancel?.invoke()
                dialog.dispose()
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
        dialog.pack()
        if (minimumSize != null) dialog.minimumSize = minimumSize
        dialog.setLocationRelativeTo(null)
        return dialog
    }

    fun frame(
        title: String,
        content: JComponent? = null,
        alwaysOnTop: Boolean = false,
        minimumSize: Dimension? = null,
        leadingButtons: (close: () -> Unit) -> List<JButton> = { emptyList() },
        buttons: (close: () -> Unit) -> List<JButton>,
    ): JFrame {
        val frame =
            JFrame(title).apply {
                defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                isAlwaysOnTop = alwaysOnTop
            }
        val close: () -> Unit = { frame.dispose() }
        frame.contentPane = wrap(content, leadingButtons(close), buttons(close))
        frame.pack()
        if (minimumSize != null) frame.minimumSize = minimumSize
        frame.setLocationRelativeTo(null)
        return frame
    }

    private fun wrap(
        content: JComponent?,
        leading: List<JButton>,
        trailing: List<JButton>,
    ): JPanel =
        JPanel(MigLayout("fill, wrap 1, ins 12", "[grow,fill]")).apply {
            if (content != null) add(content, "grow, push")
            add(buttonRow(leading, trailing), "growx")
        }

    private fun buttonRow(
        leading: List<JButton>,
        trailing: List<JButton>,
    ): JPanel =
        JPanel(MigLayout("ins 4, fillx", "[]push[]")).apply {
            add(buttonGroup(leading))
            add(buttonGroup(trailing))
        }

    private fun buttonGroup(buttons: List<JButton>): JPanel =
        JPanel(MigLayout("ins 0", "[]8[]")).apply {
            buttons.forEach(::add)
        }
}
