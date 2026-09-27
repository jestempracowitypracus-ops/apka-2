package pl.pomocdrogowa.zlecenia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFE8A317)
private val DarkAmber = Color(0xFF7A5200)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF3B4A5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F5),
    onSecondaryContainer = Color(0xFF0F1D2A),
    background = Color(0xFFFFFBF5),
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFF1E6D8),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2B1700),
    primaryContainer = DarkAmber,
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFB9C8D9),
    onSecondary = Color(0xFF233240),
    secondaryContainer = Color(0xFF3B4A5A),
    onSecondaryContainer = Color(0xFFD6E4F5),
    background = Color(0xFF17130E),
    surface = Color(0xFF17130E),
    surfaceVariant = Color(0xFF3A3129),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
