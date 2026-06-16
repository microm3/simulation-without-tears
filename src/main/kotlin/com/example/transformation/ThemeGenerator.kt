package com.example.transformation

/**
 * Generates a per-model Alloy visualizer theme (.thm) that as closely as possible matches the VP
 * OntoUML plugin's (https://github.com/OntoUML/ontouml-vp-plugin) SmartColoringUtils coloring
 * scheme, given Alloy's fixed palette of seven colours.
 */
object ThemeGenerator {
    private val COLOR_MAP: Map<String, String> =
        mapOf(
            "functional-complex" to "Red",
            "collective" to "Red",
            "quantity" to "Red",
            "relator" to "Green",
            "intrinsic-mode" to "Blue",
            "extrinsic-mode" to "Blue",
            "quality" to "Blue",
            "event" to "Yellow",
            "situation" to "Yellow",
            "abstract" to "White",
            "type" to "Blue",
        )

    private val ASPECT_STEREOTYPES: Set<String> =
        setOf(
            "relator",
            "quality",
            "mode",
            "intrinsicMode",
            "extrinsicMode",
            "intrinsic-mode",
            "extrinsic-mode",
        )
    private val DATATYPE_STEREOTYPES: Set<String> = setOf("datatype", "enumeration")

    private fun alloyParentSig(cls: TransformationClassInfo): String =
        when {
            cls.stereotype != null && cls.stereotype in DATATYPE_STEREOTYPES -> "Datatype"
            cls.stereotype != null && cls.stereotype in ASPECT_STEREOTYPES -> "Aspect"
            else -> "Object"
        }

    private const val NON_SPECIFIC_COLOR = "Gray"

    fun getDomainClassColor(domainClass: TransformationClassInfo): String? {
        val stereotype = domainClass.stereotype ?: return null
        if (stereotype in DATATYPE_STEREOTYPES) return null

        val natures = domainClass.natures

        // If all mapped natures collapse to one color, use it; otherwise use gray
        val distinctMappedColors = natures.mapNotNull { COLOR_MAP[it] }.toSet()
        return when (distinctMappedColors.size) {
            1 -> distinctMappedColors.first()
            else -> NON_SPECIFIC_COLOR
        }
    }

    fun generate(metadata: TransformationMetadata): String {
        data class NodeKey(
            val color: String,
            val shape: String,
            val parentSig: String,
        )

        val coloredNodes = mutableMapOf<NodeKey, MutableList<TransformationClassInfo>>()
        for (cls in metadata.classes) {
            val color = getDomainClassColor(cls) ?: continue
            val parentSig = alloyParentSig(cls)
            val shape = "Box"
            coloredNodes.getOrPut(NodeKey(color, shape, parentSig)) { mutableListOf() }.add(cls)
        }

        return buildString {
            appendLine(
                """
                <?xml version="1.0"?>
                <alloy>

                <view nodetheme="Classic">

                <projection> <type name="World"/> </projection>

                <defaultnode hideunconnected="yes"/>

                <defaultedge/>

                """.trimIndent(),
            )

            // Datatypes: white boxes
            appendLine(
                """
                <node shape="Box" color="White">
                   <type name="Datatype"/>
                </node>

                """.trimIndent(),
            )

            // Domain classes
            for (
            (key, classes) in
            coloredNodes.entries.sortedWith(
                compareBy({ it.key.color }, { it.key.parentSig }),
            )
            ) {
                appendLine("""<node shape="${key.shape}" color="${key.color}">""")
                for (cls in classes.sortedBy { it.alloyName }) {
                    appendLine("""   <set name="${cls.alloyName}" type="${key.parentSig}"/>""")
                }
                appendLine("""</node>""")
                appendLine()
            }

            // exists relation: render atoms but suppress their label to reduce visual clutter
            appendLine(
                """
                <node visible="yes" hideunconnected="no" showlabel="no">
                   <set name="exists" type="Endurant"/>
                </node>

                </view>

                </alloy>
                """.trimIndent(),
            )
        }
    }
}
