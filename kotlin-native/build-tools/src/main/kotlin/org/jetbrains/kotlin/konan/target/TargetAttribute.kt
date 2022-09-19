/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.target

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.konan.target.TargetAttribute.Companion.TARGET_ATTRIBUTE

private fun nameOf(target: KonanTarget, sanitizer: SanitizerKind?) = "$target${sanitizer.targetSuffix}"

/**
 * Attribute for Kotlin/Native target.
 *
 * Attribute identity is [TARGET_ATTRIBUTE].
 * Construct values with [targetAttribute].
 */
interface TargetAttribute : Named {
    companion object {
        /**
         * Identity of [TargetAttribute]
         */
        val TARGET_ATTRIBUTE = Attribute.of("org.jetbrains.kotlin.target", TargetAttribute::class.java)
    }
}

/**
 * Construct value of [TargetAttribute].
 */
fun ObjectFactory.targetAttribute(target: KonanTarget, sanitizer: SanitizerKind?) = named(TargetAttribute::class.java, nameOf(target, sanitizer))

/**
 * Construct value of [TargetAttribute].
 */
fun targetAttribute(project: Project, targetName: String): TargetAttribute {
    val platformManager = project.extensions.getByType<PlatformManager>()
    return project.objects.targetAttribute(platformManager.targetByName(targetName), null)
}