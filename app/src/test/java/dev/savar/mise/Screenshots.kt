package dev.savar.mise

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Not real tests: renders the main screens to PNG (build/shots) so they can be reviewed in CI without a phone.
 * Run by .github/workflows/screenshots.yml; fetch with scripts/shots.sh.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class Screenshots {
    @get:Rule val compose = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun shot(name: String, mode: ThemeMode, black: Boolean = false, content: @Composable () -> Unit) {
        ThemePrefs.force(mode, black)
        compose.mainClock.autoAdvance = false
        compose.setContent { MiseTheme { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A page on its own, with navigation wired to nothing. */
    @Composable
    private fun WithNav(content: @Composable () -> Unit) {
        val scope = rememberCoroutineScope()
        val nav = remember { Nav(mutableStateListOf(), SnackbarHostState(), scope) }
        CompositionLocalProvider(LocalNav provides nav) { content() }
    }

    @Test fun diaryLight() { Fixtures.seed(); shot("diary_light", ThemeMode.LIGHT) { MiseRoot(preview = true) } }

    @Test fun diaryDark() { Fixtures.seed(); shot("diary_dark", ThemeMode.DARK) { MiseRoot(preview = true) } }

    @Test fun diaryEmpty() {
        Images.offline = true
        Store.seed(emptyList(), emptyList())
        shot("diary_empty", ThemeMode.DARK, black = true) { MiseRoot(preview = true) }
    }

    @Test fun watchlist() { Fixtures.seed(); shot("watchlist_dark", ThemeMode.DARK) { MiseRoot(preview = true, startTab = Tab.WATCHLIST) } }

    @Test fun titlePage() {
        Fixtures.seed()
        shot("title_dark", ThemeMode.DARK) { MiseRoot(preview = true, startRoute = Route.TitlePage(Fixtures.interstellar.id)) }
    }

    @Test fun titlePageLight() {
        Fixtures.seed()
        shot("title_light", ThemeMode.LIGHT) { MiseRoot(preview = true, startRoute = Route.TitlePage(Fixtures.interstellar.id)) }
    }

    @Test fun logRecording() {
        Fixtures.seed()
        shot("log_recording_dark", ThemeMode.DARK) { WithNav { LogScreen(Fixtures.interstellar.id, "w1", previewRecording = true) } }
    }

    @Test fun logNewShow() {
        Fixtures.seed()
        shot("log_new_show_light", ThemeMode.LIGHT) { WithNav { LogScreen(Fixtures.arcane.id, null) } }
    }

    @Test fun settings() { Fixtures.seed(); shot("settings_dark", ThemeMode.DARK, black = true) { MiseRoot(preview = true, startTab = Tab.SETTINGS) } }

    @Test fun syncPage() { Fixtures.seed(); shot("sync_light", ThemeMode.LIGHT) { WithNav { SyncScreen() } } }

    @Test fun update() {
        Fixtures.seed()
        Updater.force(Updater.State.Available(Updater.Release("0.1.1", "- Faster", "", 2_740_000)))
        shot("diary_update", ThemeMode.DARK) { MiseRoot(preview = true) }
        Updater.force(Updater.State.Idle)
    }

    @Test fun storyCard() {
        Fixtures.seed()
        val w = Fixtures.watchInterstellar
        val bmp = runBlocking { Story.render(ctx, Fixtures.interstellar, w, StoryOptions(tier = true)) }
        File(out, "story_card.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val show = Fixtures.watches.first { it.titleId == Fixtures.arcane.id }
        val bmp2 = runBlocking { Story.render(ctx, Fixtures.arcane, show, StoryOptions()) }
        File(out, "story_card_show.png").outputStream().use { bmp2.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun appIcons() {
        Fixtures.seed()
        shot("app_icons", ThemeMode.DARK) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                AppIcon.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { row.forEach { IconTile(it, 96.dp) } }
                }
            }
        }
    }
}
