package com.example.export

import android.content.Context
import com.example.apk.ParsedApkProject
import com.example.decompiler.JavaReconstructor
import com.example.smali.SmaliGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectExporter(private val context: Context) {

    private val smaliGenerator = SmaliGenerator()
    private val javaReconstructor = JavaReconstructor()

    suspend fun exportEverything(project: ParsedApkProject): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(exportDir, "${project.info.packageName}_decompiled.zip")
        if (zipFile.exists()) zipFile.delete()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. AndroidManifest.xml
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(project.manifest.xmlText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Smali directory
            for (dex in project.dexFiles) {
                for (clazz in dex.classes) {
                    val smaliPath = "smali/" + clazz.type.trim('L', ';') + ".smali"
                    val smaliCode = smaliGenerator.generate(clazz)
                    zos.putNextEntry(ZipEntry(smaliPath))
                    zos.write(smaliCode.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }

            // 3. Java-like source directory
            for (dex in project.dexFiles) {
                for (clazz in dex.classes) {
                    val javaPath = "java/" + clazz.type.trim('L', ';') + ".java"
                    val javaCode = javaReconstructor.reconstruct(clazz)
                    zos.putNextEntry(ZipEntry(javaPath))
                    zos.write(javaCode.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }

            // 4. Analysis Report
            val reportText = buildAnalysisReport(project)
            zos.putNextEntry(ZipEntry("analysis_report.txt"))
            zos.write(reportText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        zipFile
    }

    suspend fun exportReportOnly(project: ParsedApkProject): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val reportFile = File(exportDir, "${project.info.packageName}_report.txt")
        reportFile.writeText(buildAnalysisReport(project))
        reportFile
    }

    private fun buildAnalysisReport(project: ParsedApkProject): String {
        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("         APK DECOMPILER PRO - ANALYSIS REPORT       \n")
        sb.append("====================================================\n\n")
        sb.append("File:             ").append(project.info.fileName).append("\n")
        sb.append("Package:          ").append(project.info.packageName).append("\n")
        sb.append("Version Name:     ").append(project.info.versionName).append("\n")
        sb.append("Version Code:     ").append(project.info.versionCode).append("\n")
        sb.append("Min SDK:          ").append(project.info.minSdk).append("\n")
        sb.append("Target SDK:       ").append(project.info.targetSdk).append("\n")
        sb.append("File Size:        ").append(formatBytes(project.info.fileSize)).append("\n")
        sb.append("SHA-256:          ").append(project.info.sha256).append("\n\n")

        sb.append("--- DEX & CODE STATISTICS ---\n")
        sb.append("DEX Files:        ").append(project.info.dexCount).append("\n")
        sb.append("Total Classes:    ").append(project.info.classesCount).append("\n")
        sb.append("Total Methods:    ").append(project.info.methodsCount).append("\n")
        sb.append("Total Strings:    ").append(project.info.stringsCount).append("\n")
        sb.append("Native ABIs:      ").append(project.info.nativeLibs.joinToString(", ")).append("\n\n")

        sb.append("--- COMPONENTS ---\n")
        sb.append("Activities (").append(project.manifest.components.activities.size).append("):\n")
        project.manifest.components.activities.forEach { sb.append("  * ").append(it.name).append(if (it.isExported) " [EXPORTED]" else "").append("\n") }

        sb.append("\nServices (").append(project.manifest.components.services.size).append("):\n")
        project.manifest.components.services.forEach { sb.append("  * ").append(it.name).append("\n") }

        sb.append("\nReceivers (").append(project.manifest.components.receivers.size).append("):\n")
        project.manifest.components.receivers.forEach { sb.append("  * ").append(it.name).append("\n") }

        sb.append("\nProviders (").append(project.manifest.components.providers.size).append("):\n")
        project.manifest.components.providers.forEach { sb.append("  * ").append(it.name).append("\n") }

        sb.append("\n--- PERMISSIONS (").append(project.manifest.components.permissions.size).append(") ---\n")
        project.manifest.components.permissions.forEach {
            sb.append("  * ").append(it.name).append(" [").append(it.level).append("] - ").append(it.description).append("\n")
        }

        return sb.toString()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.2f MB".format(mb)
    }
}
