package com.example.dex

data class DexHeader(
    val magic: String,
    val checksum: Long,
    val signature: String,
    val fileSize: Int,
    val headerSize: Int,
    val endianTag: Int,
    val linkSize: Int,
    val linkOff: Int,
    val mapOff: Int,
    val stringIdsSize: Int,
    val stringIdsOff: Int,
    val typeIdsSize: Int,
    val typeIdsOff: Int,
    val protoIdsSize: Int,
    val protoIdsOff: Int,
    val fieldIdsSize: Int,
    val fieldIdsOff: Int,
    val methodIdsSize: Int,
    val methodIdsOff: Int,
    val classDefsSize: Int,
    val classDefsOff: Int,
    val dataSize: Int,
    val dataOff: Int
)

data class DexProto(
    val shorty: String,
    val returnType: String,
    val parameters: List<String>
)

data class DexField(
    val classType: String,
    val name: String,
    val type: String,
    val accessFlags: Int
) {
    val isStatic: Boolean get() = (accessFlags and 0x0008) != 0
    val isPublic: Boolean get() = (accessFlags and 0x0001) != 0
    val isPrivate: Boolean get() = (accessFlags and 0x0002) != 0
    val isProtected: Boolean get() = (accessFlags and 0x0004) != 0
    val isFinal: Boolean get() = (accessFlags and 0x0010) != 0
}

data class DexMethod(
    val classType: String,
    val name: String,
    val proto: DexProto,
    val accessFlags: Int,
    val codeItem: DexCodeItem?
) {
    val isPublic: Boolean get() = (accessFlags and 0x0001) != 0
    val isPrivate: Boolean get() = (accessFlags and 0x0002) != 0
    val isProtected: Boolean get() = (accessFlags and 0x0004) != 0
    val isStatic: Boolean get() = (accessFlags and 0x0008) != 0
    val isFinal: Boolean get() = (accessFlags and 0x0010) != 0
    val isConstructor: Boolean get() = (accessFlags and 0x10000) != 0 || name == "<init>"
    val isStaticConstructor: Boolean get() = (accessFlags and 0x20000) != 0 || name == "<clinit>"
}

data class DexTryCatch(
    val startAddr: Int,
    val insnCount: Int,
    val handlerOffset: Int,
    val exceptionType: String?
)

data class DexInstruction(
    val offset: Int,
    val opcode: Int,
    val opcodeName: String,
    val registers: List<Int> = emptyList(),
    val literal: Long? = null,
    val stringVal: String? = null,
    val targetOffset: Int? = null,
    val targetType: String? = null,
    val targetField: String? = null,
    val targetMethod: String? = null,
    val switchKeys: List<Int>? = null,
    val switchTargets: List<Int>? = null,
    val arrayElements: List<Any>? = null
)

data class DexCodeItem(
    val registersSize: Int,
    val insSize: Int,
    val outsSize: Int,
    val triesSize: Int,
    val debugInfoOff: Int,
    val instructions: List<DexInstruction>,
    val tries: List<DexTryCatch> = emptyList()
)

data class DexClass(
    val type: String,
    val accessFlags: Int,
    val superType: String?,
    val interfaces: List<String>,
    val sourceFile: String?,
    val fields: List<DexField>,
    val methods: List<DexMethod>,
    val annotations: List<String> = emptyList()
) {
    val simpleName: String
        get() = type.trim('L', ';').substringAfterLast('/').substringAfterLast('$')

    val packageName: String
        get() {
            val cleaned = type.trim('L', ';')
            return if (cleaned.contains('/')) cleaned.substringBeforeLast('/').replace('/', '.') else ""
        }

    val isInterface: Boolean get() = (accessFlags and 0x0200) != 0
    val isAbstract: Boolean get() = (accessFlags and 0x0400) != 0
    val isFinal: Boolean get() = (accessFlags and 0x0010) != 0
    val isPublic: Boolean get() = (accessFlags and 0x0001) != 0
}

data class DexFile(
    val name: String,
    val header: DexHeader,
    val strings: List<String>,
    val types: List<String>,
    val protos: List<DexProto>,
    val fields: List<DexField>,
    val methods: List<DexMethod>,
    val classes: List<DexClass>
)
