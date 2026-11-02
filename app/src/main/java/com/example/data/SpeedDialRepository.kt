package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

class SpeedDialRepository(private val dao: SpeedDialDao) {

    val allItems: Flow<List<SpeedDialEntity>> = dao.getAllItems()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(List::class.java, SpeedDialJsonItem::class.java)
    private val adapter = moshi.adapter<List<SpeedDialJsonItem>>(listType)

    suspend fun insertItem(item: SpeedDialEntity) {
        dao.insertItem(item)
    }

    suspend fun updateItem(item: SpeedDialEntity) {
        dao.updateItem(item)
    }

    suspend fun deleteItemById(id: String) {
        dao.deleteItemById(id)
        dao.deleteByParentId(id)
    }

    suspend fun deleteChildFromFolder(childId: String, folderId: String) {
        dao.deleteItemById(childId)
    }
    
    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun importJson(jsonString: String): Boolean {
        return try {
            val jsonItems = adapter.fromJson(jsonString)
            if (jsonItems != null) {
                dao.clearAll()
                val entities = SpeedDialConverter.toDatabaseEntities(jsonItems)
                dao.insertItems(entities)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun exportJson(entities: List<SpeedDialEntity>): String {
        val jsonStructure = SpeedDialConverter.toJsonStructure(entities)
        return adapter.indent("  ").toJson(jsonStructure) ?: "[]"
    }

    suspend fun loadDefaultData() {
        val defaultJson = """
        [
          {
            "id": "t1",
            "type": "link",
            "name": "GOOGLE",
            "url": "https://www.google.com",
            "desc": "Motore di ricerca",
            "icon": "https://www.google.com/s2/favicons?sz=128&domain=google.com",
            "color": [0, 242, 255]
          },
          {
            "id": "t2",
            "type": "link",
            "name": "GITHUB",
            "url": "https://github.com",
            "desc": "Sviluppo software",
            "icon": "https://www.google.com/s2/favicons?sz=128&domain=github.com",
            "color": [24, 28, 37]
          },
          {
            "id": "f1",
            "type": "folder",
            "name": "SOCIAL",
            "color": [130, 100, 240],
            "children": [
              {
                "id": "t3",
                "type": "link",
                "name": "FACEBOOK",
                "url": "https://www.facebook.com",
                "desc": "Restiamo in contatto",
                "icon": "https://www.google.com/s2/favicons?sz=128&domain=facebook.com",
                "color": [24, 119, 242]
              },
              {
                "id": "t4",
                "type": "link",
                "name": "REDDIT",
                "url": "https://www.reddit.com",
                "desc": "Frontpage of the internet",
                "icon": "https://www.google.com/s2/favicons?sz=128&domain=reddit.com",
                "color": [255, 69, 0]
              }
            ]
          }
        ]
        """.trimIndent()
        importJson(defaultJson)
    }
}
