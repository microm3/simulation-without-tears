package com.example.transformation

import com.example.model.ModelMetadata
import com.example.model.RelationEndpoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class ClassEndpointInfo(
    val name: String? = null,
    val alloyName: String,
)

@Serializable
data class TransformationClassInfo(
    val id: String,
    val name: String? = null,
    val alloyName: String,
    val stereotype: String? = null,
    val natures: List<String>,
)

@Serializable
data class TransformationRelationInfo(
    val id: String,
    val name: String? = null,
    val alloyName: String,
    val stereotype: String? = null,
    val source: ClassEndpointInfo,
    val target: ClassEndpointInfo,
    // holds the name of the generated targetAccessor function to return the target-ends of a
    // source instance
    val targetAccessor: String? = null,
    // holds the name of the generated sourceAccessor function to return the source-ends of a
    // target instance
    val sourceAccessor: String? = null,
)

@Serializable
data class TransformationGeneralizationInfo(
    val specific: String,
    val general: String,
)

@Serializable
data class TransformationMetadata(
    val classes: List<TransformationClassInfo>,
    val relations: List<TransformationRelationInfo>,
    val generalizations: List<TransformationGeneralizationInfo>? = null,
) : ModelMetadata {
    private val endurantClasses: List<TransformationClassInfo>
        get() = classes.filter { it.stereotype != "datatype" && it.stereotype != "enumeration" }

    override fun domainClassNames(): List<String> = endurantClasses.map { it.alloyName }

    override fun allRelationNames(): List<String> = relations.map { it.alloyName }

    override fun navigableRelations(): List<RelationEndpoint> =
        relations.mapNotNull { rel ->
            if (rel.targetAccessor.isNullOrBlank()) return@mapNotNull null
            RelationEndpoint(
                name = rel.alloyName,
                source = rel.source.alloyName,
                target = rel.target.alloyName,
                targetAccessor = rel.targetAccessor,
            )
        }

    private fun endurantNeighborMap(): Map<String, Set<String>> {
        val endurantNames = endurantClasses.map { it.alloyName }.toSet()

        val neighborSetsByNode =
            (
                relations.map { it.source.alloyName to it.target.alloyName } +
                    generalizations.orEmpty().map { it.specific to it.general }
            ).flatMap { (a, b) ->
                // Each undirected link becomes two (from, to) tuples so groupBy builds mutual adjacency
                if (a == b || a !in endurantNames || b !in endurantNames) {
                    emptyList()
                } else {
                    listOf(a to b, b to a)
                }
            }.groupBy({ it.first }, { it.second })
                .mapValues { (_, neighbors) -> neighbors.toSet() }
        return endurantNames.associateWith { neighborSetsByNode[it] ?: emptySet() }
    }

    override fun neighborhood(
        mainClass: String,
        hops: Int,
    ): Set<String> {
        if (hops < 0) return emptySet()
        val adjacency = endurantNeighborMap()
        if (mainClass !in adjacency) return emptySet()

        val visited = linkedSetOf(mainClass)
        var frontier: Set<String> = setOf(mainClass)
        repeat(hops) {
            val next = mutableSetOf<String>()
            for (node in frontier) {
                for (nbr in adjacency.getValue(node)) {
                    if (visited.add(nbr)) next.add(nbr)
                }
            }
            if (next.isEmpty()) return visited
            frontier = next
        }
        return visited
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromPath(path: Path): TransformationMetadata = json.decodeFromString(Files.readString(path))
    }
}
