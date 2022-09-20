/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kotlinp

import kotlinx.metadata.*
import kotlinx.metadata.jvm.*
import kotlin.contracts.ExperimentalContracts

private object SpecialCharacters {
    const val TYPE_ALIAS_MARKER = '^'
}

@OptIn(ExperimentalContextReceivers::class, ExperimentalContracts::class)
private fun visitFunction(
    function: KmFunction,
    settings: KotlinpSettings,
    sb: StringBuilder,
    flags: Flags = function.flags,
    name: String = function.name
) {
    val typeParams = function.typeParameters.map {
        printTypeParameter(it, settings)
    }
    val receiverParameterType = function.receiverParameterType?.let(::printType)
    val contextReceiverTypes = function.contextReceiverTypes.map(::printType)
    val params = function.valueParameters.map(::printValueParameter)
    val returnType = printType(function.returnType)
    val versionRequirements = function.versionRequirements.map(::printVersionRequirement)
    val contract = function.contract?.let(::printContract)
    val jvmSignature = function.signature
    val lambdaClassOriginName = function.lambdaClassOriginName

    sb.appendLine()
    if (lambdaClassOriginName != null) {
        sb.appendLine("  // lambda class origin: $lambdaClassOriginName")
    }
    for (versionRequirement in versionRequirements) {
        sb.appendLine("  // $versionRequirement")
    }
    if (jvmSignature != null) {
        sb.appendLine("  // signature: $jvmSignature")
    }
    if (contextReceiverTypes.isNotEmpty()) {
        sb.appendLine(contextReceiverTypes.joinToString(prefix = "  context(", postfix = ")"))
    }
    sb.append("  ")
    sb.appendFlags(flags, FUNCTION_FLAGS_MAP)
    sb.append("fun ")
    if (typeParams.isNotEmpty()) {
        typeParams.joinTo(sb, prefix = "<", postfix = ">")
        sb.append(" ")
    }
    if (receiverParameterType != null) {
        sb.append(receiverParameterType).append(".")
    }
    sb.append(name)
    params.joinTo(sb, prefix = "(", postfix = ")")
    sb.append(": ").append(returnType)
    sb.appendLine()
    if (contract != null) {
        sb.appendLine("    $contract")
    }
}

@OptIn(ExperimentalContextReceivers::class)
private fun visitProperty(
    property: KmProperty,
    settings: KotlinpSettings,
    sb: StringBuilder,
    flags: Flags = property.flags,
    name: String = property.name,
    getterFlags: Flags = property.getterFlags,
    setterFlags: Flags = property.setterFlags
) {
    val typeParams = property.typeParameters.map { printTypeParameter(it, settings) }
    val receiverParameterType = property.receiverParameterType?.let(::printType)
    val contextReceiverTypes = property.contextReceiverTypes.map(::printType)
    val returnType = property.returnType.let(::printType)
    val setterParameter = property.setterParameter?.let { printValueParameter(it) }
    val versionRequirements = property.versionRequirements.map { printVersionRequirement(it) }
    val jvmFieldSignature: JvmMemberSignature? = property.fieldSignature
    val jvmGetterSignature: JvmMemberSignature? = property.getterSignature
    val jvmSetterSignature: JvmMemberSignature? = property.setterSignature
    val jvmSyntheticMethodForAnnotationsSignature: JvmMemberSignature? = property.syntheticMethodForAnnotations
    val jvmSyntheticMethodForDelegateSignature: JvmMemberSignature? = property.syntheticMethodForDelegate
    val isMovedFromInterfaceCompanion: Boolean = JvmFlag.Property.IS_MOVED_FROM_INTERFACE_COMPANION(property.jvmFlags)

    sb.appendLine()
    for (versionRequirement in versionRequirements) {
        sb.appendLine("  // $versionRequirement")
    }
    if (jvmFieldSignature != null) {
        sb.appendLine("  // field: $jvmFieldSignature")
    }
    if (jvmGetterSignature != null) {
        sb.appendLine("  // getter: $jvmGetterSignature")
    }
    if (jvmSetterSignature != null) {
        sb.appendLine("  // setter: $jvmSetterSignature")
    }
    if (jvmSyntheticMethodForAnnotationsSignature != null) {
        sb.appendLine("  // synthetic method for annotations: $jvmSyntheticMethodForAnnotationsSignature")
    }
    if (jvmSyntheticMethodForDelegateSignature != null) {
        sb.appendLine("  // synthetic method for delegate: $jvmSyntheticMethodForDelegateSignature")
    }
    if (isMovedFromInterfaceCompanion) {
        sb.appendLine("  // is moved from interface companion")
    }
    if (contextReceiverTypes.isNotEmpty()) {
        sb.appendLine(contextReceiverTypes.joinToString(prefix = "  context(", postfix = ")"))
    }
    sb.append("  ")
    sb.appendFlags(flags, PROPERTY_FLAGS_MAP)
    sb.append(if (Flag.Property.IS_VAR(flags)) "var " else "val ")
    if (typeParams.isNotEmpty()) {
        typeParams.joinTo(sb, prefix = "<", postfix = ">")
        sb.append(" ")
    }
    if (receiverParameterType != null) {
        sb.append(receiverParameterType).append(".")
    }
    sb.append(name)
    sb.append(": ").append(returnType)
    if (Flag.Property.HAS_CONSTANT(flags)) {
        sb.append(" /* = ... */")
    }
    sb.appendLine()
    if (Flag.Property.HAS_GETTER(flags)) {
        sb.append("    ")
        sb.appendFlags(getterFlags, PROPERTY_ACCESSOR_FLAGS_MAP)
        sb.appendLine("get")
    }
    if (Flag.Property.HAS_SETTER(flags)) {
        sb.append("    ")
        sb.appendFlags(setterFlags, PROPERTY_ACCESSOR_FLAGS_MAP)
        sb.append("set")
        if (setterParameter != null) {
            sb.append("(").append(setterParameter).append(")")
        }
        sb.appendLine()
    }
}


private fun visitConstructor(constructor: KmConstructor, sb: StringBuilder, flags: Flags = constructor.flags) {
    val params = constructor.valueParameters.map(::printValueParameter)
    val versionRequirements = constructor.versionRequirements.map(::printVersionRequirement)
    val jvmSignature: JvmMemberSignature? = constructor.signature
    sb.appendLine()
    for (versionRequirement in versionRequirements) {
        sb.appendLine("  // $versionRequirement")
    }
    if (jvmSignature != null) {
        sb.appendLine("  // signature: $jvmSignature")
    }
    sb.append("  ")
    sb.appendFlags(flags, CONSTRUCTOR_FLAGS_MAP)
    sb.append("constructor(")
    params.joinTo(sb)
    sb.appendLine(")")
}

private fun visitTypeAlias(
    typeAlias: KmTypeAlias,
    settings: KotlinpSettings,
    sb: StringBuilder,
    flags: Flags = typeAlias.flags,
    name: String = typeAlias.name
) {
    val annotations = typeAlias.annotations
    val typeParams = typeAlias.typeParameters.map { printTypeParameter(it, settings) }
    val underlyingType = typeAlias.underlyingType.let(::printType)
    val expandedType = typeAlias.expandedType.let(::printType)
    val versionRequirements = typeAlias.versionRequirements.map(::printVersionRequirement)

    sb.appendLine()
    for (versionRequirement in versionRequirements) {
        sb.appendLine("  // $versionRequirement")
    }
    for (annotation in annotations) {
        sb.append("  ").append("@").append(renderAnnotation(annotation)).appendLine()
    }
    sb.append("  ")
    sb.appendFlags(flags, VISIBILITY_FLAGS_MAP)
    sb.append("typealias ").append(name)
    if (typeParams.isNotEmpty()) {
        typeParams.joinTo(sb, prefix = "<", postfix = ">")
    }
    sb.append(" = ").append(underlyingType)
    sb.append(" /* = ").append(expandedType).append(" */")
    sb.appendLine()
}

private fun printType(type: KmType, flags: Flags = type.flags): String {
    val classifier = when (val cls = type.classifier) {
        is KmClassifier.Class -> cls.name
        is KmClassifier.TypeParameter -> "T#${cls.id}"
        is KmClassifier.TypeAlias -> "${cls.name}${SpecialCharacters.TYPE_ALIAS_MARKER}"
    }

    val arguments = mutableListOf<String>()
    type.arguments.forEach { argument ->
        if (argument == KmTypeProjection.STAR) {
            arguments += "*"
        } else {
            val (variance, argumentType) = argument
            if (variance == null || argumentType == null)
                throw InconsistentKotlinMetadataException("Variance and type must be set for non-star type projection")
            val argumentTypeString = printType(argumentType)
            arguments += buildString {
                if (variance != KmVariance.INVARIANT) {
                    append(variance.name.lowercase()).append(" ")
                }
                append(argumentTypeString)
            }
        }
    }

    val abbreviatedType = type.abbreviatedType?.let(::printType)
    val outerType = type.outerType?.let(::printType)
    val platformTypeUpperBound = type.flexibleTypeUpperBound?.let {
        (if (it.typeFlexibilityId == JvmTypeExtensionVisitor.PLATFORM_TYPE_ID) {
            printType(it.type, it.type.flags)
        } else null)
    }
    val jvmIsRaw = type.isRaw
    val jvmAnnotations: MutableList<KmAnnotation> = type.annotations

    return buildString {
        for (annotation in jvmAnnotations) {
            append("@").append(renderAnnotation(annotation)).append(" ")
        }
        if (jvmIsRaw) {
            append("/* raw */ ")
        }
        appendFlags(flags, TYPE_FLAGS_MAP)
        if (outerType != null) {
            append(outerType).append(".").append(classifier.substringAfterLast('.'))
        } else {
            append(classifier)
        }
        if (arguments.isNotEmpty()) {
            arguments.joinTo(this, prefix = "<", postfix = ">")
        }
        if (Flag.Type.IS_NULLABLE(flags)) {
            append("?")
        }
        if (Flag.Type.IS_DEFINITELY_NON_NULL(flags)) {
            append(" & Any")
        }
        if (abbreviatedType != null) {
            append(" /* = ").append(abbreviatedType).append(" */")
        }

        if (platformTypeUpperBound == "$this?") {
            append("!")
        } else if (platformTypeUpperBound != null) {
            append("..").append(platformTypeUpperBound)
        }
    }
}

private fun printTypeParameter(
    typeParameter: KmTypeParameter,
    settings: KotlinpSettings,
    flags: Flags = typeParameter.flags,
    name: String = typeParameter.name,
    id: Int = typeParameter.id,
    variance: KmVariance = typeParameter.variance,
): String {
    val bounds = typeParameter.upperBounds.map(::printType)
    val jvmAnnotations = typeParameter.annotations
    return buildString {
        appendFlags(flags, TYPE_PARAMETER_FLAGS_MAP)
        for (annotation in jvmAnnotations) {
            append("@").append(renderAnnotation(annotation)).append(" ")
        }
        if (variance != KmVariance.INVARIANT) {
            append(variance.name.lowercase()).append(" ")
        }
        append("T#$id")
        if (settings.isVerbose) {
            append(" /* $name */")
        }
        if (bounds.isNotEmpty()) {
            bounds.joinTo(this, separator = " & ", prefix = " : ")
        }
    }
}

private fun printValueParameter(
    valueParameter: KmValueParameter,
    flags: Flags = valueParameter.flags,
    name: String = valueParameter.name,
): String {
    val type = printType(valueParameter.type)
    val varargElementType = valueParameter.varargElementType?.let(::printType)
    return buildString {
        appendFlags(flags, VALUE_PARAMETER_FLAGS_MAP)
        if (varargElementType != null) {
            append("vararg ").append(name).append(": ").append(varargElementType).append(" /* ").append(type).append(" */")
        } else {
            append(name).append(": ").append(type)
        }
        if (Flag.ValueParameter.DECLARES_DEFAULT_VALUE(flags)) {
            append(" /* = ... */")
        }
    }
}

private fun renderAnnotation(annotation: KmAnnotation): String =
    annotation.className + if (annotation.arguments.isEmpty()) "" else
        annotation.arguments.entries.joinToString(prefix = "(", postfix = ")") { (name, argument) ->
            "$name = ${renderAnnotationArgument(argument)}"
        }

private fun renderAnnotationArgument(arg: KmAnnotationArgument): String =
    when (arg) {
        is KmAnnotationArgument.ByteValue -> arg.value.toString() + ".toByte()"
        is KmAnnotationArgument.CharValue -> "'${arg.value.toString().sanitize(quote = '\'')}'"
        is KmAnnotationArgument.ShortValue -> arg.value.toString() + ".toShort()"
        is KmAnnotationArgument.IntValue -> arg.value.toString()
        is KmAnnotationArgument.LongValue -> arg.value.toString() + "L"
        is KmAnnotationArgument.FloatValue -> arg.value.toString() + "f"
        is KmAnnotationArgument.DoubleValue -> arg.value.toString()
        is KmAnnotationArgument.UByteValue -> arg.value.toString() + ".toUByte()"
        is KmAnnotationArgument.UShortValue -> arg.value.toString() + ".toUShort()"
        is KmAnnotationArgument.UIntValue -> arg.value.toString() + "u"
        is KmAnnotationArgument.ULongValue -> arg.value.toString() + "uL"
        is KmAnnotationArgument.BooleanValue -> arg.value.toString()
        is KmAnnotationArgument.StringValue -> "\"${arg.value.sanitize(quote = '"')}\""
        is KmAnnotationArgument.KClassValue -> buildString {
            repeat(arg.arrayDimensionCount) { append("kotlin/Array<") }
            append(arg.className).append("::class")
            repeat(arg.arrayDimensionCount) { append(">") }
        }
        is KmAnnotationArgument.EnumValue -> "${arg.enumClassName}.${arg.enumEntryName}"
        is KmAnnotationArgument.AnnotationValue -> arg.annotation.let { annotation ->
            val args = annotation.arguments.entries.joinToString { (name, argument) ->
                "$name = ${renderAnnotationArgument(argument)}"
            }
            "${annotation.className}($args)"
        }
        is KmAnnotationArgument.ArrayValue -> arg.elements.joinToString(
            prefix = "[",
            postfix = "]",
            transform = ::renderAnnotationArgument
        )
    }

private fun String.sanitize(quote: Char): String =
    buildString(length) {
        for (c in this@sanitize) {
            when (c) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                quote -> append("\\").append(quote)
                else -> append(if (c.isISOControl()) "\\u%04x".format(c.code) else c)
            }
        }
    }

private fun printVersionRequirement(versionRequirement: KmVersionRequirement): String {
    val version = with(versionRequirement.version) { "$major.$minor.$patch" }

    return buildString {
        append("requires ").append(
            when (versionRequirement.kind) {
                KmVersionRequirementVersionKind.LANGUAGE_VERSION -> "language version"
                KmVersionRequirementVersionKind.COMPILER_VERSION -> "compiler version"
                KmVersionRequirementVersionKind.API_VERSION -> "API version"
            }
        ).append(" ").append(version)

        listOfNotNull(
            "level=${versionRequirement.level}",
            versionRequirement.errorCode?.let { "errorCode=$it" },
            versionRequirement.message?.let { "message=\"$it\"" }
        ).joinTo(this, prefix = " (", postfix = ")")
    }
}

private fun StringBuilder.appendFlags(flags: Flags, map: Map<Flag, String>) {
    for ((modifier, string) in map) {
        if (modifier(flags)) {
            append(string)
            if (string.isNotEmpty()) append(" ")
        }
    }
}

private fun StringBuilder.appendDeclarationContainerExtensions(
    settings: KotlinpSettings,
    localDelegatedProperties: List<StringBuilder>,
    moduleName: String?
) {
    for ((i, sb) in localDelegatedProperties.withIndex()) {
        appendLine()
        appendLine("  // local delegated property #$i")
        for (line in sb.lineSequence()) {
            if (line.isBlank()) continue
            // Comment all uncommented lines to not make it look like these properties are declared here
            appendLine(
                if (line.startsWith("  ") && !line.startsWith("  //")) line.replaceFirst("  ", "  // ")
                else line
            )
        }
    }

    if (settings.isVerbose && moduleName != null) {
        appendLine()
        appendLine("  // module name: $moduleName")
    }
}

@ExperimentalContracts
private fun printContract(kmContract: KmContract): String {
    val effects = kmContract.effects.map(::printEffect)
    return buildString {
        appendLine("contract {")
        for (effect in effects) {
            appendLine("      $effect")
        }
        append("    }")
    }
}

@ExperimentalContracts
private fun printEffect(
    kmEffect: KmEffect,
    type: KmEffectType = kmEffect.type,
    invocationKind: KmEffectInvocationKind? = kmEffect.invocationKind
): String {
    var argument: String? = null
    kmEffect.constructorArguments.forEach {
        // If there are several arguments, only the first is taken, see ContractDeserializerImpl.deserializeSimpleEffect
        if (argument == null) {
            argument = printEffectExpression(it)
        }
    }
    val conclusion: String? = kmEffect.conclusion?.let(::printEffectExpression)

    return buildString {
        when (type) {
            KmEffectType.RETURNS_CONSTANT -> {
                append("returns(")
                if (argument != null) {
                    append(argument)
                }
                append(")")
            }
            KmEffectType.CALLS -> {
                append("callsInPlace($argument")
                if (invocationKind != null) {
                    append(", InvocationKind.${invocationKind.name}")
                }
                append(")")
            }
            KmEffectType.RETURNS_NOT_NULL -> {
                append("returnsNotNull()")
            }
        }
        if (conclusion != null) {
            append(" implies ($conclusion)")
        }
    }
}

@ExperimentalContracts
private fun printEffectExpression(effectExpression: KmEffectExpression): String {
    val flags: Flags = effectExpression.flags
    val parameterIndex: Int? = effectExpression.parameterIndex
    val constantValue: List<Any?>? = effectExpression.constantValue?.let { listOf(it.value) }
    val isInstanceType = effectExpression.isInstanceType?.let(::printType)
    val andArguments = effectExpression.andArguments.map(::printEffectExpression)
    val orArguments = effectExpression.orArguments.map(::printEffectExpression)

    fun wrapIfNeeded(s: String): String =
        // A simple heuristic to avoid wrapping into unnecessary parentheses
        if ('&' in s || '|' in s) "($s)" else s

    return buildString {
        append(
            when {
                constantValue != null -> constantValue.single().toString()
                parameterIndex != null -> "p#$parameterIndex"
                else -> ""
            }
        )
        if (isInstanceType != null) {
            append(" ")
            if (Flag.EffectExpression.IS_NEGATED(flags)) append("!")
            append("is $isInstanceType")
        }
        if (Flag.EffectExpression.IS_NULL_CHECK_PREDICATE(flags)) {
            append(if (Flag.EffectExpression.IS_NEGATED(flags)) " != " else " == ")
            append("null")
        }

        if (orArguments.isEmpty()) {
            for (andArgument in andArguments) {
                if (!isEmpty()) append(" && ")
                append(wrapIfNeeded(andArgument))
            }
        }
        if (andArguments.isEmpty()) {
            for (orArgument in orArguments) {
                if (!isEmpty()) append(" || ")
                append(wrapIfNeeded(orArgument))
            }
        }
    }
}

interface AbstractPrinter<in T : KotlinClassMetadata> {
    fun print(klass: T): String
}

class ClassPrinter(private val settings: KotlinpSettings) : AbstractPrinter<KotlinClassMetadata.Class> {
    private val sb = StringBuilder()
    internal val result = StringBuilder()

    private var flags: Flags? = null
    private var name: ClassName? = null
    private val typeParams = mutableListOf<String>()
    private val supertypes = mutableListOf<String>()
    private val contextReceiverTypes = mutableListOf<String>()
    private val versionRequirements = mutableListOf<String>()
    private var anonymousObjectOriginName: String? = null

    private fun visitEnd() {
        if (anonymousObjectOriginName != null) {
            result.appendLine("// anonymous object origin: $anonymousObjectOriginName")
        }
        for (versionRequirement in versionRequirements) {
            result.appendLine("// $versionRequirement")
        }
        if (contextReceiverTypes.isNotEmpty()) {
            result.appendLine(contextReceiverTypes.joinToString(prefix = "context(", postfix = ")"))
        }
        result.appendFlags(flags!!, CLASS_FLAGS_MAP)
        result.append(name)
        if (typeParams.isNotEmpty()) {
            typeParams.joinTo(result, prefix = "<", postfix = ">")
        }
        if (supertypes.isNotEmpty()) {
            result.append(" : ")
            supertypes.joinTo(result)
        }
        result.appendLine(" {")
        result.append(sb)
        result.appendLine("}")
    }

    private fun visitCompanionObject(name: String) {
        sb.appendLine()
        sb.appendLine("  // companion object: $name")
    }

    private fun visitNestedClass(name: String) {
        sb.appendLine()
        sb.appendLine("  // nested class: $name")
    }

    private fun visitEnumEntry(name: String) {
        sb.appendLine()
        sb.appendLine("  $name,")
    }

    private fun visitSealedSubclass(name: ClassName) {
        sb.appendLine()
        sb.appendLine("  // sealed subclass: $name")
    }

    private fun visitInlineClassUnderlyingPropertyName(name: String) {
        sb.appendLine()
        sb.appendLine("  // underlying property: $name")
    }

    private fun visitInlineClassUnderlyingType(type: String) {
        sb.appendLine()
        sb.appendLine("  // underlying type: $type")
    }

    private fun visitExtensions(kclass: KmClass) {
        val localDelegatedProperties = mutableListOf<StringBuilder>()
        val moduleName: String? = kclass.moduleName
        val jvmFlags: Flags = kclass.jvmFlags
        anonymousObjectOriginName = kclass.anonymousObjectOriginName

        kclass.localDelegatedProperties.forEach { p ->
            visitProperty(
                p, settings, StringBuilder().also { localDelegatedProperties.add(it) }
            )
        }

        sb.appendDeclarationContainerExtensions(settings, localDelegatedProperties, moduleName)
        if (JvmFlag.Class.HAS_METHOD_BODIES_IN_INTERFACE(jvmFlags)) {
            sb.appendLine()
            sb.appendLine("  // has method bodies in interface")
        }
        if (JvmFlag.Class.IS_COMPILED_IN_COMPATIBILITY_MODE(jvmFlags)) {
            sb.appendLine()
            sb.appendLine("  // is compiled in compatibility mode")
        }
    }

    override fun print(klass: KotlinClassMetadata.Class): String = print(klass.toKmClass())

    @OptIn(ExperimentalContextReceivers::class)
    fun print(kmClass: KmClass): String {
        flags = kmClass.flags
        name = kmClass.name
        kmClass.typeParameters.forEach { typeParams.add(printTypeParameter(it, settings)) }
        supertypes.addAll(kmClass.supertypes.map { printType(it) })

        kmClass.constructors.forEach { visitConstructor(it, sb) }
        kmClass.functions.forEach { visitFunction(it, settings, sb) }
        kmClass.properties.forEach { visitProperty(it, settings, sb) }
        kmClass.typeAliases.forEach { visitTypeAlias(it, settings, sb) }
        kmClass.companionObject?.let { visitCompanionObject(it) }
        kmClass.nestedClasses.forEach { visitNestedClass(it) }
        kmClass.enumEntries.forEach { visitEnumEntry(it) }
        kmClass.sealedSubclasses.forEach { visitSealedSubclass(it) }
        kmClass.inlineClassUnderlyingPropertyName?.let { visitInlineClassUnderlyingPropertyName(it) }
        kmClass.inlineClassUnderlyingType?.let { visitInlineClassUnderlyingType(printType(it)) }
        kmClass.contextReceiverTypes.forEach { contextReceiverTypes.add(printType(it)) }
        kmClass.versionRequirements.forEach { versionRequirements.add(printVersionRequirement(it)) }

        visitExtensions(kmClass)
        visitEnd()
        return result.toString()
    }
}

abstract class PackagePrinter(private val settings: KotlinpSettings) {
    internal val sb = StringBuilder().apply {
        appendLine("package {")
    }

    private fun visitExtensions(kmPackage: KmPackage) {
        val localDelegatedProperties = mutableListOf<StringBuilder>()
        val moduleName: String? = null // kmPackage.moduleName was unused here, why?

        kmPackage.localDelegatedProperties.forEach { p ->
            visitProperty(p, settings, StringBuilder().also { localDelegatedProperties.add(it) })
        }
        sb.appendDeclarationContainerExtensions(settings, localDelegatedProperties, moduleName)
    }

    fun print(kmPackage: KmPackage) {
        kmPackage.functions.forEach { visitFunction(it, settings, sb) }
        kmPackage.properties.forEach { visitProperty(it, settings, sb, it.flags) }
        kmPackage.typeAliases.forEach { visitTypeAlias(it, settings, sb) }
        visitExtensions(kmPackage)
        sb.appendLine("}")
    }
}

class FileFacadePrinter(settings: KotlinpSettings) : PackagePrinter(settings), AbstractPrinter<KotlinClassMetadata.FileFacade> {
    override fun print(klass: KotlinClassMetadata.FileFacade): String {
        print(klass.toKmPackage())
        return sb.toString()
    }
}

class LambdaPrinter(private val settings: KotlinpSettings) : AbstractPrinter<KotlinClassMetadata.SyntheticClass> {
    override fun print(klass: KotlinClassMetadata.SyntheticClass): String {
        val sb = StringBuilder().apply {
            appendLine("lambda {")
        }
        val kLambda = klass.toKmLambda() ?: throw KotlinpException("Synthetic class $klass is not a lambda")
        visitFunction(kLambda.function, settings, sb)
        sb.appendLine("}")
        return sb.toString()
    }
}

class MultiFileClassPartPrinter(
    settings: KotlinpSettings
) : PackagePrinter(settings), AbstractPrinter<KotlinClassMetadata.MultiFileClassPart> {
    override fun print(klass: KotlinClassMetadata.MultiFileClassPart): String {
        sb.appendLine("  // facade: ${klass.facadeClassName}")
        print(klass.toKmPackage())
        return sb.toString()
    }
}

class MultiFileClassFacadePrinter : AbstractPrinter<KotlinClassMetadata.MultiFileClassFacade> {
    override fun print(klass: KotlinClassMetadata.MultiFileClassFacade): String =
        buildString {
            appendLine("multi-file class {")
            for (part in klass.partClassNames) {
                appendLine("  // $part")
            }
            appendLine("}")
        }
}

class ModuleFilePrinter(private val settings: KotlinpSettings) {
    private val optionalAnnotations = mutableListOf<String>()

    private val sb = StringBuilder().apply {
        appendLine("module {")
    }

    private fun visitPackageParts(fqName: String, fileFacades: List<String>, multiFileClassParts: Map<String, String>) {
        val presentableFqName = fqName.ifEmpty { "<root>" }
        sb.appendLine("  package $presentableFqName {")
        for (fileFacade in fileFacades) {
            sb.appendLine("    $fileFacade")
        }
        for ((multiFileClassPart, facade) in multiFileClassParts) {
            sb.appendLine("    $multiFileClassPart ($facade)")
        }
        sb.appendLine("  }")
    }

    private fun visitEnd() {
        if (optionalAnnotations.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("  // Optional annotations")
            sb.appendLine()
            for (element in optionalAnnotations) {
                sb.appendLine("  " + element.replace("\n", "\n  ").trimEnd())
            }
        }
        sb.appendLine("}")
    }

    fun print(metadata: KotlinModuleMetadata): String {
        val kmModule = metadata.toKmModule()
        kmModule.packageParts.forEach { (fqName, kmPackageParts) ->
            visitPackageParts(fqName, kmPackageParts.fileFacades, kmPackageParts.multiFileClassParts)
        }
//        kmModule.annotations.forEach { visitAnnotation(it) } TODO
        optionalAnnotations.addAll(kmModule.optionalAnnotationClasses.map { ClassPrinter(settings).print(it) })
        visitEnd()
        return sb.toString()
    }
}

private val VISIBILITY_FLAGS_MAP = mapOf(
    Flag.IS_INTERNAL to "internal",
    Flag.IS_PRIVATE to "private",
    Flag.IS_PRIVATE_TO_THIS to "private",
    Flag.IS_PROTECTED to "protected",
    Flag.IS_PUBLIC to "public",
    Flag.IS_LOCAL to "local"
)

private val COMMON_FLAGS_MAP = VISIBILITY_FLAGS_MAP + mapOf(
    Flag.IS_FINAL to "final",
    Flag.IS_OPEN to "open",
    Flag.IS_ABSTRACT to "abstract",
    Flag.IS_SEALED to "sealed"
)

private val CLASS_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flag.Class.IS_INNER to "inner",
    Flag.Class.IS_DATA to "data",
    Flag.Class.IS_EXTERNAL to "external",
    Flag.Class.IS_EXPECT to "expect",
    Flag.Class.IS_VALUE to "value",
    Flag.Class.IS_FUN to "fun",

    Flag.Class.IS_CLASS to "class",
    Flag.Class.IS_INTERFACE to "interface",
    Flag.Class.IS_ENUM_CLASS to "enum class",
    Flag.Class.IS_ENUM_ENTRY to "enum entry",
    Flag.Class.IS_ANNOTATION_CLASS to "annotation class",
    Flag.Class.IS_OBJECT to "object",
    Flag.Class.IS_COMPANION_OBJECT to "companion object"
)

private val CONSTRUCTOR_FLAGS_MAP = VISIBILITY_FLAGS_MAP + mapOf(
    Flag.Constructor.IS_SECONDARY to "/* secondary */",
    Flag.Constructor.HAS_NON_STABLE_PARAMETER_NAMES to "/* non-stable parameter names */"
)

private val FUNCTION_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flag.Function.IS_DECLARATION to "",
    Flag.Function.IS_FAKE_OVERRIDE to "/* fake override */",
    Flag.Function.IS_DELEGATION to "/* delegation */",
    Flag.Function.IS_SYNTHESIZED to "/* synthesized */",

    Flag.Function.IS_OPERATOR to "operator",
    Flag.Function.IS_INFIX to "infix",
    Flag.Function.IS_INLINE to "inline",
    Flag.Function.IS_TAILREC to "tailrec",
    Flag.Function.IS_EXTERNAL to "external",
    Flag.Function.IS_SUSPEND to "suspend",
    Flag.Function.IS_EXPECT to "expect",

    Flag.Function.HAS_NON_STABLE_PARAMETER_NAMES to "/* non-stable parameter names */"
)

private val PROPERTY_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flag.Property.IS_DECLARATION to "",
    Flag.Property.IS_FAKE_OVERRIDE to "/* fake override */",
    Flag.Property.IS_DELEGATION to "/* delegation */",
    Flag.Property.IS_SYNTHESIZED to "/* synthesized */",

    Flag.Property.IS_CONST to "const",
    Flag.Property.IS_LATEINIT to "lateinit",
    Flag.Property.IS_EXTERNAL to "external",
    Flag.Property.IS_DELEGATED to "/* delegated */",
    Flag.Property.IS_EXPECT to "expect"
)

private val PROPERTY_ACCESSOR_FLAGS_MAP = COMMON_FLAGS_MAP + mapOf(
    Flag.PropertyAccessor.IS_NOT_DEFAULT to "/* non-default */",
    Flag.PropertyAccessor.IS_EXTERNAL to "external",
    Flag.PropertyAccessor.IS_INLINE to "inline"
)

private val VALUE_PARAMETER_FLAGS_MAP = mapOf(
    Flag.ValueParameter.IS_CROSSINLINE to "crossinline",
    Flag.ValueParameter.IS_NOINLINE to "noinline"
)

private val TYPE_PARAMETER_FLAGS_MAP = mapOf(
    Flag.TypeParameter.IS_REIFIED to "reified"
)

private val TYPE_FLAGS_MAP = mapOf(
    Flag.Type.IS_SUSPEND to "suspend"
)
