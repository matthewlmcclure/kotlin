/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.calls

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.synthetic.buildSyntheticProperty
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.scopes.*
import org.jetbrains.kotlin.fir.symbols.SyntheticSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.ConeNullability.NOT_NULL
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.AbstractTypeChecker

/**
 * This symbol is bound to a synthetic property based on Java getter/setter call
 *
 * See details about such properties here: https://kotlinlang.org/docs/java-interop.html#getters-and-setters).
 * Frontend IR creates this kind of symbol each time when x.foo should be resolved to x.getFoo() or x.setFoo().
 */
class FirSimpleSyntheticPropertySymbol(
    propertyId: CallableId,
    getterId: CallableId
) : FirSyntheticPropertySymbol(propertyId, getterId), SyntheticSymbol {
    override fun copy(): FirSyntheticPropertySymbol = FirSimpleSyntheticPropertySymbol(callableId, getterId)
}

class FirSyntheticFunctionSymbol(
    callableId: CallableId
) : FirNamedFunctionSymbol(callableId), SyntheticSymbol

class FirSyntheticPropertiesScope private constructor(
    val session: FirSession,
    private val baseScope: FirTypeScope,
    private val dispatchReceiverType: ConeKotlinType,
    private val syntheticNamesProvider: FirSyntheticNamesProvider
) : FirContainingNamesAwareScope() {
    companion object {
        fun createIfSyntheticNamesProviderIsDefined(
            session: FirSession,
            dispatchReceiverType: ConeKotlinType,
            baseScope: FirTypeScope
        ): FirSyntheticPropertiesScope? {
            val syntheticNamesProvider = session.syntheticNamesProvider ?: return null
            return FirSyntheticPropertiesScope(
                session,
                baseScope,
                dispatchReceiverType,
                syntheticNamesProvider
            )
        }
    }

    override fun processPropertiesByName(name: Name, processor: (FirVariableSymbol<*>) -> Unit) {
        val getterNames = syntheticNamesProvider.possibleGetterNamesByPropertyName(name)
        var getterFound = false
        for (getterName in getterNames) {
            baseScope.processFunctionsByName(getterName) {
                checkGetAndCreateSynthetic(name, getterName, it, needCheckForSetter = true, processor)
                getterFound = true
            }
        }
        if (!getterFound && shouldSearchForJavaRecordComponents()) {
            baseScope.processFunctionsByName(name) {
                if (it.fir.isJavaRecordComponent == true) {
                    checkGetAndCreateSynthetic(name, name, it, needCheckForSetter = false, processor)
                }
            }
        }
    }

    private fun shouldSearchForJavaRecordComponents(): Boolean {
        /*
         * Fast path: if dispatch receiver type is simple type and corresponding
         *   class is not a java record then there is no need to additional
         *   search for record components
         */
        val dispatchSymbol = dispatchReceiverType.toRegularClassSymbol(session) ?: return true
        return dispatchSymbol.fir.isJavaRecord ?: false
    }

    override fun getCallableNames(): Set<Name> = baseScope.getCallableNames().flatMapTo(hashSetOf()) { propertyName ->
        syntheticNamesProvider.possiblePropertyNamesByAccessorName(propertyName)
    }

    override fun getClassifierNames(): Set<Name> = emptySet()

    private fun checkGetAndCreateSynthetic(
        propertyName: Name,
        getterName: Name,
        getterSymbol: FirFunctionSymbol<*>,
        needCheckForSetter: Boolean,
        processor: (FirVariableSymbol<*>) -> Unit
    ) {
        if (getterSymbol !is FirNamedFunctionSymbol) return
        val getter = getterSymbol.fir

        if (getter.typeParameters.isNotEmpty()) return
        if (getter.valueParameters.isNotEmpty()) return
        if (getter.isStatic) return
        val getterReturnType = (getter.returnTypeRef as? FirResolvedTypeRef)?.type
        if ((getterReturnType as? ConeClassLikeType)?.lookupTag?.classId == StandardClassIds.Unit) return

        if (!getterSymbol.hasJavaOverridden()) return

        var matchingSetter: FirSimpleFunction? = null
        if (needCheckForSetter && getterReturnType != null) {
            val setterName = syntheticNamesProvider.setterNameByGetterName(getterName)
            baseScope.processFunctionsByName(setterName, fun(setterSymbol: FirNamedFunctionSymbol) {
                if (matchingSetter != null) return
                val setter = setterSymbol.fir
                val parameter = setter.valueParameters.singleOrNull() ?: return
                if (setter.typeParameters.isNotEmpty() || setter.isStatic) return
                val parameterType = (parameter.returnTypeRef as? FirResolvedTypeRef)?.type ?: return
                // TODO: at this moment it works for cases like
                // class Base {
                //     void setSomething(Object value) {}
                // }
                // class Derived extends Base {
                //     String getSomething() { return ""; }
                // }
                // In FE 1.0, we should have also Object getSomething() in class Base for this to work
                // I think details here are worth designing
                if (!AbstractTypeChecker.isSubtypeOf(
                        session.typeContext,
                        getterReturnType.withNullability(NOT_NULL, session.typeContext),
                        parameterType.withNullability(NOT_NULL, session.typeContext)
                    )
                ) {
                    return
                }
                matchingSetter = setter
            })
        }

        val classLookupTag = getterSymbol.originalOrSelf().dispatchReceiverClassOrNull()
        val packageName = classLookupTag?.classId?.packageFqName ?: getterSymbol.callableId.packageName
        val className = classLookupTag?.classId?.relativeClassName

        val property = buildSyntheticProperty {
            moduleData = session.moduleData
            name = propertyName
            symbol = FirSimpleSyntheticPropertySymbol(
                getterId = getterSymbol.callableId,
                propertyId = CallableId(packageName, className, propertyName)
            )
            delegateGetter = getter
            delegateSetter = matchingSetter
            deprecationsProvider = getDeprecationsProviderFromAccessors(getter, matchingSetter, session.firCachesFactory)
        }
        val syntheticSymbol = property.symbol
        (baseScope as? FirUnstableSmartcastTypeScope)?.apply {
            if (isSymbolFromUnstableSmartcast(getterSymbol)) {
                markSymbolFromUnstableSmartcast(syntheticSymbol)
            }
        }
        processor(syntheticSymbol)
    }

    private fun FirNamedFunctionSymbol.hasJavaOverridden(): Boolean {
        var result = false
        baseScope.processOverriddenFunctionsAndSelf(this) {
            if (it.unwrapFakeOverrides().fir.origin == FirDeclarationOrigin.Enhancement) {
                result = true
                ProcessorAction.STOP
            } else {
                ProcessorAction.NEXT
            }
        }

        return result
    }
}
