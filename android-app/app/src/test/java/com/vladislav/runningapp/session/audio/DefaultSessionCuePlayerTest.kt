package com.vladislav.runningapp.session.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSessionCuePlayerTest {
    @Test
    fun fallsBackToToneWhenVoiceSpeakerIsUnavailable() {
        val voiceCueSpeaker = FakeVoiceCueSpeaker(acceptPrompt = false)
        val toneCuePlayer = FakeToneCuePlayer()
        val cuePlayer = DefaultSessionCuePlayer(
            voiceCueSpeaker = voiceCueSpeaker,
            toneCuePlayer = toneCuePlayer,
        )

        cuePlayer.play("Бежим легко.")

        assertEquals(listOf("Бежим легко."), voiceCueSpeaker.prompts)
        assertEquals(1, toneCuePlayer.playCount)
    }

    @Test
    fun keepsToneSilentWhenVoiceSpeakerAcceptsPrompt() {
        val voiceCueSpeaker = FakeVoiceCueSpeaker(acceptPrompt = true)
        val toneCuePlayer = FakeToneCuePlayer()
        val cuePlayer = DefaultSessionCuePlayer(
            voiceCueSpeaker = voiceCueSpeaker,
            toneCuePlayer = toneCuePlayer,
        )

        cuePlayer.play("Бежим легко.")

        assertEquals(listOf("Бежим легко."), voiceCueSpeaker.prompts)
        assertEquals(0, toneCuePlayer.playCount)
    }

    @Test
    fun usesToneFallbackWhenVoiceSpeakerFailsAfterAcceptingPrompt() {
        val voiceCueSpeaker = FakeVoiceCueSpeaker(
            acceptPrompt = true,
            failAfterAccept = true,
        )
        val toneCuePlayer = FakeToneCuePlayer()
        val cuePlayer = DefaultSessionCuePlayer(
            voiceCueSpeaker = voiceCueSpeaker,
            toneCuePlayer = toneCuePlayer,
        )

        cuePlayer.play("Финишный отрезок.")

        assertEquals(listOf("Финишный отрезок."), voiceCueSpeaker.prompts)
        assertEquals(1, toneCuePlayer.playCount)
    }

    private class FakeVoiceCueSpeaker(
        private val acceptPrompt: Boolean,
        private val failAfterAccept: Boolean = false,
    ) : VoiceCueSpeaker {
        val prompts = mutableListOf<String>()

        override fun speak(
            prompt: String,
            onFailure: () -> Unit,
        ): Boolean {
            prompts += prompt
            if (!acceptPrompt) {
                return false
            }
            if (failAfterAccept) {
                onFailure()
            }
            return true
        }
    }

    private class FakeToneCuePlayer : ToneCuePlayer {
        var playCount: Int = 0

        override fun play() {
            playCount += 1
        }
    }
}
