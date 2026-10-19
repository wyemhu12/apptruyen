package com.personal.apptruyen.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.apptruyen.data.remote.WebViewContentLoader

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceLoginScreen(
    viewModel: SourceLoginViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val webViewBundle = rememberSaveable { Bundle() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.config?.sourceName?.let { "Đăng nhập $it" } ?: "Đăng nhập nguồn truyện",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onShowPasteDialog() },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Dán Cookie PC",
                        )
                    }
                    if (uiState.isLoggedIn) {
                        IconButton(
                            onClick = { viewModel.logout() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Đăng xuất",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.config?.loginInstructions?.let { instructions ->
                if (instructions.isNotBlank()) {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = instructions,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            val config = uiState.config
            if (config != null) {
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                                webViewClient =
                                    object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                        ): Boolean {
                                            val uri = request?.url ?: return false
                                            val scheme = uri.scheme?.lowercase() ?: ""
                                            if (scheme != "http" && scheme != "https") return true
                                            val allowed = WebViewContentLoader.isAllowedDomain(uri.host, config.allowlistHosts)
                                            return if (allowed) {
                                                false
                                            } else {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                                    ctx.startActivity(intent)
                                                } catch (_: Exception) {
                                                }
                                                true
                                            }
                                        }

                                        override fun onPageFinished(
                                            view: WebView?,
                                            url: String?,
                                        ) {
                                            super.onPageFinished(view, url)
                                            if (url != null && url.startsWith(config.successRedirectPrefix)) {
                                                viewModel.onLoginDetected()
                                            }
                                        }
                                    }

                                if (!webViewBundle.isEmpty) {
                                    restoreState(webViewBundle)
                                } else {
                                    loadUrl(config.loginUrl, config.customHeaders)
                                }
                            }
                        },
                        onRelease = { webView ->
                            webView.saveState(webViewBundle)
                            webView.stopLoading()
                            webView.destroy()
                        },
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (uiState.showPasteDialog && uiState.config != null) {
            PasteCookieDialog(
                sourceName = uiState.config?.sourceName.orEmpty(),
                cookieName = uiState.config?.sessionCookieName.orEmpty(),
                isVerifying = uiState.isVerifyingPaste,
                errorMessage = uiState.errorMessage,
                onDismiss = { viewModel.onDismissPasteDialog() },
                onConfirm = { cookie -> viewModel.onPasteCookie(cookie) },
            )
        }
    }
}
