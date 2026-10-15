package com.personal.apptruyen.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
data class PokemonCatalog(
    val version: Int = 1,
    val entries: List<PokemonEntry> = emptyList(),
)

@Immutable
@Serializable
data class PokemonEntry(
    val id: Int,
    val nameCn: String,
    val nameVn: String,
    val nameHv: String,
    val nameEn: String = "",
    val nameJp: String = "",
    val aliases: List<String> = emptyList(),
    val forms: List<PokemonForm> = emptyList(),
)

@Immutable
@Serializable
data class PokemonForm(
    val formCn: String,
    val formVn: String,
    val formHv: String,
    val imageUrl: String,
    val types: List<String> = emptyList(),
)

@Immutable
data class PokemonMatchRange(
    val start: Int,
    val end: Int,
    val matchedText: String,
    val entry: PokemonEntry,
    val initialFormIndex: Int = 0,
)

@Immutable
data class PokemonSelection(
    val entry: PokemonEntry,
    val matchedText: String,
    val initialFormIndex: Int = 0,
)

enum class PokemonNameLanguage(
    val label: String,
) {
    HAN_VIET("Hán-Việt"),
    VIET_ROMAJI("Việt / Romaji"),
    CHINESE("Trung"),
    ENGLISH("Anh"),
}
