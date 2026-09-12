package com.example.decompiler

import com.example.dex.DexClass
import com.example.dex.DexCodeItem
import com.example.dex.DexMethod

enum class InferredKind {
    INT, BOOLEAN, BYTE, CHAR, SHORT, LONG, FLOAT, DOUBLE, STRING, OBJECT, VOID, UNKNOWN
}

data class InferredVar(
    val reg: Int,
    val rawType: String,
    val simpleType: String,
    val varName: String,
    val isParameter: Boolean = false,
    val isThis: Boolean = false
)

class TypeInference {

    fun inferVariables(
        dexClass: DexClass,
        method: DexMethod,
        code: DexCodeItem
    ): Map<Int, InferredVar> {
        val regTypes = mutableMapOf<Int, String>()
        val totalRegs = code.registersSize
        val insSize = code.insSize
        val isStatic = method.isStatic

        // 1. Parameter registers setup
        val paramTypes = method.proto.parameters
        var currentParamReg = totalRegs - insSize

        if (!isStatic) {
            regTypes[currentParamReg] = dexClass.type
            currentParamReg++
        }

        for (pType in paramTypes) {
            regTypes[currentParamReg] = pType
            currentParamReg += if (pType == "J" || pType == "D") 2 else 1
        }

        // 2. Scan instructions for type clues
        var pendingReturnTargetType: String? = null
        for (ins in code.instructions) {
            val regs = ins.registers

            when {
                ins.opcodeName.startsWith("invoke") -> {
                    val returnType = ins.targetMethod?.substringAfter(")") ?: "V"
                    pendingReturnTargetType = returnType
                }

                ins.opcodeName.startsWith("move-result") && regs.isNotEmpty() -> {
                    if (pendingReturnTargetType != null && pendingReturnTargetType != "V") {
                        regTypes[regs[0]] = pendingReturnTargetType
                    }
                    pendingReturnTargetType = null
                }

                ins.opcodeName.startsWith("const-string") && regs.isNotEmpty() -> {
                    regTypes[regs[0]] = "Ljava/lang/String;"
                }

                ins.opcodeName == "new-instance" && regs.isNotEmpty() -> {
                    ins.targetType?.let { regTypes[regs[0]] = it }
                }

                ins.opcodeName == "new-array" && regs.isNotEmpty() -> {
                    ins.targetType?.let { regTypes[regs[0]] = it }
                }

                ins.opcodeName == "check-cast" && regs.isNotEmpty() -> {
                    ins.targetType?.let { regTypes[regs[0]] = it }
                }

                ins.opcodeName.startsWith("iget") && regs.size >= 2 -> {
                    val fType = ins.targetField?.substringAfterLast(':')
                    if (fType != null) regTypes[regs[0]] = fType
                }

                ins.opcodeName.startsWith("sget") && regs.isNotEmpty() -> {
                    val fType = ins.targetField?.substringAfterLast(':')
                    if (fType != null) regTypes[regs[0]] = fType
                }

                ins.opcodeName.startsWith("const-wide") && regs.isNotEmpty() -> {
                    if (!regTypes.containsKey(regs[0])) regTypes[regs[0]] = "J"
                }

                ins.opcodeName.startsWith("const") && regs.isNotEmpty() -> {
                    if (!regTypes.containsKey(regs[0])) regTypes[regs[0]] = "I"
                }

                ins.opcodeName.startsWith("move") && regs.size >= 2 -> {
                    val srcType = regTypes[regs[1]]
                    if (srcType != null) regTypes[regs[0]] = srcType
                }
            }
        }

        // 3. Build friendly name and InferredVar
        val result = mutableMapOf<Int, InferredVar>()
        val nameCounts = mutableMapOf<String, Int>()

        var pIndex = 0
        var paramRegStart = totalRegs - insSize

        for (r in 0 until totalRegs) {
            val isParam = r >= paramRegStart
            val isThis = !isStatic && r == paramRegStart

            val raw = regTypes[r] ?: "Ljava/lang/Object;"
            val simple = cleanType(raw)

            val name = when {
                isThis -> "this"
                isParam -> {
                    val pName = "p${pIndex++}"
                    pName
                }
                else -> {
                    val base = when {
                        simple == "String" -> "str"
                        simple == "int" -> "num"
                        simple == "boolean" -> "flag"
                        simple == "long" -> "lVal"
                        simple == "float" || simple == "double" -> "fVal"
                        simple.endsWith("[]") -> "arr"
                        simple == "Context" -> "context"
                        simple == "View" -> "view"
                        simple == "Intent" -> "intent"
                        simple == "Bundle" -> "bundle"
                        simple == "Exception" || simple.endsWith("Exception") -> "e"
                        else -> simple.replaceFirstChar { it.lowercase() }
                    }
                    val count = (nameCounts[base] ?: 0) + 1
                    nameCounts[base] = count
                    if (count == 1) base else "${base}_$count"
                }
            }

            result[r] = InferredVar(
                reg = r,
                rawType = raw,
                simpleType = simple,
                varName = name,
                isParameter = isParam,
                isThis = isThis
            )
        }

        return result
    }

    fun cleanType(descriptor: String): String {
        var d = descriptor
        var arrayDim = 0
        while (d.startsWith("[")) {
            arrayDim++
            d = d.substring(1)
        }
        val base = when (d) {
            "V" -> "void"
            "Z" -> "boolean"
            "B" -> "byte"
            "S" -> "short"
            "C" -> "char"
            "I" -> "int"
            "J" -> "long"
            "F" -> "float"
            "D" -> "double"
            else -> {
                if (d.startsWith("L") && d.endsWith(";")) {
                    d.substring(1, d.length - 1).substringAfterLast('/').substringAfterLast('$')
                } else {
                    d
                }
            }
        }
        return base + "[]".repeat(arrayDim)
    }
}
