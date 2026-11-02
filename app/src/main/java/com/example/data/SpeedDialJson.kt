package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SpeedDialJsonItem(
    val id: String,
    val type: String, // "link" or "folder"
    val name: String,
    val url: String? = null,
    val desc: String? = null,
    val icon: String? = null,
    val color: List<Int>? = null,
    val children: List<SpeedDialJsonItem>? = null
)

// Helper objects to convert between database entities and UI/JSON structures
object SpeedDialConverter {
    
    fun toDatabaseEntities(jsonItems: List<SpeedDialJsonItem>): List<SpeedDialEntity> {
        val entities = mutableListOf<SpeedDialEntity>()
        jsonItems.forEachIndexed { rootIndex, root ->
            val colorList = root.color ?: listOf(100, 100, 100)
            val r = colorList.getOrNull(0) ?: 100
            val g = colorList.getOrNull(1) ?: 100
            val b = colorList.getOrNull(2) ?: 100

            entities.add(
                SpeedDialEntity(
                    id = root.id,
                    type = root.type,
                    name = root.name,
                    url = root.url,
                    desc = root.desc,
                    icon = root.icon,
                    r = r,
                    g = g,
                    b = b,
                    parentId = null,
                    orderIndex = rootIndex
                )
            )

            root.children?.forEachIndexed { childIndex, child ->
                val childColorList = child.color ?: listOf(100, 100, 100)
                val cr = childColorList.getOrNull(0) ?: 100
                val cg = childColorList.getOrNull(1) ?: 100
                val cb = childColorList.getOrNull(2) ?: 100

                entities.add(
                    SpeedDialEntity(
                        id = child.id,
                        type = "link",
                        name = child.name,
                        url = child.url,
                        desc = child.desc,
                        icon = child.icon,
                        r = cr,
                        g = cg,
                        b = cb,
                        parentId = root.id,
                        orderIndex = childIndex
                    )
                )
            }
        }
        return entities
    }

    fun toJsonStructure(entities: List<SpeedDialEntity>): List<SpeedDialJsonItem> {
        val rootEntities = entities.filter { it.parentId == null }.sortedBy { it.orderIndex }
        return rootEntities.map { root ->
            if (root.type == "folder") {
                val childrenEntities = entities.filter { it.parentId == root.id }.sortedBy { it.orderIndex }
                val childrenJson = childrenEntities.map { child ->
                    SpeedDialJsonItem(
                        id = child.id,
                        type = "link",
                        name = child.name,
                        url = child.url,
                        desc = child.desc,
                        icon = child.icon,
                        color = listOf(child.r, child.g, child.b)
                    )
                }
                SpeedDialJsonItem(
                    id = root.id,
                    type = "folder",
                    name = root.name,
                    color = listOf(root.r, root.g, root.b),
                    children = childrenJson
                )
            } else {
                SpeedDialJsonItem(
                    id = root.id,
                    type = "link",
                    name = root.name,
                    url = root.url,
                    desc = root.desc,
                    icon = root.icon,
                    color = listOf(root.r, root.g, root.b)
                )
            }
        }
    }
}
