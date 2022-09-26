/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(ExternalKotlinTargetApi::class)

package org.jetbrains.kotlin.gradle.plugin.mpp.external

import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.Kotlin2JvmSourceSetProcessor
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilationFactory
import org.jetbrains.kotlin.gradle.plugin.pluginConfigurationName
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import org.jetbrains.kotlin.gradle.tasks.locateOrRegisterTask
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

@ExternalKotlinTargetApi
fun KotlinMultiplatformExtension.createExternalKotlinTarget(descriptor: ExternalKotlinTargetDescriptor): ExternalKotlinTarget {
    val compilationsContainerFactory = createCompilationsContainerFactory(descriptor)
    val defaultConfiguration = project.configurations.maybeCreate(lowerCamelCaseName(descriptor.targetName, "default"))
    val apiElementsConfiguration = project.configurations.maybeCreate(lowerCamelCaseName(descriptor.targetName, "apiElements"))
    val runtimeElementsConfiguration = project.configurations.maybeCreate(lowerCamelCaseName(descriptor.targetName, "runtimeElements"))
    val artifactsTaskLocator = ExternalKotlinTarget.ArtifactsTaskLocator { target ->
        target.project.locateOrRegisterTask<Jar>(lowerCamelCaseName(descriptor.targetName, "jar"))
    }

    val target = ExternalKotlinTarget(
        project = project,
        targetName = descriptor.targetName,
        platformType = descriptor.platformType,
        compilationsContainerFactory = compilationsContainerFactory,
        defaultConfiguration = defaultConfiguration,
        apiElementsConfiguration = apiElementsConfiguration,
        runtimeElementsConfiguration = runtimeElementsConfiguration,
        publishable = true,
        kotlinComponents = emptySet(),
        artifactsTaskLocator = artifactsTaskLocator
    )

    target.compilations.all { compilation ->
        compilation.relatedConfigurationNames.plus(compilation.pluginConfigurationName).forEach { name ->
            project.configurations.maybeCreate(name)
        }

        project.configurations.getByName(compilation.compileDependencyConfigurationName).also { compileDependency ->
            compileDependency.extendsFrom(project.configurations.getByName(compilation.compileOnlyConfigurationName))
            compileDependency.extendsFrom(project.configurations.getByName(compilation.implementationConfigurationName))
            compileDependency.extendsFrom(project.configurations.getByName(compilation.apiConfigurationName))
        }
        compilation.source(compilation.defaultSourceSet)

        // TODO NOW.
        if(compilation is KotlinJvmCompilation) {
            val tasksProvider = KotlinTasksProvider()
            Kotlin2JvmSourceSetProcessor(tasksProvider, compilation).run()
        }
    }


    target.onCreated()
    targets.add(target)
    return target
}

@OptIn(ExternalKotlinTargetApi::class)
private fun KotlinMultiplatformExtension.createCompilationsContainerFactory(
    descriptor: ExternalKotlinTargetDescriptor
): ExternalKotlinTarget.CompilationsContainerLocator {
    return when (descriptor.platformType) {
        KotlinPlatformType.jvm, KotlinPlatformType.androidJvm -> ExternalKotlinTarget.CompilationsContainerLocator { target ->
            target.project.container(KotlinJvmCompilation::class.java, KotlinJvmCompilationFactory(target))
        }

        else -> error("Unsupported platformType: ${descriptor.platformType}")
    }
}