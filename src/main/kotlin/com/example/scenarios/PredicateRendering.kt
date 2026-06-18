package com.example.scenarios

import com.example.model.ModelMetadata

const val DEFAULT_WORLD_SCOPE = 3
const val DEFAULT_INT_SCOPE = 7
const val DEFAULT_ATOM_COUNT = 20

/**
 * Resolves Scenario.guards.worldScope, a literal int or a template like e.g. "<<world_count>>"
 * Returns null when the template is null or still contains an unresolved placeholder
 */
fun resolveWorldScope(
    template: String?,
    params: Map<String, Any>,
    modelMetadata: ModelMetadata?,
): Int? {
    if (template == null) return null
    template.trim().toIntOrNull()?.let {
        return it
    }
    return runCatching { ScenarioExpansion.renderScenarioTemplate(template, params, modelMetadata) }
        .getOrNull()?.trim()?.toIntOrNull()
}

/**
 * Maps each non-empty `class_ref` parameter named in `guards.nonEmptyClasses` to its declared scope.
 */
fun resolveGuards(
    scenario: ScenarioDefinition,
    alloyParams: Map<String, Any>,
): Map<String, NonEmptyScope> {
    val directive = scenario.guards?.nonEmptyClasses ?: return emptyMap()
    val singletons =
        directive.classes.mapNotNull { id ->
            val name = (alloyParams[id] as? String)?.trim().orEmpty()
            if (name.isEmpty()) null else mapOf(name to directive.scope)
        }
    return mergeGuards(singletons)
}

/**
 * One scenario's contribution to a (possibly combined) predicate
 */
data class CombinedPredicatePart(
    val scenarioId: String,
    val scenarioName: String,
    val naturalLanguage: String,
    val constraintExpression: String,
    val nonEmptyGuards: Map<String, NonEmptyScope>,
    val worldScope: Int?,
)

/**
 * Banner used in the rendered predicate and GUI preview
 */
fun combinedBanner(scenarioNames: List<String>): String = "[COMBINED CONSTRAINT: " + scenarioNames.joinToString(separator = " AND ") + "]"

/**
 * Renders [parts] into a single Alloy predicate plus its `run` command.
 * Multiple parts are conjoined and prefixed with [combinedBanner].
 */
fun formatPred(
    parts: List<CombinedPredicatePart>,
    intScope: Int = DEFAULT_INT_SCOPE,
    atomCount: Int = DEFAULT_ATOM_COUNT,
): String {
    require(parts.isNotEmpty()) { "formatPred requires at least one part" }
    val name = parts.joinToString("_") { it.scenarioId } + "_${System.currentTimeMillis()}"
    val nlLine = parts.joinToString(" AND ") { it.naturalLanguage }
    val header =
        if (parts.size == 1) {
            "--$nlLine"
        } else {
            "--${combinedBanner(parts.map { it.scenarioName })}\n--$nlLine"
        }
    val guardBlock = createNonEmptinessGuard(mergeGuards(parts.map { it.nonEmptyGuards }))
    val body = parts.joinToString("\n\n    ") { it.constraintExpression }
    val worldScope = (parts.mapNotNull { it.worldScope } + DEFAULT_WORLD_SCOPE).max()
    return "\n\n$header\npred $name {\n$guardBlock    $body\n}\n\n" +
        "run $name for $atomCount but $worldScope World, $intScope Int"
}

private fun mergeGuards(maps: List<Map<String, NonEmptyScope>>): Map<String, NonEmptyScope> {
    val merged = linkedMapOf<String, NonEmptyScope>()
    for (m in maps) {
        for ((k, v) in m) {
            merged[k] =
                if (merged[k] == NonEmptyScope.PER_WORLD || v == NonEmptyScope.PER_WORLD) {
                    NonEmptyScope.PER_WORLD
                } else {
                    NonEmptyScope.SOME_WORLD
                }
        }
    }
    return merged
}

private fun createNonEmptinessGuard(scopeByClass: Map<String, NonEmptyScope>): String {
    if (scopeByClass.isEmpty()) return ""
    val lines =
        scopeByClass.map { (cls, scope) ->
            when (scope) {
                NonEmptyScope.PER_WORLD -> "all w: World | some w.$cls"
                NonEmptyScope.SOME_WORLD -> "some w: World | some w.$cls"
            }
        }
    return "    -- non-emptiness guards (to avoid vacuous truths)\n    " +
        lines.joinToString("\n    ") +
        "\n\n"
}
