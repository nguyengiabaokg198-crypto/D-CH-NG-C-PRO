package com.example.dex

import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexParser {

    fun parse(bytes: ByteArray, name: String = "classes.dex"): DexFile {
        if (bytes.size < 0x70) {
            throw IllegalArgumentException("File too small to be a valid DEX ($name)")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Validate Header
        val magicBytes = ByteArray(8)
        buffer.get(magicBytes)
        val magic = String(magicBytes)
        if (!magic.startsWith("dex\n")) {
            throw IllegalArgumentException("Invalid DEX magic header: $magic")
        }

        val checksum = buffer.int.toLong() and 0xFFFFFFFFL
        val signatureBytes = ByteArray(20)
        buffer.get(signatureBytes)
        val signature = signatureBytes.joinToString("") { "%02x".format(it) }

        val fileSize = buffer.int
        val headerSize = buffer.int
        val endianTag = buffer.int
        val linkSize = buffer.int
        val linkOff = buffer.int
        val mapOff = buffer.int
        val stringIdsSize = buffer.int
        val stringIdsOff = buffer.int
        val typeIdsSize = buffer.int
        val typeIdsOff = buffer.int
        val protoIdsSize = buffer.int
        val protoIdsOff = buffer.int
        val fieldIdsSize = buffer.int
        val fieldIdsOff = buffer.int
        val methodIdsSize = buffer.int
        val methodIdsOff = buffer.int
        val classDefsSize = buffer.int
        val classDefsOff = buffer.int
        val dataSize = buffer.int
        val dataOff = buffer.int

        val header = DexHeader(
            magic = magic,
            checksum = checksum,
            signature = signature,
            fileSize = fileSize,
            headerSize = headerSize,
            endianTag = endianTag,
            linkSize = linkSize,
            linkOff = linkOff,
            mapOff = mapOff,
            stringIdsSize = stringIdsSize,
            stringIdsOff = stringIdsOff,
            typeIdsSize = typeIdsSize,
            typeIdsOff = typeIdsOff,
            protoIdsSize = protoIdsSize,
            protoIdsOff = protoIdsOff,
            fieldIdsSize = fieldIdsSize,
            fieldIdsOff = fieldIdsOff,
            methodIdsSize = methodIdsSize,
            methodIdsOff = methodIdsOff,
            classDefsSize = classDefsSize,
            classDefsOff = classDefsOff,
            dataSize = dataSize,
            dataOff = dataOff
        )

        // 2. Parse String IDs
        val strings = ArrayList<String>(stringIdsSize)
        buffer.position(stringIdsOff)
        val stringOffsets = IntArray(stringIdsSize)
        for (i in 0 until stringIdsSize) {
            stringOffsets[i] = buffer.int
        }

        for (off in stringOffsets) {
            if (off in 0 until bytes.size) {
                buffer.position(off)
                readUleb128(buffer) // utf16_size
                val str = readMutf8String(buffer)
                strings.add(str)
            } else {
                strings.add("")
            }
        }

        // 3. Parse Type IDs
        val types = ArrayList<String>(typeIdsSize)
        buffer.position(typeIdsOff)
        for (i in 0 until typeIdsSize) {
            val descriptorIdx = buffer.int
            val typeStr = if (descriptorIdx in strings.indices) strings[descriptorIdx] else "Type_$descriptorIdx"
            types.add(typeStr)
        }

        // 4. Parse Proto IDs
        val protos = ArrayList<DexProto>(protoIdsSize)
        buffer.position(protoIdsOff)
        for (i in 0 until protoIdsSize) {
            val shortyIdx = buffer.int
            val returnTypeIdx = buffer.int
            val paramsOff = buffer.int

            val shorty = if (shortyIdx in strings.indices) strings[shortyIdx] else ""
            val returnType = if (returnTypeIdx in types.indices) types[returnTypeIdx] else "V"

            val params = mutableListOf<String>()
            if (paramsOff > 0 && paramsOff < bytes.size) {
                val savedPos = buffer.position()
                buffer.position(paramsOff)
                val paramCount = buffer.int
                for (p in 0 until paramCount) {
                    val pTypeIdx = buffer.short.toInt() and 0xFFFF
                    val pType = if (pTypeIdx in types.indices) types[pTypeIdx] else "Object"
                    params.add(pType)
                }
                buffer.position(savedPos)
            }

            protos.add(DexProto(shorty, returnType, params))
        }

        // 5. Parse Field IDs
        val fields = ArrayList<DexField>(fieldIdsSize)
        buffer.position(fieldIdsOff)
        for (i in 0 until fieldIdsSize) {
            val classIdx = buffer.short.toInt() and 0xFFFF
            val typeIdx = buffer.short.toInt() and 0xFFFF
            val nameIdx = buffer.int

            val classType = if (classIdx in types.indices) types[classIdx] else "Unknown"
            val type = if (typeIdx in types.indices) types[typeIdx] else "Unknown"
            val nameStr = if (nameIdx in strings.indices) strings[nameIdx] else "field_$i"

            fields.add(DexField(classType, nameStr, type, 0))
        }

        // 6. Parse Method IDs
        val methods = ArrayList<DexMethod>(methodIdsSize)
        buffer.position(methodIdsOff)
        for (i in 0 until methodIdsSize) {
            val classIdx = buffer.short.toInt() and 0xFFFF
            val protoIdx = buffer.short.toInt() and 0xFFFF
            val nameIdx = buffer.int

            val classType = if (classIdx in types.indices) types[classIdx] else "Unknown"
            val proto = if (protoIdx in protos.indices) protos[protoIdx] else DexProto("V", "V", emptyList())
            val nameStr = if (nameIdx in strings.indices) strings[nameIdx] else "method_$i"

            methods.add(DexMethod(classType, nameStr, proto, 0, null))
        }

        val disassembler = DalvikDisassembler(strings, types, fields, methods)

        // 7. Parse Class Definitions
        val classes = ArrayList<DexClass>(classDefsSize)
        buffer.position(classDefsOff)

        for (i in 0 until classDefsSize) {
            val classIdx = buffer.int
            val accessFlags = buffer.int
            val superclassIdx = buffer.int
            val interfacesOff = buffer.int
            val sourceFileIdx = buffer.int
            val annotationsOff = buffer.int
            val classDataOff = buffer.int
            val staticValuesOff = buffer.int

            val classType = if (classIdx in types.indices) types[classIdx] else "LClass$i;"
            val superType = if (superclassIdx in types.indices) types[superclassIdx] else null
            val sourceFile = if (sourceFileIdx in strings.indices) strings[sourceFileIdx] else null

            val interfaces = mutableListOf<String>()
            if (interfacesOff > 0 && interfacesOff < bytes.size) {
                val savedPos = buffer.position()
                buffer.position(interfacesOff)
                val ifaceCount = buffer.int
                for (j in 0 until ifaceCount) {
                    val ifaceTypeIdx = buffer.short.toInt() and 0xFFFF
                    if (ifaceTypeIdx in types.indices) {
                        interfaces.add(types[ifaceTypeIdx])
                    }
                }
                buffer.position(savedPos)
            }

            val classFields = mutableListOf<DexField>()
            val classMethods = mutableListOf<DexMethod>()

            if (classDataOff > 0 && classDataOff < bytes.size) {
                val savedPos = buffer.position()
                buffer.position(classDataOff)

                val staticFieldsSize = readUleb128(buffer)
                val instanceFieldsSize = readUleb128(buffer)
                val directMethodsSize = readUleb128(buffer)
                val virtualMethodsSize = readUleb128(buffer)

                // Static fields
                var fieldIdx = 0
                for (f in 0 until staticFieldsSize) {
                    fieldIdx += readUleb128(buffer)
                    val fAccess = readUleb128(buffer)
                    if (fieldIdx in fields.indices) {
                        classFields.add(fields[fieldIdx].copy(accessFlags = fAccess))
                    }
                }

                // Instance fields
                fieldIdx = 0
                for (f in 0 until instanceFieldsSize) {
                    fieldIdx += readUleb128(buffer)
                    val fAccess = readUleb128(buffer)
                    if (fieldIdx in fields.indices) {
                        classFields.add(fields[fieldIdx].copy(accessFlags = fAccess))
                    }
                }

                // Direct methods
                var methodIdx = 0
                for (m in 0 until directMethodsSize) {
                    methodIdx += readUleb128(buffer)
                    val mAccess = readUleb128(buffer)
                    val codeOff = readUleb128(buffer)
                    val codeItem = if (codeOff > 0) parseCodeItem(buffer, bytes, codeOff, disassembler) else null
                    if (methodIdx in methods.indices) {
                        classMethods.add(methods[methodIdx].copy(accessFlags = mAccess, codeItem = codeItem))
                    }
                }

                // Virtual methods
                methodIdx = 0
                for (m in 0 until virtualMethodsSize) {
                    methodIdx += readUleb128(buffer)
                    val mAccess = readUleb128(buffer)
                    val codeOff = readUleb128(buffer)
                    val codeItem = if (codeOff > 0) parseCodeItem(buffer, bytes, codeOff, disassembler) else null
                    if (methodIdx in methods.indices) {
                        classMethods.add(methods[methodIdx].copy(accessFlags = mAccess, codeItem = codeItem))
                    }
                }

                buffer.position(savedPos)
            }

            classes.add(
                DexClass(
                    type = classType,
                    accessFlags = accessFlags,
                    superType = superType,
                    interfaces = interfaces,
                    sourceFile = sourceFile,
                    fields = classFields,
                    methods = classMethods
                )
            )
        }

        return DexFile(
            name = name,
            header = header,
            strings = strings,
            types = types,
            protos = protos,
            fields = fields,
            methods = methods,
            classes = classes
        )
    }

    private fun parseCodeItem(buffer: ByteBuffer, bytes: ByteArray, codeOff: Int, disassembler: DalvikDisassembler): DexCodeItem? {
        if (codeOff <= 0 || codeOff + 16 > bytes.size) return null
        val savedPos = buffer.position()
        try {
            buffer.position(codeOff)
            val registersSize = buffer.short.toInt() and 0xFFFF
            val insSize = buffer.short.toInt() and 0xFFFF
            val outsSize = buffer.short.toInt() and 0xFFFF
            val triesSize = buffer.short.toInt() and 0xFFFF
            val debugInfoOff = buffer.int
            val insnsSize = buffer.int

            if (insnsSize <= 0 || codeOff + 16 + insnsSize * 2 > bytes.size) {
                return DexCodeItem(registersSize, insSize, outsSize, triesSize, debugInfoOff, emptyList())
            }

            val insns = ShortArray(insnsSize)
            for (i in 0 until insnsSize) {
                insns[i] = buffer.short
            }

            val instructions = disassembler.disassemble(insns)

            val tries = mutableListOf<DexTryCatch>()
            if (triesSize > 0 && buffer.position() + (if ((insnsSize % 2) != 0) 2 else 0) + triesSize * 8 <= bytes.size) {
                if ((insnsSize % 2) != 0) {
                    buffer.short // 2-byte alignment padding
                }
                for (t in 0 until triesSize) {
                    val startAddr = buffer.int
                    val insnCount = buffer.short.toInt() and 0xFFFF
                    val handlerOff = buffer.short.toInt() and 0xFFFF
                    tries.add(DexTryCatch(startAddr, insnCount, handlerOff, "Exception"))
                }
            }

            return DexCodeItem(registersSize, insSize, outsSize, triesSize, debugInfoOff, instructions, tries)
        } catch (e: Exception) {
            return null
        } finally {
            buffer.position(savedPos)
        }
    }

    private fun readUleb128(buffer: ByteBuffer): Int {
        var result = 0
        var shift = 0
        while (buffer.hasRemaining()) {
            val byte = buffer.get().toInt()
            result = result or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readMutf8String(buffer: ByteBuffer): String {
        val bytes = ArrayList<Byte>()
        while (buffer.hasRemaining()) {
            val b = buffer.get()
            if (b.toInt() == 0) break
            bytes.add(b)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
