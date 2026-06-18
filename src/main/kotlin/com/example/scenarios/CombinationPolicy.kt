package com.example.scenarios

// Maximum allowed combined scenarios in the GUI
const val MAX_COMBINED_SCENARIOS = 5

// Two scenarios conflict iff they are distinct templates and their [Scenario.conflictGroups]
// lists share a group-id. 
object CombinationPolicy {
    private fun isPairwiseCombinable(
        a: ScenarioDefinition,
        b: ScenarioDefinition,
    ): Boolean = a.id == b.id || a.conflictGroups.intersect(b.conflictGroups).isEmpty()

    fun isCombinable(
        candidate: ScenarioDefinition,
        selected: List<ScenarioDefinition>,
    ): Boolean = selected.all { isPairwiseCombinable(it, candidate) }

    fun compatibleAdditions(
        selected: List<ScenarioDefinition>,
        all: List<ScenarioDefinition>,
    ): List<ScenarioDefinition> = all.filter { isCombinable(it, selected) }

    fun canCombineMore(selected: List<ScenarioDefinition>): Boolean = selected.size < MAX_COMBINED_SCENARIOS
}
