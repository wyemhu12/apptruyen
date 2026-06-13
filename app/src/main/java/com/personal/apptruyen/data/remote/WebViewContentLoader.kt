package com.personal.apptruyen.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Headless WebView loader for JS-rendered chapter content.
 * Falls back to this when OkHttp+Jsoup returns empty/short content.
 */
@Singleton
class WebViewContentLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val LOAD_TIMEOUT_MS = 15_000L

            // Allowed domains for WebView content loading
            private val ALLOWED_DOMAINS =
                listOf(
                    "truyencom.com",
                    "tangthuvien.vn",
                    "sstruyen.net",
                    "truyenfull.today",
                )

            // JS to extract chapter content after page loads
            private const val EXTRACT_JS = """
            (function() {
                var selectors = [
                    '#chapter-c', '#chapter-content', '.chapter-c',
                    '.chapter-content', '#content', '.truyen-content',
                    '#js-read__content', 'div.text-justify'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el && el.innerText.trim().length > 50) {
                        // Clone để không ảnh hưởng DOM gốc
                        var clone = el.cloneNode(true);
                        // Xóa navigation, headers, ads — tránh TTS đọc lặp tên truyện/chương
                        var toRemove = clone.querySelectorAll(
                            'h1, h2, h3, h4, .chapter_wrap, script, .ads, .ad, iframe, noscript'
                        );
                        for (var j = 0; j < toRemove.length; j++) {
                            toRemove[j].remove();
                        }
                        // Xóa navigation links (Chương trước/tiếp)
                        var links = clone.querySelectorAll('a');
                        for (var k = 0; k < links.length; k++) {
                            var t = links[k].innerText.trim();
                            if (t.indexOf('Chương trước') >= 0 || t.indexOf('Chương tiếp') >= 0 ||
                                t.indexOf('Chương cuối') >= 0 || t.indexOf('Tải Ebook') >= 0) {
                                links[k].remove();
                            }
                        }
                        var text = clone.innerText.trim();
                        if (text.length > 50) return text;
                    }
                }
                // Fallback: try body text
                return document.body ? document.body.innerText : '';
            })()
        """
        }

        /**
         * Load a URL in a headless WebView, wait for JS to render,
         * then extract text content using CSS selectors.
         * Must be called from a coroutine context (will switch to Main internally).
         */
        @SuppressLint("SetJavaScriptEnabled")
        suspend fun loadContent(url: String): String? =
            withContext(Dispatchers.Main) {
                val deferred = CompletableDeferred<String?>()

                val webView =
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.blockNetworkImage = true // Don't load images → faster
                        settings.userAgentString = BaseScraper.DEFAULT_MOBILE_UA // M3: Reuse shared constant
                    }

                // Guard against double-destroy (onPageFinished/onReceivedError + timeout)
                val isDestroyed = AtomicBoolean(false)

                fun safeDestroyWebView() {
                    if (isDestroyed.compareAndSet(false, true)) {
                        webView.destroy()
                    }
                }

                webView.webViewClient =
                    object : WebViewClient() {
                        // P4: Validate URL origin to prevent XSS on redirected domains
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val host = request.url?.host.orEmpty()
                            return !ALLOWED_DOMAINS.any { host.endsWith(it) }
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            loadedUrl: String?,
                        ) {
                            // Guard: WebView may have been destroyed by onReceivedError or timeout
                            if (isDestroyed.get()) return
                            // Wait a bit for JS to finish rendering
                            Handler(Looper.getMainLooper()).postDelayed({
                                // Guard: WebView may have been destroyed during delay
                                if (isDestroyed.get()) return@postDelayed
                                webView.evaluateJavascript(EXTRACT_JS) { result ->
                                    val text =
                                        result
                                            ?.removeSurrounding("\"")
                                            ?.replace("\\n", "\n")
                                            ?.replace("\\t", "")
                                            ?.trim()
                                    if (!text.isNullOrBlank() && text.length > 50 && text != "null") {
                                        deferred.complete(text)
                                    } else {
                                        deferred.complete(null)
                                    }
                                    safeDestroyWebView()
                                }
                            }, 2000) // 2s delay for JS rendering
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?,
                        ) {
                            if (!deferred.isCompleted) {
                                deferred.complete(null)
                            }
                            safeDestroyWebView()
                        }
                    }

                webView.loadUrl(url)

                // Timeout fallback
                withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    deferred.await()
                } ?: run {
                    deferred.complete(null) // Prevent leak: complete before destroy
                    safeDestroyWebView()
                    null
                }
            }
    }
