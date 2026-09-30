package com.rama.rss.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class RssViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var url by mutableStateOf(savedState.get<String>("feedUrl").orEmpty())
        private set
    var feed by mutableStateOf<RssFeed?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun updateUrl(value: String) {
        url = value
        savedState["feedUrl"] = value
    }

    fun load() {
        if (loading) return
        val address = url
        loading = true
        error = null
        viewModelScope.launch {
            try {
                feed = RssRepository().load(address)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: XmlPullParserException) {
                error = "La respuesta no contiene un XML RSS o Atom válido."
            } catch (_: IOException) {
                error = "No se pudo descargar el feed. Comprueba la conexión y la URL."
            } catch (exception: Exception) {
                error = exception.message ?: "No se pudo leer el feed."
            } finally {
                loading = false
            }
        }
    }
}
