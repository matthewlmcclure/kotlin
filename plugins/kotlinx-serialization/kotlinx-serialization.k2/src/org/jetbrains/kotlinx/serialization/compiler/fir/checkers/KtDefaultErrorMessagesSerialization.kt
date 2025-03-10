/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.serialization.compiler.fir.checkers

import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticRenderer
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers

object KtDefaultErrorMessagesSerialization {
    fun getRendererForDiagnostic(diagnostic: KtDiagnostic): KtDiagnosticRenderer {
        val factory = diagnostic.factory
        return MAP[factory] ?: factory.ktRenderer
    }

    val MAP = KtDiagnosticFactoryToRendererMap("Serialization").apply {
        put(
            FirSerializationErrors.INLINE_CLASSES_NOT_SUPPORTED,
            "Inline classes require runtime serialization library version at least {0}, while your classpath has {1}.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
        )
        put(
            FirSerializationErrors.PLUGIN_IS_NOT_ENABLED,
            "kotlinx.serialization compiler plugin is not applied to the module, so this annotation would not be processed. " +
                    "Make sure that you've setup your buildscript correctly and re-import project."
        )
        put(
            FirSerializationErrors.ANONYMOUS_OBJECTS_NOT_SUPPORTED,
            "Anonymous objects or contained in it classes can not be serializable."
        )
        put(
            FirSerializationErrors.INNER_CLASSES_NOT_SUPPORTED,
            "Inner (with reference to outer this) serializable classes are not supported. Remove @Serializable annotation or 'inner' keyword."
        )
        put(
            FirSerializationErrors.EXPLICIT_SERIALIZABLE_IS_REQUIRED,
            "Explicit @Serializable annotation on enum class is required when @SerialName or @SerialInfo annotations are used on its members."
        )
        put(
            FirSerializationErrors.SERIALIZABLE_ANNOTATION_IGNORED,
            "@Serializable annotation without arguments can be used only on sealed interfaces." +
                    "Non-sealed interfaces are polymorphically serializable by default."
        )
        put(
            FirSerializationErrors.NON_SERIALIZABLE_PARENT_MUST_HAVE_NOARG_CTOR,
            "Impossible to make this class serializable because its parent is not serializable and does not have exactly one constructor without parameters"
        )
        put(
            FirSerializationErrors.PRIMARY_CONSTRUCTOR_PARAMETER_IS_NOT_A_PROPERTY,
            "This class is not serializable automatically because it has primary constructor parameters that are not properties"
        )
        put(
            FirSerializationErrors.DUPLICATE_SERIAL_NAME,
            "Serializable class has duplicate serial name of property ''{0}'', either in the class itself or its supertypes",
            CommonRenderers.STRING
        )
        put(
            FirSerializationErrors.DUPLICATE_SERIAL_NAME_ENUM,
            "Enum class ''{0}'' has duplicate serial name ''{1}'' in entry ''{2}''",
            FirDiagnosticRenderers.SYMBOL,
            CommonRenderers.STRING,
            CommonRenderers.STRING
        )
        put(
            FirSerializationErrors.SERIALIZER_NOT_FOUND,
            "Serializer has not been found for type ''{0}''. " +
                    "To use context serializer as fallback, explicitly annotate type or property with @Contextual",
            FirDiagnosticRenderers.RENDER_TYPE_WITH_ANNOTATIONS
        )
        put(
            FirSerializationErrors.SERIALIZER_NULLABILITY_INCOMPATIBLE,
            "Type ''{1}'' is non-nullable and therefore can not be serialized with serializer for nullable type ''{0}''",
            FirDiagnosticRenderers.RENDER_TYPE,
            FirDiagnosticRenderers.RENDER_TYPE
        )
        put(
            FirSerializationErrors.SERIALIZER_TYPE_INCOMPATIBLE,
            "Class ''{1}'', which is serializer for type ''{2}'', is applied here to type ''{0}''. This may lead to errors or incorrect behavior.",
            FirDiagnosticRenderers.RENDER_TYPE,
            FirDiagnosticRenderers.RENDER_TYPE,
            FirDiagnosticRenderers.RENDER_TYPE
        )
        put(
            FirSerializationErrors.LOCAL_SERIALIZER_USAGE,
            "Class ''{0}'' can't be used as a serializer since it is local",
            FirDiagnosticRenderers.RENDER_TYPE
        )
        put(
            FirSerializationErrors.TRANSIENT_MISSING_INITIALIZER,
            "This property is marked as @Transient and therefore must have an initializing expression"
        )
        put(
            FirSerializationErrors.TRANSIENT_IS_REDUNDANT,
            "Property does not have backing field which makes it non-serializable and therefore @Transient is redundant"
        )
        put(
            FirSerializationErrors.INCORRECT_TRANSIENT,
            "@kotlin.jvm.Transient does not affect @Serializable classes. Please use @kotlinx.serialization.Transient instead."
        )
        put(
            FirSerializationErrors.REQUIRED_KOTLIN_TOO_HIGH,
            "Your current Kotlin version is {0}, while kotlinx.serialization core runtime {1} requires at least Kotlin {2}. " +
                    "Please update your Kotlin compiler and IDE plugin.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
            CommonRenderers.STRING
        )

        put(
            FirSerializationErrors.PROVIDED_RUNTIME_TOO_LOW,
            "Your current kotlinx.serialization core version is {0}, while current Kotlin compiler plugin {1} requires at least {2}. " +
                    "Please update your kotlinx.serialization runtime dependency.",
            CommonRenderers.STRING,
            CommonRenderers.STRING,
            CommonRenderers.STRING
        )

        put(
            FirSerializationErrors.INCONSISTENT_INHERITABLE_SERIALINFO,
            "Argument values for inheritable serial info annotation ''{0}'' must be the same as the values in parent type ''{1}''",
            FirDiagnosticRenderers.RENDER_TYPE,
            FirDiagnosticRenderers.RENDER_TYPE
        )

        put(
            FirSerializationErrors.EXTERNAL_CLASS_NOT_SERIALIZABLE,
            "Cannot generate external serializer ''{0}'': class ''{1}'' have constructor parameters which are not properties and therefore it is not serializable automatically",
            FirDiagnosticRenderers.SYMBOL,
            FirDiagnosticRenderers.RENDER_TYPE
        )

        put(
            FirSerializationErrors.EXTERNAL_CLASS_IN_ANOTHER_MODULE,
            "Cannot generate external serializer ''{0}'': class ''{1}'' is defined in another module",
            FirDiagnosticRenderers.SYMBOL,
            FirDiagnosticRenderers.RENDER_TYPE
        )
    }
}
