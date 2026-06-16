@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.example.scenarios

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class ScenarioDefinition(
    val id: String,
    val name: String,
    val category: String,
    val conflictGroups: List<String>,
    val reference: String? = null,
    val displayDescription: String,
    val alloyTemplate: String,
    val naturalLanguageTemplate: String,
    val parameters: List<Parameter> = emptyList(),
    val guards: ScenarioGuards? = null,
    val parameterConstraints: List<ParameterConstraint> = emptyList(),
)

@Serializable
@JsonClassDiscriminator("type")
sealed class Parameter {
    abstract val id: String
    abstract val name: String

    @Serializable
    @SerialName("int")
    data class IntInput(
        override val id: String,
        override val name: String,
        val min: Int? = null,
        val max: Int? = null,
        val default: Int? = null,
    ) : Parameter()

    @Serializable
    @SerialName("class_ref")
    data class ClassRef(
        override val id: String,
        override val name: String,
    ) : Parameter()

    @Serializable
    @SerialName("association")
    data class Association(
        override val id: String,
        override val name: String,
        val sourceClassParameter: String,
        val targetClassParameter: String,
    ) : Parameter()

    @Serializable
    @SerialName("enum")
    data class Option(
        override val id: String,
        override val name: String,
        val choices: List<OptionChoice>,
    ) : Parameter()
}

@Serializable
data class OptionChoice(
    val id: String,
    val name: String,
    val alloySnippet: String,
    val naturalLanguageSnippet: String,
    val parameters: List<Parameter> = emptyList(),
)

// Scope at which an extension must be non-empty to avoid vacuously-true instances
@Serializable
enum class NonEmptyScope {
    @SerialName("per_world")
    PER_WORLD,

    @SerialName("some_world")
    SOME_WORLD,
}

@Serializable
data class NonEmptyClasses(
    val scope: NonEmptyScope,
    val classes: List<String>,
)

//  Guards to prevent vacuously-true instances
@Serializable
data class ScenarioGuards(
    @Serializable(with = JsonPrimitiveAsStringSerializer::class)
    val worldScope: String? = null,
    val nonEmptyClasses: NonEmptyClasses? = null,
)

@Serializable
enum class ParameterConstraintKind {
    @SerialName("distinct")
    DISTINCT,
}

// Constraints on parameter choices, for now used to enforce disjointness of class parameters
@Serializable
data class ParameterConstraint(
    val type: ParameterConstraintKind,
    val params: List<String>,
)

// A scenario with its parameter slots filled in by the user, ready to be appended and run
data class RenderedScenario(
    val predicateText: String,
    val naturalLanguage: String,
    val alloyExpression: String,
    val scenarioId: String,
    val worldScope: Int = DEFAULT_WORLD_SCOPE,
)

/**
 * Deserializes a nullable String from either a JSON string or a JSON number.
 * Needed because ScenarioGuards.worldScope is encoded as a bare integer in some scenarios 
 * and as a template string in others.
 */
private object JsonPrimitiveAsStringSerializer :
    JsonTransformingSerializer<String?>(String.serializer().nullable) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive && !element.isString) JsonPrimitive(element.content) else element
}
