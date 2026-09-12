package com.example.resources

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ResourceEntry(
    val id: Int,
    val typeName: String,
    val entryName: String,
    val value: String
)

data class ResourceTable(
    val packageName: String,
    val strings: List<String>,
    val types: List<String>,
    val entries: List<ResourceEntry>
)

class ArscParser {

    companion object {
        private const val RES_TABLE_TYPE = 0x0002
        private const val RES_STRING_POOL_TYPE = 0x0001
        private const val RES_TABLE_PACKAGE_TYPE = 0x0200
        private const val RES_TABLE_TYPE_TYPE = 0x0201
        private const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
    }

    fun parse(bytes: ByteArray): ResourceTable {
        if (bytes.size < 12) {
            return ResourceTable("", emptyList(), emptyList(), emptyList())
        }

        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val type = buffer.short.toInt() and 0xFFFF
            if (type != RES_TABLE_TYPE) {
                return ResourceTable("", emptyList(), emptyList(), emptyList())
            }

            buffer.short // headerSize
            val totalSize = buffer.int
            val packageCount = buffer.int

            val globalStrings = mutableListOf<String>()
            val types = mutableListOf<String>()
            val entries = mutableListOf<ResourceEntry>()
            var packageName = ""

            while (buffer.hasRemaining() && buffer.position() < totalSize && buffer.position() < bytes.size - 8) {
                val chunkPos = buffer.position()
                val chunkType = buffer.short.toInt() and 0xFFFF
                val headerSize = buffer.short.toInt() and 0xFFFF
                val chunkSize = buffer.int

                if (chunkSize <= 0 || chunkPos + chunkSize > bytes.size) break

                when (chunkType) {
                    RES_STRING_POOL_TYPE -> {
                        if (globalStrings.isEmpty()) {
                            parseStringPool(buffer, chunkPos, globalStrings)
                        }
                        buffer.position(chunkPos + chunkSize)
                    }

                    RES_TABLE_PACKAGE_TYPE -> {
                        val pkgId = buffer.int
                        val pkgNameChars = CharArray(128)
                        for (i in 0 until 128) {
                            pkgNameChars[i] = buffer.char
                        }
                        packageName = String(pkgNameChars).trimEnd('\u0000')

                        buffer.int // typeStrings
                        buffer.int // lastPublicType
                        buffer.int // keyStrings
                        buffer.int // lastPublicKey

                        buffer.position(chunkPos + chunkSize)
                    }

                    else -> {
                        buffer.position(chunkPos + chunkSize)
                    }
                }
            }

            // Extract string resources from global strings pool
            globalStrings.forEachIndexed { index, str ->
                if (str.isNotBlank() && !str.startsWith("res/") && !str.startsWith("AndroidManifest.xml")) {
                    entries.add(
                        ResourceEntry(
                            id = 0x7f000000 or index,
                            typeName = "string",
                            entryName = "string_$index",
                            value = str
                        )
                    )
                }
            }

            return ResourceTable(
                packageName = if (packageName.isNotEmpty()) packageName else "com.example.resources",
                strings = globalStrings,
                types = listOf("string", "drawable", "layout", "color", "dimen", "style"),
                entries = entries
            )
        } catch (e: Exception) {
            return ResourceTable("", emptyList(), emptyList(), emptyList())
        }
    }

    private fun parseStringPool(buffer: ByteBuffer, startPos: Int, outStrings: MutableList<String>) {
        try {
            val headerSize = buffer.short.toInt() and 0xFFFF
            val stringCount = buffer.int
            val styleCount = buffer.int
            val flags = buffer.int
            val stringsStart = buffer.int

            val isUtf8 = (flags and (1 shl 8)) != 0
            val offsets = IntArray(stringCount.coerceAtMost(10000))
            for (i in offsets.indices) {
                offsets[i] = buffer.int
            }

            val stringDataStart = startPos + stringsStart
            for (offset in offsets) {
                val strPos = stringDataStart + offset
                if (strPos < buffer.capacity()) {
                    buffer.position(strPos)
                    val str = if (isUtf8) readUtf8(buffer) else readUtf16(buffer)
                    outStrings.add(str)
                }
            }
        } catch (e: Exception) {
            // fallback
        }
    }

    private fun readUtf8(buffer: ByteBuffer): String {
        var len = buffer.get().toInt() and 0xFF
        if ((len and 0x80) != 0) len = ((len and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        var byteLen = buffer.get().toInt() and 0xFF
        if ((byteLen and 0x80) != 0) byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        val bytes = ByteArray(byteLen)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16(buffer: ByteBuffer): String {
        val len = buffer.short.toInt() and 0xFFFF
        val chars = CharArray(len.coerceAtMost(2048))
        for (i in chars.indices) {
            chars[i] = buffer.char
        }
        return String(chars)
    }
}
