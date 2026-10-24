package ru.eurovafel.guitartuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.eurovafel.guitartuner.ui.AppRoot
import ru.eurovafel.guitartuner.ui.theme.GuitarTunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Служебный запуск для проверки импорта:
        // adb shell am start -n ru.eurovafel.guitartuner/.MainActivity --ez import_examples true
        val importExamples = intent?.getBooleanExtra(EXTRA_IMPORT_EXAMPLES, false) == true
        intent?.removeExtra(EXTRA_IMPORT_EXAMPLES)

        enableEdgeToEdge()
        setContent {
            GuitarTunerTheme {
                AppRoot(importExamples = importExamples)
            }
        }
    }

    private companion object {
        const val EXTRA_IMPORT_EXAMPLES = "import_examples"
    }
}
