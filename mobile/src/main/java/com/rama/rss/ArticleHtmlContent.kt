package com.rama.rss

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rama.rss.reader.RssEntry
import com.rama.rss.ui.theme.RssTheme
import kotlin.math.roundToInt

@Composable
internal fun ArticleHtmlContent(
    entry: RssEntry,
    baseUrl: String,
    modifier: Modifier = Modifier,
    fitContent: Boolean = false,
    audioDetail: Boolean = false,
    nativeAudio: Boolean = false,
    includeHeader: Boolean = true
) {
    if (LocalInspectionMode.current) {
        ArticlePreviewContent(
            entry, modifier, scrollable = !fitContent, audioDetail = audioDetail,
            compactCard = fitContent && !audioDetail, includeHeader = includeHeader
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onSurface.toCssHex()
    val linkColor = colors.primary.toCssHex()
    val cardTypography = if (fitContent && !audioDetail) MaterialTheme.typography else null
    val document = remember(entry, textColor, linkColor, audioDetail, cardTypography, nativeAudio, includeHeader) {
        articleDocument(
            entry, textColor, linkColor, audioDetail = audioDetail, cardTypography = cardTypography,
            nativeAudio = nativeAudio, includeHeader = includeHeader
        )
    }
    val density = LocalDensity.current
    AndroidView(
        // Keep loading items tall enough that LazyColumn does not scroll past unrendered WebViews.
        modifier = modifier.defaultMinSize(minHeight = if (fitContent) 100.dp else 0.dp).clipToBounds(),
        factory = { context ->
            ArticleWebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
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
                        view.requestLayout()
                    }
                }
            }
        },
        update = { view ->
            view.fitContent = fitContent
            view.isVerticalScrollBarEnabled = !fitContent
            view.overScrollMode = if (fitContent) WebView.OVER_SCROLL_NEVER else WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
            view.settings.setSupportZoom(!fitContent)
            val textZoom = if (cardTypography != null) (density.fontScale * 100).roundToInt() else 100
            if (view.settings.textZoom != textZoom) view.settings.textZoom = textZoom
            val contentKey = document to baseUrl
            if (view.tag != contentKey) {
                view.tag = contentKey
                view.loadDataWithBaseURL(baseUrl, document, "text/html", "UTF-8", null)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        }
    )
}

@Preview(name = "Contenido HTML", group = "Artículos", showBackground = true, widthDp = 360, heightDp = 440)
@Composable
private fun ArticleHtmlContentPreview() {
    RssTheme(darkTheme = true, dynamicColor = false) {
        ArticleHtmlContent(
            RssEntry("Una historia para descubrir", "<p>Contenido del artículo con <strong>texto destacado</strong>.</p>", "", ""),
            "https://example.com/feed.xml",
            fitContent = true
        )
    }
}

internal class ArticleWebView(context: Context) : WebView(context) {
    var fitContent = false

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
