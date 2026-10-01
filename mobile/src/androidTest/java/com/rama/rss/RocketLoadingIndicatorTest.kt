package com.rama.rss

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rama.rss.reader.RssSource
import com.rama.rss.ui.theme.RssTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class RocketLoadingIndicatorTest {
    @Test
    fun composeRocketCoversScreenLoopsAndRestartsOnRefresh() {
        val loading = mutableStateOf(true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    RssTheme(darkTheme = true, dynamicColor = false) {
                        RssAppContent(RssSource.AUDIOBOOM, feed = null, loading = loading.value)
                    }
                }
            }
            awaitState(scenario, "IGNITION")
            scenario.onActivity { activity ->
                val rocket = findRocket(activity.window.decorView)!!
                val content = activity.findViewById<View>(android.R.id.content)
                assertEquals(content.width, rocket.boundsInRoot.width.roundToInt())
                assertEquals(content.height, rocket.boundsInRoot.height.roundToInt())
                assertEquals(0, rocket.boundsInRoot.left.roundToInt())
                assertEquals(0, rocket.boundsInRoot.top.roundToInt())
                val texts = semanticsNodes(activity.window.decorView).flatMap {
                    it.config.getOrNull(SemanticsProperties.Text).orEmpty()
                }.map { it.text }
                assertFalse(texts.contains("Despegar"))
                assertFalse(texts.contains("Repetir"))
                assertTrue(semanticsNodes(activity.window.decorView).any {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Cohete despegando") == true
                })
            }
            awaitState(scenario, "LAUNCHING")
            awaitState(scenario, "FINISHED")
            awaitState(scenario, "IGNITION")
            scenario.onActivity { loading.value = false }
            awaitCondition(scenario) { root -> findRocket(root) == null }
            scenario.onActivity { loading.value = true }
            awaitCondition(scenario) { root ->
                findRocket(root)?.config?.getOrNull(SemanticsProperties.StateDescription) in listOf("IGNITION", "LAUNCHING")
            }
            awaitState(scenario, "LAUNCHING")
        }
    }

    private fun awaitState(scenario: ActivityScenario<MainActivity>, state: String) {
        awaitCondition(scenario) { root ->
            findRocket(root)?.config?.getOrNull(SemanticsProperties.StateDescription) == state
        }
    }

    private fun awaitCondition(scenario: ActivityScenario<MainActivity>, condition: (View) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var satisfied = false
            scenario.onActivity { satisfied = condition(it.window.decorView) }
            if (satisfied) return
            SystemClock.sleep(100)
        }
        throw AssertionError("El indicador del cohete no alcanzó el estado esperado")
    }

    private fun findRocket(view: View): SemanticsNode? = semanticsNodes(view).firstOrNull {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Cargando entradas") == true &&
            it.config.getOrNull(SemanticsProperties.StateDescription) != null
    }

    private fun semanticsNodes(view: View): List<SemanticsNode> =
        findComposeRoot(view)?.semanticsOwner?.getAllSemanticsNodes(mergingEnabled = false).orEmpty()

    private fun findComposeRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findComposeRoot(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}

