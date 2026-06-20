package com.baysoft.gallerywall

import android.util.Base64
import java.util.regex.Pattern

object PromptFilter {

    private const val ENCODED_DICT = "KTQoKWk8ODQyJWA8MCI8Yz0/JjEpLj41NTomZyArKDUlL2k6PDEjICVgLDUqOiw8eDYoPSkmJj0qOTY8aywtPzEnKz02NSlgODU8JjkxOCxqOSk6LCFzPSQ+Li8tYCQ8LCx7MTk/LCl+LT81NSwyOmA3KTsrJDcwNG0pPiAxLTY4L2AuMz89K2EyODApLTV4Jj0tOzY8ayUpLyQiMCs2NSlgOzkhIig9Njo0LCh4KCcrIik4MyQoYCI9Kzp7MiAtKjc6OygieCkvPyUnIDErIio3azI5JSY7PTp7IiMvPjk8KmE4MS0pICJ4KDcrI2k6NSAvJ2k8OC00LjglPCN+Iz40eDolOjg1NitzJSQjLm0qLTYxMCwjbT4tPDkhO2EjIS80LCE1JjssP2ktIjM+Izc7Kit7JDQ4LTU/Jj4keDMvJysxNzs6ZzA3IyQ+OyAzK3MjKSMiOHwmID08MSw1"
    private const val KEY = "GALLERY_WALL_PROMPT_FILTER_KEY"

    private val patterns: List<Pattern> by lazy {
        val decodedBytes = Base64.decode(ENCODED_DICT, Base64.DEFAULT)
        val xorBytes = ByteArray(decodedBytes.size)
        val keyBytes = KEY.toByteArray()
        for (i in decodedBytes.indices) {
            xorBytes[i] = (decodedBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        val words = String(xorBytes).split(",")
        words.map { word ->
            // Create a regex that allows for optional non-word characters (and underscores) between letters.
            // Each letter is mapped to a character class containing common leetspeak substitutions.
            val regex = word.map { char ->
                when (char) {
                    'a' -> "[a4@]"
                    'e' -> "[e3]"
                    'i' -> "[i1!]"
                    'o' -> "[o0]"
                    's' -> "[s5\$]"
                    't' -> "[t7]"
                    'b' -> "[b8]"
                    'u' -> "[u3v]"
                    else -> Pattern.quote(char.toString())
                }
            }.joinToString("[\\W_]*")
            Pattern.compile("\\b$regex\\b", Pattern.CASE_INSENSITIVE)
        }
    }

    /**
     * Checks if the given prompt contains any inappropriate content.
     */
    fun containsInappropriateContent(prompt: String): Boolean {
        if (prompt.isBlank()) return false

        val lowercasePrompt = prompt.lowercase()

        return patterns.any { pattern ->
            pattern.matcher(lowercasePrompt).find()
        }
    }
}
