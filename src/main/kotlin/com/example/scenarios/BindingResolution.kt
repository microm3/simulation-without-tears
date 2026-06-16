package com.example.scenarios

import com.example.model.RelationEndpoint

/** Resolved alloy/NL binding maps + the list of active [Parameter]s, produced by [resolveBindings]. */
data class ResolvedBindings(
    val alloyParams: Map<String, Any>,
    val nlParams: Map<String, Any>,
    val activeParams: List<Parameter>,
)

/** Resolves a [ParameterisedScenario] into alloy/NL maps and active params */
fun resolveBindings(
    scenario: ScenarioDefinition,
    parameterised: ParameterisedScenario,
    relations: List<RelationEndpoint>,
): ResolvedBindings {
    val alloy = LinkedHashMap<String, Any>()
    val nl = LinkedHashMap<String, Any>()
    val active = mutableListOf<Parameter>()
    walk(scenario.parameters, parameterised.bindings, relations, alloy, nl, active)
    return ResolvedBindings(alloy, nl, active)
}

private fun walk(
    params: List<Parameter>,
    bindings: Map<String, ParamValue>,
    relations: List<RelationEndpoint>,
    alloy: MutableMap<String, Any>,
    nl: MutableMap<String, Any>,
    active: MutableList<Parameter>,
) {
    for (param in params) {
        active.add(param)
        val value = bindings.getValue(param.id)
        when (param) {
            is Parameter.IntInput -> {
                val v = (value as ParamValue.IntValue).v
                alloy[param.id] = v
                nl[param.id] = v
            }

            is Parameter.ClassRef -> {
                val v = (value as ParamValue.StringValue).v
                alloy[param.id] = v
                nl[param.id] = v
            }

            is Parameter.Association -> {
                val name = (value as ParamValue.StringValue).v
                val accessor = relations.firstOrNull { it.name == name }?.targetAccessor ?: name
                alloy[param.id] = accessor
                nl[param.id] = name
            }

            is Parameter.Option -> {
                val choiceId = (value as ParamValue.StringValue).v
                val choice = param.choices.first { it.id == choiceId }
                alloy[param.id] = choice.alloySnippet
                nl[param.id] = choice.naturalLanguageSnippet
                walk(choice.parameters, bindings, relations, alloy, nl, active)
            }
        }
    }
}
