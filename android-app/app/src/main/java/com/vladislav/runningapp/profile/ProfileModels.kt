package com.vladislav.runningapp.profile

enum class UserSex(
    val storageValue: String,
) {
    Female("female"),
    Male("male"),
    Other("other"),
    ;

    companion object {
        fun fromStorageValue(value: String): UserSex =
            entries.firstOrNull { it.storageValue == value } ?: Other
    }
}

enum class FitnessLevel(
    val storageValue: String,
) {
    Beginner("beginner"),
    Intermediate("intermediate"),
    Advanced("advanced"),
    ;

    companion object {
        fun fromStorageValue(value: String): FitnessLevel =
            entries.firstOrNull { it.storageValue == value } ?: Beginner
    }
}

data class AdditionalPromptField(
    val label: String,
    val value: String,
)

data class UserProfile(
    val heightCm: Int,
    val weightKg: Int,
    val sex: UserSex,
    val age: Int,
    val trainingDaysPerWeek: Int,
    val fitnessLevel: FitnessLevel,
    val injuriesAndLimitations: String,
    val trainingGoal: String,
    val additionalPromptFields: List<AdditionalPromptField>,
)
