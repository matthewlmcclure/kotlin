/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.*
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.*
import org.jetbrains.kotlin.gradle.plugin.sources.*
import org.jetbrains.kotlin.gradle.plugin.sources.kpm.FragmentMappedKotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.utils.*
import org.jetbrains.kotlin.tooling.core.closure
import java.util.*
import javax.inject.Inject

interface CompilationDetails<T : KotlinCommonOptions> {
    val target: KotlinTarget

    val compileDependencyFilesHolder: GradleKpmDependencyFilesHolder

    val kotlinDependenciesHolder: HasKotlinDependencies

    val compilationData: KotlinCompilationData<T>

    fun associateWith(other: CompilationDetails<*>)
    val associateCompilations: Set<CompilationDetails<*>>

    fun source(sourceSet: KotlinSourceSet)

    val directlyIncludedKotlinSourceSets: ObservableSet<KotlinSourceSet>

    val allKotlinSourceSets: ObservableSet<KotlinSourceSet>

    val defaultSourceSet: KotlinSourceSet

    @Deprecated("Use defaultSourceSet.name instead", ReplaceWith("defaultSourceSet.name"), level = DeprecationLevel.WARNING)
    val defaultSourceSetName: String get() = defaultSourceSet.name

    @Suppress("UNCHECKED_CAST")
    val compilation: KotlinCompilation<T>
        get() = target.compilations.getByName(compilationData.compilationPurpose) as KotlinCompilation<T>
}

interface CompilationDetailsWithRuntime<T : KotlinCommonOptions> : CompilationDetails<T> {
    val runtimeDependencyFilesHolder: GradleKpmDependencyFilesHolder
}

internal val CompilationDetails<*>.associateCompilationsClosure: Iterable<CompilationDetails<*>>
    get() = closure { it.associateCompilations }

internal open class VariantMappedCompilationDetails<T : KotlinCommonOptions>(
    open val variant: GradleKpmVariantInternal,
    override val target: KotlinTarget,
    defaultSourceSet: FragmentMappedKotlinSourceSet,
) : AbstractCompilationDetails<T>(defaultSourceSet) {

    @Suppress("UNCHECKED_CAST")
    override val compilationData: KotlinCompilationData<T>
        get() = variant.compilationData as KotlinCompilationData<T>

    override fun whenSourceSetAdded(sourceSet: KotlinSourceSet) {
        compilation.defaultSourceSet.dependsOn(sourceSet)
    }

    override fun associateWith(other: CompilationDetails<*>) {
        if (other !is VariantMappedCompilationDetails<*>)
            error("a mapped variant can't be associated with a legacy one")
        val otherModule = other.variant.containingModule
        if (otherModule === variant.containingModule)
            error("cannot associate $compilation with ${other.compilation} as they are mapped to the same $otherModule")
        variant.containingModule.dependencies { implementation(otherModule) }
    }

    override val associateCompilations: Set<CompilationDetails<*>> get() = emptySet()

    override val compileDependencyFilesHolder: GradleKpmDependencyFilesHolder
        get() = GradleKpmDependencyFilesHolder.ofVariantCompileDependencies(variant)

    override val kotlinDependenciesHolder: HasKotlinDependencies
        get() = variant

}

internal open class VariantMappedCompilationDetailsWithRuntime<T : KotlinCommonOptions>(
    override val variant: GradleKpmVariantWithRuntimeInternal,
    target: KotlinTarget,
    defaultSourceSet: FragmentMappedKotlinSourceSet
) : VariantMappedCompilationDetails<T>(variant, target, defaultSourceSet),
    CompilationDetailsWithRuntime<T> {
    override val runtimeDependencyFilesHolder: GradleKpmDependencyFilesHolder
        get() = GradleKpmDependencyFilesHolder.ofVariantRuntimeDependencies(variant)
}

internal class WithJavaCompilationDetails<T : KotlinCommonOptions, CO : CompilerCommonOptions>(
    target: KotlinTarget,
    compilationPurpose: String,
    defaultSourceSet: KotlinSourceSet,
    createCompilerOptions: DefaultCompilationDetails<T, CO>.() -> HasCompilerOptions<CO>,
    createKotlinOptions: DefaultCompilationDetails<T, CO>.() -> T
) : DefaultCompilationDetailsWithRuntime<T, CO>(target, compilationPurpose, defaultSourceSet, createCompilerOptions, createKotlinOptions) {
    @Suppress("UNCHECKED_CAST")
    override val compilation: KotlinWithJavaCompilation<T, CO>
        get() = super.compilation as KotlinWithJavaCompilation<T, CO>

    val javaSourceSet: SourceSet
        get() = compilation.javaSourceSet

    override val output: KotlinCompilationOutput by lazy { KotlinWithJavaCompilationOutput(compilation) }

    override val compileDependencyFilesHolder: GradleKpmDependencyFilesHolder
        get() = object : GradleKpmDependencyFilesHolder {
            override val dependencyConfigurationName: String by javaSourceSet::compileClasspathConfigurationName
            override var dependencyFiles: FileCollection by javaSourceSet::compileClasspath
        }

    override val runtimeDependencyFilesHolder: GradleKpmDependencyFilesHolder
        get() = object : GradleKpmDependencyFilesHolder {
            override val dependencyConfigurationName: String by javaSourceSet::runtimeClasspathConfigurationName
            override var dependencyFiles: FileCollection by javaSourceSet::runtimeClasspath
        }

    override fun addAssociateCompilationDependencies(other: KotlinCompilation<*>) {
        if (compilationPurpose != SourceSet.TEST_SOURCE_SET_NAME || other.name != SourceSet.MAIN_SOURCE_SET_NAME) {
            super.addAssociateCompilationDependencies(other)
        } // otherwise, do nothing: the Java Gradle plugin adds these dependencies for us, we don't need to add them to the classpath
    }
}

class AndroidCompilationDetails(
    target: KotlinTarget,
    compilationPurpose: String,
    defaultSourceSet: KotlinSourceSet,
    val androidVariant: BaseVariant,
    /** Workaround mutual creation order: a compilation is not added to the target's compilations collection until some point, pass it here */
    private val getCompilationInstance: () -> KotlinJvmAndroidCompilation
) : DefaultCompilationDetailsWithRuntime<KotlinJvmOptions, CompilerJvmOptions>(
    target,
    compilationPurpose,
    defaultSourceSet,
    {
        object : HasCompilerOptions<CompilerJvmOptions> {
            override val options: CompilerJvmOptions =
                target.project.objects.newInstance(CompilerJvmOptionsDefault::class.java)
        }
    },
    {
        object : KotlinJvmOptions {
            override val options: CompilerJvmOptions
                get() = compilerOptions.options
        }
    }
) {
    override val compilation: KotlinJvmAndroidCompilation get() = getCompilationInstance()

    override val friendArtifacts: FileCollection
        get() = target.project.files(super.friendArtifacts, compilation.testedVariantArtifacts)

    /*
    * Example of how multiplatform dependencies from common would get to Android test classpath:
    * commonMainImplementation -> androidDebugImplementation -> debugImplementation -> debugAndroidTestCompileClasspath
    * After the fix for KT-35916 MPP compilation configurations receive a 'compilation' postfix for disambiguation.
    * androidDebugImplementation remains a source set configuration, but no longer contains compilation dependencies.
    * Therefore, it doesn't get dependencies from common source sets.
    * We now explicitly add associate compilation dependencies to the Kotlin test compilation configurations (test classpaths).
    * This helps, because the Android test classpath configurations extend from the Kotlin test compilations' directly.
    */
    override fun addAssociateCompilationDependencies(other: KotlinCompilation<*>) {
        compilation.compileDependencyConfigurationName.addAllDependenciesFromOtherConfigurations(
            project,
            other.apiConfigurationName,
            other.implementationConfigurationName,
            other.compileOnlyConfigurationName
        )
    }

    override val kotlinDependenciesHolder: HasKotlinDependencies
        get() = object : HasKotlinDependencies by super.kotlinDependenciesHolder {
            override val relatedConfigurationNames: List<String>
                get() = super.relatedConfigurationNames + listOf(
                    "${androidVariant.name}ApiElements",
                    "${androidVariant.name}RuntimeElements",
                    androidVariant.compileConfiguration.name,
                    androidVariant.runtimeConfiguration.name
                )
        }
}

internal class MetadataCompilationDetails(
    target: KotlinTarget,
    name: String,
    defaultSourceSet: KotlinSourceSet,
) : DefaultCompilationDetails<KotlinMultiplatformCommonOptions, CompilerMultiplatformCommonOptions>(
    target,
    name,
    defaultSourceSet,
    {
        object : HasCompilerOptions<CompilerMultiplatformCommonOptions> {
            override val options: CompilerMultiplatformCommonOptions =
                target.project.objects.newInstance(CompilerMultiplatformCommonOptionsDefault::class.java)
        }
    },
    {
        object : KotlinMultiplatformCommonOptions {
            override val options: CompilerMultiplatformCommonOptions
                get() = compilerOptions.options
        }
    }
) {

    override val friendArtifacts: FileCollection
        get() = super.friendArtifacts.plus(run {
            val project = target.project
            val friendSourceSets = getVisibleSourceSetsFromAssociateCompilations(defaultSourceSet)
            project.files(friendSourceSets.mapNotNull { target.compilations.findByName(it.name)?.output?.classesDirs })
        })
}

internal open class JsCompilationDetails(
    target: KotlinTarget,
    compilationPurpose: String,
    defaultSourceSet: KotlinSourceSet,
) : DefaultCompilationDetailsWithRuntime<KotlinJsOptions, CompilerJsOptions>(
    target,
    compilationPurpose,
    defaultSourceSet,
    {
        object : HasCompilerOptions<CompilerJsOptions> {
            override val options: CompilerJsOptions =
                target.project.objects.newInstance(CompilerJsOptionsDefault::class.java)
        }
    },
    {
        object : KotlinJsOptions {
            override val options: CompilerJsOptions
                get() = compilerOptions.options
        }
    }
) {

    internal abstract class JsCompilationDependenciesHolder @Inject constructor(
        val target: KotlinTarget,
        val compilationPurpose: String
    ) : HasKotlinDependencies {
        override val apiConfigurationName: String
            get() = disambiguateNameInPlatform(API)

        override val implementationConfigurationName: String
            get() = disambiguateNameInPlatform(IMPLEMENTATION)

        override val compileOnlyConfigurationName: String
            get() = disambiguateNameInPlatform(COMPILE_ONLY)

        override val runtimeOnlyConfigurationName: String
            get() = disambiguateNameInPlatform(RUNTIME_ONLY)

        protected open val disambiguationClassifierInPlatform: String?
            get() = when (target) {
                is KotlinJsTarget -> target.disambiguationClassifierInPlatform
                is KotlinJsIrTarget -> target.disambiguationClassifierInPlatform
                else -> error("Unexpected target type of $target")
            }

        private fun disambiguateNameInPlatform(simpleName: String): String {
            return lowerCamelCaseName(
                disambiguationClassifierInPlatform,
                compilationPurpose.takeIf { it != KotlinCompilation.MAIN_COMPILATION_NAME },
                "compilation",
                simpleName
            )
        }

        override fun dependencies(configure: KotlinDependencyHandler.() -> Unit): Unit =
            DefaultKotlinDependencyHandler(this, target.project).run(configure)

        override fun dependencies(configure: Action<KotlinDependencyHandler>) =
            dependencies { configure.execute(this) }
    }

    override val kotlinDependenciesHolder: HasKotlinDependencies
        get() = target.project.objects.newInstance(JsCompilationDependenciesHolder::class.java, target, compilationPurpose)

}

internal class JsIrCompilationDetails(
    target: KotlinTarget, compilationPurpose: String, defaultSourceSet: KotlinSourceSet
) : JsCompilationDetails(target, compilationPurpose, defaultSourceSet) {

    override fun addSourcesToCompileTask(sourceSet: KotlinSourceSet, addAsCommonSources: Lazy<Boolean>) {
        super.addSourcesToCompileTask(sourceSet, addAsCommonSources)
        (compilation as KotlinJsIrCompilation).allSources.add(sourceSet.kotlin)
    }

    internal abstract class JsIrCompilationDependencyHolder @Inject constructor(target: KotlinTarget, compilationPurpose: String) :
        JsCompilationDependenciesHolder(target, compilationPurpose) {
        override val disambiguationClassifierInPlatform: String?
            get() = (target as KotlinJsIrTarget).disambiguationClassifierInPlatform
    }

    override val kotlinDependenciesHolder: HasKotlinDependencies
        get() = target.project.objects.newInstance(JsIrCompilationDependencyHolder::class.java, target, compilationPurpose)
}

internal abstract class KotlinDependencyConfigurationsHolder @Inject constructor(
    val project: Project,
    private val configurationNamesPrefix: String?,
) : HasKotlinDependencies {

    override val apiConfigurationName: String
        get() = lowerCamelCaseName(configurationNamesPrefix, API)

    override val implementationConfigurationName: String
        get() = lowerCamelCaseName(configurationNamesPrefix, IMPLEMENTATION)

    override val compileOnlyConfigurationName: String
        get() = lowerCamelCaseName(configurationNamesPrefix, COMPILE_ONLY)

    override val runtimeOnlyConfigurationName: String
        get() = lowerCamelCaseName(configurationNamesPrefix, RUNTIME_ONLY)

    override fun dependencies(configure: KotlinDependencyHandler.() -> Unit): Unit =
        DefaultKotlinDependencyHandler(this, project).run(configure)

    override fun dependencies(configure: Action<KotlinDependencyHandler>) =
        dependencies { configure.execute(this) }
}
