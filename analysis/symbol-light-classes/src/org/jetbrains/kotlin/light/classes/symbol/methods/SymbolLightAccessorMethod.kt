/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol.methods

import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.*
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertyAccessorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertyGetterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySetterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.asJava.builder.LightMemberOrigin
import org.jetbrains.kotlin.asJava.classes.METHOD_INDEX_FOR_GETTER
import org.jetbrains.kotlin.asJava.classes.METHOD_INDEX_FOR_SETTER
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.light.classes.symbol.*
import org.jetbrains.kotlin.light.classes.symbol.annotations.computeAnnotations
import org.jetbrains.kotlin.light.classes.symbol.annotations.getJvmNameFromAnnotation
import org.jetbrains.kotlin.light.classes.symbol.annotations.hasDeprecatedAnnotation
import org.jetbrains.kotlin.light.classes.symbol.annotations.hasJvmStaticAnnotation
import org.jetbrains.kotlin.light.classes.symbol.classes.SymbolLightClassBase
import org.jetbrains.kotlin.light.classes.symbol.modifierLists.SymbolLightMemberModifierList
import org.jetbrains.kotlin.light.classes.symbol.parameters.SymbolLightParameterList
import org.jetbrains.kotlin.light.classes.symbol.parameters.SymbolLightSetterParameter
import org.jetbrains.kotlin.light.classes.symbol.parameters.SymbolLightTypeParameterList
import org.jetbrains.kotlin.load.java.JvmAbi.getterName
import org.jetbrains.kotlin.load.java.JvmAbi.setterName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue

internal class SymbolLightAccessorMethod(
    propertyAccessorSymbol: KtPropertyAccessorSymbol,
    containingPropertySymbol: KtPropertySymbol,
    lightMemberOrigin: LightMemberOrigin?,
    containingClass: SymbolLightClassBase,
    private val isTopLevel: Boolean,
    private val suppressStatic: Boolean = false,
    private val ktModule: KtModule,
) : SymbolLightMethodBase(
    lightMemberOrigin,
    containingClass,
    if (propertyAccessorSymbol is KtPropertyGetterSymbol) METHOD_INDEX_FOR_GETTER else METHOD_INDEX_FOR_SETTER
) {
    private val isGetter: Boolean = propertyAccessorSymbol is KtPropertyGetterSymbol

    private val propertyAccessorDeclaration: KtPropertyAccessor? = propertyAccessorSymbol.psi as? KtPropertyAccessor
    private val propertyAccessorSymbolPointer: KtSymbolPointer<KtPropertyAccessorSymbol> = propertyAccessorSymbol.createPointer()
    private fun KtAnalysisSession.propertyAccessorSymbol(): KtPropertyAccessorSymbol {
        return getOrRestoreSymbol(propertyAccessorDeclaration, propertyAccessorSymbolPointer)
    }

    private val containingPropertyDeclaration: KtCallableDeclaration? = containingPropertySymbol.psi as? KtCallableDeclaration
    private val containingPropertySymbolPointer: KtSymbolPointer<KtPropertySymbol> = containingPropertySymbol.createPointer()
    private fun KtAnalysisSession.propertySymbol(): KtPropertySymbol {
        return getOrRestoreSymbol(containingPropertyDeclaration, containingPropertySymbolPointer)
    }

    private fun String.abiName() = if (isGetter) getterName(this) else setterName(this)

    private val _name: String by lazyPub {
        analyze(ktModule) {
            propertyAccessorSymbol().getJvmNameFromAnnotation(accessorSite) ?: run {
                val symbol = propertySymbol()
                val defaultName = symbol.name.identifier.let {
                    if (containingClass.isAnnotationType) it else it.abiName()
                }

                symbol.computeJvmMethodName(defaultName, containingClass, accessorSite)
            }
        }
    }

    override fun getName(): String = _name

    private val _typeParameterList: PsiTypeParameterList? by lazyPub {
        hasTypeParameters().ifTrue {
            analyze(ktModule) {
                SymbolLightTypeParameterList(
                    owner = this@SymbolLightAccessorMethod,
                    symbolWithTypeParameterList = propertySymbol(),
                )
            }
        }
    }

    override fun hasTypeParameters(): Boolean = containingPropertyDeclaration?.typeParameters?.isNotEmpty() ?: analyze(ktModule) {
        propertySymbol().typeParameters.isNotEmpty()
    }

    override fun getTypeParameterList(): PsiTypeParameterList? = _typeParameterList
    override fun getTypeParameters(): Array<PsiTypeParameter> = _typeParameterList?.typeParameters ?: PsiTypeParameter.EMPTY_ARRAY

    override fun isVarArgs(): Boolean = false

    override val kotlinOrigin: KtDeclaration? get() = propertyAccessorDeclaration

    private val accessorSite
        get() = if (isGetter) AnnotationUseSiteTarget.PROPERTY_GETTER else AnnotationUseSiteTarget.PROPERTY_SETTER

    //TODO Fix it when SymbolConstructorValueParameter be ready
    private val isParameter: Boolean get() = containingPropertyDeclaration == null || containingPropertyDeclaration is KtParameter

    private fun computeAnnotations(isPrivate: Boolean): List<PsiAnnotation> = analyze(ktModule) {
        val nullabilityApplicable = isGetter && !isPrivate && !(isParameter && containingClass.isAnnotationType)

        val propertySymbol = propertySymbol()
        val nullabilityType = if (nullabilityApplicable) getTypeNullability(propertySymbol.returnType) else NullabilityType.Unknown
        val annotationsFromProperty = propertySymbol.computeAnnotations(
            parent = this@SymbolLightAccessorMethod,
            nullability = nullabilityType,
            annotationUseSiteTarget = accessorSite,
            includeAnnotationsWithoutSite = false,
        )

        val propertyAccessorSymbol = propertyAccessorSymbol()
        val annotationsFromAccessor = propertyAccessorSymbol.computeAnnotations(
            parent = this@SymbolLightAccessorMethod,
            nullability = NullabilityType.Unknown,
            annotationUseSiteTarget = accessorSite,
        )

        annotationsFromProperty + annotationsFromAccessor
    }

    private fun computeModifiers(): Set<String> = analyze(ktModule) {
        val propertySymbol = propertySymbol()
        val propertyAccessorSymbol = propertyAccessorSymbol()
        val isOverrideMethod = propertyAccessorSymbol.isOverride || propertySymbol.isOverride
        val isInterfaceMethod = containingClass.isInterface

        val modifiers = mutableSetOf<String>()

        propertySymbol.computeModalityForMethod(
            isTopLevel = isTopLevel,
            suppressFinal = isOverrideMethod || isInterfaceMethod,
            result = modifiers,
        )

        val visibility = isOverrideMethod.ifTrue {
            tryGetEffectiveVisibility(propertySymbol)?.toPsiVisibilityForMember()
        } ?: propertyAccessorSymbol.toPsiVisibilityForMember()
        modifiers.add(visibility)

        if (!suppressStatic &&
            (propertySymbol.hasJvmStaticAnnotation() || propertyAccessorSymbol.hasJvmStaticAnnotation(accessorSite))
        ) {
            modifiers.add(PsiModifier.STATIC)
        }

        if (isInterfaceMethod) {
            modifiers.add(PsiModifier.ABSTRACT)
        }

        modifiers
    }

    private val _modifierList: PsiModifierList by lazyPub {
        val modifiers = computeModifiers()
        val annotations = computeAnnotations(modifiers.contains(PsiModifier.PRIVATE))
        SymbolLightMemberModifierList(this, modifiers, annotations)
    }

    override fun getModifierList(): PsiModifierList = _modifierList

    override fun isConstructor(): Boolean = false

    private val _isDeprecated: Boolean by lazyPub {
        analyze(ktModule) {
            propertySymbol().hasDeprecatedAnnotation(accessorSite)
        }
    }

    override fun isDeprecated(): Boolean = _isDeprecated

    private val _identifier: PsiIdentifier by lazyPub {
        analyze(ktModule) {
            SymbolLightIdentifier(this@SymbolLightAccessorMethod, propertySymbol())
        }
    }

    override fun getNameIdentifier(): PsiIdentifier = _identifier

    private val _returnedType: PsiType by lazyPub {
        if (!isGetter) return@lazyPub PsiType.VOID
        analyze(ktModule) {
            propertySymbol().returnType.asPsiType(
                this@SymbolLightAccessorMethod,
                KtTypeMappingMode.RETURN_TYPE,
                containingClass.isAnnotationType,
            )
        } ?: nonExistentType()
    }

    override fun getReturnType(): PsiType = _returnedType

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SymbolLightAccessorMethod) return false
        if (propertyAccessorDeclaration != null) {
            return propertyAccessorDeclaration == other.propertyAccessorDeclaration
        }

        return isGetter == other.isGetter && other.propertyAccessorDeclaration == null && ktModule == other.ktModule && analyze(ktModule) {
            propertyAccessorSymbolPointer.restoreSymbol() == other.propertyAccessorSymbolPointer.restoreSymbol()
        }
    }

    override fun hashCode(): Int = propertyAccessorDeclaration.hashCode()

    private val _parametersList by lazyPub {
        analyze(ktModule) {
            val propertySymbol = propertySymbol()
            val accessorSymbol = propertyAccessorSymbol()
            SymbolLightParameterList(this@SymbolLightAccessorMethod, propertySymbol) { builder ->
                val propertyParameter = (accessorSymbol as? KtPropertySetterSymbol)?.parameter
                if (propertyParameter != null) {
                    builder.addParameter(
                        SymbolLightSetterParameter(
                            propertySymbol, propertyParameter, this@SymbolLightAccessorMethod
                        )
                    )
                }
            }
        }
    }

    override fun getParameterList(): PsiParameterList = _parametersList

    override fun isValid(): Boolean = super.isValid() &&
            propertyAccessorDeclaration?.isValid ?: analyze(ktModule) { propertyAccessorSymbolPointer.restoreSymbol() != null }

    override fun isOverride(): Boolean = analyze(ktModule) { propertyAccessorSymbol().isOverride }

    private val _defaultValue: PsiAnnotationMemberValue? by lazyPub {
        if (!containingClass.isAnnotationType) return@lazyPub null
        analyze(ktModule) {
            when (val initializer = propertySymbol().initializer) {
                is KtConstantInitializerValue -> initializer.constant.createPsiLiteral(this@SymbolLightAccessorMethod)
                is KtConstantValueForAnnotation -> initializer.annotationValue.toAnnotationMemberValue(this@SymbolLightAccessorMethod)
                is KtNonConstantInitializerValue -> null
                null -> null
            }
        }
    }

    override fun getDefaultValue(): PsiAnnotationMemberValue? = _defaultValue
}
