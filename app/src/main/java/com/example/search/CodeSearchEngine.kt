package com.example.search

import com.example.apk.ParsedApkProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SearchCategory {
    ALL,
    CLASS,
    METHOD,
    FIELD,
    STRING_LITERAL,
    URL_DOMAIN,
    PERMISSION,
    SECRET_KEY,
    API_CALL
}

data class SearchResult(
    val category: SearchCategory,
    val title: String,
    val subtitle: String,
    val location: String,
    val matchedText: String
)

class CodeSearchEngine {

    suspend fun search(
        project: ParsedApkProject,
        query: String,
        useRegex: Boolean = false,
        category: SearchCategory = SearchCategory.ALL
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<SearchResult>()
        val pattern = try {
            if (useRegex) Regex(query, RegexOption.IGNORE_CASE)
            else Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        // 1. Search Permissions
        if (category == SearchCategory.ALL || category == SearchCategory.PERMISSION) {
            for (perm in project.manifest.components.permissions) {
                if (pattern.containsMatchIn(perm.name)) {
                    results.add(
                        SearchResult(
                            category = SearchCategory.PERMISSION,
                            title = perm.name.substringAfterLast('.'),
                            subtitle = perm.name,
                            location = "AndroidManifest.xml",
                            matchedText = perm.name
                        )
                    )
                }
            }
        }

        // 2. Search DEX Classes, Methods, Fields, Instructions
        for (dex in project.dexFiles) {
            for (clazz in dex.classes) {
                // Class Name
                if (category == SearchCategory.ALL || category == SearchCategory.CLASS) {
                    if (pattern.containsMatchIn(clazz.type) || pattern.containsMatchIn(clazz.simpleName)) {
                        results.add(
                            SearchResult(
                                category = SearchCategory.CLASS,
                                title = clazz.simpleName,
                                subtitle = clazz.type,
                                location = "${dex.name} -> ${clazz.type}",
                                matchedText = clazz.type
                            )
                        )
                    }
                }

                // Fields
                if (category == SearchCategory.ALL || category == SearchCategory.FIELD) {
                    for (field in clazz.fields) {
                        if (pattern.containsMatchIn(field.name) || pattern.containsMatchIn(field.type)) {
                            results.add(
                                SearchResult(
                                    category = SearchCategory.FIELD,
                                    title = field.name,
                                    subtitle = "${field.type} in ${clazz.simpleName}",
                                    location = "${clazz.simpleName}.${field.name}",
                                    matchedText = "${field.name}: ${field.type}"
                                )
                            )
                        }
                    }
                }

                // Methods & Invocations
                for (method in clazz.methods) {
                    if (category == SearchCategory.ALL || category == SearchCategory.METHOD) {
                        if (pattern.containsMatchIn(method.name)) {
                            results.add(
                                SearchResult(
                                    category = SearchCategory.METHOD,
                                    title = method.name,
                                    subtitle = "${clazz.simpleName}->${method.name}${method.proto.returnType}",
                                    location = "${clazz.simpleName}.${method.name}()",
                                    matchedText = method.name
                                )
                            )
                        }
                    }

                    // Inside method instructions
                    val code = method.codeItem
                    if (code != null) {
                        for (ins in code.instructions) {
                            if (ins.stringVal != null && pattern.containsMatchIn(ins.stringVal)) {
                                val isUrl = ins.stringVal.startsWith("http://") || ins.stringVal.startsWith("https://")
                                val isSecret = ins.stringVal.contains("key", true) || ins.stringVal.contains("token", true) || ins.stringVal.contains("secret", true)

                                val cat = when {
                                    isUrl -> SearchCategory.URL_DOMAIN
                                    isSecret -> SearchCategory.SECRET_KEY
                                    else -> SearchCategory.STRING_LITERAL
                                }

                                if (category == SearchCategory.ALL || category == cat) {
                                    results.add(
                                        SearchResult(
                                            category = cat,
                                            title = "\"${ins.stringVal}\"",
                                            subtitle = "in ${clazz.simpleName}.${method.name}()",
                                            location = "${clazz.simpleName} -> ${method.name}",
                                            matchedText = ins.stringVal
                                        )
                                    )
                                }
                            }

                            if (ins.targetMethod != null && (category == SearchCategory.ALL || category == SearchCategory.API_CALL)) {
                                if (pattern.containsMatchIn(ins.targetMethod)) {
                                    results.add(
                                        SearchResult(
                                            category = SearchCategory.API_CALL,
                                            title = ins.targetMethod.substringAfter("->").substringBefore("("),
                                            subtitle = "Called in ${clazz.simpleName}.${method.name}()",
                                            location = ins.targetMethod,
                                            matchedText = ins.targetMethod
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Search Resources / Global strings
        if (category == SearchCategory.ALL || category == SearchCategory.STRING_LITERAL) {
            for (res in project.resourceTable.entries) {
                if (pattern.containsMatchIn(res.value) || pattern.containsMatchIn(res.entryName)) {
                    results.add(
                        SearchResult(
                            category = SearchCategory.STRING_LITERAL,
                            title = res.value,
                            subtitle = "@string/${res.entryName}",
                            location = "resources.arsc",
                            matchedText = res.value
                        )
                    )
                }
            }
        }

        return@withContext results.take(300) // Cap to maintain snappy UI performance
    }
}
