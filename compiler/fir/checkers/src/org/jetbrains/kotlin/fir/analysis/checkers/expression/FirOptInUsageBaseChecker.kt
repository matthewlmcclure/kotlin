/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.checkers.*
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.expressions.FirConstExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toFirRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.processDirectlyOverriddenFunctions
import org.jetbrains.kotlin.fir.scopes.processDirectlyOverriddenProperties
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.kotlin.utils.addIfNotNull

object FirOptInUsageBaseChecker {
    data class Experimentality(
        val annotationClassId: ClassId,
        val severity: Severity,
        val message: String?,
        val supertypeName: String? = null,
        val fromSupertype: Boolean = false
    ) {
        enum class Severity { WARNING, ERROR }
        companion object {
            val DEFAULT_SEVERITY = Severity.ERROR
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Experimentality) return false

            if (annotationClassId != other.annotationClassId) return false
            if (severity != other.severity) return false
            if (message != other.message) return false

            return true
        }

        override fun hashCode(): Int {
            var result = annotationClassId.hashCode()
            result = 31 * result + severity.hashCode()
            result = 31 * result + (message?.hashCode() ?: 0)
            return result
        }
    }

    // Note: receiver is an OptIn marker class and parameter is an annotated member owner class / self class name
    fun FirRegularClassSymbol.loadExperimentalityForMarkerAnnotation(annotatedOwnerClassName: String? = null): Experimentality? {
        lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
        @OptIn(SymbolInternals::class)
        return fir.loadExperimentalityForMarkerAnnotation(annotatedOwnerClassName)
    }

    fun FirBasedSymbol<*>.loadExperimentalitiesFromAnnotationTo(session: FirSession, result: MutableCollection<Experimentality>) {
        lazyResolveToPhase(FirResolvePhase.STATUS)
        @OptIn(SymbolInternals::class)
        fir.loadExperimentalitiesFromAnnotationTo(session, result, fromSupertype = false)
    }

    private fun FirDeclaration.loadExperimentalitiesFromAnnotationTo(
        session: FirSession,
        result: MutableCollection<Experimentality>,
        fromSupertype: Boolean
    ) {
        for (annotation in annotations) {
            val annotationType = annotation.annotationTypeRef.coneTypeSafe<ConeClassLikeType>() ?: continue
            val className = when (this) {
                is FirRegularClass -> name.asString()
                is FirCallableDeclaration -> symbol.callableId.className?.shortName()?.asString()
                else -> null
            }
            result.addIfNotNull(
                annotationType.lookupTag.toFirRegularClassSymbol(
                    session
                )?.loadExperimentalityForMarkerAnnotation(className)
            )
            if (fromSupertype) {
                if (annotationType.lookupTag.classId == OptInNames.SUBCLASS_OPT_IN_REQUIRED_CLASS_ID) {
                    val annotationClass = annotation.findArgumentByName(OptInNames.OPT_IN_ANNOTATION_CLASS) ?: continue
                    result.addIfNotNull(
                        annotationClass.extractClassFromArgument()?.loadExperimentalityForMarkerAnnotation()?.copy(fromSupertype = true)
                    )
                }
            }
        }
    }

    fun loadExperimentalitiesFromTypeArguments(
        context: CheckerContext,
        typeArguments: List<FirTypeProjection>
    ): Set<Experimentality> {
        if (typeArguments.isEmpty()) return emptySet()
        return loadExperimentalitiesFromConeArguments(context, typeArguments.map { it.toConeTypeProjection() })
    }

    fun loadExperimentalitiesFromConeArguments(
        context: CheckerContext,
        typeArguments: List<ConeTypeProjection>
    ): Set<Experimentality> {
        if (typeArguments.isEmpty()) return emptySet()
        val result = SmartSet.create<Experimentality>()
        typeArguments.forEach {
            if (!it.isStarProjection) it.type?.addExperimentalities(context, result)
        }
        return result
    }

    fun FirBasedSymbol<*>.loadExperimentalities(
        context: CheckerContext, fromSetter: Boolean, dispatchReceiverType: ConeKotlinType?
    ): Set<Experimentality> = loadExperimentalities(
        context, knownExperimentalities = null, visited = mutableSetOf(), fromSetter, dispatchReceiverType, fromSupertype = false
    )

    fun FirClassLikeSymbol<*>.loadExperimentalitiesFromSupertype(context: CheckerContext): Set<Experimentality> = loadExperimentalities(
        context, knownExperimentalities = null, visited = mutableSetOf(),
        fromSetter = false, dispatchReceiverType = null, fromSupertype = true
    )

    @OptIn(SymbolInternals::class)
    private fun FirBasedSymbol<*>.loadExperimentalities(
        context: CheckerContext,
        knownExperimentalities: SmartSet<Experimentality>?,
        visited: MutableSet<FirDeclaration>,
        fromSetter: Boolean,
        dispatchReceiverType: ConeKotlinType?,
        fromSupertype: Boolean,
    ): Set<Experimentality> {
        lazyResolveToPhase(FirResolvePhase.STATUS)
        val fir = this.fir
        if (!visited.add(fir)) return emptySet()
        val result = knownExperimentalities ?: SmartSet.create()
        val session = context.session
        if (fir is FirCallableDeclaration) {
            val parentClassSymbol = fir.containingClass()?.toSymbol(session) as? FirRegularClassSymbol
            if (fir.isSubstitutionOrIntersectionOverride) {
                parentClassSymbol?.lazyResolveToPhase(FirResolvePhase.STATUS)
                val parentClassScope = parentClassSymbol?.unsubstitutedScope(context)
                if (this is FirNamedFunctionSymbol) {
                    parentClassScope?.processDirectlyOverriddenFunctions(this) {
                        it.loadExperimentalities(
                            context, result, visited, fromSetter = false, dispatchReceiverType = null, fromSupertype = false
                        )
                        ProcessorAction.NEXT
                    }
                } else if (this is FirPropertySymbol) {
                    parentClassScope?.processDirectlyOverriddenProperties(this) {
                        it.loadExperimentalities(context, result, visited, fromSetter, dispatchReceiverType = null, fromSupertype = false)
                        ProcessorAction.NEXT
                    }
                }
            }
            if (fir !is FirConstructor) {
                // Without coneTypeSafe v fails in MT test (FirRenderer.kt)
                fir.returnTypeRef.coneTypeSafe<ConeKotlinType>().addExperimentalities(context, result, visited)
                fir.receiverTypeRef?.coneType.addExperimentalities(context, result, visited)
                if (fir is FirSimpleFunction) {
                    fir.valueParameters.forEach {
                        it.returnTypeRef.coneType.addExperimentalities(context, result, visited)
                    }
                }
            }
            if (dispatchReceiverType == null) {
                parentClassSymbol?.loadExperimentalities(
                    context, result, visited, fromSetter = false, dispatchReceiverType = null, fromSupertype = false
                )
            } else {
                dispatchReceiverType.addExperimentalities(context, result, visited)
            }
            if (fromSetter && this is FirPropertySymbol) {
                setterSymbol?.loadExperimentalities(
                    context, result, visited, fromSetter = false, dispatchReceiverType, fromSupertype = false
                )
            }
        } else if (this is FirRegularClassSymbol && fir is FirRegularClass && !fir.isLocal) {
            val parentClassSymbol = outerClassSymbol(context)
            parentClassSymbol?.loadExperimentalities(
                context, result, visited, fromSetter = false, dispatchReceiverType = null, fromSupertype = false
            )
        }

        fir.loadExperimentalitiesFromAnnotationTo(session, result, fromSupertype)

        if (fir is FirTypeAlias) {
            fir.expandedTypeRef.coneType.addExperimentalities(context, result, visited)
        }

        if (fir.getAnnotationByClassId(OptInNames.WAS_EXPERIMENTAL_CLASS_ID) != null) {
            val accessibility = fir.checkSinceKotlinVersionAccessibility(context)
            if (accessibility is FirSinceKotlinAccessibility.NotAccessibleButWasExperimental) {
                accessibility.markerClasses.forEach {
                    it.lazyResolveToPhase(FirResolvePhase.STATUS)
                    result.addIfNotNull(it.fir.loadExperimentalityForMarkerAnnotation())
                }
            }
        }

        // TODO: getAnnotationsOnContainingModule
        return result
    }

    private fun ConeKotlinType?.addExperimentalities(
        context: CheckerContext,
        result: SmartSet<Experimentality>,
        visited: MutableSet<FirDeclaration> = mutableSetOf()
    ) {
        if (this !is ConeClassLikeType) return
        lookupTag.toSymbol(context.session)?.loadExperimentalities(
            context, result, visited, fromSetter = false, dispatchReceiverType = null, fromSupertype = false
        )
        fullyExpandedType(context.session).typeArguments.forEach {
            if (!it.isStarProjection) it.type?.addExperimentalities(context, result, visited)
        }
    }

    // Note: receiver is an OptIn marker class and parameter is an annotated member owner class / self class name
    private fun FirRegularClass.loadExperimentalityForMarkerAnnotation(annotatedOwnerClassName: String? = null): Experimentality? {
        val experimental = getAnnotationByClassId(OptInNames.REQUIRES_OPT_IN_CLASS_ID)
            ?: return null

        val levelArgument = experimental.findArgumentByName(LEVEL) as? FirQualifiedAccessExpression
        val levelName = levelArgument?.calleeReference?.resolved?.name?.asString()
        val level = OptInLevel.values().firstOrNull { it.name == levelName } ?: OptInLevel.DEFAULT
        val message = (experimental.findArgumentByName(MESSAGE) as? FirConstExpression<*>)?.value as? String
        return Experimentality(symbol.classId, level.severity, message, annotatedOwnerClassName)
    }

    fun reportNotAcceptedExperimentalities(
        experimentalities: Collection<Experimentality>,
        element: FirElement,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        for ((annotationClassId, severity, message, _, fromSupertype) in experimentalities) {
            if (!isExperimentalityAcceptableInContext(annotationClassId, context, fromSupertype)) {
                val (diagnostic, verb) = when (severity) {
                    Experimentality.Severity.WARNING -> FirErrors.OPT_IN_USAGE to "should"
                    Experimentality.Severity.ERROR -> FirErrors.OPT_IN_USAGE_ERROR to "must"
                }
                val fqName = annotationClassId.asSingleFqName()
                val reportedMessage = message?.takeIf { it.isNotBlank() }
                    ?: OptInNames.buildDefaultDiagnosticMessage(OptInNames.buildMessagePrefix(verb), fqName.asString())
                reporter.reportOn(element.source, diagnostic, fqName, reportedMessage, context)
            }
        }
    }

    @SymbolInternals
    fun reportNotAcceptedOverrideExperimentalities(
        experimentalities: Collection<Experimentality>,
        symbol: FirCallableSymbol<*>,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        for ((annotationClassId, severity, markerMessage, supertypeName) in experimentalities) {
            if (!symbol.fir.isExperimentalityAcceptable(annotationClassId, fromSupertype = false) &&
                !isExperimentalityAcceptableInContext(annotationClassId, context, fromSupertype = false)
            ) {
                val (diagnostic, verb) = when (severity) {
                    Experimentality.Severity.WARNING -> FirErrors.OPT_IN_OVERRIDE to "should"
                    Experimentality.Severity.ERROR -> FirErrors.OPT_IN_OVERRIDE_ERROR to "must"
                }
                val message = OptInNames.buildOverrideMessage(
                    supertypeName ?: "???",
                    markerMessage,
                    verb,
                    markerName = annotationClassId.asFqNameString()
                )
                reporter.reportOn(symbol.source, diagnostic, annotationClassId.asSingleFqName(), message, context)
            }
        }
    }

    private fun isExperimentalityAcceptableInContext(
        annotationClassId: ClassId,
        context: CheckerContext,
        fromSupertype: Boolean
    ): Boolean {
        val languageVersionSettings = context.session.languageVersionSettings
        val fqNameAsString = annotationClassId.asFqNameString()
        if (fqNameAsString in languageVersionSettings.getFlag(AnalysisFlags.optIn)) {
            return true
        }
        for (annotationContainer in context.annotationContainers) {
            if (annotationContainer.isExperimentalityAcceptable(annotationClassId, fromSupertype)) {
                return true
            }
        }
        return false
    }

    private fun FirAnnotationContainer.isExperimentalityAcceptable(annotationClassId: ClassId, fromSupertype: Boolean): Boolean {
        return getAnnotationByClassId(annotationClassId) != null || isAnnotatedWithOptIn(annotationClassId) ||
                fromSupertype && isAnnotatedWithSubclassOptInRequired(annotationClassId)
    }

    private fun FirAnnotationContainer.isAnnotatedWithOptIn(annotationClassId: ClassId): Boolean {
        for (annotation in annotations) {
            val coneType = annotation.annotationTypeRef.coneType as? ConeClassLikeType
            if (coneType?.lookupTag?.classId != OptInNames.OPT_IN_CLASS_ID) {
                continue
            }
            val annotationClasses = annotation.findArgumentByName(OptInNames.OPT_IN_ANNOTATION_CLASS) ?: continue
            if (annotationClasses.extractClassesFromArgument().any { it.classId == annotationClassId }) {
                return true
            }
        }
        return false
    }

    private fun FirAnnotationContainer.isAnnotatedWithSubclassOptInRequired(annotationClassId: ClassId): Boolean {
        for (annotation in annotations) {
            val coneType = annotation.annotationTypeRef.coneType as? ConeClassLikeType
            if (coneType?.lookupTag?.classId != OptInNames.SUBCLASS_OPT_IN_REQUIRED_CLASS_ID) {
                continue
            }
            val annotationClass = annotation.findArgumentByName(OptInNames.OPT_IN_ANNOTATION_CLASS) ?: continue
            if (annotationClass.extractClassFromArgument()?.classId == annotationClassId) {
                return true
            }
        }
        return false
    }

    private val LEVEL = Name.identifier("level")
    private val MESSAGE = Name.identifier("message")

    private enum class OptInLevel(val severity: Experimentality.Severity) {
        WARNING(Experimentality.Severity.WARNING),
        ERROR(Experimentality.Severity.ERROR),
        DEFAULT(Experimentality.DEFAULT_SEVERITY)
    }
}
