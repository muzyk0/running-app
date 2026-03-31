package com.vladislav.runningapp.training.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: String,
    val title: String,
    val summary: String?,
    val goal: String?,
    val disclaimer: String?,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "workout_steps",
    primaryKeys = ["workout_id", "step_index"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workout_id"]),
    ],
)
data class WorkoutStepEntity(
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
    @ColumnInfo(name = "step_index")
    val stepIndex: Int,
    val type: String,
    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,
    @ColumnInfo(name = "voice_prompt")
    val voicePrompt: String,
)

data class WorkoutRecord(
    @Embedded
    val workout: WorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workout_id",
    )
    val steps: List<WorkoutStepEntity>,
)

internal fun Workout.toWorkoutEntity(updatedAtEpochMs: Long): WorkoutEntity = WorkoutEntity(
    id = id,
    schemaVersion = schemaVersion,
    title = title,
    summary = summary,
    goal = goal,
    disclaimer = disclaimer,
    updatedAtEpochMs = updatedAtEpochMs,
)

internal fun Workout.toWorkoutStepEntities(): List<WorkoutStepEntity> = steps.mapIndexed { index, step ->
    WorkoutStepEntity(
        workoutId = id,
        stepIndex = index,
        type = step.type.canonicalValue,
        durationSec = step.durationSec,
        voicePrompt = step.voicePrompt,
    )
}

internal fun WorkoutRecord.toDomainModel(): Workout = Workout(
    id = workout.id,
    schemaVersion = workout.schemaVersion,
    title = workout.title,
    summary = workout.summary,
    goal = workout.goal,
    disclaimer = workout.disclaimer,
    steps = steps
        .sortedBy { step -> step.stepIndex }
        .map { step ->
            WorkoutStep(
                type = WorkoutStepType.fromRawValue(step.type),
                durationSec = step.durationSec,
                voicePrompt = step.voicePrompt,
            )
        },
)
