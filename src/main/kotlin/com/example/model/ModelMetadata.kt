package com.example.model

data class RelationEndpoint(
    val name: String,
    val source: String,
    val target: String,
    val targetAccessor: String,
)

interface ModelMetadata {
    /** Non-datatype, non-enumeration class names. Used for class_ref parameters and class-population guards. */
    fun domainClassNames(): List<String>

    /** Alloy-level names of every relation (World sig fields) */
    fun allRelationNames(): List<String>

    /** Relations that have a generated navigator fun. Used as ASSOCIATION parameter choices. */
    fun navigableRelations(): List<RelationEndpoint>

    /** Class names reachable from [mainClass] within [hops] undirected edges (inclusive). */
    fun neighborhood(
        mainClass: String,
        hops: Int,
    ): Set<String>
}
