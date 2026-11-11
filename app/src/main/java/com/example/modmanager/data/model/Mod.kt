package com.darkestdungeon.modmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Mod(
    val id: String,
    val title: String,
    val description: String = "",
    val author: String = "Unknown Author",
    val version: String = "1.0",
    val folderName: String,
    val folderPath: String,
    val isEnabled: Boolean = true,
    val loadOrder: Int = 0,
    val iconPath: String? = null,
    val dependencies: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String? = null,
    val gameVersion: String? = null,
    val lastModified: Long = 0L,
    val hasPreviewImage: Boolean = false
)

data class ModMetadata(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    val version: String? = null,
    val dependencies: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val language: String? = null,
    val gameVersion: String? = null
)
