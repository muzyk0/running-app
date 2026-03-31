package com.vladislav.runningapp.session

import com.vladislav.runningapp.session.audio.AndroidTextToSpeechSpeaker
import com.vladislav.runningapp.session.audio.AndroidToneCuePlayer
import com.vladislav.runningapp.session.audio.DefaultSessionCuePlayer
import com.vladislav.runningapp.session.audio.SessionCuePlayer
import com.vladislav.runningapp.session.audio.ToneCuePlayer
import com.vladislav.runningapp.session.audio.VoiceCueSpeaker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds
    @Singleton
    abstract fun bindWorkoutSessionController(
        defaultWorkoutSessionController: DefaultWorkoutSessionController,
    ): WorkoutSessionController

    @Binds
    @Singleton
    abstract fun bindSessionCuePlayer(
        defaultSessionCuePlayer: DefaultSessionCuePlayer,
    ): SessionCuePlayer

    @Binds
    @Singleton
    abstract fun bindVoiceCueSpeaker(
        androidTextToSpeechSpeaker: AndroidTextToSpeechSpeaker,
    ): VoiceCueSpeaker

    @Binds
    @Singleton
    abstract fun bindToneCuePlayer(
        androidToneCuePlayer: AndroidToneCuePlayer,
    ): ToneCuePlayer
}
