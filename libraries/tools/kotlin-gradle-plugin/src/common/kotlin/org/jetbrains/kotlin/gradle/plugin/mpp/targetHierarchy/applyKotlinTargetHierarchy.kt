/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy

import org.gradle.api.DomainObjectCollection
import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

internal fun applyKotlinTargetHierarchy(
    hierarchyDescriptor: KotlinTargetHierarchyDescriptor,
    targets: DomainObjectCollection<KotlinTarget>,
    sourceSets: NamedDomainObjectContainer<KotlinSourceSet>
) {
    targets
        .matching { target -> target.platformType != KotlinPlatformType.common }
        .all { target ->
            target.compilations.all { compilation ->
                hierarchyDescriptor.buildKotlinTargetHierarchies(compilation).forEach { hierarchy ->
                    applyKotlinTargetHierarchy(hierarchy, compilation, sourceSets)
                }
            }
        }
}

private fun applyKotlinTargetHierarchy(
    hierarchy: KotlinTargetHierarchy,
    compilation: KotlinCompilation<*>,
    sourceSets: NamedDomainObjectContainer<KotlinSourceSet>
): KotlinSourceSet {
    val sharedSourceSet = sourceSets.maybeCreate(lowerCamelCaseName(hierarchy.group, compilation.name))

    hierarchy.children
        .map { childHierarchy -> applyKotlinTargetHierarchy(childHierarchy, compilation, sourceSets) }
        .forEach { childSourceSet -> childSourceSet.dependsOn(sharedSourceSet) }

    if (hierarchy.children.isEmpty()) {
        compilation.defaultSourceSet.dependsOn(sharedSourceSet)
    }

    return sharedSourceSet
}
