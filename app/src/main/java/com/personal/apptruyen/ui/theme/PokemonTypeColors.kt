package com.personal.apptruyen.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.personal.apptruyen.ui.reader.ReaderViewModel
import com.personal.apptruyen.util.PokemonLookupHelper

@Immutable
data class PokemonTypeBadgeStyle(
    val backgroundColor: Color,
    val contentColor: Color,
)

object PokemonTypeColors {
    private val DarkBadgeContent = Color(0xFF1A1A1A)
    private val LightBadgeContent = Color(0xFFFFFFFF)

    private val GrassBg = Color(0xFFB4F000)
    private val PoisonBg = Color(0xFFD28CD2)
    private val FireBg = Color(0xFFFF6900)
    private val FlyingBg = Color(0xFF78DCFF)
    private val IceBg = Color(0xFF14F5FF)
    private val PsychicBg = Color(0xFFF08CDC)
    private val DragonBg = Color(0xFF5078DC)
    private val NormalBg = Color(0xFFDCDCDC)
    private val FightingBg = Color(0xFFDC6900)
    private val FairyBg = Color(0xFFFFAFDC)
    private val GroundBg = Color(0xFFFAC85A)
    private val RockBg = Color(0xFFB48C64)
    private val ElectricBg = Color(0xFFFFE100)
    private val WaterBg = Color(0xFF14B9FF)
    private val BugBg = Color(0xFF46C846)
    private val SteelBg = Color(0xFFAAC8F0)
    private val DarkBg = Color(0xFF787878)
    private val GhostBg = Color(0xFFA08CFF)
    private val DefaultBg = Color(0xFF9E9E9E)

    private val ReaderHighlightLight = Color(0xFFD63031)
    private val ReaderHighlightDark = Color(0xFFFF7675)
    private val ReaderHighlightSepia = Color(0xFFB33939)

    val DialogImageBackgroundLight = Color(0xFFF8F6F2)
    val DialogImageBackgroundDark = Color(0xFF2B2930)

    fun getBadgeStyle(rawType: String): PokemonTypeBadgeStyle =
        when (PokemonLookupHelper.formatTypeLabel(rawType)) {
            "Hệ Cỏ" -> PokemonTypeBadgeStyle(GrassBg, DarkBadgeContent)
            "Hệ Độc" -> PokemonTypeBadgeStyle(PoisonBg, DarkBadgeContent)
            "Hệ Lửa" -> PokemonTypeBadgeStyle(FireBg, LightBadgeContent)
            "Hệ Bay" -> PokemonTypeBadgeStyle(FlyingBg, DarkBadgeContent)
            "Hệ Băng" -> PokemonTypeBadgeStyle(IceBg, DarkBadgeContent)
            "Hệ Siêu Linh" -> PokemonTypeBadgeStyle(PsychicBg, DarkBadgeContent)
            "Hệ Rồng" -> PokemonTypeBadgeStyle(DragonBg, LightBadgeContent)
            "Hệ Thường" -> PokemonTypeBadgeStyle(NormalBg, DarkBadgeContent)
            "Hệ Giác Đấu" -> PokemonTypeBadgeStyle(FightingBg, LightBadgeContent)
            "Hệ Tiên" -> PokemonTypeBadgeStyle(FairyBg, DarkBadgeContent)
            "Hệ Đất" -> PokemonTypeBadgeStyle(GroundBg, DarkBadgeContent)
            "Hệ Đá" -> PokemonTypeBadgeStyle(RockBg, LightBadgeContent)
            "Hệ Điện" -> PokemonTypeBadgeStyle(ElectricBg, DarkBadgeContent)
            "Hệ Nước" -> PokemonTypeBadgeStyle(WaterBg, LightBadgeContent)
            "Hệ Côn Trùng" -> PokemonTypeBadgeStyle(BugBg, LightBadgeContent)
            "Hệ Thép" -> PokemonTypeBadgeStyle(SteelBg, DarkBadgeContent)
            "Hệ Bóng Tối" -> PokemonTypeBadgeStyle(DarkBg, LightBadgeContent)
            "Hệ Ma" -> PokemonTypeBadgeStyle(GhostBg, DarkBadgeContent)
            else -> PokemonTypeBadgeStyle(DefaultBg, LightBadgeContent)
        }

    fun pokemonHighlightColor(theme: ReaderViewModel.ReaderTheme): Color =
        when (theme) {
            ReaderViewModel.ReaderTheme.LIGHT -> ReaderHighlightLight
            ReaderViewModel.ReaderTheme.DARK -> ReaderHighlightDark
            ReaderViewModel.ReaderTheme.SEPIA -> ReaderHighlightSepia
        }
}
