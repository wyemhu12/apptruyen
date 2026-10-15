# Kế hoạch triển khai: Đăng nhập nguồn truyện (WebView + Dán cookie) & WikidichScraper

> **Trạng thái**: Superseded
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm tính năng đăng nhập nguồn truyện (bằng WebView trong app và dán cookie dự phòng) và tích hợp nguồn truyện mới Wikidich (`wikicv.org`) vào hệ thống cào dữ liệu của AppTruyen.

**Architecture:** Sử dụng Clean Architecture + MVVM. Tầng Remote bổ sung `SourceAuthManager` quản lý phiên đăng nhập qua Android `CookieManager` và `SourceAuthConfig`. `WikidichScraper` kế thừa `BaseScraper`, cào dữ liệu qua `OkHttpClient` (đã gắn `WebViewCookieHandler`). UI bổ sung `SourceLoginScreen` (Jetpack Compose + `AndroidView(WebView)`), `PasteCookieDialog`, tích hợp vào Cài đặt và Tìm kiếm.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Android WebView, OkHttp 5, Jsoup, Hilt DI, Coroutines + StateFlow, JUnit 5 + MockK.

## Global Constraints

- Tuân thủ quy tắc `AGENTS.md` và `docs/architecture.md`.
- UI 100% tiếng Việt.
- Compose + Material 3 (không dùng XML layout).
- Không thêm dependency mới (dùng native `AndroidView` cho `WebView`).
- Mã nguồn package: `com.personal.apptruyen`.
- Chạy ktlintCheck và unit test sau mỗi task; commit thường xuyên.

---

### Task 1: Định nghĩa `LoginRequiredException` & `CookieStringParser`

**Files:**
- Modify: `app/src/main/java/com/personal/apptruyen/data/remote/ScraperException.kt`
- Create: `app/src/main/java/com/personal/apptruyen/data/remote/auth/CookieStringParser.kt`
- Create: `app/src/test/java/com/personal/apptruyen/data/remote/auth/CookieStringParserTest.kt`

**Interfaces:**
- Produces: `ScraperException.LoginRequiredException(val sourceId: String, message: String)`
- Produces: `CookieStringParser.parse(rawInput: String, defaultCookieName: String = "express.sid"): List<Pair<String, String>>`

- [ ] **Step 1: Viết failing test cho `CookieStringParserTest`**

```kotlin
package com.personal.apptruyen.data.remote.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CookieStringParserTest {

    @Test
    fun `parse empty or blank string returns empty list`() {
        assertTrue(CookieStringParser.parse("").isEmpty())
        assertTrue(CookieStringParser.parse("   ").isEmpty())
    }

    @Test
    fun `parse single raw value without equals assigns defaultCookieName`() {
        val result = CookieStringParser.parse("s%3Asessionid123456", defaultCookieName = "express.sid")
        assertEquals(1, result.size)
        assertEquals("express.sid" to "s%3Asessionid123456", result[0])
    }

    @Test
    fun `parse standard cookie header string`() {
        val input = "express.sid=s%3Aabc123; cf_clearance=xyz789; _ga=GA1.2.345"
        val result = CookieStringParser.parse(input)
        assertEquals(3, result.size)
        assertEquals("express.sid" to "s%3Aabc123", result[0])
        assertEquals("cf_clearance" to "xyz789", result[1])
        assertEquals("_ga" to "GA1.2.345", result[2])
    }

    @Test
    fun `parse string with Cookie prefix header`() {
        val input = "Cookie: express.sid=token_value_here; other=val"
        val result = CookieStringParser.parse(input)
        assertEquals(2, result.size)
        assertEquals("express.sid" to "token_value_here", result[0])
        assertEquals("other" to "val", result[1])
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.auth.CookieStringParserTest"`
Expected: FAIL (Unresolved reference: auth / CookieStringParser)

- [ ] **Step 3: Thêm `LoginRequiredException` vào `ScraperException.kt` và tạo `CookieStringParser.kt`**

Trong `app/src/main/java/com/personal/apptruyen/data/remote/ScraperException.kt`, bổ sung subclass:
```kotlin
    class LoginRequiredException(
        val sourceId: String,
        message: String = "Nguồn yêu cầu đăng nhập tài khoản để xem nội dung",
        cause: Throwable? = null,
    ) : ScraperException(message, cause)
```

Tạo file `app/src/main/java/com/personal/apptruyen/data/remote/auth/CookieStringParser.kt`:
```kotlin
package com.personal.apptruyen.data.remote.auth

object CookieStringParser {
    /**
     * Parse chuỗi cookie người dùng dán vào thành danh sách các cặp (tên, giá trị).
     * Hỗ trợ các định dạng:
     * - "name1=val1; name2=val2"
     * - "Cookie: name1=val1; name2=val2"
     * - Chỉ dán duy nhất giá trị session token (không chứa dấu '=') -> gán tên mặc định [defaultCookieName]
     */
    fun parse(rawInput: String, defaultCookieName: String = "express.sid"): List<Pair<String, String>> {
        val trimmed = rawInput.trim().removePrefix("Cookie:").removePrefix("cookie:").trim()
        if (trimmed.isBlank()) return emptyList()

        if (!trimmed.contains("=")) {
            return listOf(defaultCookieName to trimmed)
        }

        return trimmed.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { part ->
                val eqIdx = part.indexOf('=')
                if (eqIdx in 1 until part.length) {
                    val name = part.substring(0, eqIdx).trim()
                    val value = part.substring(eqIdx + 1).trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) name to value else null
                } else {
                    null
                }
            }
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.auth.CookieStringParserTest"`
Expected: PASS

- [ ] **Step 5: Format ktlint & Commit**

```powershell
.\gradlew.bat ktlintFormat
git add app/src/main/java/com/personal/apptruyen/data/remote/ScraperException.kt app/src/main/java/com/personal/apptruyen/data/remote/auth/CookieStringParser.kt app/src/test/java/com/personal/apptruyen/data/remote/auth/CookieStringParserTest.kt
git commit -m "feat(auth): add LoginRequiredException and CookieStringParser with unit tests"
```

---

### Task 2: Tạo `SourceAuthConfig` và `SourceAuthManager`

**Files:**
- Create: `app/src/main/java/com/personal/apptruyen/data/remote/auth/SourceAuthConfig.kt`
- Create: `app/src/main/java/com/personal/apptruyen/data/remote/auth/SourceAuthManager.kt`
- Modify: `app/src/main/java/com/personal/apptruyen/di/AppModule.kt`

**Interfaces:**
- Produces: `SourceAuthConfig` (chứa metadata xác thực nguồn: domain, loginUrl, loginReferer, sessionCookies, loggedOutMarker)
- Produces: `SourceAuthManager` (`isLoggedIn(sourceId): Boolean`, `getAuthState(sourceId): StateFlow<Boolean>`, `importCookies(sourceId, rawInput): Boolean`, `logout(sourceId)`, `markSessionExpired(sourceId)`)

- [ ] **Step 1: Tạo `SourceAuthConfig.kt`**

```kotlin
package com.personal.apptruyen.data.remote.auth

data class SourceAuthConfig(
    val sourceId: String,
    val displayName: String,
    val baseUrl: String,
    val loginUrl: String,
    val loginReferer: String? = null,
    val domains: List<String>,
    val sessionCookieNames: List<String>,
    val primaryCookieName: String,
    val loggedOutMarker: String,
) {
    companion object {
        val WIKIDICH = SourceAuthConfig(
            sourceId = "wikidich",
            displayName = "Wikidich",
            baseUrl = "https://wikicv.org",
            loginUrl = "https://wikicv.org/auth/dth",
            loginReferer = "https://wikicv.org/",
            domains = listOf("https://wikicv.org", "https://wikicv.net", "wikicv.org", ".wikicv.org", "forum.dichtienghoa.com"),
            sessionCookieNames = listOf("express.sid"),
            primaryCookieName = "express.sid",
            loggedOutMarker = "data-action=\"login\"",
        )

        val ALL_CONFIGS = mapOf(
            WIKIDICH.sourceId to WIKIDICH,
        )

        fun forSource(sourceId: String): SourceAuthConfig? = ALL_CONFIGS[sourceId]
    }
}
```

- [ ] **Step 2: Tạo `SourceAuthManager.kt`**

```kotlin
package com.personal.apptruyen.data.remote.auth

import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceAuthManager @Inject constructor() {

    private val cookieManager: CookieManager by lazy {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
    }

    private val _authStates = mutableMapOf<String, MutableStateFlow<Boolean>>()

    fun getAuthState(sourceId: String): StateFlow<Boolean> {
        return _authStates.getOrPut(sourceId) {
            MutableStateFlow(isLoggedIn(sourceId))
        }.asStateFlow()
    }

    fun isLoggedIn(sourceId: String): Boolean {
        val config = SourceAuthConfig.forSource(sourceId) ?: return false
        for (domain in config.domains) {
            val cookieStr = cookieManager.getCookie(domain) ?: continue
            if (cookieStr.isBlank()) continue
            val hasSessionCookie = config.sessionCookieNames.any { name ->
                cookieStr.contains("$name=", ignoreCase = true)
            }
            if (hasSessionCookie) {
                return true
            }
        }
        return false
    }

    fun importCookies(sourceId: String, rawInput: String): Boolean {
        val config = SourceAuthConfig.forSource(sourceId) ?: return false
        val pairs = CookieStringParser.parse(rawInput, defaultCookieName = config.primaryCookieName)
        if (pairs.isEmpty()) return false

        for ((name, value) in pairs) {
            val cookieString = "$name=$value; Path=/; Domain=.${config.baseUrl.removePrefix("https://")}; Secure; Max-Age=2592000"
            cookieManager.setCookie(config.baseUrl, cookieString)
        }
        cookieManager.flush()

        val loggedIn = isLoggedIn(sourceId)
        _authStates[sourceId]?.value = loggedIn
        Timber.d("Imported cookies for $sourceId: success=$loggedIn")
        return loggedIn
    }

    fun onLoginDetected(sourceId: String) {
        cookieManager.flush()
        _authStates[sourceId]?.value = true
        Timber.d("Login detected and confirmed for $sourceId")
    }

    fun markSessionExpired(sourceId: String) {
        _authStates[sourceId]?.value = false
        Timber.w("Session marked as expired for $sourceId")
    }

    fun logout(sourceId: String) {
        val config = SourceAuthConfig.forSource(sourceId) ?: return
        for (domain in config.domains) {
            cookieManager.setCookie(domain, "${config.primaryCookieName}=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
        }
        cookieManager.flush()
        _authStates[sourceId]?.value = false
        Timber.d("Logged out from $sourceId")
    }
}
```

- [ ] **Step 3: Cập nhật `AppModule.kt` để inject `SourceAuthManager`**

Kiểm tra `di/AppModule.kt`, `SourceAuthManager` dùng `@Inject constructor()` và `@Singleton` nên Hilt tự động cung cấp, không cần thêm thủ công `@Provides` trừ khi cần custom.

- [ ] **Step 4: Kiểm tra build**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Format ktlint & Commit**

```powershell
.\gradlew.bat ktlintFormat
git add app/src/main/java/com/personal/apptruyen/data/remote/auth/
git commit -m "feat(auth): add SourceAuthConfig and SourceAuthManager"
```

---

### Task 3: Refactor `WebViewCookieHandler` & kết nối với `SourceAuthManager`

**Files:**
- Modify: `app/src/main/java/com/personal/apptruyen/data/remote/cookie/WebViewCookieHandler.kt`
- Modify: `app/src/main/java/com/personal/apptruyen/di/AppModule.kt`

- [ ] **Step 1: Sửa `WebViewCookieHandler.kt` để inject `SourceAuthManager` hoặc giữ vai trò CookieJar sạch**

Bỏ hàm hardcode `isLoggedIn()` của LacaTruyen trong `WebViewCookieHandler.kt` và ủy nhiệm cho `SourceAuthManager`:
```kotlin
package com.personal.apptruyen.data.remote.cookie

import android.webkit.CookieManager
import com.personal.apptruyen.data.remote.auth.SourceAuthManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebViewCookieHandler @Inject constructor(
    private val authManager: SourceAuthManager,
) : CookieJar {

    private val cookieManager: CookieManager by lazy {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        val urlString = url.toString()
        cookies.forEach { cookie ->
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookieString = cookieManager.getCookie(urlString) ?: return emptyList()
        return cookieString
            .split(";")
            .mapNotNull { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { Cookie.parse(url, it) }
    }

    fun isLoggedIn(sourceId: String): Boolean = authManager.isLoggedIn(sourceId)

    fun clearCookies(domain: String? = null) {
        if (domain != null) {
            cookieManager.setCookie(domain, "")
        } else {
            cookieManager.removeAllCookies(null)
        }
        cookieManager.flush()
    }
}
```

- [ ] **Step 2: Cập nhật `AppModule.kt`**

Đảm bảo `provideWebViewCookieHandler(authManager: SourceAuthManager)` nhận `SourceAuthManager` và truyền vào `WebViewCookieHandler`.

- [ ] **Step 3: Chạy test toàn bộ để đảm bảo không gãy DI**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 4: Format ktlint & Commit**

```powershell
.\gradlew.bat ktlintFormat
git add app/src/main/java/com/personal/apptruyen/data/remote/cookie/WebViewCookieHandler.kt app/src/main/java/com/personal/apptruyen/di/AppModule.kt
git commit -m "refactor(cookie): delegate authentication checks to SourceAuthManager"
```

---

### Task 4: Triển khai `WikidichScraper` & HTML Parsing Tests

**Files:**
- Create: `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt`
- Create: `app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt`
- Create Test Fixtures:
  - `app/src/test/resources/wikidich/story_detail.html` (copy từ `build/wikicv_story.html`)
  - `app/src/test/resources/wikidich/chapter_content.html` (copy từ `build/wikicv_chap.html`)
  - `app/src/test/resources/wikidich/search_logged_out.html` (copy từ `build/wk_search.html`)

**Interfaces:**
- Implements: `StorySource` (`sourceId = "wikidich"`, `sourceName = "Wikidich"`, `baseUrl = "https://wikicv.org"`)

- [ ] **Step 1: Tạo test fixtures từ các file HTML đã cào thực tế**

Copy nội dung `build/wikicv_story.html` vào `app/src/test/resources/wikidich/story_detail.html`,
`build/wikicv_chap.html` vào `app/src/test/resources/wikidich/chapter_content.html`,
`build/wk_search.html` vào `app/src/test/resources/wikidich/search_logged_out.html`.

- [ ] **Step 2: Viết Unit Test `WikidichScraperTest.kt`**

```kotlin
package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.remote.auth.SourceAuthManager
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WikidichScraperTest {

    private val authManager: SourceAuthManager = mockk(relaxed = true)
    private val client: OkHttpClient = OkHttpClient()
    private lateinit var scraper: WikidichScraper

    @BeforeEach
    fun setup() {
        scraper = WikidichScraper(client, authManager)
    }

    @Test
    fun `parseStoryDetail parses title author genre and description correctly`() {
        val html = javaClass.getResourceAsStream("/wikidich/story_detail.html")!!.bufferedReader().readText()
        val doc = Jsoup.parse(html, "https://wikicv.org/truyen/hoa-anh-nguoi-qua-duong-giap-cung-muon-c-apUOovTaEF0DhxIe")
        val story = scraper.parseStoryDetailFromDoc(doc, "https://wikicv.org/truyen/hoa-anh-nguoi-qua-duong-giap-cung-muon-c-apUOovTaEF0DhxIe")

        assertEquals("Hỏa ảnh người qua đường Giáp cũng muốn c", story.title)
        assertFalse(story.coverUrl.isNullOrBlank())
        assertTrue(story.description.isNotBlank())
        assertEquals("wikidich", story.sourceId)
    }

    @Test
    fun `parseChapterContent extracts text paragraphs and strips ads`() {
        val html = javaClass.getResourceAsStream("/wikidich/chapter_content.html")!!.bufferedReader().readText()
        val doc = Jsoup.parse(html, "https://wikicv.org/truyen/sample/chuong-1")
        val content = scraper.parseChapterContentFromDoc(doc)

        assertTrue(content.contains("Uchiha Obito"))
        assertFalse(content.contains("Quét khối tên bạn muốn thêm"))
    }

    @Test
    fun `search throws LoginRequiredException when logged out marker is present and results are empty`() {
        every { authManager.isLoggedIn("wikidich") } returns false
        val html = javaClass.getResourceAsStream("/wikidich/search_logged_out.html")!!.bufferedReader().readText()
        val doc = Jsoup.parse(html, "https://wikicv.org/tim-kiem?qs=1&q=tien")

        assertThrows(ScraperException.LoginRequiredException::class.java) {
            scraper.parseSearchResultsFromDoc(doc)
        }
    }
}
```

- [ ] **Step 3: Chạy test để xác nhận fail**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.WikidichScraperTest"`
Expected: FAIL (Unresolved reference: WikidichScraper)

- [ ] **Step 4: Viết `WikidichScraper.kt`**

Tạo `app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt`:
Kế thừa `BaseScraper(client)`.
- `sourceId = "wikidich"`, `sourceName = "Wikidich"`, `baseUrl = "https://wikicv.org"`
- Override `userAgent = "Mozilla/5.0 (Linux; Android 14) Chrome/130.0.0.0 Mobile Safari/537.36"`
- Override `contentSelectors = listOf("#bookContentBody", ".book-content", "#chapter-content", ".chapter-content")`
- Override `adsSelectors = "script, .ads, .ad, iframe, noscript, .text-center, .add-name-tip"`
- Implement `parseStoryDetailFromDoc`, `parseChapterContentFromDoc`, `parseSearchResultsFromDoc`
- Thêm kiểm tra `loggedOutMarker`: nếu doc có `data-action="login"` và không có thẻ `.book-item` -> ném `ScraperException.LoginRequiredException("wikidich")`
- Không sử dụng in-memory cache cho URL tìm kiếm để tránh lưu kết quả rỗng khi chưa đăng nhập.

- [ ] **Step 5: Chạy test để xác nhận pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.personal.apptruyen.data.remote.WikidichScraperTest"`
Expected: PASS

- [ ] **Step 6: Format ktlint & Commit**

```powershell
.\gradlew.bat ktlintFormat
git add app/src/main/java/com/personal/apptruyen/data/remote/WikidichScraper.kt app/src/test/java/com/personal/apptruyen/data/remote/WikidichScraperTest.kt app/src/test/resources/wikidich/
git commit -m "feat(scraper): implement WikidichScraper with unit tests and fixtures"
```

---

### Task 5: Tạo `SourceLoginViewModel`, `SourceLoginScreen` & `PasteCookieDialog`

**Files:**
- Create: `app/src/main/java/com/personal/apptruyen/ui/login/SourceLoginViewModel.kt`
- Create: `app/src/main/java/com/personal/apptruyen/ui/login/SourceLoginScreen.kt`
- Create: `app/src/main/java/com/personal/apptruyen/ui/login/PasteCookieDialog.kt`

**Interfaces:**
- Produces: `SourceLoginScreen(sourceId: String, onNavigateBack: () -> Unit)`
- Produces: `SourceLoginViewModel` (quản lý trạng thái: `isLoggedIn`, `isLoading`, `showPasteDialog`, `importCookie(text)`)

- [ ] **Step 1: Viết `SourceLoginViewModel.kt`**

```kotlin
package com.personal.apptruyen.ui.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.remote.auth.SourceAuthConfig
import com.personal.apptruyen.data.remote.auth.SourceAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourceLoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authManager: SourceAuthManager,
) : ViewModel() {

    val sourceId: String = checkNotNull(savedStateHandle["sourceId"])
    val config: SourceAuthConfig = checkNotNull(SourceAuthConfig.forSource(sourceId)) {
        "Không tìm thấy cấu hình nguồn cho sourceId: $sourceId"
    }

    val isLoggedIn: StateFlow<Boolean> = authManager.getAuthState(sourceId)

    private val _showPasteDialog = MutableStateFlow(false)
    val showPasteDialog: StateFlow<Boolean> = _showPasteDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun onPageFinished(url: String) {
        if (authManager.isLoggedIn(sourceId)) {
            authManager.onLoginDetected(sourceId)
        }
    }

    fun openPasteDialog() {
        _showPasteDialog.value = true
        _errorMessage.value = null
    }

    fun dismissPasteDialog() {
        _showPasteDialog.value = false
        _errorMessage.value = null
    }

    fun importCookieString(rawCookie: String) {
        val success = authManager.importCookies(sourceId, rawCookie)
        if (success) {
            _showPasteDialog.value = false
            _errorMessage.value = null
        } else {
            _errorMessage.value = "Chuỗi cookie không hợp lệ hoặc thiếu cookie phiên (${config.primaryCookieName})"
        }
    }

    fun logout() {
        authManager.logout(sourceId)
    }
}
```

- [ ] **Step 2: Viết `PasteCookieDialog.kt`**

Compose dialog cho phép dán cookie:
- `OutlinedTextField` nhập cookie.
- Hướng dẫn ngắn cách lấy cookie trên PC: "Mở trình duyệt PC -> F12 -> tab Application (hoặc Storage) -> Cookies -> tìm '${config.primaryCookieName}'".
- Nút "Hủy" và "Lưu cookie".

- [ ] **Step 3: Viết `SourceLoginScreen.kt`**

Màn hình Compose có:
- TopAppBar hiển thị tiêu đề "Đăng nhập ${config.displayName}", nút Back, icon Action "Dán cookie" và "Làm mới".
- Banner trạng thái: Nếu `isLoggedIn == true`, hiện thanh xanh "Đã đăng nhập thành công" và nút "Hoàn tất".
- `AndroidView` chứa `WebView`:
  - `javaScriptEnabled = true`, `domStorageEnabled = true`, `databaseEnabled = true`.
  - Bật nhận third-party cookies: `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)`.
  - Tải URL: `webView.loadUrl(config.loginUrl, config.loginReferer?.let { mapOf("Referer" to it) } ?: emptyMap())`.
  - `WebViewClient`: override `onPageFinished` -> gọi `viewModel.onPageFinished(url)`.
- Hiển thị `PasteCookieDialog` khi `showPasteDialog == true`.

- [ ] **Step 4: Kiểm tra compile**

Run: `.\gradlew.bat compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Format ktlint & Commit**

```powershell
.\gradlew.bat ktlintFormat
git add app/src/main/java/com/personal/apptruyen/ui/login/
git commit -m "feat(ui): add SourceLoginScreen and PasteCookieDialog"
```

---

### Task 6: Tích hợp Navigation, Cài đặt, Tìm kiếm & ScraperModule

**Files:**
- Modify: `app/src/main/java/com/personal/apptruyen/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/personal/apptruyen/di/ScraperModule.kt`
- Modify: `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/personal/apptruyen/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/personal/apptruyen/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/personal/apptruyen/ui/search/SearchViewModel.kt`
- Modify: `app/src/main/java/com/personal/apptruyen/data/repository/StoryRepository.kt`

- [ ] **Step 1: Đăng ký `WikidichScraper` vào `ScraperModule.kt`**

```kotlin
    @Binds
    @IntoSet
    abstract fun bindWikidich(impl: WikidichScraper): StorySource
```

- [ ] **Step 2: Thêm route đăng nhập vào `AppNavigation.kt`**

Thêm route `source_login/{sourceId}`:
```kotlin
composable(
    route = "source_login/{sourceId}",
    arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
) {
    SourceLoginScreen(
        onNavigateBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 3: Bổ sung mục "Tài khoản nguồn truyện" trong `SettingsScreen.kt`**

- Hiển thị danh sách nguồn yêu cầu tài khoản (Wikidich).
- Hiển thị trạng thái: "Đã đăng nhập" (màu xanh) hoặc "Chưa đăng nhập" (màu xám).
- Nút "Đăng nhập" -> điều hướng đến `source_login/wikidich`.
- Nút "Đăng xuất" nếu đã đăng nhập.

- [ ] **Step 4: Xử lý `LoginRequiredException` trong `StoryRepository.kt` & `SearchViewModel`**

- Trong `StoryRepository.kt`: khi gọi `source.search()` song song, nếu một nguồn bắn ra `LoginRequiredException`, log cảnh báo và không tính nguồn đó vào danh sách lỗi làm hỏng toàn bộ tìm kiếm. Phát event hoặc lưu lại nguồn cần đăng nhập.
- Trong `SearchScreen.kt`: nếu kết quả tìm kiếm của Wikidich báo cần đăng nhập, hiện một banner nhẹ ở đầu kết quả: "Wikidich yêu cầu đăng nhập để tìm kiếm. [Đăng nhập ngay]".

- [ ] **Step 5: Kiểm tra compile & Unit Test**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 6: Format ktlint & Commit**

```powershell
.\gradlew.bat ktlintFormat
git add app/src/main/java/com/personal/apptruyen/
git commit -m "feat(integration): wire Wikidich to ScraperModule, Navigation, Settings and Search"
```

---

### Task 7: Cập nhật tài liệu kỹ thuật & Hoàn thiện xác minh

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/scraping.md`
- Modify: `docs/progress.md`
- Modify: `docs/changelog.md`

- [ ] **Step 1: Cập nhật `docs/architecture.md`**
- Thêm `SourceAuthManager`, `SourceAuthConfig`, `CookieStringParser` vào sơ đồ Remote Layer.
- Thêm `WikidichScraper` vào danh sách `StorySource`.
- Thêm màn hình `SourceLoginScreen` vào UI Layer.
- Cập nhật timestamp.

- [ ] **Step 2: Cập nhật `docs/scraping.md`**
- Thêm mục mới: **Wikidich (`wikicv.org`)**.
- Ghi rõ URL Patterns: chi tiết truyện, đọc chương, tìm kiếm.
- CSS Selectors: `#bookContentBody`, `.book-content`, `.book-item`.
- Ghi rõ cơ chế xác thực: SSO qua `forum.dichtienghoa.com/dth/auth` (bắt buộc Referer), session cookie `express.sid`.

- [ ] **Step 3: Cập nhật `docs/progress.md` & `docs/changelog.md`**
- Đánh dấu hoàn thành tính năng đăng nhập nguồn và tích hợp Wikidich.
- Thêm entry vào `[Unreleased]` trong changelog.

- [ ] **Step 4: Chạy kiểm tra lint và build cuối cùng**

Run: `.\gradlew.bat ktlintCheck; .\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL, không có lint error.

- [ ] **Step 5: Commit tài liệu**

```powershell
git add docs/
git commit -m "docs: update architecture, scraping, progress and changelog for Wikidich and source auth"
```
