package com.personal.apptruyen.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper xử lý thay thế chữ bị biến dạng (filter) trong nội dung truyện.
 * Lớp 1: Danh sách mặc định (hardcode, không xóa được)
 * Lớp 2: User tự thêm (lưu DataStore dạng JSON)
 */
object TextReplacementHelper {

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
     * Áp dụng thay thế lên text.
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
