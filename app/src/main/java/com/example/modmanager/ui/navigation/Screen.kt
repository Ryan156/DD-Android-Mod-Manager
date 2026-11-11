package com.darkestdungeon.modmanager.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable
    data object ModList : Screen

    @Serializable
    data class ModDetail(val modId: String) : Screen

    @Serializable
    data object Settings : Screen
}
