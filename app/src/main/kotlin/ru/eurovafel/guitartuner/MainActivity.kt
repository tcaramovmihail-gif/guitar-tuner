package ru.eurovafel.guitartuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.osmdroid.config.Configuration
import ru.eurovafel.guitartuner.ui.AppRoot
import ru.eurovafel.guitartuner.ui.theme.GuitarTunerTheme
import ru.eurovafel.guitartuner.ui.theme.loadThemeChoice
import ru.eurovafel.guitartuner.ui.theme.loadThemeMode
import ru.eurovafel.guitartuner.ui.theme.saveThemeChoice
import ru.eurovafel.guitartuner.ui.theme.saveThemeMode
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Служебный запуск для проверки импорта:
        // adb shell am start -n ru.eurovafel.guitartuner/.MainActivity --ez import_examples true
        val importExamples = intent?.getBooleanExtra(EXTRA_IMPORT_EXAMPLES, false) == true
        intent?.removeExtra(EXTRA_IMPORT_EXAMPLES)

        // Кеш тайлов OSM — в приватном каталоге приложения, без доступа к файлам.
        Configuration.getInstance().apply {
            setUserAgentValue(packageName)
            load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
            val basePath = File(cacheDir, "osmdroid")
            val tileCache = File(basePath, "tiles")
            basePath.mkdirs()
            tileCache.mkdirs()
            setOsmdroidBasePath(basePath)
            setOsmdroidTileCache(tileCache)
        }

        enableEdgeToEdge()
        setContent {
            // Тема живёт в Activity: смена схемы сразу перекрашивает все экраны.
            var themeChoice by remember { mutableStateOf(loadThemeChoice(this)) }
            var themeMode by remember { mutableStateOf(loadThemeMode(this)) }
            GuitarTunerTheme(choice = themeChoice, mode = themeMode) {
                AppRoot(
                    importExamples = importExamples,
                    themeChoice = themeChoice,
                    onThemeChange = { choice ->
                        themeChoice = choice
                        saveThemeChoice(this, choice)
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        saveThemeMode(this, mode)
                    },
                )
            }
        }
    }

    private companion object {
        const val EXTRA_IMPORT_EXAMPLES = "import_examples"
    }
}
