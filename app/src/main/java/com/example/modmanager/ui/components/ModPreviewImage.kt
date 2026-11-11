package com.darkestdungeon.modmanager.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkestdungeon.modmanager.data.model.Mod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads and displays a mod's preview image (the game's convention:
 * `preview-icon.png` inside the mod folder), falling back to a default
 * placeholder icon when the image is missing or fails to decode.
 */
@Composable
fun ModPreviewImage(
    mod: Mod,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uri = resolvePreviewUri(mod)

    val bitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = uri
    ) {
        value = uri?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap.value
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = mod.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            // Default placeholder when no image is available or decoding failed.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (size >= 48.dp) {
                        Icons.Rounded.ImageNotSupported
                    } else {
                        Icons.Rounded.Extension
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (size >= 48.dp) size / 3 else size / 2)
                )
                if (size >= 80.dp) {
                    Text(
                        text = "No image",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Resolves the preview-image URI for a mod (SAF content URI or file path). */
private fun resolvePreviewUri(mod: Mod): Uri? {
    // Scanner-found image wins.
    mod.iconPath?.let { path ->
        if (path.startsWith("content://")) return Uri.parse(path)
        val file = File(path)
        if (file.isFile) return Uri.fromFile(file)
    }

    // SAF mods: folderPath is a content:// tree URI, not a file path.
    val folderPath = mod.folderPath
    if (folderPath.startsWith("content://")) return null

    // File-based mods: look in the folder directly (preferred names).
    val folder = File(folderPath).takeIf { it.isDirectory } ?: return null
    val preferred = listOf(
        "preview_icon.png", "preview-icon.png", "preview_icon.jpg", "preview-icon.jpg",
        "preview.png", "preview.jpg", "icon.png", "icon.jpg", "cover.png", "cover.jpg"
    )
    for (name in preferred) {
        val f = File(folder, name)
        if (f.isFile) return Uri.fromFile(f)
    }
    return null
}