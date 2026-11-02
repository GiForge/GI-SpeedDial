package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_dial_items")
data class SpeedDialEntity(
    @PrimaryKey val id: String,
    val type: String, // "link" or "folder"
    val name: String,
    val url: String? = null,
    val desc: String? = null,
    val icon: String? = null,
    val r: Int = 100,
    val g: Int = 100,
    val b: Int = 100,
    val parentId: String? = null, // ID of the parent folder, null if root
    val orderIndex: Int = 0
)
