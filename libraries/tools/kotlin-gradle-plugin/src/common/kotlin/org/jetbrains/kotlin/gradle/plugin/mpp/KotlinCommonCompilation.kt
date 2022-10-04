 /*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.CompilerMultiplatformCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformCommonOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.targets.metadata.isKotlinGranularMetadataEnabled
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import javax.inject.Inject

 interface KotlinMetadataCompilation<T : KotlinCommonOptions> : KotlinCompilation<T>

abstract class KotlinCommonCompilation @Inject constructor(
    compilationDetails: CompilationDetails<KotlinMultiplatformCommonOptions>
) : AbstractKotlinCompilation<KotlinMultiplatformCommonOptions>(compilationDetails),
    KotlinMetadataCompilation<KotlinMultiplatformCommonOptions> {

    @Suppress("DEPRECATION")
    @Deprecated("Accessing task instance directly is deprecated", replaceWith = ReplaceWith("compileTaskProvider"))
    override val compileKotlinTask: KotlinCompileCommon
        get() = super.compileKotlinTask as KotlinCompileCommon

    @Suppress("UNCHECKED_CAST")
    override val compileTaskProvider: TaskProvider<KotlinCompilationTask<CompilerMultiplatformCommonOptions>>
        get() = super.compileTaskProvider as TaskProvider<KotlinCompilationTask<CompilerMultiplatformCommonOptions>>

    internal val isKlibCompilation: Boolean
        get() = target.project.isKotlinGranularMetadataEnabled && !forceCompilationToKotlinMetadata

    internal var forceCompilationToKotlinMetadata: Boolean = false
}
