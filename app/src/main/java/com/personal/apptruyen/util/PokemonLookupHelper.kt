package com.personal.apptruyen.util

import com.personal.apptruyen.data.model.PokemonCatalog
import com.personal.apptruyen.data.model.PokemonEntry
import com.personal.apptruyen.data.model.PokemonForm
import com.personal.apptruyen.data.model.PokemonMatchRange
import com.personal.apptruyen.data.model.PokemonNameLanguage
import kotlinx.serialization.json.Json

class PokemonMatcher private constructor(
    private val root: TrieNode,
    val entryCount: Int,
) {
    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var matchEntry: PokemonEntry? = null
        var matchFormIndex: Int = 0
    }

    fun findMatches(text: String): List<PokemonMatchRange> {
        if (text.isEmpty()) return emptyList()
        val results = ArrayList<PokemonMatchRange>()
        val length = text.length
        var i = 0

        while (i < length) {
            var currentNode = root
            var j = i
            var bestEnd = -1
            var bestEntry: PokemonEntry? = null
            var bestFormIndex = 0

            while (j < length) {
                val nextNode = currentNode.children[Character.toLowerCase(text[j])] ?: break
                currentNode = nextNode
                j++
                val candidateEntry = currentNode.matchEntry
                if (candidateEntry != null && PokemonLookupHelper.isWordBoundary(text, i, j)) {
                    bestEnd = j
                    bestEntry = candidateEntry
                    bestFormIndex = currentNode.matchFormIndex
                }
            }

            if (bestEnd != -1 && bestEntry != null) {
                results.add(
                    PokemonMatchRange(
                        start = i,
                        end = bestEnd,
                        matchedText = text.substring(i, bestEnd),
                        entry = bestEntry,
                        initialFormIndex = bestFormIndex,
                    ),
                )
                i = bestEnd
            } else {
                i++
            }
        }

        return results
    }

    companion object {
        fun fromCatalog(catalog: PokemonCatalog): PokemonMatcher {
            val root = TrieNode()

            fun insertKeyword(
                rawKeyword: String,
                entry: PokemonEntry,
                formIndex: Int,
                overwriteFormIndex: Boolean = false,
            ) {
                val keyword = rawKeyword.trim()
                if (PokemonLookupHelper.shouldExcludeAlias(keyword)) return
                var node = root
                for (idx in 0 until keyword.length) {
                    val ch = Character.toLowerCase(keyword[idx])
                    node = node.children.getOrPut(ch) { TrieNode() }
                }
                if (node.matchEntry == null) {
                    node.matchEntry = entry
                    node.matchFormIndex = formIndex
                } else if (overwriteFormIndex && node.matchEntry?.id == entry.id && node.matchFormIndex == 0 && formIndex > 0) {
                    node.matchFormIndex = formIndex
                }
            }

            for (entry in catalog.entries) {
                insertKeyword(entry.nameCn, entry, 0)
                insertKeyword(entry.nameVn, entry, 0)
                insertKeyword(entry.nameHv, entry, 0)
                insertKeyword(entry.nameEn, entry, 0)
                insertKeyword(entry.nameJp, entry, 0)
                for (alias in entry.aliases) {
                    insertKeyword(alias, entry, 0)
                }

                entry.forms.forEachIndexed { formIndex, form ->
                    val isVariantForm = formIndex > 0
                    insertKeyword(form.formCn, entry, formIndex, overwriteFormIndex = isVariantForm)
                    insertKeyword(form.formVn, entry, formIndex, overwriteFormIndex = isVariantForm)
                    insertKeyword(form.formHv, entry, formIndex, overwriteFormIndex = isVariantForm)

                    if (isVariantForm) {
                        for (variantAlias in PokemonLookupHelper.generateFormAliases(entry, form)) {
                            insertKeyword(variantAlias, entry, formIndex, overwriteFormIndex = true)
                        }
                    }
                }
            }

            return PokemonMatcher(root = root, entryCount = catalog.entries.size)
        }
    }
}

object PokemonLookupHelper {
    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    val EXCLUDED_COMMON_ALIASES: Set<String> =
        setOf(
            "ba ba",
            "baba",
            "bọt",
            "mộng",
            "cầu",
            "ấn",
            "hồ",
            "đá",
            "lửa",
            "nước",
            "cỏ",
            "điện",
            "băng",
            "rồng",
            "ma",
            "thép",
            "đất",
            "bay",
            "độc",
            "tiên",
            "thường",
        )

    private val TYPE_TRANSLATION_MAP: Map<String, String> =
        mapOf(
            "草" to "Hệ Cỏ",
            "cỏ" to "Hệ Cỏ",
            "hệ cỏ" to "Hệ Cỏ",
            "thảo" to "Hệ Cỏ",
            "毒" to "Hệ Độc",
            "độc" to "Hệ Độc",
            "hệ độc" to "Hệ Độc",
            "火" to "Hệ Lửa",
            "lửa" to "Hệ Lửa",
            "hệ lửa" to "Hệ Lửa",
            "hỏa" to "Hệ Lửa",
            "飞行" to "Hệ Bay",
            "bay" to "Hệ Bay",
            "hệ bay" to "Hệ Bay",
            "phi hành" to "Hệ Bay",
            "冰" to "Hệ Băng",
            "băng" to "Hệ Băng",
            "hệ băng" to "Hệ Băng",
            "超能力" to "Hệ Siêu Linh",
            "siêu linh" to "Hệ Siêu Linh",
            "hệ siêu linh" to "Hệ Siêu Linh",
            "siêu năng lực" to "Hệ Siêu Linh",
            "龙" to "Hệ Rồng",
            "rồng" to "Hệ Rồng",
            "hệ rồng" to "Hệ Rồng",
            "long" to "Hệ Rồng",
            "一般" to "Hệ Thường",
            "thường" to "Hệ Thường",
            "hệ thường" to "Hệ Thường",
            "bình thường" to "Hệ Thường",
            "格斗" to "Hệ Giác Đấu",
            "giác đấu" to "Hệ Giác Đấu",
            "hệ giác đấu" to "Hệ Giác Đấu",
            "cách đấu" to "Hệ Giác Đấu",
            "妖精" to "Hệ Tiên",
            "tiên" to "Hệ Tiên",
            "hệ tiên" to "Hệ Tiên",
            "yêu tinh" to "Hệ Tiên",
            "地面" to "Hệ Đất",
            "đất" to "Hệ Đất",
            "hệ đất" to "Hệ Đất",
            "địa diện" to "Hệ Đất",
            "岩石" to "Hệ Đá",
            "đá" to "Hệ Đá",
            "hệ đá" to "Hệ Đá",
            "nham thạch" to "Hệ Đá",
            "电" to "Hệ Điện",
            "điện" to "Hệ Điện",
            "hệ điện" to "Hệ Điện",
            "水" to "Hệ Nước",
            "nước" to "Hệ Nước",
            "hệ nước" to "Hệ Nước",
            "thủy" to "Hệ Nước",
            "虫" to "Hệ Côn Trùng",
            "côn trùng" to "Hệ Côn Trùng",
            "hệ côn trùng" to "Hệ Côn Trùng",
            "trùng" to "Hệ Côn Trùng",
            "钢" to "Hệ Thép",
            "thép" to "Hệ Thép",
            "hệ thép" to "Hệ Thép",
            "cương" to "Hệ Thép",
            "恶" to "Hệ Bóng Tối",
            "bóng tối" to "Hệ Bóng Tối",
            "hệ bóng tối" to "Hệ Bóng Tối",
            "ác" to "Hệ Bóng Tối",
            "幽灵" to "Hệ Ma",
            "鬼" to "Hệ Ma",
            "ma" to "Hệ Ma",
            "hệ ma" to "Hệ Ma",
            "u linh" to "Hệ Ma",
        )

    fun parseCatalogJson(jsonString: String): PokemonCatalog {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return PokemonCatalog()
        return jsonParser.decodeFromString(PokemonCatalog.serializer(), trimmed)
    }

    fun shouldExcludeAlias(keyword: String): Boolean {
        if (keyword.isBlank()) return true
        val normalized = buildLowercaseString(keyword)
        if (normalized in EXCLUDED_COMMON_ALIASES) return true
        val hasCjkOrKana = (0 until keyword.length).any { isCjkOrKana(keyword[it]) }
        if (!hasCjkOrKana && keyword.length < 3) return true
        return false
    }

    fun isCjkOrKana(ch: Char): Boolean =
        when (Character.UnicodeScript.of(ch.code)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            -> true
            else -> false
        }

    fun isWordBoundary(
        text: String,
        start: Int,
        end: Int,
    ): Boolean {
        if (start < 0 || end > text.length || start >= end) return false
        val firstChar = text[start]
        if (!isCjkOrKana(firstChar) && Character.isLetterOrDigit(firstChar)) {
            if (start > 0) {
                val prev = text[start - 1]
                if (!isCjkOrKana(prev) && Character.isLetterOrDigit(prev)) {
                    return false
                }
            }
        }

        val lastChar = text[end - 1]
        if (!isCjkOrKana(lastChar) && Character.isLetterOrDigit(lastChar)) {
            if (end < text.length) {
                val next = text[end]
                if (!isCjkOrKana(next) && Character.isLetterOrDigit(next)) {
                    return false
                }
            }
        }
        return true
    }

    fun formatTypeLabel(rawType: String): String {
        val cleaned = rawType.trim()
        if (cleaned.isEmpty()) return "Hệ Thường"
        val key = buildLowercaseString(cleaned)
        TYPE_TRANSLATION_MAP[key]?.let { return it }
        TYPE_TRANSLATION_MAP[cleaned]?.let { return it }
        return if (key.startsWith("hệ ")) {
            "Hệ " + cleaned.substring(3).trim()
        } else {
            "Hệ $cleaned"
        }
    }

    fun formatFormButtonLabel(
        entry: PokemonEntry,
        form: PokemonForm,
        index: Int,
        language: PokemonNameLanguage,
    ): String {
        if (index == 0) return "Mặc định"
        return when (language) {
            PokemonNameLanguage.HAN_VIET -> {
                var raw = form.formHv.ifBlank { form.formVn }
                if (entry.nameHv.isNotBlank()) {
                    raw = raw.replace(entry.nameHv, "", ignoreCase = true)
                }
                for (alias in entry.aliases) {
                    if (alias.isNotBlank() && raw.contains(alias, ignoreCase = true)) {
                        raw = raw.replace(alias, "", ignoreCase = true)
                    }
                }
                raw =
                    raw
                        .replace(Regex("(?i)\\b(dạng|hình thái)\\b"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim(' ', '-', '–', '—')
                raw.ifBlank { "Mặc định" }
            }
            PokemonNameLanguage.VIET_ROMAJI,
            PokemonNameLanguage.ENGLISH,
            -> {
                var raw = form.formVn.ifBlank { form.formHv }
                if (entry.nameVn.isNotBlank() && raw.contains(entry.nameVn, ignoreCase = true)) {
                    raw = raw.replace(entry.nameVn, "", ignoreCase = true)
                } else if (entry.nameEn.isNotBlank() && raw.contains(entry.nameEn, ignoreCase = true)) {
                    raw = raw.replace(entry.nameEn, "", ignoreCase = true)
                }
                raw =
                    raw
                        .replace(Regex("(?i)\\bdạng\\s+"), "")
                        .replace(Regex("\\s+"), " ")
                        .trim(' ', '-', '–', '—')
                raw.ifBlank { "Mặc định" }
            }
            PokemonNameLanguage.CHINESE -> {
                var raw = form.formCn.ifBlank { form.formVn }
                if (entry.nameCn.isNotBlank() && raw.contains(entry.nameCn)) {
                    raw = raw.replace(entry.nameCn, "")
                }
                raw =
                    raw
                        .replace(Regex("的样子$"), "")
                        .replace('Ｘ', 'X')
                        .replace('Ｙ', 'Y')
                        .trim()
                raw.ifBlank { "Mặc định" }
            }
        }
    }

    fun formatDisplayName(
        entry: PokemonEntry,
        form: PokemonForm,
        language: PokemonNameLanguage,
    ): String =
        when (language) {
            PokemonNameLanguage.HAN_VIET -> form.formHv.ifBlank { entry.nameHv }
            PokemonNameLanguage.VIET_ROMAJI -> form.formVn.ifBlank { entry.nameVn }
            PokemonNameLanguage.CHINESE -> form.formCn.ifBlank { entry.nameCn }
            PokemonNameLanguage.ENGLISH -> {
                val baseEn = entry.nameEn.ifBlank { entry.nameVn }
                val formIdx = entry.forms.indexOf(form)
                if (formIdx <= 0) {
                    baseEn
                } else {
                    val formSuffix = formatFormButtonLabel(entry, form, formIdx, PokemonNameLanguage.VIET_ROMAJI)
                    if (formSuffix.isBlank() || formSuffix == "Mặc định") baseEn else "$baseEn ($formSuffix)"
                }
            }
        }

    fun formatSubtitleNames(
        entry: PokemonEntry,
        form: PokemonForm,
    ): String {
        val parts = LinkedHashSet<String>()
        val hv = form.formHv.ifBlank { entry.nameHv }.trim()
        val vn = form.formVn.ifBlank { entry.nameVn }.trim()
        val en = entry.nameEn.trim()
        val cn = form.formCn.ifBlank { entry.nameCn }.trim()
        if (hv.isNotEmpty()) parts.add(hv)
        if (vn.isNotEmpty()) parts.add(vn)
        if (en.isNotEmpty()) parts.add(en)
        if (cn.isNotEmpty()) parts.add(cn)
        return parts.joinToString(" • ")
    }

    fun detectInitialLanguage(
        entry: PokemonEntry,
        matchedText: String,
    ): PokemonNameLanguage {
        val trimmed = matchedText.trim()
        if (trimmed.isEmpty()) return PokemonNameLanguage.HAN_VIET
        val hasCjk = (0 until trimmed.length).any { Character.UnicodeScript.of(trimmed[it].code) == Character.UnicodeScript.HAN }
        if (hasCjk) return PokemonNameLanguage.HAN_VIET
        if (trimmed.equals(entry.nameVn, ignoreCase = true) ||
            entry.forms.any { it.formVn.equals(trimmed, ignoreCase = true) }
        ) {
            return PokemonNameLanguage.VIET_ROMAJI
        }
        if (trimmed.equals(entry.nameEn, ignoreCase = true)) {
            return PokemonNameLanguage.ENGLISH
        }
        return PokemonNameLanguage.HAN_VIET
    }

    internal fun generateFormAliases(
        entry: PokemonEntry,
        form: PokemonForm,
    ): List<String> {
        val result = LinkedHashSet<String>()
        val cn = form.formCn.replace('Ｘ', 'X').replace('Ｙ', 'Y')
        val vn = form.formVn
        val hv = form.formHv
        if (hv.isNotBlank()) result.add(hv)

        val baseNames =
            buildList {
                add(entry.nameHv)
                add(entry.nameVn)
                add(entry.nameEn)
                addAll(entry.aliases)
            }.map { it.trim() }.filter { it.length >= 3 }.distinct()

        when {
            cn.startsWith("超级") && cn.endsWith("X") -> {
                for (base in baseNames) {
                    result.add("Siêu Cấp $base X")
                    result.add("Mega $base X")
                    result.add("$base Mega X")
                }
            }
            cn.startsWith("超级") && cn.endsWith("Y") -> {
                for (base in baseNames) {
                    result.add("Siêu Cấp $base Y")
                    result.add("Mega $base Y")
                    result.add("$base Mega Y")
                }
            }
            cn.startsWith("超级") -> {
                for (base in baseNames) {
                    result.add("Siêu Cấp $base")
                    result.add("Mega $base")
                    result.add("$base Mega")
                }
            }
            cn.startsWith("超极巨化") -> {
                for (base in baseNames) {
                    result.add("Siêu Cực Cự Hóa $base")
                    result.add("Cực Cự Hóa $base")
                    result.add("$base Gigamax")
                    result.add("Gigantamax $base")
                }
            }
            cn.contains("阿罗拉") || vn.contains("Alola", ignoreCase = true) -> {
                for (base in baseNames) {
                    result.add("$base A La Lạp")
                    result.add("A La Lạp $base")
                    result.add("$base Alola")
                    result.add("$base dạng Alola")
                }
            }
            cn.contains("伽勒尔") || vn.contains("Galar", ignoreCase = true) -> {
                for (base in baseNames) {
                    result.add("$base Già Lặc Nhĩ")
                    result.add("Già Lặc Nhĩ $base")
                    result.add("$base Galar")
                    result.add("$base dạng Galar")
                }
            }
            cn.contains("洗翠") || vn.contains("Hisui", ignoreCase = true) -> {
                for (base in baseNames) {
                    result.add("$base Tẩy Thúy")
                    result.add("Tẩy Thúy $base")
                    result.add("$base Hisui")
                    result.add("$base dạng Hisui")
                }
            }
            cn.contains("帕底亚") || vn.contains("Paldea", ignoreCase = true) -> {
                for (base in baseNames) {
                    result.add("$base Phạt Đế Á")
                    result.add("Phạt Đế Á $base")
                    result.add("$base Paldea")
                    result.add("$base dạng Paldea")
                }
            }
            cn.startsWith("原始") -> {
                for (base in baseNames) {
                    result.add("Nguyên Thủy $base")
                    result.add("$base Nguyên Thủy")
                }
            }
        }
        return result.toList()
    }

    private fun buildLowercaseString(input: String): String {
        val sb = StringBuilder(input.length)
        for (i in 0 until input.length) {
            sb.append(Character.toLowerCase(input[i]))
        }
        return sb.toString()
    }
}
