package com.personal.apptruyen.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Mô hình một quy tắc thay thế chữ (hỗ trợ bật/tắt phân biệt hoa thường).
 *
 * @property from Từ gốc cần tìm để thay thế
 * @property to Từ dùng để thay thế
 * @property caseSensitive `true` nếu phân biệt chữ hoa/thường (`ignoreCase = false`),
 *   `false` nếu không phân biệt chữ hoa/thường (`ignoreCase = true`).
 */
data class TextReplacementItem(
    val from: String,
    val to: String,
    val caseSensitive: Boolean = false,
)

/**
 * Helper xử lý thay thế chữ bị biến dạng (filter) trong nội dung truyện.
 * Lớp 1: Danh sách mặc định (hardcode, không xóa được)
 * Lớp 2: User tự thêm (lưu DataStore / Room dạng JSON)
 */
object TextReplacementHelper {

    /**
     * Giá trị mặc định của `caseSensitive` khi nạp JSON cũ chưa có khóa `"caseSensitive"`
     * (theo quyết định sản phẩm PD-1 Phương án B: chuyển toàn bộ từ cũ sang không phân biệt hoa/thường).
     */
    const val LEGACY_JSON_DEFAULT_CASE_SENSITIVE = false

    /**
     * Các ký tự dạng "dot" mà trang truyện có thể dùng để filter từ nhạy cảm.
     * VD: "ch·ết", "gi•ết", "hi⋅ếp"
     * Không ký tự nào xuất hiện trong tiếng Việt bình thường → xóa hết.
     */
    private val DOTS_TO_REMOVE =
        listOf(
            "·", // Middle dot (U+00B7) — phổ biến nhất
            "•", // Bullet (U+2022)
            "⋅", // Dot operator (U+22C5)
        )

    /**
     * Áp dụng danh sách [TextReplacementItem] (hỗ trợ `caseSensitive`) lên text.
     */
    fun applyReplacementItems(
        text: String,
        enabled: Boolean,
        customReplacements: List<TextReplacementItem> = emptyList(),
    ): String {
        if (!enabled) return text

        var result = text
        for (dot in DOTS_TO_REMOVE) {
            result = result.replace(dot, "")
        }

        for (item in customReplacements) {
            if (item.from.isNotBlank()) {
                result = result.replace(item.from, item.to, ignoreCase = !item.caseSensitive)
            }
        }

        return result
    }

    /**
     * Áp dụng cả 2 lớp thay thế với [TextReplacementItem]: global (chạy trước) + per-story (chạy sau).
     */
    fun applyAllReplacementItems(
        text: String,
        globalEnabled: Boolean,
        globalCustomReplacements: List<TextReplacementItem>,
        storyEnabled: Boolean,
        storyCustomReplacements: List<TextReplacementItem>,
    ): String {
        var result = applyReplacementItems(text, globalEnabled, globalCustomReplacements)

        if (storyEnabled) {
            for (item in storyCustomReplacements) {
                if (item.from.isNotBlank()) {
                    result = result.replace(item.from, item.to, ignoreCase = !item.caseSensitive)
                }
            }
        }

        return result
    }

    /**
     * Parse JSON string thành danh sách [TextReplacementItem].
     * Tương thích ngược với JSON cũ chỉ có `{"from":"abc","to":"xyz"}`.
     */
    fun parseReplacementItems(json: String?): List<TextReplacementItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val caseSensitive =
                    if (obj.has("caseSensitive")) {
                        obj.optBoolean("caseSensitive", false)
                    } else {
                        LEGACY_JSON_DEFAULT_CASE_SENSITIVE
                    }
                TextReplacementItem(
                    from = obj.getString("from"),
                    to = obj.getString("to"),
                    caseSensitive = caseSensitive,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serialize danh sách [TextReplacementItem] thành JSON string.
     */
    fun serializeReplacementItems(list: List<TextReplacementItem>): String {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("from", item.from)
            obj.put("to", item.to)
            obj.put("caseSensitive", item.caseSensitive)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Áp dụng thay thế lên text (overload tương thích ngược dùng [Pair]).
     * @param text Nội dung gốc
     * @param enabled Có bật thay chữ không
     * @param customReplacements Danh sách custom từ user
     * @return Text đã xử lý
     */
    fun applyReplacements(
        text: String,
        enabled: Boolean,
        customReplacements: List<Pair<String, String>> = emptyList(),
    ): String {
        if (!enabled) return text

        // Xóa toàn bộ các ký tự dot-like khỏi nội dung
        var result = text
        for (dot in DOTS_TO_REMOVE) {
            result = result.replace(dot, "")
        }

        // Áp dụng danh sách custom (plain text replace)
        for ((from, to) in customReplacements) {
            if (from.isNotBlank()) {
                result = result.replace(from, to)
            }
        }

        return result
    }

    /**
     * Áp dụng cả 2 lớp thay thế: global (chạy trước) + per-story (chạy sau).
     * Global ưu tiên = chạy trước → output của global là input cho per-story.
     */
    fun applyAllReplacements(
        text: String,
        globalEnabled: Boolean,
        globalCustomReplacements: List<Pair<String, String>>,
        storyEnabled: Boolean,
        storyCustomReplacements: List<Pair<String, String>>,
    ): String {
        // Bước 1: Global (ưu tiên — chạy trước)
        var result = applyReplacements(text, globalEnabled, globalCustomReplacements)

        // Bước 2: Per-story (chạy sau)
        if (storyEnabled) {
            for ((from, to) in storyCustomReplacements) {
                if (from.isNotBlank()) {
                    result = result.replace(from, to)
                }
            }
        }

        return result
    }

    /**
     * Parse JSON string thành danh sách cặp thay thế.
     * Format: [{"from":"abc","to":"xyz"}, ...]
     */
    fun parseCustomReplacements(json: String?): List<Pair<String, String>> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                obj.getString("from") to obj.getString("to")
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serialize danh sách cặp thay thế thành JSON string.
     */
    @Deprecated(
        message = "Use serializeReplacementItems to preserve caseSensitive flag",
        replaceWith =
            ReplaceWith(
                "serializeReplacementItems(list.map { TextReplacementItem(it.first, it.second, false) })",
            ),
    )
    fun serializeCustomReplacements(list: List<Pair<String, String>>): String {
        val array = JSONArray()
        for ((from, to) in list) {
            val obj = JSONObject()
            obj.put("from", from)
            obj.put("to", to)
            array.put(obj)
        }
        return array.toString()
    }
}
