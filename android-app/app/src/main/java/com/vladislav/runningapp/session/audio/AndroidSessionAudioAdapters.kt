package com.vladislav.runningapp.session.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTextToSpeechSpeaker @Inject constructor(
    @ApplicationContext context: Context,
) : VoiceCueSpeaker {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    @Volatile
    private var readiness: Readiness = Readiness.NotInitialized

    @Volatile
    private var textToSpeech: TextToSpeech? = null

    override fun speak(
        prompt: String,
        onFailure: () -> Unit,
    ): Boolean {
        if (prompt.isBlank()) {
            return false
        }

        ensureInitialized()
        val engine = textToSpeech ?: return false
        if (readiness != Readiness.Ready) {
            return false
        }

        mainExecutor.execute {
            val result = engine.speak(
                prompt,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "running-app-session-${System.nanoTime()}",
            )
            if (result == TextToSpeech.ERROR) {
                onFailure()
            }
        }
        return true
    }

    private fun ensureInitialized() {
        if (readiness != Readiness.NotInitialized) {
            return
        }
        readiness = Readiness.Initializing
        mainExecutor.execute {
            if (textToSpeech != null) {
                return@execute
            }
            textToSpeech = TextToSpeech(appContext) { status ->
                readiness = if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.setLanguage(Locale.forLanguageTag("ru-RU"))
                    Readiness.Ready
                } else {
                    Readiness.Failed
                }
            }
        }
    }

    private enum class Readiness {
        NotInitialized,
        Initializing,
        Ready,
        Failed,
    }
}

@Singleton
class AndroidToneCuePlayer @Inject constructor() : ToneCuePlayer {
    private val toneGenerator: ToneGenerator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    }

    override fun play() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }
}
