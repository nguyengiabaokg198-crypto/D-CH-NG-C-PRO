package com.example.apk

enum class PermissionLevel {
    NORMAL,
    DANGEROUS,
    SIGNATURE,
    UNKNOWN
}

data class PermissionInfo(
    val name: String,
    val level: PermissionLevel,
    val description: String
)

data class ComponentInfo(
    val name: String,
    val type: String, // Activity, Service, Receiver, Provider
    val isExported: Boolean,
    val permission: String?,
    val intentFilters: List<String>
)

data class ComponentSummary(
    val activities: List<ComponentInfo> = emptyList(),
    val services: List<ComponentInfo> = emptyList(),
    val receivers: List<ComponentInfo> = emptyList(),
    val providers: List<ComponentInfo> = emptyList(),
    val permissions: List<PermissionInfo> = emptyList()
)

data class SignatureInfo(
    val subject: String,
    val issuer: String,
    val algorithm: String,
    val sha1: String,
    val sha256: String
)

data class ArchiveEntryInfo(
    val path: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean
)

data class ApkInfo(
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int = 0,
    val dexCount: Int = 0,
    val classesCount: Int = 0,
    val methodsCount: Int = 0,
    val stringsCount: Int = 0,
    val nativeLibs: List<String> = emptyList(),
    val signatures: List<SignatureInfo> = emptyList(),
    val components: ComponentSummary = ComponentSummary(),
    val entries: List<ArchiveEntryInfo> = emptyList()
)
