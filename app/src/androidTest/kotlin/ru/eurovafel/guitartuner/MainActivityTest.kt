package ru.eurovafel.guitartuner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tunerScreenIsVisibleOnLaunch() {
        composeRule.onNodeWithTag("tuner_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("tuner_status").assertIsDisplayed()
        composeRule.onNodeWithTag("tuner_toggle").assertIsDisplayed()
    }

    @Test
    fun chordsTabShowsBasicChords() {
        composeRule.onNodeWithText("Аккорды").performClick()
        composeRule.onNodeWithTag("chords_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("chord_C").assertIsDisplayed()
    }

    @Test
    fun songsTabShowsSongList() {
        composeRule.onNodeWithText("Песни").performClick()
        composeRule.onNodeWithTag("songs_screen").assertIsDisplayed()
    }

    @Test
    fun stringCanBeSelectedManuallyAndReturnedToAuto() {
        composeRule.onNodeWithTag("tuner_string_50")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("tuner_string_50").assertIsSelected()
        composeRule.onNodeWithTag("tuner_current_string")
            .assertTextContains("Выбрана вручную", substring = true)
            .assertTextContains("D3", substring = true)
        composeRule.onNodeWithText("Тон").assertIsDisplayed()

        composeRule.onNodeWithText("Авто")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("tuner_string_50").assertIsNotSelected()
    }

    @Test
    fun savedSongsCanBeSearchedByName() {
        composeRule.onNodeWithText("Песни").performClick()
        composeRule.onNodeWithTag("songs_local_search").performTextInput("Учебный пример")

        // Демо-песни лежат в папке «Учебный пример»; поиск показывает их без папок.
        composeRule.onNodeWithTag("songs_song_demo-1").assertIsDisplayed()
        composeRule.onNodeWithTag("songs_song_demo-2").assertIsDisplayed()
        composeRule.onNodeWithTag("songs_local_empty").assertDoesNotExist()

        composeRule.onNodeWithText("✕").performClick()
        composeRule.onNodeWithTag("songs_song_demo-1").assertDoesNotExist()
    }

    @Test
    fun stableCaptureCanBeSwitchedOff() {
        composeRule.onNodeWithTag("tuner_stable_capture")
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }
}
