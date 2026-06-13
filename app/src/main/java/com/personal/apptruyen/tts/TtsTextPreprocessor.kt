package com.personal.apptruyen.tts

/**
 * Preprocesses text before sending to TTS engine.
 * Fixes the issue where TTS pauses too long at periods in abbreviations
 * and acronyms (e.g., Dr.Bach, S.H.I.E.L.D., Mr. Bean).
 *
 * Only affects text sent to TTS — UI display remains unchanged.
 */
object TtsTextPreprocessor {

    // Common abbreviations → full English words (keys WITHOUT the dot)
    private val ABBREVIATIONS =
        mapOf(
            "Dr" to "Doctor",
            "Mr" to "Mister",
            "Mrs" to "Misses",
            "Ms" to "Miss",
            "Prof" to "Professor",
            "vs" to "versus",
            "St" to "Saint",
            "Jr" to "Junior",
            "Sr" to "Senior",
            "etc" to "et cetera",
            "Corp" to "Corporation",
            "Inc" to "Incorporated",
            "Ltd" to "Limited",
        )

    // Matches abbreviation key + optional spaces + dot: Dr. / Dr.Bach / Dr .Bach
    private val ABBREVIATION_REGEX: Regex

    // Matches acronyms with dots: S.H.I.E.L.D. or S.H.I.E.L.D (≥2 letter-dot pairs)
    private val ACRONYM_DOTS_REGEX = Regex("\\b([A-Za-z]\\.){2,}[A-Za-z]?")

    // Fallback: dot between two letters (abc.xyz → abc xyz)
    private val MID_WORD_DOT_REGEX = Regex("(?<=[A-Za-zÀ-ỹ])\\.(?=[A-Za-zÀ-ỹ])")

    init {
        val keys =
            ABBREVIATIONS.keys
                .sortedByDescending { it.length }
                .joinToString("|") { Regex.escape(it) }
        ABBREVIATION_REGEX = Regex("\\b($keys) *\\.")
    }

    fun preprocess(text: String): String {
        var result = text

        // Step 1: Acronym dots (S.H.I.E.L.D. → SHIELD)
        result = ACRONYM_DOTS_REGEX.replace(result) { it.value.replace(".", "") }

        // Step 2: Known abbreviations (Dr. → Doctor, Dr.Bach → Doctor Bach)
        result =
            ABBREVIATION_REGEX.replace(result) { match ->
                val key = match.groupValues[1]
                val replacement = ABBREVIATIONS[key] ?: return@replace match.value
                val afterIdx = match.range.last + 1
                val afterChar = result.getOrNull(afterIdx)
                // Add space if the next char is a letter (Dr.Bach → Doctor Bach)
                if (afterChar != null && afterChar.isLetter()) "$replacement " else replacement
            }

        // Step 3: Remaining mid-word dots → space (abc.xyz → abc xyz)
        result = MID_WORD_DOT_REGEX.replace(result, " ")

        return result
    }
}
