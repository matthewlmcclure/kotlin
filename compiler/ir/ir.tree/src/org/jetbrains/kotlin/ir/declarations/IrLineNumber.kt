/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations

import org.jetbrains.kotlin.ir.IrFileEntry
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor

class IrLineNumber(
    override val startOffset: Int,
    override val endOffset: Int,
    override var type: IrType,
    val calleeFile: IrFileEntry,
    val lineNumber: Int,
    val inlineCall: IrCall?,
    val callee: IrFunction?
) : IrExpression() {
    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
        return visitor.visitLineNumber(this, data)
    }
}