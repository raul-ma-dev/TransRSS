package com.rama.rss

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rama.rss.reader.RssEntry
import com.rama.rss.reader.RssFeed
import com.rama.rss.reader.RssSource
import com.rama.rss.ui.theme.RssTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedPaginationScrollTest {
    @Test
    fun listShowsFiveThenLoadsMoreAsItScrollsWithoutRefreshingNetwork() {
        val feed = RssFeed(
            "Prueba de paginación",
            (1..12).map { RssEntry("Episodio $it", "<p>Resumen $it</p>", "", "30/09/2026") },
            RssSource.AUDIOBOOM.url
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var list: LazyListState
            var refreshes = 0
            scenario.onActivity { activity ->
                list = LazyListState()
                activity.setContent {
                    RssTheme(darkTheme = true, dynamicColor = false) {
                        RssReader(
                            RssSource.AUDIOBOOM, feed, false, null, { refreshes++ },
                            modifier = Modifier.height(300.dp), listState = list
                        )
                    }
                }
            }
            // One header + 5 entries + the "more entries" footer.
            awaitItemCount(scenario, list, 7)
            scenario.onActivity { activity -> activity.lifecycleScope.launch { list.scrollToItem(3) } }
            awaitItemCount(scenario, list, 12)
            scenario.onActivity { activity -> activity.lifecycleScope.launch { list.scrollToItem(8) } }
            // The final partial page contains 12 entries and no pagination footer.
            awaitItemCount(scenario, list, 13)
            scenario.onActivity { assertEquals(0, refreshes) }
        }
    }

    private fun awaitItemCount(scenario: ActivityScenario<MainActivity>, list: LazyListState, expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var actual = 0
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { actual = list.layoutInfo.totalItemsCount }
            if (actual == expected) break
            SystemClock.sleep(100)
        }
        assertTrue("Se esperaban $expected elementos pero se muestran $actual", actual == expected)
    }
}
