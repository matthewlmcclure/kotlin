// DO NOT EDIT MANUALLY!
// Generated by org/jetbrains/kotlin/generators/arguments/GenerateGradleOptions.kt
package org.jetbrains.kotlin.gradle.dsl

@Suppress("DEPRECATION")
enum class JvmTarget (val target: String) {
    @Deprecated("Will be removed soon") JVM_1_6("1.6"),
    JVM_1_8("1.8"),
    JVM_9("9"),
    JVM_10("10"),
    JVM_11("11"),
    JVM_12("12"),
    JVM_13("13"),
    JVM_14("14"),
    JVM_15("15"),
    JVM_16("16"),
    JVM_17("17"),
    JVM_18("18");

    companion object {
        fun fromTarget(target: String): JvmTarget =
            JvmTarget.values().firstOrNull { it.target == target }
                ?: throw IllegalArgumentException("Unknown Kotlin JVM target: $target")
    }
}
