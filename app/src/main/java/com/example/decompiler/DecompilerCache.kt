package com.example.decompiler

import androidx.collection.LruCache

object DecompilerCache {
    // Cache up to 100 decompiled classes in RAM to prevent redundant CPU work and control heap memory
    private val classCodeCache = LruCache<String, String>(100)

    fun get(classType: String, deobfuscated: Boolean): String? {
        val key = "$classType:deobf=$deobfuscated"
        return classCodeCache[key]
    }

    fun put(classType: String, deobfuscated: Boolean, code: String) {
        val key = "$classType:deobf=$deobfuscated"
        classCodeCache.put(key, code)
    }

    fun clear() {
        classCodeCache.evictAll()
    }
}
