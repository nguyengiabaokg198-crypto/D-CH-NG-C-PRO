package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.apk.ApkArchiveParser
import com.example.decompiler.JavaReconstructor
import com.example.dex.DexParser
import com.example.export.ProjectExporter
import com.example.sample.SampleApkProvider
import com.example.search.CodeSearchEngine
import com.example.smali.SmaliGenerator
import com.example.terminal.EmbeddedTerminalEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `verify app name resource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("APK Decompiler Pro", appName)
    }

    @Test
    fun `verify sample apk generation and parsing`() {
        val sampleApkBytes = SampleApkProvider.createSampleApkBytes()
        assertTrue(sampleApkBytes.isNotEmpty())

        val parser = ApkArchiveParser()
        val project = parser.parse(sampleApkBytes, "DemoSecurityTarget.apk")

        assertEquals("com.target.securitytest", project.info.packageName)
        assertEquals("2.1.0", project.info.versionName)
        assertEquals(102, project.info.versionCode)
        assertEquals(24, project.info.minSdk)
        assertEquals(34, project.info.targetSdk)
        assertTrue(project.dexFiles.isNotEmpty())

        val firstDex = project.dexFiles.first()
        assertTrue(firstDex.classes.isNotEmpty())
        val mainClass = firstDex.classes.find { it.simpleName == "MainActivity" }
        assertNotNull(mainClass)

        // Test Smali generation
        val smaliGen = SmaliGenerator()
        val smali = smaliGen.generate(mainClass!!)
        assertTrue("Expected class definition in smali: $smali", smali.contains(".class") && smali.contains("MainActivity"))
        assertTrue("Expected onCreate method in smali: $smali", smali.contains("onCreate"))

        // Test Java reconstruction
        val javaRecon = JavaReconstructor()
        val javaCode = javaRecon.reconstruct(mainClass)
        assertTrue(javaCode.contains("class MainActivity"))
        assertTrue(javaCode.contains("onCreate("))

        // Test Deobfuscation mode
        val deobfCode = javaRecon.reconstruct(mainClass, deobfuscate = true)
        assertTrue(deobfCode.contains("class MainActivity"))
        assertTrue(deobfCode.contains("De-obfuscation Mode: ACTIVE"))

        // Test Search engine
        val searchEngine = CodeSearchEngine()
        val searchResults = runBlocking {
            searchEngine.search(project, "INTERNET")
        }
        assertTrue(searchResults.isNotEmpty())

        // Test Embedded Terminal Engine
        val terminal = EmbeddedTerminalEngine(searchEngine, smaliGen, javaRecon)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = ProjectExporter(context)

        runBlocking {
            val helpLines = terminal.execute("help", project, exporter)
            assertTrue(helpLines.any { it.text.contains("help") })

            val infoLines = terminal.execute("info", project, exporter)
            assertTrue(infoLines.any { it.text.contains("com.target.securitytest") })

            val smaliLines = terminal.execute("smali MainActivity", project, exporter)
            assertTrue(smaliLines.any { it.text.contains(".class") })

            val javaLines = terminal.execute("java MainActivity", project, exporter)
            assertTrue(javaLines.any { it.text.contains("class MainActivity") })

            val deobfLines = terminal.execute("deobf MainActivity", project, exporter)
            assertTrue(deobfLines.any { it.text.contains("class MainActivity") })

            val cfgLines = terminal.execute("cfg MainActivity onCreate", project, exporter)
            assertTrue(cfgLines.any { it.text.contains("CFG") || it.text.contains("Block") })

            val astLines = terminal.execute("ast MainActivity", project, exporter)
            assertTrue(astLines.any { it.text.contains("AST") || it.text.contains("Method:") })

            val deobfReportLines = terminal.execute("deobf-report", project, exporter)
            assertTrue(deobfReportLines.any { it.text.contains("BÁO CÁO") || it.text.contains("TỔNG HỢP") })

            // Test string decryptor heuristics
            val deobf = javaRecon.getDeobfuscator()
            val decHex = deobf.tryDecryptString("68747470733a2f2f6170692e6578616d706c652e636f6d")
            assertNotNull(decHex)
            assertEquals("https://api.example.com", decHex?.decrypted)
            assertEquals("Hex", decHex?.algorithm)

            val decRev = deobf.tryDecryptString("moc.elpmaxe.ipa//:sptth")
            assertNotNull(decRev)
            assertEquals("https://api.example.com", decRev?.decrypted)
            assertEquals("Reverse", decRev?.algorithm)
        }
    }
}
