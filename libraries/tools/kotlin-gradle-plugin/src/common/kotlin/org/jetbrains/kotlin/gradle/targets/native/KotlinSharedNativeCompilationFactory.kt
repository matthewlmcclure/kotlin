/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.mpp

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.DefaultKotlinCompilationFriendPathsResolver
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.DefaultKotlinCompilationSourceSetInclusion
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.KotlinCompilationSourceSetsContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.NativeKotlinCompilationAssociator
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.DefaultKotlinCompilationDependencyConfigurationsFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.KotlinCompilationImplFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.NativeCompilerOptionsFactory
import org.jetbrains.kotlin.konan.target.KonanTarget

open class KotlinSharedNativeCompilationFactory internal constructor(
    override val target: KotlinMetadataTarget,
    private val konanTargets: Set<KonanTarget>,
    private val defaultSourceSet: KotlinSourceSet,
    private val compilationImplFactory: KotlinCompilationImplFactory =
        KotlinCompilationImplFactory(
            compilationDependencyConfigurationsFactory = DefaultKotlinCompilationDependencyConfigurationsFactory.WithoutRuntime,
            compilerOptionsFactory = NativeCompilerOptionsFactory,
            compilationAssociator = NativeKotlinCompilationAssociator,

            compilationSourceSetInclusion = DefaultKotlinCompilationSourceSetInclusion(
                DefaultKotlinCompilationSourceSetInclusion.NativeAddSourcesToCompileTask
            ),

            compilationFriendPathsResolver = DefaultKotlinCompilationFriendPathsResolver(
                friendArtifactResolver = DefaultKotlinCompilationFriendPathsResolver.FriendArtifactResolver.composite(
                    DefaultKotlinCompilationFriendPathsResolver.DefaultFriendArtifactResolver,
                    DefaultKotlinCompilationFriendPathsResolver.AdditionalSharedNativeMetadataFriendArtifactResolver
                )
            ),

            /*
            Metadata compilations are created *because* of a pre-existing SourceSet.
            We therefore can create the container inline
             */
            compilationSourceSetsContainerFactory = { _, _ -> KotlinCompilationSourceSetsContainer(defaultSourceSet) }
        )
) : KotlinCompilationFactory<KotlinSharedNativeCompilation> {

    override val itemClass: Class<KotlinSharedNativeCompilation>
        get() = KotlinSharedNativeCompilation::class.java

    @Suppress("DEPRECATION")
    override fun create(name: String): KotlinSharedNativeCompilation {
        return target.project.objects.newInstance(
            itemClass, konanTargets.toList(), compilationImplFactory.create(target, name)
        )
    }
}
