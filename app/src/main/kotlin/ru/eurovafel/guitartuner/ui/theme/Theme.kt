package ru.eurovafel.guitartuner.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Цветовые схемы приложения. Выбор сохраняется на устройстве. */
enum class ThemeChoice(
    val title: String,
    val preview: Color,
) {
    MINT("Мятная", Color(0xFF7FE7C4)),
    GOLD("Золотая", Color(0xFFE8C36A)),
    BLUE("Синяя", Color(0xFF8EC5FF)),
    PURPLE("Фиолетовая", Color(0xFFD0A8FF)),
}

private val MintColors = darkColorScheme(
    primary = Color(0xFF7FE7C4),
    onPrimary = Color(0xFF00251C),
    primaryContainer = Color(0xFF0B4A3A),
    onPrimaryContainer = Color(0xFFB5F5DF),
    secondary = Color(0xFF9CCFC0),
    onSecondary = Color(0xFF0B2B22),
    secondaryContainer = Color(0xFF1B3A32),
    onSecondaryContainer = Color(0xFFCDE9DF),
    tertiary = Color(0xFFF0C987),
    onTertiary = Color(0xFF2E2108),
    tertiaryContainer = Color(0xFF4A3A1E),
    onTertiaryContainer = Color(0xFFFFE7B8),
    background = Color(0xFF0A0F0D),
    onBackground = Color(0xFFE2EAE6),
    surface = Color(0xFF101713),
    onSurface = Color(0xFFE2EAE6),
    surfaceVariant = Color(0xFF26332D),
    onSurfaceVariant = Color(0xFFB6C7BF),
    outline = Color(0xFF51635B),
)

private val GoldColors = darkColorScheme(
    primary = Color(0xFFE8C36A),
    onPrimary = Color(0xFF2E2108),
    primaryContainer = Color(0xFF4A3A1E),
    onPrimaryContainer = Color(0xFFFFE7B8),
    secondary = Color(0xFFD6C08A),
    onSecondary = Color(0xFF2B2413),
    secondaryContainer = Color(0xFF3C321C),
    onSecondaryContainer = Color(0xFFF2E2B8),
    tertiary = Color(0xFF8FD3B0),
    onTertiary = Color(0xFF05251A),
    tertiaryContainer = Color(0xFF153A2A),
    onTertiaryContainer = Color(0xFFB6EFD4),
    background = Color(0xFF0F0D08),
    onBackground = Color(0xFFEDE5D3),
    surface = Color(0xFF171308),
    onSurface = Color(0xFFEDE5D3),
    surfaceVariant = Color(0xFF332C1E),
    onSurfaceVariant = Color(0xFFCFC3A8),
    outline = Color(0xFF635A45),
)

private val BlueColors = darkColorScheme(
    primary = Color(0xFF8EC5FF),
    onPrimary = Color(0xFF00243D),
    primaryContainer = Color(0xFF0B3A5A),
    onPrimaryContainer = Color(0xFFD0E8FF),
    secondary = Color(0xFF9FC4E0),
    onSecondary = Color(0xFF0A2638),
    secondaryContainer = Color(0xFF1B3548),
    onSecondaryContainer = Color(0xFFCDE5F7),
    tertiary = Color(0xFFF0C987),
    onTertiary = Color(0xFF2E2108),
    tertiaryContainer = Color(0xFF4A3A1E),
    onTertiaryContainer = Color(0xFFFFE7B8),
    background = Color(0xFF080D12),
    onBackground = Color(0xFFDDE7EF),
    surface = Color(0xFF0E1720),
    onSurface = Color(0xFFDDE7EF),
    surfaceVariant = Color(0xFF20303D),
    onSurfaceVariant = Color(0xFFB6C9D8),
    outline = Color(0xFF4C6275),
)

private val PurpleColors = darkColorScheme(
    primary = Color(0xFFD0A8FF),
    onPrimary = Color(0xFF2A0B45),
    primaryContainer = Color(0xFF452165),
    onPrimaryContainer = Color(0xFFEEDBFF),
    secondary = Color(0xFFC3B0D8),
    onSecondary = Color(0xFF271633),
    secondaryContainer = Color(0xFF3A2947),
    onSecondaryContainer = Color(0xFFE6D8F2),
    tertiary = Color(0xFFF0C987),
    onTertiary = Color(0xFF2E2108),
    tertiaryContainer = Color(0xFF4A3A1E),
    onTertiaryContainer = Color(0xFFFFE7B8),
    background = Color(0xFF0D0812),
    onBackground = Color(0xFFE7DEEE),
    surface = Color(0xFF150D1D),
    onSurface = Color(0xFFE7DEEE),
    surfaceVariant = Color(0xFF2E2238),
    onSurfaceVariant = Color(0xFFC9BBD6),
    outline = Color(0xFF5D4C6B),
)

@Composable
fun GuitarTunerTheme(
    choice: ThemeChoice = ThemeChoice.MINT,
    content: @Composable () -> Unit,
) {
    val scheme = when (choice) {
        ThemeChoice.MINT -> MintColors
        ThemeChoice.GOLD -> GoldColors
        ThemeChoice.BLUE -> BlueColors
        ThemeChoice.PURPLE -> PurpleColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private const val PREFS = "guitar_tuner_settings"
private const val KEY_THEME = "theme_choice"

fun loadThemeChoice(context: Context): ThemeChoice {
    val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_THEME, null)
    return ThemeChoice.entries.firstOrNull { it.name == saved } ?: ThemeChoice.MINT
}

fun saveThemeChoice(context: Context, choice: ThemeChoice) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_THEME, choice.name)
        .apply()
}
