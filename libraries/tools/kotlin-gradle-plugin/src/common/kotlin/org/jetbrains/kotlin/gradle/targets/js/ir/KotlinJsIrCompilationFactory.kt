/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.ir

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinCompilationFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinOnlyTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.JsCompilationSourceSetsContainerFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.JsCompilerOptionsFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.JsKotlinCompilationDependencyConfigurationsFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.KotlinCompilationImplFactory

class KotlinJsIrCompilationFactory internal constructor(
    override val target: KotlinOnlyTarget<KotlinJsIrCompilation>,
    private val compilationImplFactory: KotlinCompilationImplFactory = KotlinCompilationImplFactory(
        compilerOptionsFactory = JsCompilerOptionsFactory,
        compilationSourceSetsContainerFactory = JsCompilationSourceSetsContainerFactory,
        compilationDependencyConfigurationsFactory = JsKotlinCompilationDependencyConfigurationsFactory
    )
) : KotlinCompilationFactory<KotlinJsIrCompilation> {
    override val itemClass: Class<KotlinJsIrCompilation>
        get() = KotlinJsIrCompilation::class.java

    override fun create(name: String): KotlinJsIrCompilation = target.project.objects.newInstance(
        itemClass, compilationImplFactory.create(target, name)
    )
}
