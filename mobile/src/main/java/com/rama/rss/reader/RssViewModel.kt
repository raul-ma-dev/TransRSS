package com.rama.rss.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

private const val DEFAULT_FEED_URL = "https://canaltrans.com/rss/transrss.xml"

class RssViewModel : ViewModel() {
    var feed by mutableStateOf<RssFeed?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                feed = RssRepository().load(DEFAULT_FEED_URL)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: XmlPullParserException) {
                error = "La respuesta no contiene un XML RSS o Atom válido."
            } catch (_: IOException) {
                error = "No se pudo descargar el feed. Comprueba tu conexión a Internet."
            } catch (exception: Exception) {
                error = exception.message ?: "No se pudo leer el feed."
            } finally {
                loading = false
            }
        }
    }
}
