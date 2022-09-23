/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy

import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetHierarchyDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.tooling.core.closure

internal fun KotlinTargetHierarchyDescriptor.buildKotlinTargetHierarchies(compilation: KotlinCompilation<*>): Set<KotlinTargetHierarchy> {
    val builder = KotlinTargetHierarchyBuilderImpl(compilation)
    this(builder)
    return builder.build()
}

private class KotlinTargetHierarchyBuilderImpl(
    override val compilation: KotlinCompilation<*>,
    private val allGroups: MutableMap<String, KotlinTargetHierarchyBuilderImpl> = mutableMapOf(),
) : KotlinTargetHierarchyBuilder {

    override val target: KotlinTarget = compilation.target

    private val declaredChildrenGroups = mutableMapOf<String, KotlinTargetHierarchyBuilderImpl>()

    override fun group(name: String, build: KotlinTargetHierarchyBuilder.() -> Unit) {
        declaredChildrenGroups.getOrPut(name) {
            allGroups.getOrPut(name) { KotlinTargetHierarchyBuilderImpl(compilation, allGroups) }
        }.also(build)
    }

    fun build(): Set<KotlinTargetHierarchy> {
        return build(mutableMapOf())
    }

    private fun build(cache: MutableMap<String /* name */, KotlinTargetHierarchy>): Set<KotlinTargetHierarchy> {
        val roots = declaredChildrenGroups.map { (name, builder) ->
            cache.getOrPut(name) { KotlinTargetHierarchy(name, builder.build(cache)) }
        }.toSet()

        /* Filter unnecessary roots that are already present in some other root */
        val childrenClosure = roots.flatMap { root -> root.closure { it.children } }.toSet()
        return roots - childrenClosure
    }

    override val isNative: Boolean get() = target is KotlinNativeTarget
    override val isApple: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family.isAppleFamily }
    override val isIos: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.IOS }
    override val isWatchos: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.WATCHOS }
    override val isMacos: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.OSX }
    override val isTvos: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.TVOS }
    override val isWindows: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.MINGW }
    override val isLinux: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.LINUX }
    override val isAndroidNative: Boolean get() = target.let { it is KotlinNativeTarget && it.konanTarget.family == Family.ANDROID }
    override val isJvm: Boolean get() = target is KotlinJvmTarget
    override val isAndroidJvm: Boolean get() = target is KotlinAndroidTarget
    override val isJsLegacy: Boolean get() = target is KotlinJsTarget
    override val isJsIr: Boolean get() = target is KotlinJsIrTarget
    override val isJs: Boolean get() = isJsIr || isJsLegacy
}
