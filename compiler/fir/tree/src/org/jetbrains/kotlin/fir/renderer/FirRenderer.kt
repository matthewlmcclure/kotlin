/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.renderer

import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.contracts.*
import org.jetbrains.kotlin.fir.contracts.description.ConeContractRenderer
import org.jetbrains.kotlin.fir.contracts.impl.FirEmptyContractDescription
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.*
import org.jetbrains.kotlin.fir.references.*
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

class FirRenderer(
    builder: StringBuilder = StringBuilder(),
    override val annotationRenderer: FirAnnotationRenderer? = FirAnnotationRenderer(),
    override val bodyRenderer: FirBodyRenderer? = FirBodyRenderer(),
    override val callArgumentsRenderer: FirCallArgumentsRenderer = FirCallArgumentsRenderer(),
    override val classMemberRenderer: FirClassMemberRenderer = FirClassMemberRenderer(),
    override val contractRenderer: ConeContractRenderer? = ConeContractRenderer(),
    override val declarationRenderer: FirDeclarationRenderer = FirDeclarationRenderer(),
    override val idRenderer: ConeIdRenderer = ConeIdRendererForDebugging(),
    override val modifierRenderer: FirModifierRenderer = FirAllModifierRenderer(),
    override val packageDirectiveRenderer: FirPackageDirectiveRenderer? = null,
    override val propertyAccessorRenderer: FirPropertyAccessorRenderer? = FirPropertyAccessorRenderer(),
    override val resolvePhaseRenderer: FirResolvePhaseRenderer? = null,
    override val typeRenderer: ConeTypeRenderer = ConeTypeRendererForDebugging(),
    override val valueParameterRenderer: FirValueParameterRenderer = FirValueParameterRenderer(),
    override val errorExpressionRenderer: FirErrorExpressionRenderer = FirErrorExpressionOnlyErrorRenderer(),
) : FirRendererComponents {

    override val visitor = Visitor()
    override val printer = FirPrinter(builder)

    companion object {
        fun noAnnotationBodiesAccessorAndArguments(): FirRenderer =
            FirRenderer(
                annotationRenderer = null, bodyRenderer = null, propertyAccessorRenderer = null,
                callArgumentsRenderer = FirCallNoArgumentsRenderer()
            )

        fun withResolvePhase(): FirRenderer =
            FirRenderer(resolvePhaseRenderer = FirResolvePhaseRenderer())

        fun withDeclarationAttributes(): FirRenderer =
            FirRenderer(declarationRenderer = FirDeclarationRendererWithAttributes())
    }

    init {
        annotationRenderer?.components = this
        bodyRenderer?.components = this
        callArgumentsRenderer.components = this
        classMemberRenderer.components = this
        contractRenderer?.components = this
        declarationRenderer.components = this
        idRenderer.builder = builder
        modifierRenderer.components = this
        packageDirectiveRenderer?.components = this
        propertyAccessorRenderer?.components = this
        resolvePhaseRenderer?.components = this
        typeRenderer.builder = builder
        typeRenderer.idRenderer = idRenderer
        valueParameterRenderer.components = this
        errorExpressionRenderer.components = this
    }

    fun renderElementAsString(element: FirElement): String {
        element.accept(visitor)
        return printer.toString()
    }

    fun renderElementWithTypeAsString(element: FirElement): String {
        print(element)
        print(": ")
        return renderElementAsString(element)
    }

    fun renderAsCallableDeclarationString(callableDeclaration: FirCallableDeclaration): String {
        visitor.visitCallableDeclaration(callableDeclaration)
        return printer.toString()
    }

    fun renderMemberDeclarationClass(firClass: FirClass) {
        visitor.visitMemberDeclaration(firClass)
    }

    fun renderAnnotations(annotationContainer: FirAnnotationContainer) {
        annotationRenderer?.render(annotationContainer)
    }

    fun renderSupertypes(regularClass: FirRegularClass) {
        if (regularClass.superTypeRefs.isNotEmpty()) {
            print(" : ")
            renderSeparated(regularClass.superTypeRefs, visitor)
        }
    }

    private fun Variance.renderVariance() {
        label.let {
            print(it)
            if (it.isNotEmpty()) {
                print(" ")
            }
        }
    }

    private fun renderContexts(contextReceivers: List<FirContextReceiver>) {
        if (contextReceivers.isEmpty()) return
        print("context(")
        renderSeparated(contextReceivers, visitor)
        print(")")
        printer.newLine()
    }

    private fun List<FirTypeParameterRef>.renderTypeParameters() {
        if (isNotEmpty()) {
            print("<")
            renderSeparated(this, visitor)
            print(">")
        }
    }

    private fun List<FirTypeProjection>.renderTypeArguments() {
        if (isNotEmpty()) {
            print("<")
            renderSeparated(this, visitor)
            print(">")
        }
    }

    private fun print(s: Any) {
        printer.print(s)
    }

    private fun renderSeparated(elements: List<FirElement>, visitor: Visitor) {
        printer.renderSeparated(elements, visitor)
    }

    inner class Visitor internal constructor() : FirVisitorVoid() {

        override fun visitElement(element: FirElement) {
            element.acceptChildren(this)
        }

        override fun visitFile(file: FirFile) {
            printer.println("FILE: ${file.name}")
            printer.pushIndent()
            annotationRenderer?.render(file)
            visitPackageDirective(file.packageDirective)
            file.imports.forEach { it.accept(this) }
            file.declarations.forEach { it.accept(this) }
            printer.popIndent()
        }

        override fun visitAnnotation(annotation: FirAnnotation) {
            annotationRenderer?.renderAnnotation(annotation)
        }

        override fun visitAnnotationCall(annotationCall: FirAnnotationCall) {
            annotationRenderer?.renderAnnotation(annotationCall)
        }

        override fun visitCallableDeclaration(callableDeclaration: FirCallableDeclaration) {
            renderContexts(callableDeclaration.contextReceivers)
            annotationRenderer?.render(callableDeclaration)
            visitMemberDeclaration(callableDeclaration)
            val receiverType = callableDeclaration.receiverTypeRef
            print(" ")
            if (receiverType != null) {
                receiverType.accept(this)
                print(".")
            }
            when (callableDeclaration) {
                is FirSimpleFunction -> {
                    idRenderer.renderCallableId(callableDeclaration.symbol.callableId)
                }
                is FirVariable -> {
                    idRenderer.renderCallableId(callableDeclaration.symbol.callableId)
                }
                else -> {}
            }

            if (callableDeclaration is FirFunction) {
                valueParameterRenderer.renderParameters(callableDeclaration.valueParameters)
            }
            print(": ")
            callableDeclaration.returnTypeRef.accept(this)
            contractRenderer?.render(callableDeclaration)
        }

        override fun visitContextReceiver(contextReceiver: FirContextReceiver) {
            contextReceiver.customLabelName?.let {
                print(it.asString() + "@")
            }

            contextReceiver.typeRef.accept(this)
        }

        override fun visitTypeParameterRef(typeParameterRef: FirTypeParameterRef) {
            typeParameterRef.symbol.fir.accept(this)
        }

        override fun visitMemberDeclaration(memberDeclaration: FirMemberDeclaration) {
            modifierRenderer.renderModifiers(memberDeclaration)
            declarationRenderer.render(memberDeclaration)
            when (memberDeclaration) {
                is FirClassLikeDeclaration -> {
                    if (memberDeclaration is FirRegularClass) {
                        print(" " + memberDeclaration.name)
                    }
                    if (memberDeclaration is FirTypeAlias) {
                        print(" " + memberDeclaration.name)
                    }
                    memberDeclaration.typeParameters.renderTypeParameters()
                }
                is FirCallableDeclaration -> {
                    // Name is handled by visitCallableDeclaration
                    if (memberDeclaration.typeParameters.isNotEmpty()) {
                        print(" ")
                        memberDeclaration.typeParameters.renderTypeParameters()
                    }
                }
            }
        }

        override fun visitRegularClass(regularClass: FirRegularClass) {
            renderContexts(regularClass.contextReceivers)
            annotationRenderer?.render(regularClass)
            visitMemberDeclaration(regularClass)
            renderSupertypes(regularClass)
            classMemberRenderer.render(regularClass)
        }

        override fun visitEnumEntry(enumEntry: FirEnumEntry) {
            visitCallableDeclaration(enumEntry)
            enumEntry.initializer?.let {
                print(" = ")
                it.accept(this)
            }
        }

        override fun visitAnonymousObjectExpression(anonymousObjectExpression: FirAnonymousObjectExpression) {
            anonymousObjectExpression.anonymousObject.accept(this)
        }

        override fun visitAnonymousObject(anonymousObject: FirAnonymousObject) {
            annotationRenderer?.render(anonymousObject)
            print("object : ")
            renderSeparated(anonymousObject.superTypeRefs, visitor)
            classMemberRenderer.render(anonymousObject.declarations)
        }

        override fun visitVariable(variable: FirVariable) {
            visitCallableDeclaration(variable)
            variable.initializer?.let {
                print(" = ")
                it.accept(this)
            }
            variable.delegate?.let {
                print("by ")
                it.accept(this)
            }
        }

        override fun visitField(field: FirField) {
            visitVariable(field)
            printer.newLine()
        }

        override fun visitProperty(property: FirProperty) {
            visitVariable(property)
            if (property.isLocal) return
            propertyAccessorRenderer?.render(property)
        }

        override fun visitBackingField(backingField: FirBackingField) {
            modifierRenderer.renderModifiers(backingField)
            print("<explicit backing field>: ")
            backingField.returnTypeRef.accept(this)

            backingField.initializer?.let {
                print(" = ")
                it.accept(this)
            }
        }

        override fun visitSimpleFunction(simpleFunction: FirSimpleFunction) {
            visitCallableDeclaration(simpleFunction)
            bodyRenderer?.render(simpleFunction)
            if (simpleFunction.body == null) {
                printer.newLine()
            }
        }

        override fun visitConstructor(constructor: FirConstructor) {
            annotationRenderer?.render(constructor)
            modifierRenderer.renderModifiers(constructor)
            declarationRenderer.render(constructor)

            constructor.typeParameters.renderTypeParameters()
            valueParameterRenderer.renderParameters(constructor.valueParameters)
            print(": ")
            constructor.returnTypeRef.accept(this)
            val body = constructor.body
            val delegatedConstructor = constructor.delegatedConstructor
            if (body == null) {
                bodyRenderer?.renderDelegatedConstructor(delegatedConstructor)
            }
            bodyRenderer?.renderBody(body, listOfNotNull<FirStatement>(delegatedConstructor))
        }

        override fun visitPropertyAccessor(propertyAccessor: FirPropertyAccessor) {
            annotationRenderer?.render(propertyAccessor)
            modifierRenderer.renderModifiers(propertyAccessor)
            declarationRenderer.render(propertyAccessor)
            valueParameterRenderer.renderParameters(propertyAccessor.valueParameters)
            print(": ")
            propertyAccessor.returnTypeRef.accept(this)
            contractRenderer?.render(propertyAccessor)
            bodyRenderer?.render(propertyAccessor)
        }

        override fun visitAnonymousFunctionExpression(anonymousFunctionExpression: FirAnonymousFunctionExpression) {
            visitAnonymousFunction(anonymousFunctionExpression.anonymousFunction)
        }

        override fun visitAnonymousFunction(anonymousFunction: FirAnonymousFunction) {
            annotationRenderer?.render(anonymousFunction)
            declarationRenderer.render(anonymousFunction)
            print(" ")
            val receiverType = anonymousFunction.receiverTypeRef
            if (receiverType != null) {
                receiverType.accept(this)
                print(".")
            }
            print("<anonymous>")
            if (anonymousFunction.valueParameters.isEmpty() &&
                anonymousFunction.hasExplicitParameterList &&
                anonymousFunction.returnTypeRef is FirImplicitTypeRef
            ) {
                print("(<no-parameters>)")
            }
            valueParameterRenderer.renderParameters(anonymousFunction.valueParameters)
            print(": ")
            anonymousFunction.returnTypeRef.accept(this)
            print(" <inline=${anonymousFunction.inlineStatus}")
            if (anonymousFunction.invocationKind != null) {
                print(", kind=${anonymousFunction.invocationKind}")
            }
            print("> ")
            bodyRenderer?.render(anonymousFunction)
        }

        override fun visitFunction(function: FirFunction) {
            valueParameterRenderer.renderParameters(function.valueParameters)
            declarationRenderer.render(function)
            bodyRenderer?.render(function)
        }

        override fun visitAnonymousInitializer(anonymousInitializer: FirAnonymousInitializer) {
            print("init")
            bodyRenderer?.renderBody(anonymousInitializer.body)
        }

        override fun visitBlock(block: FirBlock) {
            bodyRenderer?.renderBody(block)
        }

        override fun visitTypeAlias(typeAlias: FirTypeAlias) {
            annotationRenderer?.render(typeAlias)
            visitMemberDeclaration(typeAlias)
            print(" = ")
            typeAlias.expandedTypeRef.accept(this)
            printer.newLine()
        }

        override fun visitTypeParameter(typeParameter: FirTypeParameter) {
            annotationRenderer?.render(typeParameter)
            modifierRenderer.renderModifiers(typeParameter)
            resolvePhaseRenderer?.render(typeParameter)
            typeParameter.variance.renderVariance()
            print(typeParameter.name)

            val meaningfulBounds = typeParameter.bounds.filter {
                if (it !is FirResolvedTypeRef) return@filter true
                if (!it.type.isNullable) return@filter true
                val type = it.type as? ConeLookupTagBasedType ?: return@filter true
                type.lookupTag.safeAs<ConeClassLikeLookupTag>()?.classId != StandardClassIds.Any
            }

            if (meaningfulBounds.isNotEmpty()) {
                print(" : ")
                renderSeparated(meaningfulBounds, visitor)
            }
        }

        override fun visitSafeCallExpression(safeCallExpression: FirSafeCallExpression) {
            safeCallExpression.receiver.accept(this)
            print("?.{ ")
            safeCallExpression.selector.accept(this)
            print(" }")
        }

        override fun visitCheckedSafeCallSubject(checkedSafeCallSubject: FirCheckedSafeCallSubject) {
            print("\$subj\$")
        }

        override fun visitValueParameter(valueParameter: FirValueParameter) {
            valueParameterRenderer.renderParameter(valueParameter)
        }

        override fun visitImport(import: FirImport) {
            visitElement(import)
        }

        override fun visitStatement(statement: FirStatement) {
            if (statement is FirStubStatement) {
                print("[StubStatement]")
            } else {
                visitElement(statement)
            }
        }

        override fun visitReturnExpression(returnExpression: FirReturnExpression) {
            annotationRenderer?.render(returnExpression)
            print("^")
            val target = returnExpression.target
            val labeledElement = target.labeledElement
            if (labeledElement is FirSimpleFunction) {
                print("${labeledElement.name}")
            } else {
                val labelName = target.labelName
                if (labelName != null) {
                    print("@$labelName")
                }
            }
            print(" ")
            returnExpression.result.accept(this)
        }

        override fun visitWhenBranch(whenBranch: FirWhenBranch) {
            val condition = whenBranch.condition
            if (condition is FirElseIfTrueCondition) {
                print("else")
            } else {
                condition.accept(this)
            }
            print(" -> ")
            whenBranch.result.accept(this)
        }

        override fun visitWhenExpression(whenExpression: FirWhenExpression) {
            annotationRenderer?.render(whenExpression)
            print("when (")
            val subjectVariable = whenExpression.subjectVariable
            if (subjectVariable != null) {
                subjectVariable.accept(this)
            } else {
                whenExpression.subject?.accept(this)
            }
            printer.println(") {")
            printer.pushIndent()
            for (branch in whenExpression.branches) {
                branch.accept(this)
            }
            printer.popIndent()
            printer.println("}")
        }

        override fun visitWhenSubjectExpression(whenSubjectExpression: FirWhenSubjectExpression) {
            print("\$subj\$")
        }

        override fun visitTryExpression(tryExpression: FirTryExpression) {
            annotationRenderer?.render(tryExpression)
            print("try")
            tryExpression.tryBlock.accept(this)
            for (catchClause in tryExpression.catches) {
                print("catch (")
                catchClause.parameter.accept(this)
                print(")")
                catchClause.block.accept(this)
            }
            val finallyBlock = tryExpression.finallyBlock ?: return
            print("finally")
            finallyBlock.accept(this)
        }

        override fun visitDoWhileLoop(doWhileLoop: FirDoWhileLoop) {
            val label = doWhileLoop.label
            if (label != null) {
                print("${label.name}@")
            }
            print("do")
            doWhileLoop.block.accept(this)
            print("while(")
            doWhileLoop.condition.accept(this)
            print(")")
        }

        override fun visitWhileLoop(whileLoop: FirWhileLoop) {
            val label = whileLoop.label
            if (label != null) {
                print("${label.name}@")
            }
            print("while(")
            whileLoop.condition.accept(this)
            print(")")
            whileLoop.block.accept(this)
        }

        private val loopJumpStack = Stack<FirLoopJump>()

        override fun visitLoopJump(loopJump: FirLoopJump) {
            if (loopJumpStack.contains(loopJump)) {
                // For example,
                //   do {
                //     ...
                //   } while(
                //       when (...) {
                //         ... -> break
                //       }
                //   )
                // That `break` condition is `when` expression, and while visiting its branch result, we will see the same `break` again.
                return
            }
            loopJumpStack.push(loopJump)
            val target = loopJump.target
            val labeledElement = target.labeledElement
            print("@@@[")
            labeledElement.condition.accept(this)
            print("] ")
            loopJumpStack.pop()
        }

        override fun visitBreakExpression(breakExpression: FirBreakExpression) {
            annotationRenderer?.render(breakExpression)
            print("break")
            visitLoopJump(breakExpression)
        }

        override fun visitContinueExpression(continueExpression: FirContinueExpression) {
            annotationRenderer?.render(continueExpression)
            print("continue")
            visitLoopJump(continueExpression)
        }

        override fun visitExpression(expression: FirExpression) {
            if (expression !is FirLazyExpression) {
                annotationRenderer?.render(expression)
            }
            print(
                when (expression) {
                    is FirExpressionStub -> "STUB"
                    is FirLazyExpression -> "LAZY_EXPRESSION"
                    is FirUnitExpression -> "Unit"
                    is FirElseIfTrueCondition -> "else"
                    is FirNoReceiverExpression -> ""
                    else -> "??? ${expression.javaClass}"
                }
            )
        }

        override fun <T> visitConstExpression(constExpression: FirConstExpression<T>) {
            annotationRenderer?.render(constExpression)
            val kind = constExpression.kind
            val value = constExpression.value
            print("$kind(")
            if (value !is Char) {
                print(value.toString())
            } else {
                if (value.code in 32..127) {
                    print(value)
                } else {
                    print(value.code)
                }
            }
            print(")")
        }

        override fun visitWrappedDelegateExpression(wrappedDelegateExpression: FirWrappedDelegateExpression) {
            wrappedDelegateExpression.expression.accept(this)
        }

        override fun visitNamedArgumentExpression(namedArgumentExpression: FirNamedArgumentExpression) {
            print(namedArgumentExpression.name)
            print(" = ")
            if (namedArgumentExpression.isSpread) {
                print("*")
            }
            namedArgumentExpression.expression.accept(this)
        }

        override fun visitSpreadArgumentExpression(spreadArgumentExpression: FirSpreadArgumentExpression) {
            if (spreadArgumentExpression.isSpread) {
                print("*")
            }
            spreadArgumentExpression.expression.accept(this)
        }

        override fun visitLambdaArgumentExpression(lambdaArgumentExpression: FirLambdaArgumentExpression) {
            print("<L> = ")
            lambdaArgumentExpression.expression.accept(this)
        }

        override fun visitVarargArgumentsExpression(varargArgumentsExpression: FirVarargArgumentsExpression) {
            print("vararg(")
            renderSeparated(varargArgumentsExpression.arguments, visitor)
            print(")")
        }

        override fun visitCall(call: FirCall) {
            callArgumentsRenderer.renderArguments(call.arguments)
        }

        override fun visitStringConcatenationCall(stringConcatenationCall: FirStringConcatenationCall) {
            print("<strcat>")
            visitCall(stringConcatenationCall)
        }

        override fun visitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall) {
            print("(")
            typeOperatorCall.argument.accept(this)
            print(" ")
            print(typeOperatorCall.operation.operator)
            print(" ")
            typeOperatorCall.conversionTypeRef.accept(this)
            print(")")
        }

        override fun visitDelegatedConstructorCall(delegatedConstructorCall: FirDelegatedConstructorCall) {
            val dispatchReceiver = delegatedConstructorCall.dispatchReceiver
            if (dispatchReceiver !is FirNoReceiverExpression) {
                dispatchReceiver.accept(this)
                print(".")
            }
            if (delegatedConstructorCall.isSuper) {
                print("super<")
            } else if (delegatedConstructorCall.isThis) {
                print("this<")
            }
            delegatedConstructorCall.constructedTypeRef.accept(this)
            print(">")
            visitCall(delegatedConstructorCall)
        }

        override fun visitTypeRef(typeRef: FirTypeRef) {
            annotationRenderer?.render(typeRef)
            visitElement(typeRef)
        }

        override fun visitErrorTypeRef(errorTypeRef: FirErrorTypeRef) {
            annotationRenderer?.render(errorTypeRef)
            print("<ERROR TYPE REF: ${errorTypeRef.diagnostic.reason}>")
        }

        override fun visitImplicitTypeRef(implicitTypeRef: FirImplicitTypeRef) {
            print("<implicit>")
        }

        override fun visitTypeRefWithNullability(typeRefWithNullability: FirTypeRefWithNullability) {
            if (typeRefWithNullability.isMarkedNullable) {
                print("?")
            }
        }

        override fun visitDynamicTypeRef(dynamicTypeRef: FirDynamicTypeRef) {
            annotationRenderer?.render(dynamicTypeRef)
            print("<dynamic>")
            visitTypeRefWithNullability(dynamicTypeRef)
        }

        override fun visitFunctionTypeRef(functionTypeRef: FirFunctionTypeRef) {
            if (functionTypeRef.contextReceiverTypeRefs.isNotEmpty()) {
                print("context(")
                renderSeparated(functionTypeRef.contextReceiverTypeRefs, visitor)
                print(")")
            }

            annotationRenderer?.renderAnnotations(functionTypeRef.annotations.dropExtensionFunctionAnnotation())
            print("( ")
            modifierRenderer.renderModifiers(functionTypeRef)
            functionTypeRef.receiverTypeRef?.let {
                it.accept(this)
                print(".")
            }
            valueParameterRenderer.renderParameters(functionTypeRef.valueParameters)
            print(" -> ")
            functionTypeRef.returnTypeRef.accept(this)
            print(" )")
            visitTypeRefWithNullability(functionTypeRef)
        }

        override fun visitResolvedTypeRef(resolvedTypeRef: FirResolvedTypeRef) {
            typeRenderer.renderAsPossibleFunctionType(resolvedTypeRef.type)
        }

        override fun visitUserTypeRef(userTypeRef: FirUserTypeRef) {
            annotationRenderer?.render(userTypeRef)
            if (userTypeRef.customRenderer) {
                print(userTypeRef.toString())
                return
            }
            for ((index, qualifier) in userTypeRef.qualifier.withIndex()) {
                if (index != 0) {
                    print(".")
                }
                print(qualifier.name)
                if (qualifier.typeArgumentList.typeArguments.isNotEmpty()) {
                    print("<")
                    renderSeparated(qualifier.typeArgumentList.typeArguments, visitor)
                    print(">")
                }
            }
            visitTypeRefWithNullability(userTypeRef)
        }

        override fun visitTypeProjection(typeProjection: FirTypeProjection) {
            visitElement(typeProjection)
        }

        override fun visitTypeProjectionWithVariance(typeProjectionWithVariance: FirTypeProjectionWithVariance) {
            typeProjectionWithVariance.variance.renderVariance()
            typeProjectionWithVariance.typeRef.accept(this)
        }

        override fun visitStarProjection(starProjection: FirStarProjection) {
            print("*")
        }

        private fun FirBasedSymbol<*>.render(): String {
            return when (this) {
                is FirCallableSymbol<*> -> callableId.toString()
                is FirClassLikeSymbol<*> -> classId.toString()
                else -> "?"
            }
        }

        override fun visitNamedReference(namedReference: FirNamedReference) {
            val symbol = namedReference.candidateSymbol
            when {
                namedReference is FirErrorNamedReference -> print("<${namedReference.diagnostic.reason}>#")
                symbol != null -> print("R?C|${symbol.render()}|")
                else -> print("${namedReference.name}#")
            }
        }

        override fun visitBackingFieldReference(backingFieldReference: FirBackingFieldReference) {
            print("F|")
            print(backingFieldReference.resolvedSymbol.fir.propertySymbol.callableId)
            print("|")
        }

        override fun visitDelegateFieldReference(delegateFieldReference: FirDelegateFieldReference) {
            print("D|")
            print(delegateFieldReference.resolvedSymbol.callableId)
            print("|")
        }

        override fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference) {
            print("R|")
            val symbol = resolvedNamedReference.resolvedSymbol
            val isSubstitutionOverride = (symbol.fir as? FirCallableDeclaration)?.isSubstitutionOverride == true

            if (isSubstitutionOverride) {
                print("SubstitutionOverride<")
            }

            print(symbol.unwrapIntersectionOverrides().render())

            if (resolvedNamedReference is FirResolvedCallableReference) {
                if (resolvedNamedReference.inferredTypeArguments.isNotEmpty()) {
                    print("<")
                    for ((index, element) in resolvedNamedReference.inferredTypeArguments.withIndex()) {
                        if (index > 0) {
                            print(", ")
                        }
                        typeRenderer.render(element)
                    }
                    print(">")
                }
            }

            if (isSubstitutionOverride) {
                when (symbol) {
                    is FirNamedFunctionSymbol -> {
                        print(": ")
                        symbol.fir.returnTypeRef.accept(this)
                    }
                    is FirPropertySymbol -> {
                        print(": ")
                        symbol.fir.returnTypeRef.accept(this)
                    }
                }
                print(">")
            }
            print("|")
        }

        private fun FirBasedSymbol<*>.unwrapIntersectionOverrides(): FirBasedSymbol<*> {
            (this as? FirCallableSymbol<*>)?.baseForIntersectionOverride?.let { return it.unwrapIntersectionOverrides() }
            return this
        }

        override fun visitResolvedCallableReference(resolvedCallableReference: FirResolvedCallableReference) {
            visitResolvedNamedReference(resolvedCallableReference)
        }

        override fun visitThisReference(thisReference: FirThisReference) {
            print("this")
            val labelName = thisReference.labelName
            val symbol = thisReference.boundSymbol
            when {
                symbol != null -> print("@R|${symbol.render()}|")
                labelName != null -> print("@$labelName#")
                else -> print("#")
            }
        }

        override fun visitSuperReference(superReference: FirSuperReference) {
            print("super<")
            superReference.superTypeRef.accept(this)
            print(">")
            superReference.labelName?.let {
                print("@$it#")
            }
        }

        override fun visitQualifiedAccess(qualifiedAccess: FirQualifiedAccess) {
            val explicitReceiver = qualifiedAccess.explicitReceiver
            val dispatchReceiver = qualifiedAccess.dispatchReceiver
            val extensionReceiver = qualifiedAccess.extensionReceiver
            var hasSomeReceiver = true
            when {
                dispatchReceiver !is FirNoReceiverExpression && extensionReceiver !is FirNoReceiverExpression -> {
                    print("(")
                    dispatchReceiver.accept(this)
                    print(", ")
                    extensionReceiver.accept(this)
                    print(")")
                }
                dispatchReceiver !is FirNoReceiverExpression -> {
                    dispatchReceiver.accept(this)
                }
                extensionReceiver !is FirNoReceiverExpression -> {
                    extensionReceiver.accept(this)
                }
                explicitReceiver != null -> {
                    explicitReceiver.accept(this)
                }
                else -> {
                    hasSomeReceiver = false
                }
            }
            if (hasSomeReceiver) {
                print(".")
            }
        }

        override fun visitCheckNotNullCall(checkNotNullCall: FirCheckNotNullCall) {
            checkNotNullCall.argument.accept(this)
            print("!!")
        }

        override fun visitElvisExpression(elvisExpression: FirElvisExpression) {
            elvisExpression.lhs.accept(this)
            print(" ?: ")
            elvisExpression.rhs.accept(this)
        }

        override fun visitCallableReferenceAccess(callableReferenceAccess: FirCallableReferenceAccess) {
            annotationRenderer?.render(callableReferenceAccess)
            callableReferenceAccess.explicitReceiver?.accept(this)
            if (callableReferenceAccess.hasQuestionMarkAtLHS && callableReferenceAccess.explicitReceiver !is FirResolvedQualifier) {
                print("?")
            }
            print("::")
            callableReferenceAccess.calleeReference.accept(this)
        }

        override fun visitQualifiedAccessExpression(qualifiedAccessExpression: FirQualifiedAccessExpression) {
            annotationRenderer?.render(qualifiedAccessExpression)
            visitQualifiedAccess(qualifiedAccessExpression)
            qualifiedAccessExpression.calleeReference.accept(this)
            qualifiedAccessExpression.typeArguments.renderTypeArguments()
        }

        override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression) {
            visitQualifiedAccessExpression(propertyAccessExpression)
        }

        override fun visitThisReceiverExpression(thisReceiverExpression: FirThisReceiverExpression) {
            visitQualifiedAccessExpression(thisReceiverExpression)
        }

        override fun visitSmartCastExpression(smartCastExpression: FirSmartCastExpression) {
            smartCastExpression.originalExpression.accept(this)
        }

        override fun visitVariableAssignment(variableAssignment: FirVariableAssignment) {
            annotationRenderer?.render(variableAssignment)
            visitQualifiedAccess(variableAssignment)
            variableAssignment.lValue.accept(this)
            print(" ")
            print(FirOperation.ASSIGN.operator)
            print(" ")
            variableAssignment.rValue.accept(visitor)
        }

        override fun visitAugmentedArraySetCall(augmentedArraySetCall: FirAugmentedArraySetCall) {
            annotationRenderer?.render(augmentedArraySetCall)
            print("ArraySet:[")
            augmentedArraySetCall.lhsGetCall.accept(this)
            print(" ")
            print(augmentedArraySetCall.operation.operator)
            print(" ")
            augmentedArraySetCall.rhs.accept(this)
            print("]")
        }

        override fun visitFunctionCall(functionCall: FirFunctionCall) {
            annotationRenderer?.render(functionCall)
            visitQualifiedAccess(functionCall)
            functionCall.calleeReference.accept(this)
            functionCall.typeArguments.renderTypeArguments()
            visitCall(functionCall)
        }

        override fun visitIntegerLiteralOperatorCall(integerLiteralOperatorCall: FirIntegerLiteralOperatorCall) {
            visitFunctionCall(integerLiteralOperatorCall)
        }

        override fun visitImplicitInvokeCall(implicitInvokeCall: FirImplicitInvokeCall) {
            visitFunctionCall(implicitInvokeCall)
        }

        override fun visitComparisonExpression(comparisonExpression: FirComparisonExpression) {
            print("CMP(${comparisonExpression.operation.operator}, ")
            comparisonExpression.compareToCall.accept(this)
            print(")")
        }

        override fun visitAssignmentOperatorStatement(assignmentOperatorStatement: FirAssignmentOperatorStatement) {
            annotationRenderer?.render(assignmentOperatorStatement)
            print(assignmentOperatorStatement.operation.operator)
            print("(")
            assignmentOperatorStatement.leftArgument.accept(visitor)
            print(", ")
            assignmentOperatorStatement.rightArgument.accept(visitor)
            print(")")
        }

        override fun visitEqualityOperatorCall(equalityOperatorCall: FirEqualityOperatorCall) {
            annotationRenderer?.render(equalityOperatorCall)
            print(equalityOperatorCall.operation.operator)
            visitCall(equalityOperatorCall)
        }

        override fun visitComponentCall(componentCall: FirComponentCall) {
            visitFunctionCall(componentCall)
        }

        override fun visitGetClassCall(getClassCall: FirGetClassCall) {
            annotationRenderer?.render(getClassCall)
            print("<getClass>")
            visitCall(getClassCall)
        }

        override fun visitClassReferenceExpression(classReferenceExpression: FirClassReferenceExpression) {
            annotationRenderer?.render(classReferenceExpression)
            print("<getClass>")
            print("(")
            classReferenceExpression.classTypeRef.accept(this)
            print(")")
        }

        override fun visitArrayOfCall(arrayOfCall: FirArrayOfCall) {
            annotationRenderer?.render(arrayOfCall)
            print("<implicitArrayOf>")
            visitCall(arrayOfCall)
        }

        override fun visitThrowExpression(throwExpression: FirThrowExpression) {
            annotationRenderer?.render(throwExpression)
            print("throw ")
            throwExpression.exception.accept(this)
        }

        override fun visitErrorExpression(errorExpression: FirErrorExpression) {
            errorExpressionRenderer.renderErrorExpression(errorExpression)
        }

        override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier) {
            annotationRenderer?.render(resolvedQualifier)
            print("Q|")
            val classId = resolvedQualifier.classId
            if (classId != null) {
                print(classId.asString())
            } else {
                print(resolvedQualifier.packageFqName.asString().replace(".", "/"))
            }
            if (resolvedQualifier.isNullableLHSForCallableReference) {
                print("?")
            }
            print("|")
        }

        override fun visitBinaryLogicExpression(binaryLogicExpression: FirBinaryLogicExpression) {
            binaryLogicExpression.leftOperand.accept(this)
            print(" ${binaryLogicExpression.kind.token} ")
            binaryLogicExpression.rightOperand.accept(this)
        }

        override fun visitErrorNamedReference(errorNamedReference: FirErrorNamedReference) {
            visitNamedReference(errorNamedReference)
        }

        override fun visitEffectDeclaration(effectDeclaration: FirEffectDeclaration) {
            contractRenderer?.render(effectDeclaration)
        }

        override fun visitContractDescription(contractDescription: FirContractDescription) {
            require(contractDescription is FirEmptyContractDescription)
        }

        override fun visitPackageDirective(packageDirective: FirPackageDirective) {
            packageDirectiveRenderer?.render(packageDirective)
        }
    }
}
