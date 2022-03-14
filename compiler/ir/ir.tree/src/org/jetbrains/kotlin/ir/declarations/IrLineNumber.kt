/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations

import org.jetbrains.kotlin.ir.IrElementBase
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor

class IrLineNumber(
    override val startOffset: Int,
    override val endOffset: Int,
    override var type: IrType,
    val lineNumber: Int,
    val sourcePosition: Triple<Int, String, String>?,
    var inlineCall: IrCall?,
    var callee: IrFunction?
) : IrExpression() {
    init {
//        inlineCall?.let { call ->
//            call.dispatchReceiver = null
//            call.extensionReceiver = null
//            (0 until call.valueArgumentsCount).forEach { call.putValueArgument(it, null) }
//        }
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
        return visitor.visitLineNumber(this, data)
    }

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
//        inlineCall?.accept(visitor, data)
//        callee?.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
//        val transform = inlineCall?.transform(transformer, data)
//        inlineCall = transform as? IrCall
//        callee = callee?.transform(transformer, data) as? IrFunction
    }
}