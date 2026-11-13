package com.darkestdungeon.modmanager.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.darkestdungeon.modmanager.R
import com.darkestdungeon.modmanager.data.model.ModCategory

data class AppLanguageOption(
    val tag: String,
    val label: String
)

object AppLanguage {
    private const val PREFS = "dd_mod_manager_prefs"
    private const val KEY_LANGUAGE = "app_language"

    val options = listOf(
        AppLanguageOption("en", "English"),
        AppLanguageOption("zh-CN", "中文（普通话）"),
        AppLanguageOption("pt", "Português"),
        AppLanguageOption("es", "Español"),
        AppLanguageOption("hi", "हिन्दी"),
        AppLanguageOption("fil", "Tagalog"),
        AppLanguageOption("th", "ไทย"),
        AppLanguageOption("vi", "Tiếng Việt"),
        AppLanguageOption("id", "Bahasa Indonesia"),
        AppLanguageOption("ms", "Bahasa Melayu")
    )

    fun selectedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "en")
            .orEmpty()
            .ifBlank { "en" }

    fun setLanguage(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
        if (context is Activity) context.recreate()
    }

    fun localizedContext(context: Context): Context {
        val tag = selectedTag(context)
        if (tag.isBlank()) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(configuration)
    }
}

@Composable
fun localizedCategoryName(category: ModCategory): String {
    val resourceId = when (category.id) {
        "ui" -> R.string.category_ui
        "districts" -> R.string.category_districts
        "dungeons" -> R.string.category_dungeons
        "quirks" -> R.string.category_quirks
        "trinkets" -> R.string.category_trinkets
        "enemies" -> R.string.category_enemies
        "class_patch" -> R.string.category_class_patch
        "class" -> R.string.category_class
        "skins" -> R.string.category_skins
        else -> null
    }
    return resourceId?.let { stringResource(it) } ?: category.name
}
