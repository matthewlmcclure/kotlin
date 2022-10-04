/*
* Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
* Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
*/

package org.jetbrains.kotlin.gradle.targets.js.ir

import org.gradle.api.file.SourceDirectorySet
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationDetailsImpl.JsIrCompilationDetails
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.dukat.ExternalsOutputFormat
import javax.inject.Inject

abstract class KotlinJsIrCompilation @Inject internal constructor(
    compilationDetails: JsIrCompilationDetails
) : KotlinJsCompilation(compilationDetails) {

    override val externalsOutputFormat: ExternalsOutputFormat = ExternalsOutputFormat.SOURCE

    internal val allSources: MutableSet<SourceDirectorySet> = mutableSetOf()
}