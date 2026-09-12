package com.example.decompiler

import android.util.Base64
import com.example.dex.DexClass
import com.example.dex.DexField
import com.example.dex.DexInstruction
import com.example.dex.DexMethod

data class DecryptedString(
    val original: String,
    val decrypted: String,
    val algorithm: String // "Base64", "Hex", "XOR", "Reverse"
)

data class SensitiveApiCall(
    val category: String, // "Crypto", "Network", "Storage", "Device ID", "Exec", "DCL", "Reflection"
    val methodCalled: String,
    val instructionOffset: Int
)

data class DeobfuscationResult(
    val className: String,
    val suggestedName: String,
    val detectedLibrary: String?,
    val methodAliases: Map<String, String>,
    val fieldAliases: Map<String, String>,
    val decodedStrings: Map<String, DecryptedString>,
    val sensitiveApis: List<SensitiveApiCall>
)

class Deobfuscator {

    fun isObfuscatedName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name == "<init>" || name == "<clinit>") return false
        // Single or double char names, ProGuard short random characters, or names containing solely dollar signs/digits
        return name.length <= 2 ||
                name.matches(Regex("^[a-z]{1,3}[0-9]{0,2}$")) ||
                name.matches(Regex("^[_$]+[0-9]*$")) ||
                name.matches(Regex("^[A-Z]{1,2}[0-9]{0,2}$"))
    }

    fun analyzeClass(dexClass: DexClass): DeobfuscationResult {
        val methodAliases = mutableMapOf<String, String>()
        val fieldAliases = mutableMapOf<String, String>()
        val decodedStrings = mutableMapOf<String, DecryptedString>()
        val sensitiveApis = mutableListOf<SensitiveApiCall>()

        // 1. Detect Third-Party Library Fingerprints
        val detectedLibrary = fingerprintLibrary(dexClass)

        // 2. Analyze class name & inheritance
        val rawSimple = dexClass.simpleName
        val suggestedClassName = if (isObfuscatedName(rawSimple)) {
            inferClassName(dexClass, detectedLibrary)
        } else {
            rawSimple
        }

        // 3. Scan fields for meaningful types & usages
        for (f in dexClass.fields) {
            if (isObfuscatedName(f.name)) {
                val inferredFieldName = inferFieldName(f)
                fieldAliases[f.name] = inferredFieldName
            }
        }

        // 4. Scan methods: API call graph, control-flow hints, string decryptors
        for (m in dexClass.methods) {
            val code = m.codeItem
            val insns = code?.instructions ?: emptyList()

            // Analyze invocations for Sensitive APIs & semantic naming
            val invokedApis = mutableListOf<String>()
            for (ins in insns) {
                if (ins.opcodeName.startsWith("invoke") && ins.targetMethod != null) {
                    val target = ins.targetMethod
                    invokedApis.add(target)

                    // Classify sensitive APIs
                    val sensitive = classifySensitiveApi(target, ins.offset)
                    if (sensitive != null) {
                        sensitiveApis.add(sensitive)
                    }
                }

                // Scan and decrypt obfuscated strings
                ins.stringVal?.let { rawStr ->
                    if (!decodedStrings.containsKey(rawStr)) {
                        val dec = tryDecryptString(rawStr)
                        if (dec != null) {
                            decodedStrings[rawStr] = dec
                        }
                    }
                }
            }

            // Semantic method renaming
            if (isObfuscatedName(m.name)) {
                val semanticName = inferMethodName(m, invokedApis, insns)
                methodAliases[m.name] = semanticName
            }
        }

        return DeobfuscationResult(
            className = rawSimple,
            suggestedName = suggestedClassName,
            detectedLibrary = detectedLibrary,
            methodAliases = methodAliases,
            fieldAliases = fieldAliases,
            decodedStrings = decodedStrings,
            sensitiveApis = sensitiveApis
        )
    }

    /**
     * Identifies common libraries even under heavy ProGuard / R8 renaming
     */
    private fun fingerprintLibrary(dexClass: DexClass): String? {
        val superType = dexClass.superType ?: ""
        val interfaces = dexClass.interfaces.joinToString(";")
        val methods = dexClass.methods.map { "${it.name}:${it.proto.shorty}" }

        return when {
            dexClass.type.contains("okhttp3") || interfaces.contains("okhttp3/Interceptor") || interfaces.contains("okhttp3/Call") -> "OkHttp 4.x Client Component"
            dexClass.type.contains("retrofit2") || dexClass.annotations.any { it.contains("retrofit") } -> "Retrofit 2 REST Client"
            dexClass.type.contains("google/gson") || methods.any { it.contains("fromJson") || it.contains("toJson") } -> "Google Gson Parser"
            dexClass.type.contains("firebase") || dexClass.type.contains("google/android/gms") -> "Google Play Services / Firebase SDK"
            dexClass.type.contains("androidx/room") || superType.contains("RoomDatabase") -> "AndroidX Room Persistence Database"
            dexClass.type.contains("kotlinx/coroutines") -> "Kotlin Coroutines Dispatcher"
            dexClass.type.contains("coil") || dexClass.type.contains("glide") -> "Coil/Glide Image Loader"
            else -> null
        }
    }

    private fun inferClassName(dexClass: DexClass, detectedLibrary: String?): String {
        val raw = dexClass.simpleName
        val superType = dexClass.superType?.substringAfterLast('/')?.trimEnd(';') ?: ""

        if (detectedLibrary != null) {
            val libShort = detectedLibrary.substringBefore(' ')
            return "${libShort}_$raw"
        }

        return when {
            superType.contains("Activity") -> "Activity_$raw"
            superType.contains("Application") -> "AppApplication_$raw"
            superType.contains("Service") -> "BackgroundService_$raw"
            superType.contains("BroadcastReceiver") || superType.contains("Receiver") -> "BroadcastReceiver_$raw"
            superType.contains("ContentProvider") || superType.contains("Provider") -> "ContentProvider_$raw"
            superType.contains("Fragment") -> "FragmentView_$raw"
            superType.contains("Adapter") || superType.contains("RecyclerView") -> "ItemAdapter_$raw"
            superType.contains("ViewModel") -> "DataViewModel_$raw"
            superType.contains("AsyncTask") -> "AsyncWorkerTask_$raw"
            dexClass.interfaces.any { it.contains("OnClickListener") } -> "ClickListener_$raw"
            dexClass.interfaces.any { it.contains("Runnable") } -> "WorkerThread_$raw"
            dexClass.interfaces.any { it.contains("Callback") } -> "ActionCallback_$raw"
            dexClass.fields.all { it.isStatic && it.isFinal } && dexClass.fields.size > 3 -> "ConstantsRegistry_$raw"
            else -> "Class_$raw"
        }
    }

    private fun inferFieldName(field: DexField): String {
        val raw = field.name
        val type = field.type.substringAfterLast('/').trimEnd(';')
        return when {
            type == "String" -> if (field.isStatic && field.isFinal) "CONST_STR_$raw" else "str_$raw"
            type == "I" || type == "int" -> if (field.isStatic && field.isFinal) "CONST_INT_$raw" else "num_$raw"
            type == "Z" || type == "boolean" -> "flag_$raw"
            type.contains("Context") -> "mContext_$raw"
            type.contains("View") -> "mView_$raw"
            type.contains("TextView") -> "mTv_$raw"
            type.contains("Button") -> "mBtn_$raw"
            type.contains("ImageView") -> "mIv_$raw"
            type.contains("SharedPreferences") -> "mPrefs_$raw"
            type.contains("Database") || type.contains("SQLite") -> "mDb_$raw"
            type.contains("List") || type.contains("ArrayList") -> "mList_$raw"
            type.contains("Map") || type.contains("HashMap") -> "mMap_$raw"
            type.contains("Cipher") || type.contains("Key") -> "mCryptoKey_$raw"
            else -> "field_$raw"
        }
    }

    private fun inferMethodName(
        method: DexMethod,
        invokedApis: List<String>,
        insns: List<DexInstruction>
    ): String {
        val raw = method.name
        val ret = method.proto.returnType.substringAfterLast('/').trimEnd(';')

        // Check invoked APIs in priority order
        for (api in invokedApis) {
            when {
                api.contains("Cipher->doFinal") || api.contains("Cipher->getInstance") -> return "crypto_decrypt_$raw"
                api.contains("MessageDigest->digest") -> return "crypto_hash_$raw"
                api.contains("HttpURLConnection") || api.contains("okhttp3") || api.contains("retrofit") -> return "network_sendRequest_$raw"
                api.contains("startActivity") -> return "ui_launchActivity_$raw"
                api.contains("SharedPreferences\$Editor->put") || api.contains("SharedPreferences->get") -> return "storage_managePrefs_$raw"
                api.contains("SQLiteDatabase->rawQuery") || api.contains("SQLiteDatabase->insert") -> return "db_executeSql_$raw"
                api.contains("Runtime->exec") || api.contains("ProcessBuilder->start") -> return "system_execCommand_$raw"
                api.contains("TelephonyManager->getDeviceId") || api.contains("getImei") -> return "device_readImei_$raw"
                api.contains("LocationManager->getLastKnownLocation") -> return "location_getLastKnown_$raw"
                api.contains("DexClassLoader") || api.contains("PathClassLoader") -> return "dcl_loadDynamicDex_$raw"
                api.contains("Method->invoke") || api.contains("Class->forName") -> return "reflect_invokeMethod_$raw"
            }
        }

        // Check string constants used inside the method
        for (ins in insns) {
            val str = ins.stringVal ?: continue
            when {
                str.startsWith("http://") || str.startsWith("https://") -> return "api_callEndpoint_$raw"
                str.contains("SELECT ") || str.contains("INSERT ") || str.contains("UPDATE ") -> return "db_query_$raw"
                str.contains("AES") || str.contains("RSA") || str.contains("DES") || str.contains("SHA-") -> return "crypto_cipherInit_$raw"
                str.contains("android.permission.") -> return "perm_checkSecurity_$raw"
            }
        }

        // Fallback based on return type and params
        return when {
            ret == "Z" || ret == "boolean" -> "checkCondition_$raw"
            ret == "String" -> "getStringVal_$raw"
            ret == "I" || ret == "int" -> "calcIntVal_$raw"
            method.proto.parameters.any { it.contains("View") } -> "onViewAction_$raw"
            method.proto.parameters.any { it.contains("Context") } -> "withContext_$raw"
            else -> "sub_$raw"
        }
    }

    private fun classifySensitiveApi(targetMethod: String, offset: Int): SensitiveApiCall? {
        return when {
            targetMethod.contains("javax/crypto/Cipher") || targetMethod.contains("java/security/MessageDigest") ->
                SensitiveApiCall("Crypto", targetMethod, offset)
            targetMethod.contains("HttpURLConnection") || targetMethod.contains("okhttp3") || targetMethod.contains("Socket") ->
                SensitiveApiCall("Network", targetMethod, offset)
            targetMethod.contains("SharedPreferences") || targetMethod.contains("SQLiteDatabase") ->
                SensitiveApiCall("Storage", targetMethod, offset)
            targetMethod.contains("TelephonyManager") || targetMethod.contains("getDeviceId") || targetMethod.contains("getImei") ->
                SensitiveApiCall("Device ID", targetMethod, offset)
            targetMethod.contains("java/lang/Runtime->exec") || targetMethod.contains("ProcessBuilder") ->
                SensitiveApiCall("Exec", targetMethod, offset)
            targetMethod.contains("dalvik/system/DexClassLoader") || targetMethod.contains("PathClassLoader") ->
                SensitiveApiCall("DCL", targetMethod, offset)
            targetMethod.contains("java/lang/reflect/Method->invoke") || targetMethod.contains("Class->forName") ->
                SensitiveApiCall("Reflection", targetMethod, offset)
            targetMethod.contains("LocationManager") || targetMethod.contains("FusedLocationProvider") ->
                SensitiveApiCall("Location", targetMethod, offset)
            else -> null
        }
    }

    /**
     * Multi-heuristic String Decryptor:
     * - Base64 (RFC 4648 standard and URL safe)
     * - Hex byte arrays
     * - Reversed strings
     * - Single-byte XOR brute force for high-entropy strings
     */
    fun tryDecryptString(str: String): DecryptedString? {
        if (str.length < 4 || str.length > 500) return null

        // 1. Try Base64 Decoding
        if (isBase64(str)) {
            try {
                val decodedBytes = Base64.decode(str, Base64.DEFAULT)
                val decodedText = String(decodedBytes, Charsets.UTF_8)
                if (isPrintableAscii(decodedText) && decodedText.length >= 3 && decodedText != str) {
                    return DecryptedString(str, decodedText, "Base64")
                }
            } catch (e: Exception) {
                // Not valid base64
            }
        }

        // 2. Try Hex-encoded String (e.g. "68747470733a2f2f...")
        if (str.length % 2 == 0 && str.matches(Regex("^[0-9a-fA-F]{6,}$"))) {
            try {
                val bytes = ByteArray(str.length / 2)
                for (i in bytes.indices) {
                    val byteStr = str.substring(i * 2, i * 2 + 2)
                    bytes[i] = byteStr.toInt(16).toByte()
                }
                val hexDecoded = String(bytes, Charsets.UTF_8)
                if (isPrintableAscii(hexDecoded) && hexDecoded.length >= 3) {
                    return DecryptedString(str, hexDecoded, "Hex")
                }
            } catch (e: Exception) {
                // Not valid hex
            }
        }

        // 3. Try Reversed String detection (e.g. "moc.elgoog.api" -> "api.google.com")
        if (str.contains('.') || str.startsWith(":/") || str.startsWith("moc.")) {
            val reversed = str.reversed()
            if (reversed.startsWith("http://") || reversed.startsWith("https://") || reversed.startsWith("content://") || reversed.startsWith("android.")) {
                return DecryptedString(str, reversed, "Reverse")
            }
        }

        // 4. Try Single-Byte XOR Decryption (Common in malware and packers for strings with non-ASCII or specific XOR masks)
        if (str.any { it.code > 127 || it.code < 32 }) {
            val rawBytes = str.toByteArray(Charsets.ISO_8859_1)
            for (key in 1..255) {
                val xorBytes = ByteArray(rawBytes.size) { (rawBytes[it].toInt() xor key).toByte() }
                val cand = String(xorBytes, Charsets.UTF_8)
                if (isPrintableAscii(cand) && (cand.contains("http") || cand.contains("api") || cand.contains("key") || cand.contains("android") || cand.contains("com."))) {
                    return DecryptedString(str, cand, "XOR(0x${Integer.toHexString(key).padStart(2, '0')})")
                }
            }
        }

        return null
    }

    private fun isBase64(str: String): Boolean {
        if (str.length % 4 != 0) return false
        return str.matches(Regex("^[A-Za-z0-9+/=]+$"))
    }

    private fun isPrintableAscii(s: String): Boolean {
        if (s.isEmpty()) return false
        return s.all { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
    }
}
