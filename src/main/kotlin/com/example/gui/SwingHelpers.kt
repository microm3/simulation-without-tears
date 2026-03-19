package com.example.gui

import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.event.ItemEvent
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

/**
 * Panel that stretches to the host viewport width, so the outer scroll pane only needs a vertical
 * scrollbar.
 */
internal class ViewportTrackingPanel(layout: LayoutManager) : JPanel(layout), Scrollable {
  override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
  override fun getScrollableUnitIncrement(
          visibleRect: Rectangle,
          orientation: Int,
          direction: Int
  ): Int = 16
  override fun getScrollableBlockIncrement(
          visibleRect: Rectangle,
          orientation: Int,
          direction: Int
  ): Int = if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width
  override fun getScrollableTracksViewportWidth(): Boolean = true
  override fun getScrollableTracksViewportHeight(): Boolean = false
}

internal fun Container.setEnabledRecursively(enabled: Boolean) {
  for (child in components) {
    child.isEnabled = enabled
    if (child is Container) child.setEnabledRecursively(enabled)
  }
}

internal fun <T> JComboBox<T>.onSelect(action: () -> Unit) =
    addItemListener { if (it.stateChange == ItemEvent.SELECTED) action() }

internal fun JTextComponent.onTextChange(handler: () -> Unit) {
  document.addDocumentListener(
          object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = handler()
            override fun removeUpdate(e: DocumentEvent) = handler()
            override fun changedUpdate(e: DocumentEvent) = handler()
          }
  )
}
