package com.example.gui

import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

fun pickModelFile(): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select OntoUML model (.json) or transformed Alloy model (.als)"
        fileFilter = FileNameExtensionFilter("OntoUML JSON or Alloy model", "json", "als")
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
        println("No model selected, exiting.")
        return null
    }
    return chooser.selectedFile.toPath().toAbsolutePath().normalize()
}

fun promptAllowAbstractLeaves(): Boolean =
    JOptionPane.showOptionDialog(
        null,
        "Allow instantiation of abstract leaf classes (e.g. Category or Mixin)?",
        "Transformation options",
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null,
        arrayOf("Allow", "Disallow"),
        null,
    ) == 0
