/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.external

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetComponent
import org.jetbrains.kotlin.gradle.plugin.mpp.buildAdhocComponentsFromKotlinVariants

@ExternalKotlinTargetApi
class ExternalKotlinTarget internal constructor(
    override val project: Project,
    override val targetName: String,
    override val platformType: KotlinPlatformType,
    private val compilationsContainerFactory: CompilationsContainerLocator,
    val defaultConfiguration: Configuration,
    val apiElementsConfiguration: Configuration,
    val runtimeElementsConfiguration: Configuration,
    override val publishable: Boolean,
    internal val kotlinComponents: Set<KotlinTargetComponent>,
    private val artifactsTaskLocator: ArtifactsTaskLocator,
) : KotlinTarget {

    fun interface CompilationsContainerLocator {
        fun locate(target: ExternalKotlinTarget): NamedDomainObjectContainer<out KotlinCompilation<*>>
    }

    fun interface ArtifactsTaskLocator {
        fun locate(target: ExternalKotlinTarget): TaskProvider<out Task>
    }

    override val useDisambiguationClassifierAsSourceSetNamePrefix: Boolean = true

    override val overrideDisambiguationClassifierOnIdeImport: String? = null

    val artifactsTask: TaskProvider<out Task> by lazy {
        artifactsTaskLocator.locate(this)
    }

    override val artifactsTaskName: String
        get() = artifactsTask.name

    override val defaultConfigurationName: String
        get() = defaultConfiguration.name

    override val apiElementsConfigurationName: String
        get() = apiElementsConfiguration.name

    override val runtimeElementsConfigurationName: String
        get() = runtimeElementsConfiguration.name

    override val components: Set<SoftwareComponent> by lazy {
        project.buildAdhocComponentsFromKotlinVariants(kotlinComponents)
    }

    override val compilations: NamedDomainObjectContainer<out KotlinCompilation<*>> by lazy {
        compilationsContainerFactory.locate(this)
    }

    override fun mavenPublication(action: Action<MavenPublication>) {
        TODO("Not yet implemented")
    }

    override val preset: Nothing? = null

    override fun getAttributes(): AttributeContainer {
        TODO("Not yet implemented")
    }

    internal fun onCreated() {
        artifactsTask
        components
    }
}