package com.example.sample

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleApkProvider {

    fun createSampleApkBytes(): ByteArray {
        val dexBytes = createSampleDexBytes()
        val manifestXml = createSampleManifestXml()
        val sampleConfigJson = """
            {
              "app_name": "DemoTargetApp",
              "api_endpoint": "https://api.demotarget.internal/v1/auth",
              "debug_token": "secret_demo_api_key_897123",
              "features": ["analytics", "crypto", "telemetry"]
            }
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // AndroidManifest.xml (text format in sample, parsed smoothly by BinaryXmlParser fallback)
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(manifestXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // classes.dex
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(dexBytes)
            zos.closeEntry()

            // assets
            zos.putNextEntry(ZipEntry("assets/sample_config.json"))
            zos.write(sampleConfigJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // res/values/strings.xml
            val stringsXml = """
                <resources>
                    <string name="welcome_message">Xin chào từ ứng dụng kiểm thử bảo mật</string>
                    <string name="secret_hint">Không lưu trữ mật khẩu tĩnh trong mã nguồn</string>
                </resources>
            """.trimIndent()
            zos.putNextEntry(ZipEntry("res/values/strings.xml"))
            zos.write(stringsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // dummy native lib
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libnative-crypto.so"))
            zos.write(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0x02, 0x01, 0x01, 0x00))
            zos.closeEntry()
        }

        return baos.toByteArray()
    }

    private fun createSampleManifestXml(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.target.securitytest"
                android:versionCode="102"
                android:versionName="2.1.0">

                <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="34" />

                <uses-permission android:name="android.permission.INTERNET" />
                <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
                <uses-permission android:name="android.permission.CAMERA" />
                <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

                <application
                    android:allowBackup="true"
                    android:label="Demo Target"
                    android:debuggable="true">

                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>

                    <activity
                        android:name=".AuthLoginActivity"
                        android:exported="false" />

                    <service
                        android:name=".BackgroundSyncService"
                        android:exported="false" />

                    <receiver
                        android:name=".PushNotificationReceiver"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="com.google.android.c2dm.intent.RECEIVE" />
                        </intent-filter>
                    </receiver>
                </application>
            </manifest>
        """.trimIndent()
    }

    fun createSampleDexBytes(): ByteArray {
        val buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

        // Strings to embed
        val strList = listOf(
            "Hello, World from DEX!",
            "https://api.demotarget.internal/auth",
            "Lcom/target/securitytest/MainActivity;",
            "Ljava/lang/Object;",
            "Ljava/lang/String;",
            "MainActivity.java",
            "V",
            "VI",
            "<init>",
            "onCreate",
            "getSecretKey",
            "super_secret_token_abcdef12345"
        )

        // Magic
        buf.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        buf.position(0x70) // End of header

        // Write String data pool and compute offsets
        val strOffsets = IntArray(strList.size)
        val strDataStart = 0x200
        buf.position(strDataStart)
        for (i in strList.indices) {
            strOffsets[i] = buf.position()
            val s = strList[i]
            buf.put(s.length.toByte()) // uleb128 length
            buf.put(s.toByteArray(Charsets.UTF_8))
            buf.put(0.toByte()) // null terminator
        }

        // Write String IDs table at 0x70
        buf.position(0x70)
        val stringIdsOff = 0x70
        for (off in strOffsets) {
            buf.putInt(off)
        }

        // Type IDs table
        val typeIdsOff = buf.position()
        val typeIdxMap = listOf(2, 3, 4, 6) // MainActivity, Object, String, V
        for (strIdx in typeIdxMap) {
            buf.putInt(strIdx)
        }
        val typeIdsSize = typeIdxMap.size

        // Proto IDs table
        val protoIdsOff = buf.position()
        // Proto 0: V ()
        buf.putInt(6) // shorty "V"
        buf.putInt(3) // returnType "V"
        buf.putInt(0) // parameters_off = 0
        val protoIdsSize = 1

        // Field IDs: None
        val fieldIdsOff = buf.position()
        val fieldIdsSize = 0

        // Method IDs table
        val methodIdsOff = buf.position()
        // Method 0: MainActivity-><init>()V
        buf.putShort(0.toShort()) // classIdx = 0 (MainActivity)
        buf.putShort(0.toShort()) // protoIdx = 0 (V)
        buf.putInt(8) // nameIdx = "<init>"

        // Method 1: MainActivity->onCreate()V
        buf.putShort(0.toShort()) // classIdx = 0
        buf.putShort(0.toShort()) // protoIdx = 0
        buf.putInt(9) // nameIdx = "onCreate"
        val methodIdsSize = 2

        // Class Defs table
        val classDefsOff = buf.position()
        val classDataOff = 0x600

        buf.putInt(0) // class_idx = 0 (MainActivity)
        buf.putInt(0x0001) // access_flags = public
        buf.putInt(1) // superclass_idx = 1 (Object)
        buf.putInt(0) // interfaces_off = 0
        buf.putInt(5) // source_file_idx = "MainActivity.java"
        buf.putInt(0) // annotations_off = 0
        buf.putInt(classDataOff) // class_data_off
        buf.putInt(0) // static_values_off = 0
        val classDefsSize = 1

        // Class Data at 0x600
        buf.position(classDataOff)
        buf.put(0.toByte()) // static_fields_size
        buf.put(0.toByte()) // instance_fields_size
        buf.put(2.toByte()) // direct_methods_size

        // Method 0: <init>
        buf.put(0.toByte()) // method_idx_diff = 0
        buf.put(0x01.toByte()) // access_flags = public
        val codeOff0 = 0x700
        writeUleb128(buf, codeOff0)

        // Method 1: onCreate
        buf.put(1.toByte()) // method_idx_diff = 1
        buf.put(0x01.toByte()) // access_flags = public
        val codeOff1 = 0x780
        writeUleb128(buf, codeOff1)

        buf.put(0.toByte()) // virtual_methods_size

        // Code item 0: <init>
        buf.position(codeOff0)
        buf.putShort(1.toShort()) // registers_size = 1
        buf.putShort(1.toShort()) // ins_size = 1
        buf.putShort(0.toShort()) // outs_size = 0
        buf.putShort(0.toShort()) // tries_size = 0
        buf.putInt(0) // debug_info_off = 0
        buf.putInt(1) // insns_size = 1
        buf.putShort(0x000e.toShort()) // return-void

        // Code item 1: onCreate
        buf.position(codeOff1)
        buf.putShort(2.toShort()) // registers_size = 2
        buf.putShort(1.toShort()) // ins_size = 1
        buf.putShort(0.toShort()) // outs_size = 0
        buf.putShort(0.toShort()) // tries_size = 0
        buf.putInt(0) // debug_info_off = 0
        buf.putInt(3) // insns_size = 3 (const-string v0, string@0; return-void)
        // const-string v0, string@0 (0x1a, vA=0, idx=0)
        buf.putShort(0x001a.toShort())
        buf.putShort(0.toShort())
        // return-void
        buf.putShort(0x000e.toShort())

        val totalFileSize = 0x850

        // Fill in Header at 0x20
        buf.position(0x20)
        buf.putInt(totalFileSize) // file_size
        buf.putInt(0x70) // header_size
        buf.putInt(0x12345678) // endian_tag
        buf.putInt(0) // link_size
        buf.putInt(0) // link_off
        buf.putInt(0) // map_off
        buf.putInt(strList.size) // string_ids_size
        buf.putInt(stringIdsOff) // string_ids_off
        buf.putInt(typeIdsSize) // type_ids_size
        buf.putInt(typeIdsOff) // type_ids_off
        buf.putInt(protoIdsSize) // proto_ids_size
        buf.putInt(protoIdsOff) // proto_ids_off
        buf.putInt(fieldIdsSize) // field_ids_size
        buf.putInt(fieldIdsOff) // field_ids_off
        buf.putInt(methodIdsSize) // method_ids_size
        buf.putInt(methodIdsOff) // method_ids_off
        buf.putInt(classDefsSize) // class_defs_size
        buf.putInt(classDefsOff) // class_defs_off
        buf.putInt(totalFileSize - 0x200) // data_size
        buf.putInt(0x200) // data_off

        val finalBytes = ByteArray(totalFileSize)
        System.arraycopy(buf.array(), 0, finalBytes, 0, totalFileSize)

        // Compute SHA-1 signature (bytes 32 to end)
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(finalBytes, 32, totalFileSize - 32)
        val sig = sha1.digest()
        System.arraycopy(sig, 0, finalBytes, 12, 20)

        // Compute Adler32 checksum (bytes 12 to end)
        val adler = Adler32()
        adler.update(finalBytes, 12, totalFileSize - 12)
        val checksum = adler.value.toInt()
        val cBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(checksum)
        System.arraycopy(cBuf.array(), 0, finalBytes, 8, 4)

        return finalBytes
    }

    private fun writeUleb128(buffer: ByteBuffer, value: Int) {
        var v = value
        while (v > 0x7F) {
            buffer.put(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        buffer.put((v and 0x7F).toByte())
    }
}
