package com.vladislav.runningapp.core.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val SingletonProfileId = 1

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    val id: Int = SingletonProfileId,
    @ColumnInfo(name = "height_cm")
    val heightCm: Int,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Int,
    @ColumnInfo(name = "sex")
    val sex: String,
    @ColumnInfo(name = "age")
    val age: Int,
    @ColumnInfo(name = "training_days_per_week")
    val trainingDaysPerWeek: Int,
    @ColumnInfo(name = "fitness_level")
    val fitnessLevel: String,
    @ColumnInfo(name = "injuries_and_limitations")
    val injuriesAndLimitations: String,
    @ColumnInfo(name = "training_goal")
    val trainingGoal: String,
    @ColumnInfo(name = "additional_prompt_fields")
    val additionalPromptFields: List<ProfilePromptFieldRecord>,
)

data class ProfilePromptFieldRecord(
    val label: String,
    val value: String,
)
