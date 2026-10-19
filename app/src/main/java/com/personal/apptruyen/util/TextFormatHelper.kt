package com.personal.apptruyen.util

/**
 * Helper hỗ trợ tự động format nội dung chương truyện.
 * - Xóa chuỗi ký tự đặc biệt vô nghĩa.
 * - Sửa lỗi khoảng trắng quanh dấu câu.
 * - Tách dòng câu thoại.
 * - Ngắt đoạn văn quá dài (trên 5 câu).
 */
object TextFormatHelper {
    private val FOUR_OR_MORE_DOTS_REGEX = Regex("\\.{4,}")
    private val SPACED_ELLIPSIS_REGEX = Regex("\\.{3,}\\s+\\.")
    private val MULTIPLE_EXCLAMATION_REGEX = Regex("!{2,}")
    private val MULTIPLE_QUESTION_REGEX = Regex("\\?{2,}")
    private val MIXED_INTERROGATIVE_REGEX = Regex("(!\\?|\\?!)")
    private val SPECIAL_CHARS_NOISE_REGEX = Regex("([*~^=+_%$#@&|/<>{\\[\\]\\\\])\\1{2,}")
    private val SPACE_BEFORE_PUNCTUATION_REGEX = Regex("(?<!https?:)\\s+([.,?!:;])")
    private val ACRONYM_DOT_REGEX = Regex("(?<=\\p{Lu})\\.(?=\\p{Lu})")
    private val URL_OR_DOMAIN_REGEX =
        Regex(
            "(?i)\\b(?:https?://\\S+|www\\.\\S+|[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.(?:com|net|org|vn|io|me|info|pics|today|la|cc|tv|co|edu|gov|html|htm|php)\\b(?:/\\S*)?)",
        )
    private val SPACE_AFTER_EXCL_QUEST_SEMI_REGEX = Regex("([?!;])(?=[\\p{L}\\p{N}])")
    private val SPACE_AFTER_COMMA_COLON_REGEX = Regex("([,:])(?=\\p{L}|(?<!\\p{N}[,:])\\p{N})")
    private val SPACE_AFTER_DOT_REGEX = Regex("\\.(?=\\p{L}|(?<!\\p{N}\\.)\\p{N})")
    private val QUOTED_DIALOGUE_REGEX = Regex("(\"[^\"]+\")")
    private val PARAGRAPH_SPLIT_REGEX = Regex("\n\\s*\n")
    private val INLINE_DIALOGUE_END_REGEX = Regex("([?!])\\s+(?=[\\p{L}\"\\-–—])")
    private val SENTENCE_SPLIT_REGEX =
        Regex("(?<=(?:\\.{3}|(?<!\\b\\p{L})\\.))\\s+(?=[\\p{Lu}\\p{Ll}\\p{N}\"\\-–—])")

    fun formatChapter(text: String): String {
        if (text.isBlank()) return text

        var result = text

        // Bảo vệ tạm các URL hoặc tên miền khỏi việc bị xóa // hoặc bị chèn khoảng trắng sau dấu . / :
        val protectedUrls = mutableListOf<String>()
        if (URL_OR_DOMAIN_REGEX.containsMatchIn(result)) {
            result =
                URL_OR_DOMAIN_REGEX.replace(result) { match ->
                    val idx = protectedUrls.size
                    protectedUrls.add(match.value)
                    "\u0001$idx\u0001"
                }
        }

        // 1. Chuẩn hóa dấu chấm và dấu chấm lửng
        // ... . hoặc .... -> ...
        result = result.replace(FOUR_OR_MORE_DOTS_REGEX, "...")
        result = result.replace(SPACED_ELLIPSIS_REGEX, "...")

        // 2. Chuẩn hóa dấu chấm than và hỏi chấm
        // !?!, !!!, ??? -> !, ?
        result = result.replace(MULTIPLE_EXCLAMATION_REGEX, "!")
        result = result.replace(MULTIPLE_QUESTION_REGEX, "?")
        result = result.replace(MIXED_INTERROGATIVE_REGEX, "?")

        // 3. Xóa các chuỗi ký tự đặc biệt vô nghĩa (>= 3 ký tự liên tiếp) ảnh hưởng TTS
        // Ngoại trừ dấu chấm (.) đã xử lý ở trên
        result = result.replace(SPECIAL_CHARS_NOISE_REGEX, "")

        // 4. Xóa khoảng trắng thừa trước dấu câu (.,?!:;)
        // VD: "chữ ," -> "chữ,"
        result = result.replace(SPACE_BEFORE_PUNCTUATION_REGEX, "$1")

        // 5. Thêm khoảng trắng sau dấu câu nếu liền sau là chữ cái hoặc số
        // Ngoại trừ:
        // - Dấu chấm nối giữa các chữ hoa (từ viết tắt như S.H.I.E.L.D)
        // - Số thập phân / phân cách hàng nghìn (3.14, 1.000, 1,5)
        // - Giờ / tỷ số (12:30, 3:2)
        result = result.replace(ACRONYM_DOT_REGEX, "\u0000")
        result = result.replace(SPACE_AFTER_EXCL_QUEST_SEMI_REGEX, "$1 ")
        result = result.replace(SPACE_AFTER_COMMA_COLON_REGEX, "$1 ")
        result = result.replace(SPACE_AFTER_DOT_REGEX, ". ")
        result = result.replace("\u0000", ".")

        if (protectedUrls.isNotEmpty()) {
            for (idx in protectedUrls.indices) {
                result = result.replace("\u0001$idx\u0001", protectedUrls[idx])
            }
        }

        // 6. Tách câu thoại trong ngoặc kép ra dòng riêng
        // Tránh tạo ra nhiều \n liên tiếp
        result = result.replace(QUOTED_DIALOGUE_REGEX, "\n$1\n")

        // 7. Các câu kết thúc bằng ! hoặc ? cũng phải nằm ở dòng riêng (câu thoại)
        // Tức là sau ! hoặc ? thì xuống dòng. Nhưng không ngắt dòng nếu nằm trong ngoặc kép.
        val linesForStep8 = result.split(PARAGRAPH_SPLIT_REGEX)
        result =
            linesForStep8.joinToString("\n\n") { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("\"")) {
                    line
                } else {
                    line.replace(INLINE_DIALOGUE_END_REGEX, "$1\n")
                }
            }

        // 9. Giới hạn đoạn văn không quá 5 câu (câu kết thúc bằng . hoặc ...)
        val paragraphs = result.split(PARAGRAPH_SPLIT_REGEX)
        val finalParagraphs = mutableListOf<String>()

        for (p in paragraphs) {
            val trimmed = p.trim()
            if (trimmed.isEmpty()) continue

            // Nếu đoạn văn đã là câu thoại (ngoặc kép hoặc gạch đầu dòng) thì không cần ngắt
            if (trimmed.startsWith("\"") ||
                trimmed.startsWith("-") ||
                trimmed.startsWith("–") ||
                trimmed.startsWith("—")
            ) {
                finalParagraphs.add(trimmed)
                continue
            }

            // Tách các câu dựa vào khoảng trắng sau dấu chấm/chấm lửng
            // Dấu chấm lửng (\\.{3}) hoặc dấu chấm (\\.)
            // Sử dụng (?<!\\b\\p{L}) để bỏ qua dấu chấm đứng sau 1 chữ cái đơn lẻ (ví dụ S. H. I. E. L. D.)
            val sentences = trimmed.split(SENTENCE_SPLIT_REGEX)

            if (sentences.size > 5) {
                sentences.chunked(5).forEach { chunk ->
                    finalParagraphs.add(chunk.joinToString(" ").trim())
                }
            } else {
                finalParagraphs.add(trimmed)
            }
        }

        // 10. Dọn dẹp khoảng trắng dư thừa và nối bằng 2 ký tự \n để tạo đoạn văn chuẩn
        return finalParagraphs.joinToString("\n\n")
    }
}
