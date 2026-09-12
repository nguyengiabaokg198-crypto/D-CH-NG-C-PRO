package com.example.decompiler

import com.example.dex.DexCodeItem
import com.example.dex.DexInstruction

sealed class StructuredBlock {
    data class Simple(val lines: List<String>) : StructuredBlock()
    data class IfBlock(val condition: String, val thenBody: List<StructuredBlock>, val elseBody: List<StructuredBlock> = emptyList()) : StructuredBlock()
    data class LoopBlock(val condition: String, val body: List<StructuredBlock>) : StructuredBlock()
    data class SwitchBlock(val expression: String, val cases: List<Pair<String, List<StructuredBlock>>>) : StructuredBlock()
    data class TryCatchBlock(val tryBody: List<StructuredBlock>, val catchType: String, val catchBody: List<StructuredBlock>) : StructuredBlock()
}

class AstBuilder(
    private val typeInference: TypeInference,
    private val deobfuscator: Deobfuscator
) {

    fun buildStructuredCode(
        code: DexCodeItem,
        inferredVars: Map<Int, InferredVar>,
        deobfResult: DeobfuscationResult?
    ): List<String> {
        val outputLines = mutableListOf<String>()
        val instructions = code.instructions
        if (instructions.isEmpty()) return outputLines

        // 1. Check if method has try-catch ranges
        val tries = code.tries
        var tryHandled = false

        if (tries.isNotEmpty()) {
            for (t in tries) {
                val start = t.startAddr
                val end = t.startAddr + t.insnCount
                val handler = t.handlerOffset

                val tryInsns = instructions.filter { it.offset in start until end }
                val catchInsns = instructions.filter { it.offset >= handler }

                if (tryInsns.isNotEmpty()) {
                    outputLines.add("try {")
                    val tryBody = emitLinearBlock(tryInsns, inferredVars, deobfResult, "    ")
                    outputLines.addAll(tryBody)
                    outputLines.add("} catch (${t.exceptionType ?: "Exception"} e) {")
                    val catchBody = emitLinearBlock(catchInsns, inferredVars, deobfResult, "    ")
                    outputLines.addAll(catchBody)
                    outputLines.add("}")
                    tryHandled = true
                    break
                }
            }
        }

        if (!tryHandled) {
            val lines = emitStructuredFlow(instructions, inferredVars, deobfResult, "")
            outputLines.addAll(lines)
        }

        return outputLines
    }

    private fun emitStructuredFlow(
        instructions: List<DexInstruction>,
        inferredVars: Map<Int, InferredVar>,
        deobfResult: DeobfuscationResult?,
        indent: String
    ): List<String> {
        val lines = mutableListOf<String>()
        var i = 0

        // Find loop back-edges (instructions jumping to a previous offset)
        val loopHeaders = mutableSetOf<Int>()
        for (ins in instructions) {
            if (ins.targetOffset != null && ins.targetOffset < ins.offset) {
                loopHeaders.add(ins.targetOffset)
            }
        }

        while (i < instructions.size) {
            val ins = instructions[i]

            // Check if this is a loop header
            if (ins.offset in loopHeaders) {
                lines.add("$indent// Loop start")
            }

            // Check for switch statement
            if ((ins.opcodeName == "packed-switch" || ins.opcodeName == "sparse-switch") && ins.switchKeys != null && ins.switchTargets != null) {
                val regName = getVarName(ins.registers.firstOrNull() ?: 0, inferredVars)
                lines.add("${indent}switch ($regName) {")
                for (cIdx in ins.switchKeys.indices) {
                    val key = ins.switchKeys[cIdx]
                    val target = ins.switchTargets[cIdx]
                    lines.add("$indent    case $key: // target: label_$target")
                    lines.add("$indent        break;")
                }
                lines.add("$indent    default:")
                lines.add("$indent        break;")
                lines.add("$indent}")
                i++
                continue
            }

            // Check for conditional branch
            if (ins.opcodeName.startsWith("if-") && ins.targetOffset != null) {
                val cond = formatCondition(ins, inferredVars)
                val target = ins.targetOffset

                if (target > ins.offset) {
                    // Forward jump -> can structure as if (...) { ... }
                    val innerInsns = instructions.filter { it.offset > ins.offset && it.offset < target }
                    lines.add("${indent}if ($cond) {")
                    val innerLines = emitLinearBlock(innerInsns, inferredVars, deobfResult, "$indent    ")
                    lines.addAll(innerLines)
                    lines.add("$indent}")

                    // Fast forward i to after the target block
                    val nextIdx = instructions.indexOfFirst { it.offset >= target }
                    if (nextIdx > i) {
                        i = nextIdx
                        continue
                    }
                } else {
                    // Backward jump (loop condition)
                    lines.add("${indent}while ($cond) {")
                    lines.add("$indent    // loop body back to label_$target")
                    lines.add("$indent}")
                    i++
                    continue
                }
            }

            // Normal instruction
            val stmt = formatInstruction(ins, inferredVars, deobfResult)
            if (stmt != null) {
                lines.add("$indent$stmt")
            }
            i++
        }

        return lines
    }

    private fun emitLinearBlock(
        instructions: List<DexInstruction>,
        inferredVars: Map<Int, InferredVar>,
        deobfResult: DeobfuscationResult?,
        indent: String
    ): List<String> {
        val lines = mutableListOf<String>()
        for (ins in instructions) {
            val stmt = formatInstruction(ins, inferredVars, deobfResult)
            if (stmt != null) {
                lines.add("$indent$stmt")
            }
        }
        return lines
    }

    private fun formatInstruction(
        ins: DexInstruction,
        inferredVars: Map<Int, InferredVar>,
        deobfResult: DeobfuscationResult?
    ): String? {
        val regs = ins.registers

        return when {
            ins.opcodeName == "return-void" -> "return;"

            ins.opcodeName.startsWith("return") && regs.isNotEmpty() -> {
                val v = getVarName(regs[0], inferredVars)
                "return $v;"
            }

            ins.opcodeName.startsWith("const-string") && regs.isNotEmpty() -> {
                val rawStr = ins.stringVal ?: ""
                val escaped = rawStr.replace("\"", "\\\"").replace("\n", "\\n")
                val v = getVarName(regs[0], inferredVars)
                val type = inferredVars[regs[0]]?.simpleType ?: "String"
                val deobfNote = deobfResult?.decodedStrings?.get(rawStr)?.let { " // [Deobf: Decrypted (${it.algorithm})]: \"${it.decrypted}\"" } ?: ""
                "$type $v = \"$escaped\";$deobfNote"
            }

            ins.opcodeName.startsWith("const-wide") && regs.isNotEmpty() && ins.literal != null -> {
                val v = getVarName(regs[0], inferredVars)
                "long $v = ${ins.literal}L;"
            }

            ins.opcodeName.startsWith("const") && regs.isNotEmpty() && ins.literal != null -> {
                val v = getVarName(regs[0], inferredVars)
                val type = inferredVars[regs[0]]?.simpleType ?: "int"
                if (type == "boolean") {
                    "$type $v = ${if (ins.literal != 0L) "true" else "false"};"
                } else {
                    "$type $v = ${ins.literal};"
                }
            }

            ins.opcodeName == "new-instance" && regs.isNotEmpty() -> {
                val v = getVarName(regs[0], inferredVars)
                val type = typeInference.cleanType(ins.targetType ?: "Object")
                "$type $v = new $type();"
            }

            ins.opcodeName == "new-array" && regs.size >= 2 -> {
                val v = getVarName(regs[0], inferredVars)
                val sz = getVarName(regs[1], inferredVars)
                val type = typeInference.cleanType(ins.targetType ?: "Object[]")
                val elem = type.trimEnd('[', ']')
                "$type $v = new $elem[$sz];"
            }

            ins.opcodeName == "check-cast" && regs.isNotEmpty() -> {
                val v = getVarName(regs[0], inferredVars)
                val type = typeInference.cleanType(ins.targetType ?: "Object")
                "$v = ($type) $v;"
            }

            ins.opcodeName == "instance-of" && regs.size >= 2 -> {
                val vA = getVarName(regs[0], inferredVars)
                val vB = getVarName(regs[1], inferredVars)
                val type = typeInference.cleanType(ins.targetType ?: "Object")
                "boolean $vA = $vB instanceof $type;"
            }

            ins.opcodeName == "array-length" && regs.size >= 2 -> {
                val vA = getVarName(regs[0], inferredVars)
                val vB = getVarName(regs[1], inferredVars)
                "int $vA = $vB.length;"
            }

            ins.opcodeName.startsWith("aget") && regs.size >= 3 -> {
                val vA = getVarName(regs[0], inferredVars)
                val arr = getVarName(regs[1], inferredVars)
                val idx = getVarName(regs[2], inferredVars)
                "var $vA = $arr[$idx];"
            }

            ins.opcodeName.startsWith("aput") && regs.size >= 3 -> {
                val vA = getVarName(regs[0], inferredVars)
                val arr = getVarName(regs[1], inferredVars)
                val idx = getVarName(regs[2], inferredVars)
                "$arr[$idx] = $vA;"
            }

            ins.opcodeName.startsWith("move-result") && regs.isNotEmpty() -> {
                val v = getVarName(regs[0], inferredVars)
                val type = inferredVars[regs[0]]?.simpleType ?: "var"
                "$type $v = /* returned result */;"
            }

            ins.opcodeName.startsWith("move") && regs.size >= 2 -> {
                val dst = getVarName(regs[0], inferredVars)
                val src = getVarName(regs[1], inferredVars)
                "$dst = $src;"
            }

            ins.opcodeName.startsWith("iget") && regs.size >= 2 -> {
                val vA = getVarName(regs[0], inferredVars)
                val obj = getVarName(regs[1], inferredVars)
                val rawF = ins.targetField?.substringAfter("->")?.substringBefore(":") ?: "field"
                val fieldName = deobfResult?.fieldAliases?.get(rawF) ?: rawF
                "var $vA = $obj.$fieldName;"
            }

            ins.opcodeName.startsWith("sget") && regs.isNotEmpty() -> {
                val vA = getVarName(regs[0], inferredVars)
                val rawF = ins.targetField?.substringAfterLast('/')?.substringBefore(":") ?: "field"
                val fieldName = deobfResult?.fieldAliases?.get(rawF) ?: rawF
                "var $vA = $fieldName;"
            }

            ins.opcodeName.startsWith("iput") && regs.size >= 2 -> {
                val vA = getVarName(regs[0], inferredVars)
                val obj = getVarName(regs[1], inferredVars)
                val rawF = ins.targetField?.substringAfter("->")?.substringBefore(":") ?: "field"
                val fieldName = deobfResult?.fieldAliases?.get(rawF) ?: rawF
                "$obj.$fieldName = $vA;"
            }

            ins.opcodeName.startsWith("sput") && regs.isNotEmpty() -> {
                val vA = getVarName(regs[0], inferredVars)
                val rawF = ins.targetField?.substringAfterLast('/')?.substringBefore(":") ?: "field"
                val fieldName = deobfResult?.fieldAliases?.get(rawF) ?: rawF
                "$fieldName = $vA;"
            }

            ins.opcodeName.startsWith("invoke") -> {
                val target = ins.targetMethod ?: "method"
                val rawMethodName = target.substringAfter("->").substringBefore("(")
                val methodName = deobfResult?.methodAliases?.get(rawMethodName) ?: rawMethodName
                val classClean = typeInference.cleanType(target.substringBefore("->"))
                val isStatic = ins.opcodeName.contains("static")

                val callTarget = if (isStatic) {
                    classClean
                } else if (regs.isNotEmpty()) {
                    getVarName(regs[0], inferredVars)
                } else {
                    "this"
                }

                val argRegs = if (isStatic) regs else regs.drop(1)
                val args = argRegs.joinToString(", ") { getVarName(it, inferredVars) }

                if (methodName == "<init>") {
                    if (callTarget == "this") "super($args);" else "$callTarget = new $classClean($args);"
                } else {
                    "$callTarget.$methodName($args);"
                }
            }

            ins.opcodeName.startsWith("add") || ins.opcodeName.startsWith("sub") ||
            ins.opcodeName.startsWith("mul") || ins.opcodeName.startsWith("div") -> {
                val op = when {
                    ins.opcodeName.contains("add") -> "+"
                    ins.opcodeName.contains("sub") -> "-"
                    ins.opcodeName.contains("mul") -> "*"
                    ins.opcodeName.contains("div") -> "/"
                    else -> "+"
                }
                if (regs.size >= 2) {
                    val vA = getVarName(regs[0], inferredVars)
                    val vB = getVarName(regs[1], inferredVars)
                    val vC = if (regs.size >= 3) getVarName(regs[2], inferredVars) else ins.literal?.toString() ?: "1"
                    "$vA = $vB $op $vC;"
                } else null
            }

            ins.opcodeName == "monitor-enter" && regs.isNotEmpty() -> {
                val obj = getVarName(regs[0], inferredVars)
                "synchronized ($obj) {"
            }

            ins.opcodeName == "monitor-exit" && regs.isNotEmpty() -> {
                "}"
            }

            ins.opcodeName == "throw" && regs.isNotEmpty() -> {
                val ex = getVarName(regs[0], inferredVars)
                "throw $ex;"
            }

            ins.opcodeName.startsWith("goto") -> {
                "// jump to offset ${ins.targetOffset}"
            }

            ins.opcodeName == "nop" -> null

            else -> "// ${ins.opcodeName} (${regs.joinToString(", ") { "v$it" }})"
        }
    }

    private fun formatCondition(ins: DexInstruction, inferredVars: Map<Int, InferredVar>): String {
        val op = when (ins.opcodeName) {
            "if-eq", "if-eqz" -> "=="
            "if-ne", "if-nez" -> "!="
            "if-lt", "if-ltz" -> "<"
            "if-ge", "if-gez" -> ">="
            "if-gt", "if-gtz" -> ">"
            "if-le", "if-lez" -> "<="
            else -> "=="
        }
        val regs = ins.registers
        val left = if (regs.isNotEmpty()) getVarName(regs[0], inferredVars) else "0"
        val right = if (regs.size > 1) getVarName(regs[1], inferredVars) else "0"
        return "$left $op $right"
    }

    private fun getVarName(reg: Int, inferredVars: Map<Int, InferredVar>): String {
        return inferredVars[reg]?.varName ?: "v$reg"
    }
}
