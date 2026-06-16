package com.example.scenarios

import com.example.model.ModelMetadata

/** Expands `<<placeholder>>` and `<<func(args)>>` references in scenario templates. */
object ScenarioExpansion {
    private val placeholderRegex = Regex("<<(.+?)>>")
    private val callRegex = Regex("^(\\w+)\\(([^)]*)\\)$")
    private val arithmeticRegex = Regex("^(\\w+)\\s*([+-])\\s*(\\d+)$")

    fun renderScenarioTemplate(
        template: String,
        params: Map<String, Any>,
        modelMetadata: ModelMetadata?,
    ): String {
        var rendered = template
        var before: String

        // placeholders may themselves include placeholders, loop until stable
        do {
            before = rendered
            rendered =
                placeholderRegex.replace(rendered) { match ->
                    evaluate(match.groupValues[1].trim(), params, modelMetadata)
                        ?: error("Unresolved placeholder: ${match.value}")
                }
        } while (rendered != before)
        return rendered
    }

    private fun evaluate(
        expression: String,
        params: Map<String, Any>,
        modelMetadata: ModelMetadata?,
    ): String? {
        params[expression]?.let { return it.toString() }
        val call = callRegex.matchEntire(expression) ?: return null
        val args = parseArgs(call.groupValues[2], params)
        return invoke(call.groupValues[1], args, modelMetadata)
    }

    private fun parseArgs(
        raw: String,
        params: Map<String, Any>,
    ): List<String> =
        raw.split(',').map(String::trim).filter(String::isNotEmpty).map { token ->
            params[token]?.toString()
                ?: arithmetic(token, params)
                ?: throw IllegalArgumentException("Unknown parameter: $token")
        }

    // Supports `n+1` / `n-1` style offsets on a single integer parameter.
    private fun arithmetic(
        token: String,
        params: Map<String, Any>,
    ): String? {
        val m = arithmeticRegex.matchEntire(token) ?: return null
        val base =
            (params[m.groupValues[1]])
                .toString()
                .toInt()
        val offset = m.groupValues[3].toInt()
        return (if (m.groupValues[2] == "+") base + offset else base - offset).toString()
    }

    /** Source of truth for the `<<func(...)>>` calls listed under `_TemplateFunctions` in schema.md. */
    private fun invoke(
        name: String,
        args: List<String>,
        modelMetadata: ModelMetadata?,
    ): String? =
        when (name) {
            "world_vars" -> {
                (1..args[0].toInt()).joinToString(", ") { "w$it" }
            }

            "world_chain" -> {
                (2..args[0].toInt()).joinToString(" and ") { "w$it in w${it - 1}.next" }
            }

            "x_vars" -> {
                (1..args[0].toInt()).joinToString(", ") { "x$it" }
            }

            "x_chain" -> {
                (2..args[0].toInt()).joinToString(" and ") { "x$it in ${args[1]}[x${it - 1}, w]" }
            }

            "all_classes_at_least" -> {
                val md = requireNotNull(modelMetadata) { "$name requires a ModelMetadata" }
                alloyAtLeastN(md.domainClassNames(), args.getOrNull(0)?.toInt() ?: 1)
            }

            "all_associations_at_least" -> {
                val md = requireNotNull(modelMetadata) { "$name requires a ModelMetadata" }
                alloyAtLeastN(md.allRelationNames(), args.getOrNull(0)?.toInt() ?: 1)
            }

            "neighbors_nonempty" -> {
                val md = requireNotNull(modelMetadata) { "$name requires a ModelMetadata" }
                val keep = md.neighborhood(args[0], args[1].toInt())
                alloyAtLeastN(md.domainClassNames().filter { it in keep }, 1)
            }

            else -> {
                null
            }
        }

    /** Creates a string "#w.<name> >= n and ..." */
    private fun alloyAtLeastN(
        names: List<String>,
        n: Int,
    ): String = if (names.isEmpty()) "no none" else names.joinToString(" and ") { "#w.$it >= $n" }
}
