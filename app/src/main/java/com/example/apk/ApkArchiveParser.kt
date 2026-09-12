package com.example.apk

import com.example.dex.DexFile
import com.example.dex.DexParser
import com.example.manifest.BinaryXmlParser
import com.example.manifest.DecodedManifest
import com.example.resources.ArscParser
import com.example.resources.ResourceTable
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ParsedApkProject(
    val info: ApkInfo,
    val manifest: DecodedManifest,
    val dexFiles: List<DexFile>,
    val resourceTable: ResourceTable,
    val rawEntries: Map<String, ByteArray>
)

class ApkArchiveParser {

    private val manifestParser = BinaryXmlParser()
    private val dexParser = DexParser()
    private val arscParser = ArscParser()

    fun parse(bytes: ByteArray, fileName: String): ParsedApkProject {
        val sha256 = calculateSha256(bytes)
        val fileSize = bytes.size.toLong()

        // If directly a DEX file
        if (fileName.endsWith(".dex") || (bytes.size >= 8 && String(bytes.take(4).toByteArray()) == "dex\n")) {
            val dexFile = dexParser.parse(bytes, fileName)
            val info = ApkInfo(
                fileName = fileName,
                fileSize = fileSize,
                sha256 = sha256,
                packageName = dexFile.classes.firstOrNull()?.packageName ?: "standalone.dex",
                versionName = "1.0",
                versionCode = 1,
                minSdk = 21,
                targetSdk = 33,
                dexCount = 1,
                classesCount = dexFile.classes.size,
                methodsCount = dexFile.methods.size,
                stringsCount = dexFile.strings.size,
                entries = listOf(ArchiveEntryInfo(fileName, fileSize, fileSize, false))
            )
            return ParsedApkProject(
                info = info,
                manifest = manifestParser.parse(ByteArray(0)),
                dexFiles = listOf(dexFile),
                resourceTable = ResourceTable("", emptyList(), emptyList(), emptyList()),
                rawEntries = mapOf(fileName to bytes)
            )
        }

        // Parse as ZIP archive (APK, AAB, JAR, ZIP)
        val entries = mutableListOf<ArchiveEntryInfo>()
        val rawEntries = mutableMapOf<String, ByteArray>()
        val dexFiles = mutableListOf<DexFile>()
        val nativeLibs = mutableListOf<String>()
        val signatures = mutableListOf<SignatureInfo>()

        var manifestBytes: ByteArray? = null
        var arscBytes: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val size = if (entry.size >= 0) entry.size else 0L
                val cSize = if (entry.compressedSize >= 0) entry.compressedSize else 0L
                val isDir = entry.isDirectory

                entries.add(ArchiveEntryInfo(name, size, cSize, isDir))

                if (!isDir) {
                    val entryBytes = zis.readBytes()

                    // Store essential files in rawEntries (limit total cache to avoid OOM)
                    if (name.startsWith("AndroidManifest.xml") ||
                        name.endsWith(".dex") ||
                        name == "resources.arsc" ||
                        name.startsWith("META-INF/") ||
                        name.endsWith(".txt") ||
                        name.endsWith(".json") ||
                        name.endsWith(".xml") ||
                        name.endsWith(".png") ||
                        name.endsWith(".jpg") ||
                        name.endsWith(".webp")
                    ) {
                        rawEntries[name] = entryBytes
                    }

                    if (name == "AndroidManifest.xml") {
                        manifestBytes = entryBytes
                    } else if (name == "resources.arsc") {
                        arscBytes = entryBytes
                    } else if (name.endsWith(".dex")) {
                        try {
                            val df = dexParser.parse(entryBytes, name)
                            dexFiles.add(df)
                        } catch (e: Exception) {
                            // Dex parsing error boundary
                        }
                    } else if (name.startsWith("lib/") && name.endsWith(".so")) {
                        val abi = name.substringAfter("lib/").substringBefore("/")
                        if (!nativeLibs.contains(abi)) {
                            nativeLibs.add(abi)
                        }
                    } else if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        extractCertificate(entryBytes)?.let { signatures.add(it) }
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Decode Manifest
        val decodedManifest = if (manifestBytes != null) {
            manifestParser.parse(manifestBytes)
        } else {
            manifestParser.parse(ByteArray(0))
        }

        // Parse resources.arsc
        val resourceTable = if (arscBytes != null) {
            arscParser.parse(arscBytes)
        } else {
            ResourceTable("", emptyList(), emptyList(), emptyList())
        }

        val totalClasses = dexFiles.sumOf { it.classes.size }
        val totalMethods = dexFiles.sumOf { it.methods.size }
        val totalStrings = dexFiles.sumOf { it.strings.size }

        val apkInfo = ApkInfo(
            fileName = fileName,
            fileSize = fileSize,
            sha256 = sha256,
            packageName = decodedManifest.packageName,
            versionName = decodedManifest.versionName,
            versionCode = decodedManifest.versionCode,
            minSdk = decodedManifest.minSdk,
            targetSdk = decodedManifest.targetSdk,
            dexCount = dexFiles.size,
            classesCount = totalClasses,
            methodsCount = totalMethods,
            stringsCount = totalStrings,
            nativeLibs = nativeLibs,
            signatures = signatures,
            components = decodedManifest.components,
            entries = entries
        )

        return ParsedApkProject(
            info = apkInfo,
            manifest = decodedManifest,
            dexFiles = dexFiles,
            resourceTable = resourceTable,
            rawEntries = rawEntries
        )
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun extractCertificate(bytes: ByteArray): SignatureInfo? {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
            if (cert != null) {
                val sha1 = MessageDigest.getInstance("SHA-1").digest(cert.encoded).joinToString(":") { "%02X".format(it) }
                val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }
                SignatureInfo(
                    subject = cert.subjectDN.name,
                    issuer = cert.issuerDN.name,
                    algorithm = cert.sigAlgName,
                    sha1 = sha1,
                    sha256 = sha256
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
