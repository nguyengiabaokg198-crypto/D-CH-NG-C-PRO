package com.example.decompiler

import com.example.dex.DexClass
import com.example.dex.DexField
import com.example.dex.DexMethod

class JavaReconstructor {

    private val typeInference = TypeInference()
    private val deobfuscator = Deobfuscator()
    private val astBuilder = AstBuilder(typeInference, deobfuscator)

    fun getDeobfuscator(): Deobfuscator = deobfuscator

    fun reconstruct(dexClass: DexClass, deobfuscate: Boolean = false): String {
        val cached = DecompilerCache.get(dexClass.type, deobfuscate)
        if (cached != null) return cached

        val deobfResult = if (deobfuscate) deobfuscator.analyzeClass(dexClass) else null
        val sb = StringBuilder()

        sb.append("// ====================================================================\n")
        sb.append("// Java source decompiled by APK Decompiler Pro (Mobile JADX Engine v2.5)\n")
        sb.append("// Notice: High-Level Structure Reconstructed from Dalvik Bytecode IR\n")
        if (deobfuscate && deobfResult != null) {
            sb.append("// --------------------------------------------------------------------\n")
            sb.append("// De-obfuscation Mode: ACTIVE (Advanced Heuristic De-obfuscator)\n")
            if (deobfResult.detectedLibrary != null) {
                sb.append("//  * Fingerprint:   ").append(deobfResult.detectedLibrary).append("\n")
            }
            sb.append("//  * Restored Names: ").append(deobfResult.methodAliases.size).append(" methods, ")
                .append(deobfResult.fieldAliases.size).append(" fields\n")
            sb.append("//  * Decrypted Strings: ").append(deobfResult.decodedStrings.size).append(" encrypted strings decoded\n")
            if (deobfResult.sensitiveApis.isNotEmpty()) {
                val groupSummary = deobfResult.sensitiveApis.groupBy { it.category }
                    .map { "${it.key} [${it.value.size}]" }.joinToString(", ")
                sb.append("//  * Security Audit: Detected APIs -> ").append(groupSummary).append("\n")
            }
            sb.append("// --------------------------------------------------------------------\n")
        }
        sb.append("// ====================================================================\n\n")

        // Package
        if (dexClass.packageName.isNotEmpty()) {
            sb.append("package ").append(dexClass.packageName).append(";\n\n")
        }

        // Class Header
        sb.append(formatClassModifiers(dexClass.accessFlags))
        val className = if (deobfuscate && deobfResult != null) deobfResult.suggestedName else dexClass.simpleName
        sb.append("class ").append(className)

        val superClean = dexClass.superType?.let { typeInference.cleanType(it) }
        if (!superClean.isNullOrEmpty() && superClean != "Object") {
            sb.append(" extends ").append(superClean)
        }

        if (dexClass.interfaces.isNotEmpty()) {
            sb.append(" implements ").append(dexClass.interfaces.joinToString(", ") { typeInference.cleanType(it) })
        }

        sb.append(" {\n\n")

        // Fields
        for (field in dexClass.fields) {
            sb.append("    ").append(reconstructField(field, deobfResult)).append(";\n")
        }
        if (dexClass.fields.isNotEmpty()) sb.append("\n")

        // Methods
        for (method in dexClass.methods) {
            sb.append(reconstructMethod(dexClass, method, deobfResult)).append("\n\n")
        }

        sb.append("}\n")
        val finalCode = sb.toString()
        DecompilerCache.put(dexClass.type, deobfuscate, finalCode)
        return finalCode
    }

    private fun reconstructField(field: DexField, deobfResult: DeobfuscationResult?): String {
        val mods = formatFieldModifiers(field.accessFlags)
        val type = typeInference.cleanType(field.type)
        val name = deobfResult?.fieldAliases?.get(field.name) ?: field.name
        return "$mods$type $name"
    }

    private fun reconstructMethod(
        dexClass: DexClass,
        method: DexMethod,
        deobfResult: DeobfuscationResult?
    ): String {
        val sb = StringBuilder()
        val mods = formatMethodModifiers(method.accessFlags)
        val retType = typeInference.cleanType(method.proto.returnType)

        val methodName = if (method.isConstructor) {
            dexClass.simpleName
        } else {
            deobfResult?.methodAliases?.get(method.name) ?: method.name
        }

        // Security check for method
        if (deobfResult != null && method.codeItem != null) {
            val methodInsns = method.codeItem.instructions
            val methodSensitives = deobfResult.sensitiveApis.filter { sa ->
                methodInsns.any { it.offset == sa.instructionOffset }
            }
            if (methodSensitives.isNotEmpty()) {
                val cats = methodSensitives.map { it.category }.distinct().joinToString(", ")
                sb.append("    // [Security Alert] Invokes sensitive $cats API\n")
            }
        }

        sb.append("    ").append(mods)
        if (!method.isConstructor) {
            sb.append(retType).append(" ")
        }
        sb.append(methodName).append("(")

        val params = method.proto.parameters.mapIndexed { idx, pType ->
            "${typeInference.cleanType(pType)} p$idx"
        }.joinToString(", ")
        sb.append(params).append(")")

        val code = method.codeItem
        if (code == null || (method.accessFlags and 0x0500) != 0) { // abstract or native
            sb.append(";")
            return sb.toString()
        }

        sb.append(" {\n")

        // 1. Run Type Inference on registers
        val inferredVars = typeInference.inferVariables(dexClass, method, code)

        // 2. Build structured AST lines (Loops, Switch, Try-Catch, Conditionals)
        val structuredLines = astBuilder.buildStructuredCode(code, inferredVars, deobfResult)

        if (structuredLines.isEmpty()) {
            sb.append("        // Method body empty\n")
        } else {
            for (line in structuredLines) {
                sb.append("        ").append(line).append("\n")
            }
        }

        sb.append("    }")
        return sb.toString()
    }

    private fun formatClassModifiers(flags: Int): String {
        val list = mutableListOf<String>()
        if ((flags and 0x0001) != 0) list.add("public")
        if ((flags and 0x0400) != 0) list.add("abstract")
        if ((flags and 0x0010) != 0) list.add("final")
        return if (list.isEmpty()) "" else list.joinToString(" ") + " "
    }

    private fun formatFieldModifiers(flags: Int): String {
        val list = mutableListOf<String>()
        if ((flags and 0x0001) != 0) list.add("public")
        if ((flags and 0x0002) != 0) list.add("private")
        if ((flags and 0x0004) != 0) list.add("protected")
        if ((flags and 0x0008) != 0) list.add("static")
        if ((flags and 0x0010) != 0) list.add("final")
        return if (list.isEmpty()) "" else list.joinToString(" ") + " "
    }

    private fun formatMethodModifiers(flags: Int): String {
        val list = mutableListOf<String>()
        if ((flags and 0x0001) != 0) list.add("public")
        if ((flags and 0x0002) != 0) list.add("private")
        if ((flags and 0x0004) != 0) list.add("protected")
        if ((flags and 0x0008) != 0) list.add("static")
        if ((flags and 0x0010) != 0) list.add("final")
        if ((flags and 0x0020) != 0) list.add("synchronized")
        return if (list.isEmpty()) "" else list.joinToString(" ") + " "
    }
}
