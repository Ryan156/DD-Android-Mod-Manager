package com.darkestdungeon.modmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ModCategory(
    val id: String,
    val name: String,
    val colorHex: String = DEFAULT_COLOR_HEX,
    val builtIn: Boolean = false
) {
    companion object {
        const val DEFAULT_COLOR_HEX = "#FFFFFFFF"

        val BUILT_IN: List<ModCategory> = listOf(
            ModCategory("ui", "UI", builtIn = true),
            ModCategory("districts", "Districts", builtIn = true),
            ModCategory("dungeons", "Dungeons", builtIn = true),
            ModCategory("quirks", "Quirks", builtIn = true),
            ModCategory("trinkets", "Trinkets", builtIn = true),
            ModCategory("enemies", "Enemies", builtIn = true),
            ModCategory("class_patch", "Class Patch", builtIn = true),
            ModCategory("class", "Class", builtIn = true),
            ModCategory("skins", "Skins", builtIn = true)
        )

        fun defaults(): List<ModCategory> = BUILT_IN
    }
}
