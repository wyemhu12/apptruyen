package com.personal.apptruyen.util

import com.personal.apptruyen.data.model.PokemonNameLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PokemonLookupHelperTest {
    private lateinit var matcher: PokemonMatcher
    private lateinit var catalogJson: String

    @BeforeAll
    fun setUpCatalog() {
        val assetFile =
            listOf(
                File("src/main/assets/pokemon_pokedex.json"),
                File("app/src/main/assets/pokemon_pokedex.json"),
            ).firstOrNull { it.exists() }
                ?: error("Không tìm thấy pokemon_pokedex.json")
        catalogJson = assetFile.readText(Charsets.UTF_8)
        val catalog = PokemonLookupHelper.parseCatalogJson(catalogJson)
        matcher = PokemonMatcher.fromCatalog(catalog)
        catalogJson = assetFile.readText(Charsets.UTF_8)
    }

    @Test
    fun `TC-01 parse pokemon_pokedex json validates schema and Sino-Vietnamese names`() {
        val catalog = PokemonLookupHelper.parseCatalogJson(catalogJson)
        assertTrue(catalog.entries.size >= 1000, "Expected at least 1000 Pokémon species, got ${catalog.entries.size}")

        for (entry in catalog.entries) {
            assertTrue(entry.nameCn.isNotBlank(), "Entry ${entry.id} missing nameCn")
            assertTrue(entry.nameVn.isNotBlank(), "Entry ${entry.id} missing nameVn")
            assertTrue(entry.nameHv.isNotBlank(), "Entry ${entry.id} missing nameHv")
            assertTrue(entry.forms.isNotEmpty(), "Entry ${entry.id} has empty forms")
            for (form in entry.forms) {
                assertTrue(
                    form.imageUrl.startsWith("https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/") ||
                        form.imageUrl.startsWith("https://zukan.pokemon.co.jp/zukan-api/up/images/index/"),
                    "Invalid imageUrl for ${entry.nameVn}: ${form.imageUrl}",
                )
                assertTrue(form.types.isNotEmpty(), "Form ${form.formVn} has empty types")
            }
        }

        val iconicHanVietNames =
            listOf(
                "Bì Khả Khâu",
                "Tiểu Hỏa Long",
                "Phun Hỏa Long",
                "Diệu Oa Chủng",
                "Kiệt Ni Quy",
                "Thủy Tiễn Quy",
                "Siêu Mộng",
                "Mộng Huyễn",
                "Liệt Không Tọa",
                "Cố Lạp Đốn",
                "Cái Âu Ca",
                "Khoái Long",
                "Bạo Lý Long",
                "Cảnh Điển",
                "Hồ Địa",
                "Đại Nham Xà",
                "Cự Kim Quái",
                "Lộ Ca Lợi Áo",
                "Sa Nại Đóa",
            )
        for (hvName in iconicHanVietNames) {
            val matches = matcher.findMatches(hvName)
            assertEquals(1, matches.size, "Expected exact match for Sino-Vietnamese name '$hvName'")
            assertEquals(hvName, matches.first().matchedText)
        }
    }

    @Test
    fun `TC-02 findMatches detects Sino-Vietnamese, Romaji, English and Chinese names in paragraph`() {
        val text = "Tiểu Trí dẫn theo Bì Khả Khâu, Tiểu Hỏa Long, Siêu Mộng, Liệt Không Tọa, Pikachu và 皮卡丘 ra sân."
        val matches = matcher.findMatches(text)

        assertEquals(6, matches.size)
        assertEquals("Bì Khả Khâu", matches[0].matchedText)
        assertEquals("Pikachu", matches[0].entry.nameVn)

        assertEquals("Tiểu Hỏa Long", matches[1].matchedText)
        assertEquals("Hitokage", matches[1].entry.nameVn)

        assertEquals("Siêu Mộng", matches[2].matchedText)
        assertEquals("Mewtwo", matches[2].entry.nameVn)

        assertEquals("Liệt Không Tọa", matches[3].matchedText)
        assertEquals("Rayquaza", matches[3].entry.nameVn)

        assertEquals("Pikachu", matches[4].matchedText)
        assertEquals("Pikachu", matches[4].entry.nameVn)

        assertEquals("皮卡丘", matches[5].matchedText)
        assertEquals("Pikachu", matches[5].entry.nameVn)

        for (match in matches) {
            assertEquals(match.matchedText, text.substring(match.start, match.end))
        }
    }

    @Test
    fun `TC-03 findMatches prioritizes longest match first and resolves specific form index`() {
        val text = "Trên bầu trời xuất hiện Siêu Cấp Phun Hỏa Long X cùng Siêu Cực Cự Hóa Bì Khả Khâu."
        val matches = matcher.findMatches(text)

        assertEquals(2, matches.size)
        assertEquals("Siêu Cấp Phun Hỏa Long X", matches[0].matchedText)
        assertEquals("Lizardon", matches[0].entry.nameVn)
        assertTrue(matches[0].initialFormIndex > 0, "Expected Mega X form index > 0")

        assertEquals("Siêu Cực Cự Hóa Bì Khả Khâu", matches[1].matchedText)
        assertEquals("Pikachu", matches[1].entry.nameVn)
        assertTrue(matches[1].initialFormIndex > 0, "Expected Gigamax form index > 0")
    }

    @Test
    fun `TC-04 findMatches enforces word boundaries and excludes ambiguous Vietnamese words`() {
        val text =
            "Con muk này trong truyền thuyết, ông ba ba đi câu cá bên hồ đá lửa nước cỏ điện băng rồng ma thép đất bay độc tiên thường."
        val matches = matcher.findMatches(text)

        assertEquals(1, matches.size)
        assertEquals("muk", matches.first().matchedText)
        assertFalse(matches.any { it.matchedText.equals("ba ba", ignoreCase = true) })
    }

    @Test
    fun `TC-05 formatFormButtonLabel and formatTypeLabel return Vietnamese strings`() {
        val lizardonMatch = matcher.findMatches("Phun Hỏa Long").firstOrNull()
        assertNotNull(lizardonMatch)
        val entry = lizardonMatch!!.entry

        assertEquals(
            "Mặc định",
            PokemonLookupHelper.formatFormButtonLabel(entry, entry.forms[0], 0, PokemonNameLanguage.HAN_VIET),
        )
        if (entry.forms.size > 1) {
            val megaLabelHv =
                PokemonLookupHelper.formatFormButtonLabel(entry, entry.forms[1], 1, PokemonNameLanguage.HAN_VIET)
            assertTrue(megaLabelHv.contains("Siêu Cấp"), "Expected 'Siêu Cấp' in '$megaLabelHv'")
        }

        assertEquals("Hệ Lửa", PokemonLookupHelper.formatTypeLabel("Lửa"))
        assertEquals("Hệ Bay", PokemonLookupHelper.formatTypeLabel("飞行"))
        assertEquals("Hệ Rồng", PokemonLookupHelper.formatTypeLabel("Rồng"))
        assertEquals("Hệ Siêu Linh", PokemonLookupHelper.formatTypeLabel("超能力"))
    }

    @Test
    fun `TC-06 empty or blank input returns empty results gracefully`() {
        assertTrue(matcher.findMatches("").isEmpty())
        assertTrue(matcher.findMatches("   ").isEmpty())
        val emptyCatalog = PokemonLookupHelper.parseCatalogJson("")
        assertTrue(emptyCatalog.entries.isEmpty())
    }
}
