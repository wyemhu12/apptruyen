package com.personal.apptruyen.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TextReplacementHelperTest {

    // ═══════ applyReplacements ═══════

    @Test
    fun `applyReplacements returns original when disabled`() {
        val text = "ch·ết gi•ết"
        val result = TextReplacementHelper.applyReplacements(text, enabled = false)
        assertEquals(text, result)
    }

    @Test
    fun `applyReplacements removes middle dot`() {
        val result = TextReplacementHelper.applyReplacements("ch·ết", enabled = true)
        assertEquals("chết", result)
    }

    @Test
    fun `applyReplacements removes bullet dot`() {
        val result = TextReplacementHelper.applyReplacements("gi•ết", enabled = true)
        assertEquals("giết", result)
    }

    @Test
    fun `applyReplacements removes dot operator`() {
        val result = TextReplacementHelper.applyReplacements("hi⋅ếp", enabled = true)
        assertEquals("hiếp", result)
    }

    @Test
    fun `applyReplacements removes all dot types in one pass`() {
        val text = "ch·ết rồi gi•ết hi⋅ếp"
        val result = TextReplacementHelper.applyReplacements(text, enabled = true)
        assertEquals("chết rồi giết hiếp", result)
    }

    @Test
    fun `applyReplacements applies custom replacements`() {
        val custom = listOf("nàn" to "nàng", "chàg" to "chàng")
        val result =
            TextReplacementHelper.applyReplacements(
                "nàn đẹp quá, chàg ơi",
                enabled = true,
                customReplacements = custom,
            )
        assertEquals("nàng đẹp quá, chàng ơi", result)
    }

    @Test
    fun `applyReplacements skips blank custom from`() {
        val custom = listOf("" to "nàng", "  " to "chàng")
        val result =
            TextReplacementHelper.applyReplacements(
                "test text",
                enabled = true,
                customReplacements = custom,
            )
        assertEquals("test text", result)
    }

    @Test
    fun `applyReplacements applies dots removal before custom`() {
        // Dots are removed first, then custom replacements apply to cleaned text
        val custom = listOf("chết" to "mất")
        val result =
            TextReplacementHelper.applyReplacements(
                "ch·ết",
                enabled = true,
                customReplacements = custom,
            )
        assertEquals("mất", result)
    }

    // ═══════ applyAllReplacements ═══════

    @Test
    fun `applyAllReplacements applies global before story`() {
        val result =
            TextReplacementHelper.applyAllReplacements(
                text = "nàn ch·ết",
                globalEnabled = true,
                globalCustomReplacements = listOf("nàn" to "nàng"),
                storyEnabled = true,
                storyCustomReplacements = listOf("nàng" to "công chúa"),
            )
        // Global: "nàn ch·ết" → "nàng chết" (dots removed + "nàn"→"nàng")
        // Story: "nàng chết" → "công chúa chết" ("nàng"→"công chúa")
        assertEquals("công chúa chết", result)
    }

    @Test
    fun `applyAllReplacements only global when story disabled`() {
        val result =
            TextReplacementHelper.applyAllReplacements(
                text = "nàn ch·ết",
                globalEnabled = true,
                globalCustomReplacements = listOf("nàn" to "nàng"),
                storyEnabled = false,
                storyCustomReplacements = listOf("nàng" to "SHOULD NOT APPEAR"),
            )
        assertEquals("nàng chết", result)
    }

    @Test
    fun `applyAllReplacements only story when global disabled`() {
        val result =
            TextReplacementHelper.applyAllReplacements(
                text = "nàn ch·ết",
                globalEnabled = false,
                globalCustomReplacements = listOf("nàn" to "nàng"),
                storyEnabled = true,
                storyCustomReplacements = listOf("nàn" to "cô gái"),
            )
        // Global disabled → text unchanged by global (dots NOT removed)
        // Story: "nàn" → "cô gái" but dots remain
        assertEquals("cô gái ch·ết", result)
    }

    @Test
    fun `applyAllReplacements both disabled returns original`() {
        val text = "ch·ết gi•ết"
        val result =
            TextReplacementHelper.applyAllReplacements(
                text = text,
                globalEnabled = false,
                globalCustomReplacements = emptyList(),
                storyEnabled = false,
                storyCustomReplacements = emptyList(),
            )
        assertEquals(text, result)
    }

    // ═══════ parseCustomReplacements ═══════

    @Test
    fun `parseCustomReplacements with valid JSON`() {
        val json = """[{"from":"abc","to":"xyz"},{"from":"hello","to":"world"}]"""
        val result = TextReplacementHelper.parseCustomReplacements(json)
        assertEquals(2, result.size)
        assertEquals("abc" to "xyz", result[0])
        assertEquals("hello" to "world", result[1])
    }

    @Test
    fun `parseCustomReplacements with null returns empty`() {
        val result = TextReplacementHelper.parseCustomReplacements(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCustomReplacements with blank returns empty`() {
        val result = TextReplacementHelper.parseCustomReplacements("  ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCustomReplacements with invalid JSON returns empty`() {
        val result = TextReplacementHelper.parseCustomReplacements("not valid json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseCustomReplacements with empty array returns empty`() {
        val result = TextReplacementHelper.parseCustomReplacements("[]")
        assertTrue(result.isEmpty())
    }

    // ═══════ serializeCustomReplacements ═══════

    @Suppress("DEPRECATION")
    @Test
    fun `serializeCustomReplacements roundtrip`() {
        val original = listOf("nàn" to "nàng", "chàg" to "chàng")
        val json = TextReplacementHelper.serializeCustomReplacements(original)
        val parsed = TextReplacementHelper.parseCustomReplacements(json)
        assertEquals(original, parsed)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `serializeCustomReplacements empty list`() {
        val json = TextReplacementHelper.serializeCustomReplacements(emptyList())
        assertEquals("[]", json)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `serializeCustomReplacements preserves special chars`() {
        val original = listOf("\"quoted\"" to "'single'", "line\nnewline" to "flat")
        val json = TextReplacementHelper.serializeCustomReplacements(original)
        val parsed = TextReplacementHelper.parseCustomReplacements(json)
        assertEquals(original, parsed)
    }

    // ═══════ TextReplacementItem & caseSensitive (TC-1 -> TC-5) ═══════

    @Test
    fun `applyReplacementItems when caseSensitive is false replaces all case variants`() {
        val items =
            listOf(
                TextReplacementItem(from = "Mây tía", to = "Tử Vân", caseSensitive = false),
            )
        val result =
            TextReplacementHelper.applyReplacementItems(
                text = "Mây tía bay qua, mây tía rực rỡ, MÂY TÍA trên trời",
                enabled = true,
                customReplacements = items,
            )
        assertEquals("Tử Vân bay qua, Tử Vân rực rỡ, Tử Vân trên trời", result)
    }

    @Test
    fun `applyReplacementItems when caseSensitive is true replaces only exact case match`() {
        val items =
            listOf(
                TextReplacementItem(from = "Mây tía", to = "Tử Vân", caseSensitive = true),
            )
        val result =
            TextReplacementHelper.applyReplacementItems(
                text = "Mây tía bay qua, mây tía rực rỡ, MÂY TÍA trên trời",
                enabled = true,
                customReplacements = items,
            )
        assertEquals("Tử Vân bay qua, mây tía rực rỡ, MÂY TÍA trên trời", result)
    }

    @Test
    fun `applyAllReplacementItems combines global and story with independent caseSensitive flags`() {
        val result =
            TextReplacementHelper.applyAllReplacementItems(
                text = "tu liên cùng Tu Liên",
                globalEnabled = true,
                globalCustomReplacements =
                    listOf(
                        TextReplacementItem(from = "tu liên", to = "Tu Tiên", caseSensitive = false),
                    ),
                storyEnabled = true,
                storyCustomReplacements =
                    listOf(
                        TextReplacementItem(from = "Tu Tiên", to = "Đạo Tổ", caseSensitive = true),
                    ),
            )
        assertEquals("Đạo Tổ cùng Đạo Tổ", result)
    }

    @Test
    fun `parseReplacementItems defaults missing caseSensitive in legacy JSON to false and roundtrips new JSON`() {
        val legacyJson = """[{"from":"nàn","to":"nàng"}]"""
        val legacyParsed = TextReplacementHelper.parseReplacementItems(legacyJson)
        assertEquals(1, legacyParsed.size)
        assertEquals(TextReplacementItem("nàn", "nàng", caseSensitive = false), legacyParsed[0])

        val appliedOnLegacy =
            TextReplacementHelper.applyReplacementItems(
                text = "Nàn nhìn nàn",
                enabled = true,
                customReplacements = legacyParsed,
            )
        assertEquals("nàng nhìn nàng", appliedOnLegacy)

        val items =
            listOf(
                TextReplacementItem("abc", "xyz", caseSensitive = false),
                TextReplacementItem("Mây tía", "Tử Vân", caseSensitive = true),
            )
        val serialized = TextReplacementHelper.serializeReplacementItems(items)
        val roundtripped = TextReplacementHelper.parseReplacementItems(serialized)
        assertEquals(items, roundtripped)
    }

    @Test
    fun `applyReplacementItems skips blank from item`() {
        val items =
            listOf(
                TextReplacementItem(from = "   ", to = "Tử Vân", caseSensitive = false),
            )
        val result =
            TextReplacementHelper.applyReplacementItems(
                text = "Mây tía",
                enabled = true,
                customReplacements = items,
            )
        assertEquals("Mây tía", result)
    }
}
