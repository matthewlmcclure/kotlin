/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.android

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.kpm.external.ExternalVariantApi
import org.jetbrains.kotlin.gradle.kpm.external.project
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinTargetDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createExternalKotlinTarget
import org.jetbrains.kotlin.gradle.plugin.runtimeDependencyConfigurationName
import java.util.concurrent.Callable

@OptIn(ExternalVariantApi::class)
fun KotlinMultiplatformExtension.androidTargetPrototype() {
    val project = this.project
    val androidExtension = project.extensions.getByType<AppExtension>()

    androidExtension.applicationVariants.all { applicationVariant ->
        if (applicationVariant.name != "debug") return@all
        project.logger.quiet("Setting up: ${applicationVariant.name}")

        val targetDescriptor = ExternalKotlinTargetDescriptor("android", KotlinPlatformType.jvm)
        val target = createExternalKotlinTarget(targetDescriptor)

        /* Create compilations */
        val mainCompilation = target.compilations.create("main")
        val unitTestCompilation = target.compilations.create("unitTest")
        val instrumentedTestCompilation = target.compilations.create("instrumentedTest")

        /* Setup default dependsOn edges */
        mainCompilation.defaultSourceSet.dependsOn(sourceSets.getByName("commonMain"))
        unitTestCompilation.defaultSourceSet.dependsOn(sourceSets.getByName("commonTest"))

        /* Associate test with main compilation */
        unitTestCompilation.associateWith(mainCompilation)
        instrumentedTestCompilation.associateWith(mainCompilation)

        applicationVariant.registerPreJavacGeneratedBytecode(mainCompilation.output.classesDirs)

        target.compilations.all { compilation ->
            val compileDependencyConfiguration = project.configurations.maybeCreate(compilation.compileDependencyConfigurationName)
            val runtimeDependencyConfiguration = project.configurations.maybeCreate(compilation.runtimeDependencyConfigurationName!!)

            listOf(compileDependencyConfiguration, runtimeDependencyConfiguration).forEach { configuration ->
                configuration.attributes.attribute(AndroidArtifacts.ARTIFACT_TYPE, AndroidArtifacts.ArtifactType.CLASSES_JAR.type)
            }

            project.dependencies {
                compileDependencyConfiguration(project.getAndroidRuntimeJars())
            }
        }
    }

}

internal fun Project.getAndroidRuntimeJars(): FileCollection {
    return project.files(Callable { project.extensions.getByType<BaseExtension>().bootClasspath })
}
