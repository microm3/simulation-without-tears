package com.example.scenarios

/** A user-supplied parameter value: an int or a string (for [Parameter.Option], the [OptionChoice.id]). */
sealed class ParamValue {
    data class IntValue(val v: Int) : ParamValue()
    data class StringValue(val v: String) : ParamValue()
}

/** A scenario with its parameters filled in. [bindings] is a flat map of [Parameter.id] → [ParamValue]. */
data class ParameterisedScenario(
    val scenarioId: String,
    val bindings: Map<String, ParamValue>,
)

/** JSON-primitive representation of a [ParamValue] (an [Int] or a [String]). */
fun ParamValue.toPrimitive(): Any =
    when (this) {
        is ParamValue.IntValue -> v
        is ParamValue.StringValue -> v
    }

fun bindingsToPrimitives(bindings: Map<String, ParamValue>): Map<String, Any> =
    bindings.mapValues { (_, v) -> v.toPrimitive() }

fun bindingsFromPrimitives(primitives: Map<String, Any>): Map<String, ParamValue> =
    primitives.mapValues { (_, v) ->
        when (v) {
            is Int -> ParamValue.IntValue(v)
            is Number -> ParamValue.IntValue(v.toInt())
            is String -> ParamValue.StringValue(v)
            else -> error("unsupported binding value type for JSON round-trip: ${v::class.java.name}")
        }
    }
