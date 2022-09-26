/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.external

import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType


@ExternalKotlinTargetApi
interface ExternalKotlinTargetDescriptor {
    val targetName: String
    val platformType: KotlinPlatformType
}

@ExternalKotlinTargetApi
fun ExternalKotlinTargetDescriptor(
    targetName: String, platformType: KotlinPlatformType
): ExternalKotlinTargetDescriptor = ExternalKotlinTargetDescriptorImpl(targetName, platformType)

@ExternalKotlinTargetApi
private data class ExternalKotlinTargetDescriptorImpl(
    override val targetName: String, override val platformType: KotlinPlatformType
) : ExternalKotlinTargetDescriptor

