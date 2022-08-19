/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetHierarchyDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetHierarchyBuilder
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetHierarchyDescriptor

internal class KotlinTargetHierarchyDslImpl(private val kotlin: KotlinMultiplatformExtension) : KotlinTargetHierarchyDsl {
    override fun apply(
        hierarchyDescriptor: KotlinTargetHierarchyDescriptor,
        describeExtension: (KotlinTargetHierarchyBuilder.() -> Unit)?
    ) {
        kotlin.applyKotlinTargetHierarchy(hierarchyDescriptor.extendIfNotNull(describeExtension), kotlin.targets)
    }

    override fun default(describeExtension: (KotlinTargetHierarchyBuilder.() -> Unit)?) {
        kotlin.applyKotlinTargetHierarchy(naturalKotlinTargetHierarchy.extendIfNotNull(describeExtension), kotlin.targets)
    }

    override fun custom(describe: KotlinTargetHierarchyBuilder.() -> Unit) {
        kotlin.applyKotlinTargetHierarchy(KotlinTargetHierarchyDescriptor(describe), kotlin.targets)
    }
}
