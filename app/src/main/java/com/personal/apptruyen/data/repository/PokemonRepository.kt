package com.personal.apptruyen.data.repository

import android.content.Context
import com.personal.apptruyen.data.model.PokemonMatchRange
import com.personal.apptruyen.util.PokemonLookupHelper
import com.personal.apptruyen.util.PokemonMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PokemonRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : IPokemonRepository {
        private val mutex = Mutex()

        @Volatile
        private var cachedMatcher: PokemonMatcher? = null

        override suspend fun getMatcher(): PokemonMatcher? {
            cachedMatcher?.let { return it }
            return mutex.withLock {
                cachedMatcher?.let { return@withLock it }
                withContext(Dispatchers.IO) {
                    runCatching {
                        val jsonString =
                            context.assets
                                .open(ASSET_FILE_NAME)
                                .bufferedReader(Charsets.UTF_8)
                                .use { it.readText() }
                        val catalog = PokemonLookupHelper.parseCatalogJson(jsonString)
                        PokemonMatcher.fromCatalog(catalog).also { matcher ->
                            cachedMatcher = matcher
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                        Timber.e(e, "Failed to load $ASSET_FILE_NAME")
                    }.getOrNull()
                }
            }
        }

        override suspend fun findMatchesInParagraphs(paragraphs: List<String>): ImmutableMap<Int, ImmutableList<PokemonMatchRange>> {
            if (paragraphs.isEmpty()) return persistentMapOf()
            val matcher = getMatcher() ?: return persistentMapOf()
            return withContext(Dispatchers.Default) {
                val result = LinkedHashMap<Int, ImmutableList<PokemonMatchRange>>()
                paragraphs.forEachIndexed { index, paragraph ->
                    ensureActive()
                    if (paragraph.isNotBlank()) {
                        val matches = matcher.findMatches(paragraph)
                        if (matches.isNotEmpty()) {
                            result[index] = matches.toImmutableList()
                        }
                    }
                }
                result.toImmutableMap()
            }
        }

        companion object {
            const val ASSET_FILE_NAME = "pokemon_pokedex.json"
        }
    }
