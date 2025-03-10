/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

import kotlinx.cinterop.cValuesOf
import llvm.*
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.ir.llvmSymbolOrigin
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrConst

private fun ConstPointer.add(index: Int): ConstPointer {
    return constPointer(LLVMConstGEP(llvm, cValuesOf(Int32(index).llvm), 1)!!)
}

internal class KotlinStaticData(override val context: Context, module: LLVMModuleRef) : ContextUtils, StaticData(module) {
    private val stringLiterals = mutableMapOf<String, ConstPointer>()

    // Must match OBJECT_TAG_PERMANENT_CONTAINER in C++.
    private fun permanentTag(typeInfo: ConstPointer): ConstPointer {
        // Only pointer arithmetic via GEP works on constant pointers in LLVM.
        return typeInfo.bitcast(int8TypePtr).add(1).bitcast(kTypeInfoPtr)
    }


    private fun objHeader(typeInfo: ConstPointer): Struct {
        return Struct(runtime.objHeaderType, permanentTag(typeInfo))
    }

    private fun arrayHeader(typeInfo: ConstPointer, length: Int): Struct {
        assert(length >= 0)
        return Struct(runtime.arrayHeaderType, permanentTag(typeInfo), Int32(length))
    }

    private fun createRef(objHeaderPtr: ConstPointer) = objHeaderPtr.bitcast(kObjHeaderPtr)

    private fun createKotlinStringLiteral(value: String): ConstPointer {
        val elements = value.toCharArray().map(::Char16)
        val objRef = createConstKotlinArray(context.ir.symbols.string.owner, elements)
        return objRef
    }

    fun kotlinStringLiteral(value: String) = stringLiterals.getOrPut(value) { createKotlinStringLiteral(value) }

    fun createConstKotlinArray(arrayClass: IrClass, elements: List<LLVMValueRef>) =
            createConstKotlinArray(arrayClass, elements.map { constValue(it) }).llvm

    fun createConstKotlinArray(arrayClass: IrClass, elements: List<ConstValue>): ConstPointer {
        val typeInfo = arrayClass.typeInfoPtr

        val bodyElementType: LLVMTypeRef = elements.firstOrNull()?.llvmType ?: int8Type
        // (use [0 x i8] as body if there are no elements)
        val arrayBody = ConstArray(bodyElementType, elements)

        val compositeType = structType(runtime.arrayHeaderType, arrayBody.llvmType)

        val global = this.createGlobal(compositeType, "")

        val objHeaderPtr = global.pointer.getElementPtr(0)
        val arrayHeader = arrayHeader(typeInfo, elements.size)

        global.setInitializer(Struct(compositeType, arrayHeader, arrayBody))
        global.setConstant(true)
        global.setUnnamedAddr(true)

        return createRef(objHeaderPtr)
    }

    fun createConstKotlinObject(type: IrClass, vararg fields: ConstValue): ConstPointer {
        val typeInfo = type.typeInfoPtr
        val objHeader = objHeader(typeInfo)

        val global = this.placeGlobal("", Struct(objHeader, *fields))
        global.setUnnamedAddr(true)
        global.setConstant(true)

        val objHeaderPtr = global.pointer.getElementPtr(0)

        return createRef(objHeaderPtr)
    }

    fun createInitializer(type: IrClass, vararg fields: ConstValue): ConstValue =
            Struct(objHeader(type.typeInfoPtr), *fields)

    fun createUniqueInstance(
            kind: UniqueKind, bodyType: LLVMTypeRef, typeInfo: ConstPointer): ConstPointer {
        assert(getStructElements(bodyType).size == 1) // ObjHeader only.
        val objHeader = when (kind) {
            UniqueKind.UNIT -> objHeader(typeInfo)
            UniqueKind.EMPTY_ARRAY -> arrayHeader(typeInfo, 0)
        }
        val global = this.placeGlobal(kind.llvmName, objHeader, isExported = true)
        global.setConstant(true)
        return global.pointer
    }

    fun unique(kind: UniqueKind): ConstPointer {
        val descriptor = when (kind) {
            UniqueKind.UNIT -> context.ir.symbols.unit.owner
            UniqueKind.EMPTY_ARRAY -> context.ir.symbols.array.owner
        }
        return if (isExternal(descriptor)) {
            constPointer(importGlobal(
                    kind.llvmName, runtime.objHeaderType, origin = descriptor.llvmSymbolOrigin
            ))
        } else {
            context.generationState.llvmDeclarations.forUnique(kind).pointer
        }
    }

    /**
     * Creates static instance of `konan.ImmutableByteArray` with given values of elements.
     *
     * @param args data for constant creation.
     */
    fun createImmutableBlob(value: IrConst<String>): LLVMValueRef {
        val args = value.value.map { Int8(it.code.toByte()).llvm }
        return createConstKotlinArray(context.ir.symbols.immutableBlob.owner, args)
    }
}

internal val ContextUtils.theUnitInstanceRef: ConstPointer
    get() = staticData.unique(UniqueKind.UNIT)