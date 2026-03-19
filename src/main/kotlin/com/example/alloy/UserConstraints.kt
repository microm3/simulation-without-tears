package com.example.alloy

import com.example.common.MAIN_ALS_FILE
import com.example.common.USER_CONSTRAINTS_FILE
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Appends [constraintText] to `user_constraints.als` in [modelDir]. On first use the file is
 * created copying `main.als`; the reason main is not simply imported is to avoid `main/` prefix on
 * all visualizer atom labels.
 */
fun appendUserConstraint(
    modelDir: Path,
    constraintText: String,
): Path {
    val target = modelDir.resolve(USER_CONSTRAINTS_FILE)
    target.writeText(composedContent(modelDir, constraintText))
    return target
}

/**
 * Parse check of user edited alloy predicate. Returns null if [constraintText] would produce a
 * valid Alloy file, a user-facing error message if not.
 */
fun parseCheckConstraint(
    modelDir: Path,
    constraintText: String,
): String? {
    val testFile = Files.createTempFile(modelDir, "user_constraints_preview", ".als")
    return try {
        testFile.writeText(composedContent(modelDir, constraintText))
        parseAlloyModule(testFile)
        null
    } catch (e: Throwable) {
        "Message from the Alloy Analyzer: \n\n${e.message ?: e.javaClass.simpleName}"
    } finally {
        Files.deleteIfExists(testFile)
    }
}

private fun composedContent(
    modelDir: Path,
    constraintText: String,
): String {
    val target = modelDir.resolve(USER_CONSTRAINTS_FILE)
    val mainContent = modelDir.resolve(MAIN_ALS_FILE).readText()
    val existing = target.takeIf { it.exists() }?.readText()
    val seed = existing?.takeIf { it.startsWith(mainContent) } ?: (mainContent + "\n")
    return "$seed$constraintText\n"
}

/** Natural-language comment and predicate body recovered from a previously appended constraint. */
data class StoredUserConstraint(
    val naturalLanguage: String,
    val predicateBody: String,
)

/**
 * Best-effort lookup of the original nl comment and predicate body for [predicateName] in
 * `user_constraints.als`.
 */
fun lookupStoredUserConstraint(
    file: Path,
    predicateName: String,
): StoredUserConstraint? {
    if (!file.exists()) return null
    val lines = file.readLines()
    val predIdx = lines.indexOfFirst { it.trim() == "pred $predicateName {" }
    if (predIdx < 0) return null

    val nl =
        lines.subList(0, predIdx).takeLastWhile { it.trim().startsWith("--") }.joinToString(
            "\n",
        ) { it.trim().removePrefix("--").trim() }

    var depth = 1
    val body =
        lines
            .subList(predIdx + 1, lines.size)
            .takeWhile { line ->
                depth += line.count { it == '{' } - line.count { it == '}' }
                depth > 0
            }.joinToString("\n")
            .trimEnd()

    return StoredUserConstraint(naturalLanguage = nl, predicateBody = body)
}
