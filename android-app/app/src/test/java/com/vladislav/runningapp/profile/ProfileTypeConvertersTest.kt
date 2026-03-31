package com.vladislav.runningapp.profile

import com.vladislav.runningapp.core.storage.ProfilePromptFieldRecord
import com.vladislav.runningapp.core.storage.ProfileTypeConverters
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileTypeConvertersTest {
    private val converters = ProfileTypeConverters()

    @Test
    fun promptFieldRoundTripPreservesOrderAndText() {
        val original = listOf(
            ProfilePromptFieldRecord(
                label = "Любимое покрытие",
                value = "асфальт, парк, стадион",
            ),
            ProfilePromptFieldRecord(
                label = "Нежелательно",
                value = "ускорения|лестницы",
            ),
        )

        val encoded = converters.fromPromptFields(original)
        val restored = converters.toPromptFields(encoded)

        assertEquals(original, restored)
    }
}
