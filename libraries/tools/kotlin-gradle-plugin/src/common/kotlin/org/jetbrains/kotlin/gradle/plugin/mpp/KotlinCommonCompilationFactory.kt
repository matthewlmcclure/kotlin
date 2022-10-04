/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.DefaultKotlinCompilationFriendPathsResolver
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.KotlinCompilationSourceSetsContainer
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.CommonCompilerOptionsFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.DefaultKotlinCompilationDependencyConfigurationsFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.factory.KotlinCompilationImplFactory

class KotlinCommonCompilationFactory internal constructor(
    override val target: KotlinOnlyTarget<*>,
    private val defaultSourceSet: KotlinSourceSet,
    private val compilationFactory: KotlinCompilationImplFactory =
        KotlinCompilationImplFactory(
            compilationDependencyConfigurationsFactory = DefaultKotlinCompilationDependencyConfigurationsFactory.WithoutRuntime,
            compilerOptionsFactory = CommonCompilerOptionsFactory,
            compilationFriendPathsResolver = DefaultKotlinCompilationFriendPathsResolver(
                friendArtifactResolver = DefaultKotlinCompilationFriendPathsResolver.FriendArtifactResolver.composite(
                    DefaultKotlinCompilationFriendPathsResolver.DefaultFriendArtifactResolver,
                    DefaultKotlinCompilationFriendPathsResolver.AdditionalMetadataFriendArtifactResolver
                )
            ),
            /*
            Metadata compilations are created *because* of a pre-existing SourceSet.
            We therefore can create the container inline
            */
            compilationSourceSetsContainerFactory = { _, _ -> KotlinCompilationSourceSetsContainer(defaultSourceSet) }
        )
) : KotlinCompilationFactory<KotlinCommonCompilation> {
    override val itemClass: Class<KotlinCommonCompilation>
        get() = KotlinCommonCompilation::class.java

    override fun create(name: String): KotlinCommonCompilation = target.project.objects.newInstance(
        itemClass, compilationFactory.create(target, name)
    )
}
