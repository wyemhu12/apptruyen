package com.personal.apptruyen.util

/**
 * Helper hỗ trợ tự động format nội dung chương truyện.
 * - Xóa chuỗi ký tự đặc biệt vô nghĩa.
 * - Sửa lỗi khoảng trắng quanh dấu câu.
 * - Tách dòng câu thoại.
 * - Ngắt đoạn văn quá dài (trên 5 câu).
 */
object TextFormatHelper {

    fun formatChapter(text: String): String {
        if (text.isBlank()) return text

        var result = text

        // 1. Chuẩn hóa dấu chấm và dấu chấm lửng
        // ... . hoặc .... -> ...
        result = result.replace(Regex("\\.{4,}"), "...")
        result = result.replace(Regex("\\.{3,}\\s+\\."), "...")

        // 2. Chuẩn hóa dấu chấm than và hỏi chấm
        // !?!, !!!, ??? -> !, ?
        result = result.replace(Regex("!{2,}"), "!")
        result = result.replace(Regex("\\?{2,}"), "?")
        result = result.replace(Regex("(!\\?|\\?!)"), "?")

        // 3. Xóa các chuỗi ký tự đặc biệt vô nghĩa (>= 3 ký tự liên tiếp) ảnh hưởng TTS
        // Ngoại trừ dấu chấm (.) đã xử lý ở trên
        result = result.replace(Regex("([*~^=+_%$#@&|/<>{\\[\\]\\\\])\\1{2,}"), "")

        // 4. Xóa khoảng trắng thừa trước dấu câu (.,?!:;)
        // VD: "chữ ," -> "chữ,"
        result = result.replace(Regex("\\s+([.,?!:;])"), "$1")

        // 5. Thêm khoảng trắng sau dấu câu nếu liền sau là chữ cái hoặc số
        // Ngoại trừ dấu chấm nối giữa các chữ hoa (từ viết tắt như S.H.I.E.L.D)
        result = result.replace(Regex("(?<=\\p{Lu})\\.(?=\\p{Lu})"), "\u0000")
        result = result.replace(Regex("([.,?!:;])(?=[\\p{L}\\p{N}])"), "$1 ")
        result = result.replace("\u0000", ".")

        // 6. Tách câu thoại trong ngoặc kép ra dòng riêng
        // Tránh tạo ra nhiều \n liên tiếp
        result = result.replace(Regex("(\"[^\"]+\")"), "\n$1\n")

        // 7. Gạch đầu dòng ở dòng riêng (dấu gạch nối tiếp theo là khoảng trắng rồi chữ)
        result = result.replace(Regex("(?<!\\n)\\s*([\\-–—]\\s*[\\p{L}])"), "\n$1")

        // 8. Các câu kết thúc bằng ! hoặc ? cũng phải nằm ở dòng riêng (câu thoại)
        // Tức là sau ! hoặc ? thì xuống dòng. Nhưng không ngắt dòng nếu nằm trong ngoặc kép.
        val linesForStep8 = result.split("\n")
        result =
            linesForStep8.joinToString("\n") { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("\"")) {
                    line
                } else {
                    line.replace(Regex("([?!])\\s+(?=[\\p{L}\"\\-–—])"), "$1\n")
                }
            }

        // 9. Giới hạn đoạn văn không quá 5 câu (câu kết thúc bằng . hoặc ...)
        val paragraphs = result.split("\n")
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
            val sentences =
                trimmed.split(
                    Regex("(?<=(?:\\.{3}|(?<!\\b\\p{L})\\.))\\s+(?=[\\p{Lu}\\p{Ll}\\p{N}\"\\-–—])"),
                )

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
