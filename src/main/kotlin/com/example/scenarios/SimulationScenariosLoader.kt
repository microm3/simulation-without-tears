package com.example.scenarios

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SimulationScenariosRoot(
    val conflictGroups: Map<String, String> = emptyMap(),
    val scenarios: List<ScenarioDefinition>,
)

private val json = Json { ignoreUnknownKeys = true }

object SimulationScenariosLoader {
    val conflictGroups: Map<String, String>
    val scenarios: List<ScenarioDefinition>

    init {
        val text =
            this::class.java
                .getResourceAsStream("/simulation_scenarios.json")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("simulation_scenarios.json not found on classpath")
        val root = json.decodeFromString<SimulationScenariosRoot>(text)
        conflictGroups = root.conflictGroups
        scenarios = root.scenarios
    }
}
