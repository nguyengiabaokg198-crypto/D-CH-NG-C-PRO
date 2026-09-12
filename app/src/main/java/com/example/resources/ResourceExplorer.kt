package com.example.resources

import com.example.apk.ArchiveEntryInfo

data class ResourceNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val children: MutableList<ResourceNode> = mutableListOf(),
    val extension: String = if (isDirectory) "" else fullPath.substringAfterLast('.', "")
)

enum class ResourceCategory {
    ALL,
    DRAWABLES,
    LAYOUTS,
    VALUES,
    ASSETS,
    DEX,
    NATIVE_LIBS,
    OTHER
}

class ResourceExplorer {

    fun buildTree(entries: List<ArchiveEntryInfo>): ResourceNode {
        val root = ResourceNode(name = "root", fullPath = "", isDirectory = true, size = 0)

        for (entry in entries) {
            val parts = entry.path.trim('/').split('/')
            var current = root

            for (i in parts.indices) {
                val part = parts[i]
                val isLast = i == parts.size - 1
                val isDir = if (isLast) entry.isDirectory else true
                val existing = current.children.find { it.name == part }

                if (existing != null) {
                    current = existing
                } else {
                    val fullPath = parts.take(i + 1).joinToString("/")
                    val node = ResourceNode(
                        name = part,
                        fullPath = fullPath,
                        isDirectory = isDir,
                        size = if (isLast) entry.size else 0
                    )
                    current.children.add(node)
                    current = node
                }
            }
        }

        sortTree(root)
        return root
    }

    private fun sortTree(node: ResourceNode) {
        node.children.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        for (child in node.children) {
            if (child.isDirectory) {
                sortTree(child)
            }
        }
    }

    fun categorize(fullPath: String): ResourceCategory {
        val p = fullPath.lowercase()
        return when {
            p.startsWith("res/drawable") || p.startsWith("res/mipmap") || p.endsWith(".png") || p.endsWith(".webp") || p.endsWith(".jpg") -> ResourceCategory.DRAWABLES
            p.startsWith("res/layout") -> ResourceCategory.LAYOUTS
            p.startsWith("res/values") || p.endsWith(".arsc") -> ResourceCategory.VALUES
            p.startsWith("assets/") -> ResourceCategory.ASSETS
            p.endsWith(".dex") -> ResourceCategory.DEX
            p.startsWith("lib/") || p.endsWith(".so") -> ResourceCategory.NATIVE_LIBS
            else -> ResourceCategory.OTHER
        }
    }
}
