package com.rama.rss

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rama.rss.reader.RssEntry
import kotlin.math.ceil

@Composable
internal fun ArticleHtmlContent(
    entry: RssEntry,
    baseUrl: String,
    modifier: Modifier = Modifier,
    fitContent: Boolean = false,
    audioDetail: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onSurface.toCssHex()
    val linkColor = colors.primary.toCssHex()
    val document = remember(entry, textColor, linkColor, audioDetail) {
        articleDocument(entry, textColor, linkColor, audioDetail = audioDetail)
    }
    val density = LocalDensity.current
    var contentHeightPx by remember(document, baseUrl) { mutableIntStateOf(1) }
    val viewModifier = if (fitContent) {
        modifier.height(with(density) { contentHeightPx.toDp() }.coerceAtLeast(100.dp))
    } else modifier
    AndroidView(
        modifier = viewModifier,
        factory = { context ->
            ArticleWebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.mediaPlaybackRequiresUserGesture = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.isForMainFrame && request.hasGesture()) {
                            openInBrowser(view.context, request.url.toString())
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        (view as ArticleWebView).reportContentHeight()
                    }
                }
            }
        },
        update = { view ->
            view.fitContent = fitContent
            view.onContentHeightChanged = if (fitContent) ({ contentHeightPx = it }) else null
            view.isVerticalScrollBarEnabled = !fitContent
            view.overScrollMode = if (fitContent) WebView.OVER_SCROLL_NEVER else WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
            view.settings.setSupportZoom(!fitContent)
            val contentKey = document to baseUrl
            if (view.tag != contentKey) {
                view.tag = contentKey
                view.resetContentHeight()
                view.loadDataWithBaseURL(baseUrl, document, "text/html", "UTF-8", null)
            }
        },
        onRelease = { view ->
            view.onContentHeightChanged = null
            view.stopLoading()
            view.destroy()
        }
    )
}

internal class ArticleWebView(context: Context) : WebView(context) {
    var fitContent = false
    var onContentHeightChanged: ((Int) -> Unit)? = null
    private var lastContentHeight = 0

    fun resetContentHeight() {
        lastContentHeight = 0
    }

    fun reportContentHeight() {
        if (!fitContent || contentHeight <= 0) return
        val pixels = ceil(contentHeight * resources.displayMetrics.density.toDouble()).toInt()
        if (pixels == lastContentHeight) return
        lastContentHeight = pixels
        post { onContentHeightChanged?.invoke(pixels) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        reportContentHeight()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (fitContent) {
            parent?.requestDisallowInterceptTouchEvent(false)
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        }
        return handled
    }

    override fun performClick(): Boolean = super.performClick()
}
