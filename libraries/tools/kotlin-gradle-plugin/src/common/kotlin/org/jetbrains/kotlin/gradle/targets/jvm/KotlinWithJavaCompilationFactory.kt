/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.mpp

import org.jetbrains.kotlin.gradle.dsl.CompilerCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.HasCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.JvmKotlinCompilationAssociator
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.JvmWithJavaCompilationDependencyConfigurationsFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.KotlinCompilationImplFactory

class KotlinWithJavaCompilationFactory<KotlinOptionsType : KotlinCommonOptions, CO : CompilerCommonOptions> internal constructor(
    override val target: KotlinWithJavaTarget<KotlinOptionsType, CO>,
    val compilerOptionsFactory: () -> HasCompilerOptions<CO>,
    val kotlinOptionsFactory: (CO) -> KotlinOptionsType,
    private val compilationImplFactory: KotlinCompilationImplFactory = KotlinCompilationImplFactory(
        compilerOptionsFactory = { _, _ ->
            val compilerOptions = compilerOptionsFactory()
            val kotlinOptions = kotlinOptionsFactory(compilerOptions.options)
            KotlinCompilationImplFactory.CompilerOptionsFactory.Options(compilerOptions, kotlinOptions)
        },
        compilationAssociator = JvmKotlinCompilationAssociator,
        compilationOutputFactory = { _, compilationName ->
            KotlinWithJavaCompilationOutput(target.javaSourceSets.maybeCreate(compilationName))
        },
        compilationDependencyConfigurationsFactory = JvmWithJavaCompilationDependencyConfigurationsFactory(target)
    )
) : KotlinCompilationFactory<KotlinWithJavaCompilation<KotlinOptionsType, CO>> {

    override val itemClass: Class<KotlinWithJavaCompilation<KotlinOptionsType, CO>>
        @Suppress("UNCHECKED_CAST")
        get() = KotlinWithJavaCompilation::class.java as Class<KotlinWithJavaCompilation<KotlinOptionsType, CO>>

    @Suppress("UNCHECKED_CAST")
    override fun create(name: String): KotlinWithJavaCompilation<KotlinOptionsType, CO> {
        return project.objects.newInstance(
            KotlinWithJavaCompilation::class.java, compilationImplFactory.create(target, name), target.javaSourceSets.maybeCreate(name)
        ) as KotlinWithJavaCompilation<KotlinOptionsType, CO>
    }
}
