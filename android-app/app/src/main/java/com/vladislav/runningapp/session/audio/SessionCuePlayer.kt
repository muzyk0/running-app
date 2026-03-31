package com.vladislav.runningapp.session.audio

import javax.inject.Inject
import javax.inject.Singleton

interface SessionCuePlayer {
    fun play(prompt: String)
}

interface VoiceCueSpeaker {
    fun speak(
        prompt: String,
        onFailure: () -> Unit,
    ): Boolean
}

interface ToneCuePlayer {
    fun play()
}

@Singleton
class DefaultSessionCuePlayer @Inject constructor(
    private val voiceCueSpeaker: VoiceCueSpeaker,
    private val toneCuePlayer: ToneCuePlayer,
) : SessionCuePlayer {
    override fun play(prompt: String) {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isEmpty()) {
            toneCuePlayer.play()
            return
        }

        val acceptedByVoice = voiceCueSpeaker.speak(normalizedPrompt) {
            toneCuePlayer.play()
        }
        if (!acceptedByVoice) {
            toneCuePlayer.play()
        }
    }
}
