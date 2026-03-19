package com.example.scenarios

/** Shown in the natural-language preview when the solver returned UNSAT. */
const val UNSAT_BANNER =
    "NO INSTANCE FOUND. The constraint may be unsatisfiable or contradict the base model."

/** Shown in the natural-language preview when the user has hand-edited the generated Alloy. */
const val EDITED_BANNER =
    "ALLOY CODE EDITED MANUALLY AFTER GENERATION. Description may no longer be accurate."

const val EDITED_FILE_MARKER =
    "--[EDITED MANUALLY AFTER GENERATION. Description may not match alloy code.]"

/** Inserts [EDITED_FILE_MARKER] right above the `pred` declaration in [predicateText] */
fun injectEditedMarker(predicateText: String): String {
    val lines = predicateText.lines().toMutableList()
    val predIdx = lines.indexOfFirst { it.trimStart().startsWith("pred ") }
    if (predIdx < 0) return "$EDITED_FILE_MARKER\n$predicateText"
    lines.add(predIdx, EDITED_FILE_MARKER)
    return lines.joinToString("\n")
}
