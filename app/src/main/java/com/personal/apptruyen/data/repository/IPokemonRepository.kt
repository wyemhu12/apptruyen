package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.model.PokemonMatchRange
import com.personal.apptruyen.util.PokemonMatcher
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

interface IPokemonRepository {
    suspend fun getMatcher(): PokemonMatcher?

    suspend fun findMatchesInParagraphs(paragraphs: List<String>): ImmutableMap<Int, ImmutableList<PokemonMatchRange>>
}
