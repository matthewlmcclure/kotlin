/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetHierarchyDescriptor
import org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy.KotlinTargetHierarchy
import org.jetbrains.kotlin.gradle.plugin.mpp.targetHierarchy.buildKotlinTargetHierarchies
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.fail

class KotlinTargetHierarchyDescriptorTest {

    private val project = buildProjectWithMPP()
    private val kotlin = project.multiplatformExtension

    @Test
    fun `test - simple descriptor`() {
        val descriptor = KotlinTargetHierarchyDescriptor {
            common {
                if (target.name == "a") group("groupA")
                if (target.name == "b") group("groupB")
            }
        }

        val targetA = kotlin.linuxX64("a")
        val targetB = kotlin.linuxArm64("b")
        val targetC = kotlin.jvm()

        assertEquals(
            setOf(KotlinTargetHierarchy("common", setOf(KotlinTargetHierarchy("groupA")))),
            descriptor.buildKotlinTargetHierarchies(targetA.compilations.main)
        )

        assertEquals(
            setOf(KotlinTargetHierarchy("common", setOf(KotlinTargetHierarchy("groupB")))),
            descriptor.buildKotlinTargetHierarchies(targetB.compilations.main)
        )

        assertEquals(
            setOf(KotlinTargetHierarchy("common")),
            descriptor.buildKotlinTargetHierarchies(targetC.compilations.main)
        )
    }

    @Test
    fun `test - extend`() {
        val descriptor = KotlinTargetHierarchyDescriptor { group("base") }.extend {
            group("base") {
                group("extension")
            }
        }

        assertEquals(
            setOf(KotlinTargetHierarchy("base", setOf(KotlinTargetHierarchy("extension")))),
            descriptor.buildKotlinTargetHierarchies(kotlin.linuxX64().compilations.getByName("main"))
        )
    }


    @Test
    fun `test - extend - with new root`() {
        val descriptor = KotlinTargetHierarchyDescriptor { group("base") }.extend {
            group("newRoot") {
                group("base") {
                    group("extension")
                }
            }
        }

        assertEquals(
            KotlinTargetHierarchy("newRoot", setOf(KotlinTargetHierarchy("base", setOf(KotlinTargetHierarchy("extension"))))),
            descriptor.buildKotlinTargetHierarchies(kotlin.linuxX64().compilations.main).single()
        )
    }


    @Test
    fun `test - extend - with two new roots and two extensions`() {
        val descriptor = KotlinTargetHierarchyDescriptor { group("base") }
            .extend {
                group("newRoot1") {
                    group("base") {
                        group("extension1")
                    }
                }
            }
            .extend {
                group("newRoot2") {
                    group("base") {
                        group("extension2")
                    }
                }
            }

        val hierarchies = descriptor.buildKotlinTargetHierarchies(kotlin.linuxX64().compilations.main)

        if (hierarchies.size != 2)
            fail("Expected two hierarchies: Found $hierarchies")

        assertEquals(
            setOf(
                KotlinTargetHierarchy(
                    "newRoot1", setOf(
                        KotlinTargetHierarchy(
                            "base", setOf(
                                KotlinTargetHierarchy("extension1", emptySet()),
                                KotlinTargetHierarchy("extension2", emptySet())
                            )
                        )
                    )
                ),
                KotlinTargetHierarchy(
                    "newRoot2", setOf(
                        KotlinTargetHierarchy(
                            "base", setOf(
                                KotlinTargetHierarchy("extension1", emptySet()),
                                KotlinTargetHierarchy("extension2", emptySet())
                            )
                        )
                    )
                )
            ),
            hierarchies
        )

        fun KotlinTargetHierarchy.collectChildren(): List<KotlinTargetHierarchy> {
            return children.toList() + children.flatMap { it.collectChildren() }
        }

        /* Check that all equal hierarchies are even the same instance */
        val allNodes = hierarchies.flatMap { it.collectChildren() }
        allNodes.forEach { node ->
            val equalNodes = allNodes.filter { otherNode -> otherNode == node }
            equalNodes.forEach { equalNode ->
                assertSame(node, equalNode, "Expected equal nodes to be the same instance")
            }
        }
    }

    @Test
    fun `test - cycle`() {

    }
}
