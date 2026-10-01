package com.rama.rss.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

enum class RssSource(val title: String, val url: String, val header: String = title) {

    AUDIOBOOM(
        "Episodios",
        "https://audioboom.com/channels/3716163.rss",
        "En Caso De Que El Mundo Se Desintegre"
    ),
    CANALTRANS("Noticias", "https://canaltrans.com/rss/transrss.xml", "Noticias CanalTrans"),
}

private data class FeedState(
    val feed: RssFeed? = null,
    val loading: Boolean = false,
    val error: String? = null
)

class RssViewModel internal constructor(
    private val savedState: SavedStateHandle,
    canaltransTabEnabled: Boolean = CANALTRANS_TAB_ENABLED,
    private val loadFeed: suspend (String) -> RssFeed
) : ViewModel() {
    constructor(savedState: SavedStateHandle) : this(savedState, CANALTRANS_TAB_ENABLED, RssRepository()::load)

    val availableSources: List<RssSource> = enabledRssSources(canaltransTabEnabled)

    var selectedSource by mutableStateOf(
        runCatching { RssSource.valueOf(savedState.get<String>("selectedSource").orEmpty()) }
            .getOrNull()?.takeIf { it in availableSources } ?: availableSources.first()
    )
        private set
    private val states = mutableStateMapOf<RssSource, FeedState>()

    private var preparedFeeds by mutableStateOf<Map<RssSource, RssFeed?>>(emptyMap())
    private var preparing by mutableStateOf(false)
    private var preparation: Job? = null

    val feed: RssFeed? get() = preparedFeeds[selectedSource]
    val loading: Boolean get() = preparing || requiredSources().any { states[it]?.loading == true }
    val error: String?
        get() = requiredSources().mapNotNull { source ->
            states[source]?.error?.let { message ->
                if (selectedSource == RssSource.AUDIOBOOM) "${source.title}: $message" else message
            }
        }.joinToString("\n").takeIf { it.isNotBlank() }

    init {
        savedState["selectedSource"] = selectedSource.name
        refresh()
    }

    fun selectSource(source: RssSource) {
        if (source !in availableSources || source == selectedSource) return
        selectedSource = source
        savedState["selectedSource"] = source.name
        requiredSources().filter { states[it]?.feed == null }.forEach { download(it) }
    }

    fun refresh() {
        if (loading) return
        requiredSources().forEach { download(it) }
    }

    private fun requiredSources(): List<RssSource> = when (selectedSource) {
        RssSource.CANALTRANS -> listOf(RssSource.CANALTRANS)
        RssSource.AUDIOBOOM -> listOf(RssSource.CANALTRANS, RssSource.AUDIOBOOM)
    }

    private fun download(source: RssSource) {
        val previous = states[source] ?: FeedState()
        if (previous.loading) return
        preparation?.cancel()
        preparing = false
        states[source] = previous.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val downloaded = loadFeed(source.url)
                states[source] = states.getValue(source).copy(feed = downloaded)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: XmlPullParserException) {
                states[source] = states.getValue(source).copy(
                    error = "La respuesta no contiene un XML RSS o Atom válido."
                )
            } catch (_: IOException) {
                states[source] = states.getValue(source).copy(
                    error = "No se pudo descargar el feed. Comprueba tu conexión a Internet."
                )
            } catch (exception: Exception) {
                states[source] = states.getValue(source).copy(
                    error = exception.message ?: "No se pudo leer el feed."
                )
            } finally {
                states[source] = states.getValue(source).copy(loading = false)
                if (states.values.none { it.loading }) prepareFeeds()
            }
        }
    }

    private fun prepareFeeds() {
        val canaltrans = states[RssSource.CANALTRANS]?.feed
        val audioboom = states[RssSource.AUDIOBOOM]?.feed
        preparation?.cancel()
        preparing = true
        preparation = viewModelScope.launch {
            try {
                preparedFeeds = withContext(Dispatchers.Default) {
                    buildMap {
                        if (RssSource.CANALTRANS in availableSources) {
                            put(RssSource.CANALTRANS, canaltransWithoutRadio(canaltrans))
                        }
                        put(RssSource.AUDIOBOOM, audioboomWithRadio(canaltrans, audioboom))
                    }
                }
            } finally {
                if (currentCoroutineContext().isActive) preparing = false
            }
        }
    }
}
