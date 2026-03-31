package com.vladislav.runningapp.core.storage

import androidx.room.TypeConverter
import java.nio.charset.StandardCharsets
import java.util.Base64

class ProfileTypeConverters {
    @TypeConverter
    fun fromPromptFields(value: List<ProfilePromptFieldRecord>): String = value.joinToString("|") { field ->
        "${encode(field.label)},${encode(field.value)}"
    }

    @TypeConverter
    fun toPromptFields(value: String): List<ProfilePromptFieldRecord> {
        if (value.isBlank()) {
            return emptyList()
        }

        return value.split("|").map { entry ->
            val parts = entry.split(",", limit = 2)
            ProfilePromptFieldRecord(
                label = decode(parts.getOrElse(0) { "" }),
                value = decode(parts.getOrElse(1) { "" }),
            )
        }
    }

    // Base64 keeps the stored representation stable without adding a JSON dependency to Room.
    private fun encode(value: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(
            Base64.getUrlDecoder().decode(value),
            StandardCharsets.UTF_8,
        )
}
