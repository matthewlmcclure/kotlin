/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider

@Deprecated(
    message = "Should be removed with Js platform plugin",
    level = DeprecationLevel.ERROR
)
internal open class Kotlin2JsPlugin(
    registry: ToolingModelBuilderRegistry
) : AbstractKotlinPlugin(KotlinTasksProvider(), registry) {

    companion object {
        private const val targetName = "2Js"
    }

    override fun buildSourceSetProcessor(
        project: Project,
        compilation: KotlinCompilation<*>
    ): KotlinSourceSetProcessor<*> =
        Kotlin2JsSourceSetProcessor(tasksProvider, KotlinCompilationProjection(compilation))

    override fun apply(project: Project) {
        @Suppress("UNCHECKED_CAST")
        val target = project.objects.newInstance(
            KotlinWithJavaTarget::class.java,
            project,
            KotlinPlatformType.js,
            targetName,
            {
                object : HasCompilerOptions<CompilerJsOptions> {
                    override val options: CompilerJsOptions =
                        project.objects.newInstance(CompilerJsOptionsDefault::class.java)
                }
            },
            { compilerOptions: CompilerJsOptions ->
                object : KotlinJsOptions {
                    override val options: CompilerJsOptions
                        get() = compilerOptions
                }
            }
        ) as KotlinWithJavaTarget<KotlinJsOptions, CompilerJsOptions>

        (project.kotlinExtension as Kotlin2JsProjectExtension).setTarget(target)
        super.apply(project)
    }
}