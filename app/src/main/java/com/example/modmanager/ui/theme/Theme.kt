package com.darkestdungeon.modmanager.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

private val GothicDarkColorScheme = darkColorScheme(
    primary = TorchGold,
    onPrimary = Color(0xFF261900),
    primaryContainer = TorchGoldDark,
    onPrimaryContainer = TorchGoldLight,
    secondary = CrimsonRed,
    onSecondary = Color.White,
    secondaryContainer = CrimsonDark,
    onSecondaryContainer = CrimsonLight,
    tertiary = IronPewter,
    onTertiary = DarkObsidian,
    background = DarkObsidian,
    onBackground = ParchmentWhite,
    surface = DarkSlateSurface,
    onSurface = ParchmentWhite,
    surfaceVariant = DarkSlateVariant,
    onSurfaceVariant = AshGray,
    outline = BorderGray,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = Color(0xFF33333E)
)

private val GothicLightColorScheme = lightColorScheme(
    primary = TorchGoldDark,
    onPrimary = Color.White,
    primaryContainer = TorchGoldLight,
    onPrimaryContainer = Color(0xFF261900),
    secondary = CrimsonRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = CrimsonDark,
    tertiary = IronPewter,
    onTertiary = Color.White,
    background = Color(0xFFF9F6F0),
    onBackground = DarkObsidian,
    surface = Color.White,
    onSurface = DarkObsidian,
    surfaceVariant = Color(0xFFEBE6DC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

private val GothicShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(5.dp),
    large = RoundedCornerShape(7.dp),
    extraLarge = RoundedCornerShape(9.dp)
)

@Composable
fun DDAndroidModManagerTheme(
    darkTheme: Boolean = true, // Dark fantasy theme by default
    dynamicColor: Boolean = false, // Set false by default to retain game dark fantasy theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GothicDarkColorScheme
        else -> GothicLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = GothicShapes,
        content = content
    )
}
