package com.example.scenarios

import com.example.model.ModelMetadata
import com.example.model.RelationEndpoint

/** Outcome of validating + generating a constraint from current parameter bindings. */
sealed class ScenarioBuildOutcome {
    data class Ok(
        val result: RenderedScenario,
    ) : ScenarioBuildOutcome()

    data class Invalid(
        val message: String,
        val scenarioId: String? = null,
        val parameterId: String? = null,
    ) : ScenarioBuildOutcome()
}

data class ValidationError(
    val message: String,
    val parameterId: String? = null,
)

/** Validates and renders a list of [ParameterisedScenario]s into a single [RenderedScenario]. */
fun buildScenarioConstraint(
    parameterised: List<ParameterisedScenario>,
    modelMetadata: ModelMetadata,
    scenarioLookup: (String) -> ScenarioDefinition = { id ->
        SimulationScenariosLoader.scenarios.first { it.id == id }
    },
): ScenarioBuildOutcome {
    require(parameterised.isNotEmpty()) { "buildScenarioConstraint requires at least one parameterised scenario" }
    val relations = modelMetadata.navigableRelations()

    val rendered =
        parameterised.map { ps ->
            val scenario = scenarioLookup(ps.scenarioId)
            val resolved = resolveBindings(scenario, ps, relations)

            val validationError =
                validateAssociationBindings(resolved.activeParams, resolved.alloyParams, relations)
                    ?: validateScenarioParameterConstraints(scenario, resolved.alloyParams)
            if (validationError != null) {
                return ScenarioBuildOutcome.Invalid(
                    message = "[${scenario.name}] ${validationError.message}",
                    scenarioId = scenario.id,
                    parameterId = validationError.parameterId,
                )
            }

            CombinedPredicatePart(
                scenarioId = scenario.id,
                scenarioName = scenario.name,
                naturalLanguage =
                    renderTemplate(scenario.naturalLanguageTemplate, resolved.nlParams, modelMetadata),
                constraintExpression =
                    renderTemplate(scenario.alloyTemplate, resolved.alloyParams, modelMetadata),
                nonEmptyGuards = resolveGuards(scenario, resolved.alloyParams),
                worldScope =
                    resolveWorldScope(
                        scenario.guards?.worldScope,
                        resolved.alloyParams,
                        modelMetadata,
                    ),
            )
        }

    return ScenarioBuildOutcome.Ok(
        RenderedScenario(
            predicateText = formatPred(rendered),
            naturalLanguage =
                rendered.joinToString(separator = " AND ") { it.naturalLanguage },
            alloyExpression =
                rendered.joinToString(separator = "\n\n") { it.constraintExpression },
            scenarioId = rendered.joinToString(separator = "_") { it.scenarioId },
            worldScope = (rendered.mapNotNull { it.worldScope } + DEFAULT_WORLD_SCOPE).max(),
        ),
    )
}

// Null if all association params bind to known (source, target, accessor) triples; error otherwise.
fun validateAssociationBindings(
    allParams: List<Parameter>,
    alloyParams: Map<String, Any>,
    relations: List<RelationEndpoint>,
): ValidationError? {
    if (relations.isEmpty()) return null
    for (param in allParams) {
        if (param !is Parameter.Association) continue
        val src = alloyParams[param.sourceClassParameter] as? String ?: continue
        val tgt = alloyParams[param.targetClassParameter] as? String ?: continue
        val assoc = alloyParams[param.id] as? String ?: continue
        if (relations.none { it.targetAccessor == assoc && it.source == src && it.target == tgt }) {
            return ValidationError(
                message = "association '$assoc' (parameter '${param.id}') does not connect $src to $tgt",
                parameterId = param.id,
            )
        }
    }
    return null
}

// Currently enforces only DISTINCT: bound class refs must be pairwise different.
fun validateScenarioParameterConstraints(
    scenario: ScenarioDefinition,
    alloyParams: Map<String, Any>,
): ValidationError? {
    for (constraint in scenario.parameterConstraints.orEmpty()) {
        when (constraint.type) {
            ParameterConstraintKind.DISTINCT -> {
                val values =
                    constraint.params.mapNotNull {
                        (alloyParams[it] as? String)?.trim()?.ifEmpty { null }
                    }
                if (values.size != values.toSet().size) {
                    return ValidationError(
                        message = "scenario '${scenario.id}' requires distinct values for ${constraint.params}",
                        parameterId = constraint.params.firstOrNull(),
                    )
                }
            }
        }
    }
    return null
}

private fun renderTemplate(
    template: String,
    params: Map<String, Any>,
    modelMetadata: ModelMetadata,
): String = ScenarioExpansion.renderScenarioTemplate(template, params, modelMetadata)
