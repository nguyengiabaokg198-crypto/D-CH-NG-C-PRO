package com.example.manifest

import com.example.apk.ComponentInfo
import com.example.apk.ComponentSummary
import com.example.apk.PermissionInfo
import com.example.apk.PermissionLevel
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedManifest(
    val xmlText: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val components: ComponentSummary
)

class BinaryXmlParser {

    companion object {
        private const val RES_XML_TYPE = 0x0003
        private const val RES_STRING_POOL_TYPE = 0x0001
        private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
        private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
        private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
        private const val RES_XML_START_ELEMENT_TYPE = 0x0102
        private const val RES_XML_END_ELEMENT_TYPE = 0x0103
        private const val RES_XML_CDATA_TYPE = 0x0104

        // Attribute types
        private const val TYPE_NULL = 0x00
        private const val TYPE_REFERENCE = 0x01
        private const val TYPE_ATTRIBUTE = 0x02
        private const val TYPE_STRING = 0x03
        private const val TYPE_FLOAT = 0x04
        private const val TYPE_DIMENSION = 0x05
        private const val TYPE_FRACTION = 0x06
        private const val TYPE_INT_DEC = 0x10
        private const val TYPE_INT_HEX = 0x11
        private const val TYPE_INT_BOOLEAN = 0x12
    }

    fun parse(bytes: ByteArray): DecodedManifest {
        if (bytes.size < 8) {
            return fallbackEmpty()
        }

        // If not binary XML, check if it's plain text XML
        val headerPrefix = if (bytes.size >= 50) String(bytes.take(50).toByteArray(), Charsets.UTF_8).trim() else String(bytes, Charsets.UTF_8).trim()
        if (headerPrefix.startsWith("<?xml") || headerPrefix.startsWith("<manifest") || headerPrefix.startsWith("<man")) {
            val text = String(bytes, Charsets.UTF_8)
            return parsePlainTextManifest(text)
        }

        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val rootType = buffer.short.toInt() and 0xFFFF
            if (rootType != RES_XML_TYPE) {
                return fallbackEmpty()
            }

            buffer.short // headerSize
            val rootChunkSize = buffer.int

            val stringPool = mutableListOf<String>()
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

            var packageName = ""
            var versionName = ""
            var versionCode = 0
            var minSdk = 21
            var targetSdk = 33

            val activities = mutableListOf<ComponentInfo>()
            val services = mutableListOf<ComponentInfo>()
            val receivers = mutableListOf<ComponentInfo>()
            val providers = mutableListOf<ComponentInfo>()
            val permissions = mutableListOf<PermissionInfo>()

            var currentTag = ""
            var currentComponentName = ""
            var currentComponentExported = false
            var currentComponentPermission: String? = null
            val currentIntentFilters = mutableListOf<String>()

            val namespaces = mutableMapOf<String, String>()
            var indentLevel = 0

            while (buffer.hasRemaining() && buffer.position() < rootChunkSize && buffer.position() < bytes.size - 4) {
                val chunkPos = buffer.position()
                val chunkType = buffer.short.toInt() and 0xFFFF
                val headerSize = buffer.short.toInt() and 0xFFFF
                val chunkSize = buffer.int

                if (chunkSize <= 0 || chunkPos + chunkSize > bytes.size) {
                    break
                }

                when (chunkType) {
                    RES_STRING_POOL_TYPE -> {
                        parseStringPool(buffer, chunkPos, stringPool)
                        buffer.position(chunkPos + chunkSize)
                    }

                    RES_XML_START_NAMESPACE_TYPE -> {
                        buffer.int // lineNumber
                        buffer.int // comment
                        val prefixIdx = buffer.int
                        val uriIdx = buffer.int
                        val prefix = getString(stringPool, prefixIdx)
                        val uri = getString(stringPool, uriIdx)
                        if (prefix.isNotEmpty()) {
                            namespaces[prefix] = uri
                        }
                        buffer.position(chunkPos + chunkSize)
                    }

                    RES_XML_END_NAMESPACE_TYPE -> {
                        buffer.position(chunkPos + chunkSize)
                    }

                    RES_XML_START_ELEMENT_TYPE -> {
                        buffer.int // lineNumber
                        buffer.int // comment
                        val nsIdx = buffer.int
                        val nameIdx = buffer.int
                        buffer.short // attributeStart
                        buffer.short // attributeSize
                        val attributeCount = buffer.short.toInt() and 0xFFFF
                        buffer.short // idIndex
                        buffer.short // classIndex
                        buffer.short // styleIndex

                        val tagName = getString(stringPool, nameIdx)
                        currentTag = tagName

                        val indent = "    ".repeat(indentLevel)
                        sb.append("$indent<$tagName")

                        if (tagName == "manifest") {
                            namespaces.forEach { (p, u) ->
                                sb.append(" xmlns:$p=\"$u\"")
                            }
                        }

                        var compName = ""
                        var compExported = false
                        var compPerm: String? = null

                        for (i in 0 until attributeCount) {
                            val attrNsIdx = buffer.int
                            val attrNameIdx = buffer.int
                            val attrValIdx = buffer.int
                            val attrType = (buffer.int shr 24) and 0xFF
                            val attrData = buffer.int

                            val attrName = getString(stringPool, attrNameIdx)
                            val attrVal = formatAttributeValue(stringPool, attrValIdx, attrType, attrData)

                            sb.append(" $attrName=\"$attrVal\"")

                            // Extract manifest metrics
                            if (tagName == "manifest") {
                                if (attrName == "package") packageName = attrVal
                                if (attrName.contains("versionName")) versionName = attrVal
                                if (attrName.contains("versionCode")) versionCode = attrVal.toIntOrNull() ?: 0
                            } else if (tagName == "uses-sdk") {
                                if (attrName.contains("minSdkVersion")) minSdk = attrVal.toIntOrNull() ?: minSdk
                                if (attrName.contains("targetSdkVersion")) targetSdk = attrVal.toIntOrNull() ?: targetSdk
                            } else if (tagName == "uses-permission" && attrName.contains("name")) {
                                val level = categorizePermission(attrVal)
                                permissions.add(PermissionInfo(attrVal, level, getPermissionDesc(attrVal)))
                            }

                            if (attrName.endsWith(":name") || attrName == "name") {
                                compName = attrVal
                            }
                            if (attrName.endsWith(":exported") || attrName == "exported") {
                                compExported = attrVal.toBoolean()
                            }
                            if (attrName.endsWith(":permission") || attrName == "permission") {
                                compPerm = attrVal
                            }
                        }

                        if (tagName in listOf("activity", "service", "receiver", "provider")) {
                            currentComponentName = compName
                            currentComponentExported = compExported
                            currentComponentPermission = compPerm
                            currentIntentFilters.clear()
                        } else if (tagName == "action") {
                            // Inside intent-filter
                            if (compName.isNotEmpty()) {
                                currentIntentFilters.add(compName)
                            }
                        }

                        sb.append(">\n")
                        indentLevel++
                        buffer.position(chunkPos + chunkSize)
                    }

                    RES_XML_END_ELEMENT_TYPE -> {
                        buffer.int // lineNumber
                        buffer.int // comment
                        val nsIdx = buffer.int
                        val nameIdx = buffer.int
                        val tagName = getString(stringPool, nameIdx)

                        indentLevel = (indentLevel - 1).coerceAtLeast(0)
                        val indent = "    ".repeat(indentLevel)
                        sb.append("$indent</$tagName>\n")

                        if (tagName in listOf("activity", "service", "receiver", "provider") && currentComponentName.isNotEmpty()) {
                            val comp = ComponentInfo(
                                name = currentComponentName,
                                type = tagName.replaceFirstChar { it.uppercase() },
                                isExported = currentComponentExported,
                                permission = currentComponentPermission,
                                intentFilters = currentIntentFilters.toList()
                            )
                            when (tagName) {
                                "activity" -> activities.add(comp)
                                "service" -> services.add(comp)
                                "receiver" -> receivers.add(comp)
                                "provider" -> providers.add(comp)
                            }
                            currentComponentName = ""
                        }

                        buffer.position(chunkPos + chunkSize)
                    }

                    else -> {
                        buffer.position(chunkPos + chunkSize)
                    }
                }
            }

            return DecodedManifest(
                xmlText = sb.toString(),
                packageName = if (packageName.isNotEmpty()) packageName else "com.example.app",
                versionName = if (versionName.isNotEmpty()) versionName else "1.0",
                versionCode = if (versionCode > 0) versionCode else 1,
                minSdk = minSdk,
                targetSdk = targetSdk,
                components = ComponentSummary(
                    activities = activities,
                    services = services,
                    receivers = receivers,
                    providers = providers,
                    permissions = permissions
                )
            )
        } catch (e: Exception) {
            return fallbackEmpty()
        }
    }

    private fun parseStringPool(buffer: ByteBuffer, startPos: Int, outStrings: MutableList<String>) {
        try {
            val headerSize = buffer.short.toInt() and 0xFFFF
            val stringCount = buffer.int
            val styleCount = buffer.int
            val flags = buffer.int
            val stringsStart = buffer.int
            val stylesStart = buffer.int

            val isUtf8 = (flags and (1 shl 8)) != 0
            val offsets = IntArray(stringCount)
            for (i in 0 until stringCount) {
                offsets[i] = buffer.int
            }

            val stringDataStart = startPos + stringsStart
            for (offset in offsets) {
                val strPos = stringDataStart + offset
                if (strPos >= buffer.capacity()) {
                    outStrings.add("")
                    continue
                }
                buffer.position(strPos)
                val str = if (isUtf8) readUtf8String(buffer) else readUtf16String(buffer)
                outStrings.add(str)
            }
        } catch (e: Exception) {
            // ignore string pool errors
        }
    }

    private fun readUtf8String(buffer: ByteBuffer): String {
        var len = buffer.get().toInt() and 0xFF
        if ((len and 0x80) != 0) {
            len = ((len and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        var byteLen = buffer.get().toInt() and 0xFF
        if ((byteLen and 0x80) != 0) {
            byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        val bytes = ByteArray(byteLen)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16String(buffer: ByteBuffer): String {
        var len = buffer.short.toInt() and 0xFFFF
        if ((len and 0x8000) != 0) {
            val high = len and 0x7FFF
            val low = buffer.short.toInt() and 0xFFFF
            len = (high shl 16) or low
        }
        val chars = CharArray(len)
        for (i in 0 until len) {
            chars[i] = buffer.char
        }
        return String(chars)
    }

    private fun getString(pool: List<String>, idx: Int): String {
        return if (idx in pool.indices) pool[idx] else ""
    }

    private fun formatAttributeValue(pool: List<String>, valIdx: Int, type: Int, data: Int): String {
        return when (type) {
            TYPE_STRING -> getString(pool, valIdx)
            TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
            TYPE_INT_HEX -> "0x" + Integer.toHexString(data)
            TYPE_INT_DEC -> data.toString()
            TYPE_REFERENCE -> "@0x" + Integer.toHexString(data)
            else -> if (valIdx in pool.indices) pool[valIdx] else data.toString()
        }
    }

    private fun categorizePermission(perm: String): PermissionLevel {
        val dangerous = listOf(
            "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION",
            "READ_CONTACTS", "WRITE_CONTACTS", "READ_CALENDAR", "WRITE_CALENDAR",
            "READ_PHONE_STATE", "CALL_PHONE", "READ_SMS", "RECEIVE_SMS",
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "POST_NOTIFICATIONS"
        )
        return when {
            dangerous.any { perm.endsWith(it) } -> PermissionLevel.DANGEROUS
            perm.contains("SIGNATURE") || perm.startsWith("android.permission.BIND_") -> PermissionLevel.SIGNATURE
            perm.startsWith("android.permission.") -> PermissionLevel.NORMAL
            else -> PermissionLevel.UNKNOWN
        }
    }

    private fun getPermissionDesc(perm: String): String {
        val last = perm.substringAfterLast('.')
        return when (last) {
            "INTERNET" -> "Truy cập mạng Internet"
            "ACCESS_NETWORK_STATE" -> "Xem thông tin kết nối mạng"
            "CAMERA" -> "Sử dụng máy ảnh thiết bị"
            "RECORD_AUDIO" -> "Ghi âm micrô"
            "ACCESS_FINE_LOCATION" -> "Vị trí chính xác GPS"
            "ACCESS_COARSE_LOCATION" -> "Vị trí mạng gần đúng"
            "READ_PHONE_STATE" -> "Đọc trạng thái cuộc gọi & thông tin SIM"
            "POST_NOTIFICATIONS" -> "Gửi thông báo đẩy"
            "VIBRATE" -> "Điều khiển rung thiết bị"
            "WAKE_LOCK" -> "Giữ thiết bị không ngủ"
            else -> "Quyền hệ thống: $last"
        }
    }

    private fun parsePlainTextManifest(text: String): DecodedManifest {
        val pkg = Regex("package=\"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: "com.example.app"
        val vName = Regex("android:versionName=\"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: "1.0"
        val vCode = Regex("android:versionCode=\"([^\"]+)\"").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val minSdk = Regex("android:minSdkVersion=\"([^\"]+)\"").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 21
        val targetSdk = Regex("android:targetSdkVersion=\"([^\"]+)\"").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 33

        val permMatches = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"").findAll(text)
        val perms = permMatches.map {
            val name = it.groupValues[1]
            PermissionInfo(name, categorizePermission(name), getPermissionDesc(name))
        }.toList()

        val actMatches = Regex("<activity[^>]*android:name=\"([^\"]+)\"").findAll(text)
        val activities = actMatches.map {
            ComponentInfo(it.groupValues[1], "Activity", true, null, emptyList())
        }.toList()

        return DecodedManifest(
            xmlText = text,
            packageName = pkg,
            versionName = vName,
            versionCode = vCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            components = ComponentSummary(
                activities = activities,
                permissions = perms
            )
        )
    }

    private fun fallbackEmpty(): DecodedManifest {
        return DecodedManifest(
            xmlText = "<!-- AndroidManifest.xml decode failed or not found -->",
            packageName = "com.unknown.package",
            versionName = "1.0",
            versionCode = 1,
            minSdk = 21,
            targetSdk = 33,
            components = ComponentSummary()
        )
    }
}
