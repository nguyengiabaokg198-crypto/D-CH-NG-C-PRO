package com.example.smali

import com.example.dex.DexClass
import com.example.dex.DexField
import com.example.dex.DexInstruction
import com.example.dex.DexMethod

class SmaliGenerator {

    fun generate(dexClass: DexClass): String {
        val sb = StringBuilder()

        // Class Header
        sb.append(".class ")
        sb.append(formatClassAccessFlags(dexClass.accessFlags))
        sb.append(dexClass.type)
        sb.append("\n")

        // Superclass
        if (dexClass.superType != null) {
            sb.append(".super ").append(dexClass.superType).append("\n")
        }

        // Source file
        if (!dexClass.sourceFile.isNullOrEmpty()) {
            sb.append(".source \"").append(dexClass.sourceFile).append("\"\n")
        }

        // Interfaces
        if (dexClass.interfaces.isNotEmpty()) {
            sb.append("\n# interfaces\n")
            for (iface in dexClass.interfaces) {
                sb.append(".implements ").append(iface).append("\n")
            }
        }

        // Fields
        val staticFields = dexClass.fields.filter { it.isStatic }
        val instanceFields = dexClass.fields.filter { !it.isStatic }

        if (staticFields.isNotEmpty()) {
            sb.append("\n# static fields\n")
            for (field in staticFields) {
                sb.append(generateField(field)).append("\n")
            }
        }

        if (instanceFields.isNotEmpty()) {
            sb.append("\n# instance fields\n")
            for (field in instanceFields) {
                sb.append(generateField(field)).append("\n")
            }
        }

        // Methods
        val directMethods = dexClass.methods.filter { it.isConstructor || it.isStatic || it.isPrivate }
        val virtualMethods = dexClass.methods.filter { !it.isConstructor && !it.isStatic && !it.isPrivate }

        if (directMethods.isNotEmpty()) {
            sb.append("\n# direct methods\n")
            for (method in directMethods) {
                sb.append(generateMethod(method)).append("\n\n")
            }
        }

        if (virtualMethods.isNotEmpty()) {
            sb.append("\n# virtual methods\n")
            for (method in virtualMethods) {
                sb.append(generateMethod(method)).append("\n\n")
            }
        }

        return sb.toString().trimEnd() + "\n"
    }

    private fun generateField(field: DexField): String {
        val flags = formatFieldAccessFlags(field.accessFlags)
        return ".field $flags${field.name}:${field.type}"
    }

    private fun generateMethod(method: DexMethod): String {
        val sb = StringBuilder()
        val flags = formatMethodAccessFlags(method.accessFlags)
        val paramTypes = method.proto.parameters.joinToString("")
        sb.append(".method $flags${method.name}($paramTypes)${method.proto.returnType}\n")

        val code = method.codeItem
        if (code != null) {
            sb.append("    .registers ").append(code.registersSize).append("\n")

            // Collect labels for jump/branch targets
            val targetOffsets = mutableSetOf<Int>()
            for (ins in code.instructions) {
                if (ins.targetOffset != null) {
                    targetOffsets.add(ins.targetOffset)
                }
            }

            for (ins in code.instructions) {
                if (ins.offset in targetOffsets) {
                    sb.append("    :cond_${ins.offset}\n")
                }
                sb.append("    ").append(formatInstruction(ins)).append("\n")
            }
        } else {
            sb.append("    # abstract or native method\n")
        }

        sb.append(".end method")
        return sb.toString()
    }

    private fun formatInstruction(ins: DexInstruction): String {
        val regs = ins.registers.joinToString(", ") { "v$it" }
        return when {
            ins.targetOffset != null -> {
                val label = ":cond_${ins.targetOffset}"
                if (regs.isNotEmpty()) "${ins.opcodeName} $regs, $label" else "${ins.opcodeName} $label"
            }
            ins.targetMethod != null -> {
                "${ins.opcodeName} {$regs}, ${ins.targetMethod}"
            }
            ins.targetField != null -> {
                if (regs.isNotEmpty()) "${ins.opcodeName} $regs, ${ins.targetField}" else "${ins.opcodeName} ${ins.targetField}"
            }
            ins.targetType != null -> {
                if (regs.isNotEmpty()) "${ins.opcodeName} $regs, ${ins.targetType}" else "${ins.opcodeName} ${ins.targetType}"
            }
            ins.stringVal != null -> {
                val escaped = ins.stringVal.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                "${ins.opcodeName} $regs, \"$escaped\""
            }
            ins.literal != null -> {
                "${ins.opcodeName} $regs, 0x${java.lang.Long.toHexString(ins.literal)}"
            }
            regs.isNotEmpty() -> {
                "${ins.opcodeName} $regs"
            }
            else -> ins.opcodeName
        }
    }

    private fun formatClassAccessFlags(flags: Int): String {
        val list = mutableListOf<String>()
        if ((flags and 0x0001) != 0) list.add("public")
        if ((flags and 0x0002) != 0) list.add("private")
        if ((flags and 0x0004) != 0) list.add("protected")
        if ((flags and 0x0008) != 0) list.add("static")
        if ((flags and 0x0010) != 0) list.add("final")
        if ((flags and 0x0200) != 0) list.add("interface")
        if ((flags and 0x0400) != 0 && (flags and 0x0200) == 0) list.add("abstract")
        if ((flags and 0x1000) != 0) list.add("synthetic")
        if ((flags and 0x2000) != 0) list.add("annotation")
        if ((flags and 0x4000) != 0) list.add("enum")
        return if (list.isEmpty()) "" else list.joinToString(" ") + " "
    }

    private fun formatFieldAccessFlags(flags: Int): String {
        val list = mutableListOf<String>()
        if ((flags and 0x0001) != 0) list.add("public")
        if ((flags and 0x0002) != 0) list.add("private")
        if ((flags and 0x0004) != 0) list.add("protected")
        if ((flags and 0x0008) != 0) list.add("static")
        if ((flags and 0x0010) != 0) list.add("final")
        if ((flags and 0x0040) != 0) list.add("volatile")
        if ((flags and 0x0080) != 0) list.add("transient")
        return if (list.isEmpty()) "" else list.joinToString(" ") + " "
    }

    private fun formatMethodAccessFlags(flags: Int): String {
        val list = mutableListOf<String>()
        if ((flags and 0x0001) != 0) list.add("public")
        if ((flags and 0x0002) != 0) list.add("private")
        if ((flags and 0x0004) != 0) list.add("protected")
        if ((flags and 0x0008) != 0) list.add("static")
        if ((flags and 0x0010) != 0) list.add("final")
        if ((flags and 0x0020) != 0) list.add("synchronized")
        if ((flags and 0x0100) != 0) list.add("native")
        if ((flags and 0x0400) != 0) list.add("abstract")
        if ((flags and 0x0080) != 0) list.add("varargs")
        return if (list.isEmpty()) "" else list.joinToString(" ") + " "
    }
}
