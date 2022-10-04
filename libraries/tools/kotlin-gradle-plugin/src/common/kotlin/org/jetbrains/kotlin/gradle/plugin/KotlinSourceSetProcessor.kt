/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.internal.JavaSourceSetsAccessor
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import org.jetbrains.kotlin.gradle.tasks.configuration.AbstractKotlinCompileConfig
import org.jetbrains.kotlin.gradle.tasks.registerTask
import java.util.concurrent.Callable

internal abstract class KotlinSourceSetProcessor<T : AbstractKotlinCompile<*>>(
    val tasksProvider: KotlinTasksProvider,
    val taskDescription: String,
    kotlinCompilation: KotlinCompilationProjection
) : KotlinCompilationProcessor<T>(kotlinCompilation) {
    protected abstract fun doTargetSpecificProcessing()
    protected val logger = Logging.getLogger(this.javaClass)!!

    protected val sourceSetName: String = kotlinCompilation.compilationName

    override val kotlinTask: TaskProvider<out T> = prepareKotlinCompileTask()

    protected val javaSourceSet: SourceSet?
        get() {
            if (compilationProjection !is KotlinCompilationProjection.TCS) return null
            val compilation = compilationProjection.origin
            val target = compilationProjection.origin.target

            return (compilation as? KotlinWithJavaCompilation<*, *>)?.javaSourceSet
                ?: if (target is KotlinJvmTarget && target.withJavaEnabled && compilation is KotlinJvmCompilation)
                    project.gradle.variantImplementationFactory<JavaSourceSetsAccessor.JavaSourceSetsAccessorVariantFactory>()
                        .getInstance(project)
                        .sourceSets
                        .maybeCreate(compilation.name)
                else null

        }

    private fun prepareKotlinCompileTask(): TaskProvider<out T> =
        doRegisterTask(project, compilationProjection.compileKotlinTaskName).also { task ->
            compilationProjection.classesDirs.from(task.flatMap { it.destinationDirectory })
        }

    override fun run() {
        addKotlinDirectoriesToJavaSourceSet()
        doTargetSpecificProcessing()

        if (compilationProjection.tcsOrNull?.compilation is KotlinWithJavaCompilation<*, *>) {
            createAdditionalClassesTaskForIdeRunner()
        }
    }

    private fun addKotlinDirectoriesToJavaSourceSet() {
        val java = javaSourceSet ?: return

        // Try to avoid duplicate Java sources in allSource; run lazily to allow changing the directory set:
        val kotlinSrcDirsToAdd = Callable {
            compilationProjection.sources.map { filterOutJavaSrcDirsIfPossible(it) }
        }

        java.allJava.srcDirs(kotlinSrcDirsToAdd)
        java.allSource.srcDirs(kotlinSrcDirsToAdd)
    }

    private fun filterOutJavaSrcDirsIfPossible(sourceDirectories: SourceDirectorySet): FileCollection {
        val java = javaSourceSet ?: return sourceDirectories

        // Build a lazily-resolved file collection that filters out Java sources from sources of this sourceDirectorySet
        return sourceDirectories.sourceDirectories.minus(java.java.sourceDirectories)
    }

    private fun createAdditionalClassesTaskForIdeRunner() {
        val kotlinCompilation = compilationProjection.tcsOrNull?.compilation ?: return

        open class IDEClassesTask : DefaultTask()
        // Workaround: as per KT-26641, when there's a Kotlin compilation with a Java source set, we create another task
        // that has a name composed as '<IDE module name>Classes`, where the IDE module name is the default source set name:
        val expectedClassesTaskName = "${kotlinCompilation.defaultSourceSetName}Classes"
        project.tasks.run {
            val shouldCreateTask = expectedClassesTaskName !in names
            if (shouldCreateTask) {
                project.registerTask(expectedClassesTaskName, IDEClassesTask::class.java) {
                    it.dependsOn(getByName(kotlinCompilation.compileAllTaskName))
                }
            }
        }
    }

    protected fun applyStandardTaskConfiguration(taskConfiguration: AbstractKotlinCompileConfig<*>) {
        taskConfiguration.configureTask {
            it.description = taskDescription
            if (it is Kotlin2JsCompile) {
                it.defaultDestinationDirectory.convention(defaultKotlinDestinationDir)
            } else {
                it.destinationDirectory.convention(defaultKotlinDestinationDir)
            }
            it.libraries.from({ compilationProjection.compileDependencyFiles })
        }
    }

    protected abstract fun doRegisterTask(project: Project, taskName: String): TaskProvider<out T>
}