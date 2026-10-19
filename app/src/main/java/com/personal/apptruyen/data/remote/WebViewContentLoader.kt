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
import timber.log.Timber
import java.net.URI
import java.util.Locale
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
            private const val TAG = "WebViewContentLoader"
            private const val LOAD_TIMEOUT_MS = 15_000L

            // Allowed domains for WebView content loading
            val ALLOWED_DOMAINS =
                setOf(
                    "tangthuvien.org",
                    "tangthuvien.vn",
                    "sstruyen.pics",
                    "sstruyen.vn",
                    "sstruyen.net",
                    "wikicv.org",
                    "wikicv.net",
                )

            fun isAllowedDomain(
                host: String?,
                allowedDomains: Collection<String> = ALLOWED_DOMAINS,
            ): Boolean {
                val normalizedHost = host?.trim()?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
                if (normalizedHost.isEmpty()) return false
                return allowedDomains.any { domain ->
                    val normalizedDomain = domain.trim().lowercase(Locale.ROOT).trimEnd('.')
                    normalizedDomain.isNotEmpty() &&
                        (normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain"))
                }
            }

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
                val initialHost = runCatching { URI(url).host }.getOrNull()
                if (!isAllowedDomain(initialHost, ALLOWED_DOMAINS)) {
                    Timber.w("%s: Blocked initial URL outside allowed domains: %s", TAG, initialHost)
                    return@withContext null
                }

                val deferred = CompletableDeferred<String?>()
                val mainHandler = Handler(Looper.getMainLooper())

                val webView =
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.blockNetworkImage = true // Don't load images → faster
                        settings.userAgentString = BaseScraper.DEFAULT_MOBILE_UA // M3: Reuse shared constant
                    }

                // Guard against double-destroy (onPageFinished/onReceivedError + timeout/cancellation)
                val isDestroyed = AtomicBoolean(false)

                fun safeDestroyWebView() {
                    if (isDestroyed.compareAndSet(false, true)) {
                        mainHandler.removeCallbacksAndMessages(null)
                        webView.stopLoading()
                        webView.destroy()
                    }
                }

                webView.webViewClient =
                    object : WebViewClient() {
                        // P4: Validate URL origin to prevent XSS on redirected domains
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = !isAllowedDomain(request.url?.host, ALLOWED_DOMAINS)

                        override fun onPageFinished(
                            view: WebView?,
                            loadedUrl: String?,
                        ) {
                            // Guard: WebView may have been destroyed by onReceivedError or timeout
                            if (isDestroyed.get()) return
                            // Wait a bit for JS to finish rendering
                            mainHandler.postDelayed({
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

                try {
                    webView.loadUrl(url)
                    withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                        deferred.await()
                    }
                } finally {
                    deferred.complete(null)
                    safeDestroyWebView()
                }
            }
    }
