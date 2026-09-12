package com.example.terminal

import com.example.apk.ParsedApkProject
import com.example.decompiler.JavaReconstructor
import com.example.export.ProjectExporter
import com.example.search.CodeSearchEngine
import com.example.smali.SmaliGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT
)

enum class TerminalLineType {
    PROMPT,
    OUTPUT,
    SUCCESS,
    WARNING,
    ERROR,
    CYAN_INFO
}

class EmbeddedTerminalEngine(
    private val searchEngine: CodeSearchEngine,
    private val smaliGenerator: SmaliGenerator,
    private val javaReconstructor: JavaReconstructor
) {

    private val history = mutableListOf<String>()
    var historyIndex = -1

    suspend fun execute(
        commandLine: String,
        project: ParsedApkProject?,
        exporter: ProjectExporter?
    ): List<TerminalLine> = withContext(Dispatchers.Default) {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        history.add(trimmed)
        historyIndex = history.size

        val parts = trimmed.split(Regex("\\s+"))
        val cmd = parts[0].lowercase()
        val args = parts.drop(1)

        val output = mutableListOf<TerminalLine>()
        output.add(TerminalLine("apkdecompiler@android:~# $trimmed", TerminalLineType.PROMPT))

        when (cmd) {
            "help", "?" -> {
                output.add(TerminalLine("=== APK Decompiler Pro Terminal Commands ===", TerminalLineType.CYAN_INFO))
                output.add(TerminalLine("  help, ?                    - Hiển thị danh sách câu lệnh"))
                output.add(TerminalLine("  info                       - Hiển thị metadata & mã băm của APK"))
                output.add(TerminalLine("  manifest [grep]            - Xem nội dung AndroidManifest.xml"))
                output.add(TerminalLine("  classes [filter]           - Liệt kê các class có trong DEX"))
                output.add(TerminalLine("  methods <class>            - Liệt kê các method của class"))
                output.add(TerminalLine("  smali <class>              - Xem mã Smali của class"))
                output.add(TerminalLine("  java <class>               - Decompile Java IR của class"))
                output.add(TerminalLine("  deobf <class>              - Decompile với thuật toán De-obfuscation nâng cao"))
                output.add(TerminalLine("  deobf-report [class]       - Báo cáo phân tích giải mã Obfuscation & Sensitive APIs"))
                output.add(TerminalLine("  cfg <class> [method]       - Xuất đồ thị Control Flow Graph (CFG)"))
                output.add(TerminalLine("  ast <class>                - Cấu trúc cây cú pháp trừu tượng (AST)"))
                output.add(TerminalLine("  strings [filter]           - Xem chuỗi trong DEX String Pool"))
                output.add(TerminalLine("  find <keyword>             - Tìm kiếm toàn diện (URL, API, code)"))
                output.add(TerminalLine("  tree                       - Xem cấu trúc cây tệp trong archive"))
                output.add(TerminalLine("  ls [path]                  - Liệt kê tệp trong thư mục archive"))
                output.add(TerminalLine("  cat <file>                 - Đọc nội dung tệp văn bản"))
                output.add(TerminalLine("  hexdump <file> [off] [len] - Xem hex dump của tệp nhị phân"))
                output.add(TerminalLine("  cert                       - Xem thông tin chứng chỉ chữ ký số"))
                output.add(TerminalLine("  export                     - Xuất dự án đã decompile ra tệp zip"))
                output.add(TerminalLine("  sysinfo                    - Thông tin runtime hệ thống"))
                output.add(TerminalLine("  clear                      - Xóa toàn bộ màn hình terminal"))
            }

            "info" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở tệp APK nào. Hãy chọn tệp từ trang chủ.", TerminalLineType.ERROR))
                } else {
                    val inf = project.info
                    output.add(TerminalLine("Tệp:             ${inf.fileName}", TerminalLineType.CYAN_INFO))
                    output.add(TerminalLine("Package:         ${inf.packageName}", TerminalLineType.OUTPUT))
                    output.add(TerminalLine("Phiên bản:       ${inf.versionName} (code: ${inf.versionCode})", TerminalLineType.OUTPUT))
                    output.add(TerminalLine("Min/Target SDK:  min=${inf.minSdk}, target=${inf.targetSdk}", TerminalLineType.OUTPUT))
                    output.add(TerminalLine("Kích thước:      ${inf.fileSize} bytes", TerminalLineType.OUTPUT))
                    output.add(TerminalLine("SHA-256:         ${inf.sha256}", TerminalLineType.OUTPUT))
                    output.add(TerminalLine("DEX / Classes:   ${inf.dexCount} dex / ${inf.classesCount} classes", TerminalLineType.SUCCESS))
                    output.add(TerminalLine("Methods/Strings: ${inf.methodsCount} methods / ${inf.stringsCount} strings", TerminalLineType.SUCCESS))
                    output.add(TerminalLine("Native ABI:      ${inf.nativeLibs.joinToString(", ").ifEmpty { "None (Pure Java/Kotlin)" }}", TerminalLineType.OUTPUT))
                }
            }

            "manifest" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else {
                    val filter = args.firstOrNull()
                    val lines = project.manifest.xmlText.lines()
                    val filtered = if (filter != null) {
                        lines.filter { it.contains(filter, ignoreCase = true) }
                    } else {
                        lines.take(80)
                    }
                    filtered.forEach { output.add(TerminalLine(it, TerminalLineType.OUTPUT)) }
                    if (lines.size > 80 && filter == null) {
                        output.add(TerminalLine("... [Đã cắt bớt. Gõ 'manifest <từ khóa>' để lọc]", TerminalLineType.WARNING))
                    }
                }
            }

            "classes" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else {
                    val filter = args.firstOrNull()
                    var count = 0
                    for (dex in project.dexFiles) {
                        for (c in dex.classes) {
                            if (filter == null || c.type.contains(filter, ignoreCase = true) || c.simpleName.contains(filter, ignoreCase = true)) {
                                output.add(TerminalLine("${c.type} [${dex.name}]", TerminalLineType.OUTPUT))
                                count++
                                if (count >= 100) break
                            }
                        }
                        if (count >= 100) break
                    }
                    output.add(TerminalLine("Tìm thấy $count class${if (count >= 100) " (đã giới hạn 100 kết quả)" else ""}", TerminalLineType.CYAN_INFO))
                }
            }

            "methods" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: methods <tên_class>", TerminalLineType.WARNING))
                } else {
                    val query = args[0]
                    val clazz = project.dexFiles.flatMap { it.classes }.find {
                        it.type.contains(query, ignoreCase = true) || it.simpleName.equals(query, ignoreCase = true)
                    }
                    if (clazz == null) {
                        output.add(TerminalLine("Không tìm thấy class khớp với: $query", TerminalLineType.ERROR))
                    } else {
                        output.add(TerminalLine("Methods trong class ${clazz.type}:", TerminalLineType.CYAN_INFO))
                        clazz.methods.forEach { m ->
                            val p = m.proto.parameters.joinToString(",")
                            output.add(TerminalLine("  * ${m.name}($p):${m.proto.returnType} [regs=${m.codeItem?.registersSize ?: 0}]", TerminalLineType.OUTPUT))
                        }
                    }
                }
            }

            "smali" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: smali <tên_class>", TerminalLineType.WARNING))
                } else {
                    val query = args[0]
                    val clazz = project.dexFiles.flatMap { it.classes }.find {
                        it.type.contains(query, ignoreCase = true) || it.simpleName.equals(query, ignoreCase = true)
                    }
                    if (clazz == null) {
                        output.add(TerminalLine("Không tìm thấy class khớp với: $query", TerminalLineType.ERROR))
                    } else {
                        val smali = smaliGenerator.generate(clazz)
                        smali.lines().take(120).forEach { output.add(TerminalLine(it, TerminalLineType.OUTPUT)) }
                        if (smali.lines().size > 120) {
                            output.add(TerminalLine("... [Mã nguồn dài, hãy xem chi tiết trên tab DEX & Smali]", TerminalLineType.WARNING))
                        }
                    }
                }
            }

            "java" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: java <tên_class>", TerminalLineType.WARNING))
                } else {
                    val query = args[0]
                    val clazz = project.dexFiles.flatMap { it.classes }.find {
                        it.type.contains(query, ignoreCase = true) || it.simpleName.equals(query, ignoreCase = true)
                    }
                    if (clazz == null) {
                        output.add(TerminalLine("Không tìm thấy class: $query", TerminalLineType.ERROR))
                    } else {
                        val javaCode = javaReconstructor.reconstruct(clazz, deobfuscate = false)
                        javaCode.lines().take(120).forEach { output.add(TerminalLine(it, TerminalLineType.OUTPUT)) }
                        if (javaCode.lines().size > 120) {
                            output.add(TerminalLine("... [Mã nguồn dài, hãy xem chi tiết trên tab Java IR]", TerminalLineType.WARNING))
                        }
                    }
                }
            }

            "deobf" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: deobf <tên_class>", TerminalLineType.WARNING))
                } else {
                    val query = args[0]
                    val clazz = project.dexFiles.flatMap { it.classes }.find {
                        it.type.contains(query, ignoreCase = true) || it.simpleName.equals(query, ignoreCase = true)
                    }
                    if (clazz == null) {
                        output.add(TerminalLine("Không tìm thấy class: $query", TerminalLineType.ERROR))
                    } else {
                        output.add(TerminalLine("[+] Đang chạy bộ phân tích De-obfuscation & String Decryption...", TerminalLineType.CYAN_INFO))
                        val javaCode = javaReconstructor.reconstruct(clazz, deobfuscate = true)
                        javaCode.lines().take(120).forEach { output.add(TerminalLine(it, TerminalLineType.OUTPUT)) }
                        if (javaCode.lines().size > 120) {
                            output.add(TerminalLine("... [Mã nguồn dài, xem chi tiết trên tab Java IR]", TerminalLineType.WARNING))
                        }
                    }
                }
            }

            "deobf-report" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else {
                    val deobf = javaReconstructor.getDeobfuscator()
                    val targetQuery = args.firstOrNull()

                    if (targetQuery != null) {
                        val clazz = project.dexFiles.flatMap { it.classes }.find {
                            it.type.contains(targetQuery, ignoreCase = true) || it.simpleName.equals(targetQuery, ignoreCase = true)
                        }
                        if (clazz == null) {
                            output.add(TerminalLine("Không tìm thấy class: $targetQuery", TerminalLineType.ERROR))
                        } else {
                            val res = deobf.analyzeClass(clazz)
                            output.add(TerminalLine("=== BÁO CÁO DE-OBFUSCATION: ${clazz.simpleName} ===", TerminalLineType.CYAN_INFO))
                            output.add(TerminalLine("Tên đề xuất:      ${res.suggestedName}", TerminalLineType.SUCCESS))
                            output.add(TerminalLine("Thư viện nhận diện: ${res.detectedLibrary ?: "Tự sinh / Custom Application"}", TerminalLineType.OUTPUT))
                            output.add(TerminalLine("Khôi phục hàm:    ${res.methodAliases.size} phương thức", TerminalLineType.OUTPUT))
                            res.methodAliases.forEach { (old, new) ->
                                output.add(TerminalLine("  [Method] $old() -> $new()", TerminalLineType.OUTPUT))
                            }
                            output.add(TerminalLine("Khôi phục biến:   ${res.fieldAliases.size} trường", TerminalLineType.OUTPUT))
                            res.fieldAliases.forEach { (old, new) ->
                                output.add(TerminalLine("  [Field] $old -> $new", TerminalLineType.OUTPUT))
                            }
                            output.add(TerminalLine("Chuỗi đã giải mã: ${res.decodedStrings.size} chuỗi", TerminalLineType.CYAN_INFO))
                            res.decodedStrings.values.forEach { dec ->
                                output.add(TerminalLine("  [${dec.algorithm}] \"${dec.original.take(24)}...\" => \"${dec.decrypted}\"", TerminalLineType.SUCCESS))
                            }
                            if (res.sensitiveApis.isNotEmpty()) {
                                output.add(TerminalLine("Cảnh báo API bảo mật (${res.sensitiveApis.size} lệnh):", TerminalLineType.WARNING))
                                res.sensitiveApis.forEach { sa ->
                                    output.add(TerminalLine("  * [${sa.category}] @offset 0x${Integer.toHexString(sa.instructionOffset)}: ${sa.methodCalled.substringAfter("->")}", TerminalLineType.WARNING))
                                }
                            }
                        }
                    } else {
                        // Global APK scan
                        output.add(TerminalLine("=== TỔNG HỢP DE-OBFUSCATION TOÀN BỘ APK ===", TerminalLineType.CYAN_INFO))
                        val allClasses = project.dexFiles.flatMap { it.classes }
                        val sampleClasses = allClasses.take(50)
                        var totalRestoredMethods = 0
                        var totalRestoredFields = 0
                        val allDecoded = mutableMapOf<String, com.example.decompiler.DecryptedString>()
                        val allSensitives = mutableListOf<com.example.decompiler.SensitiveApiCall>()
                        val detectedLibs = mutableSetOf<String>()

                        for (c in sampleClasses) {
                            val res = deobf.analyzeClass(c)
                            totalRestoredMethods += res.methodAliases.size
                            totalRestoredFields += res.fieldAliases.size
                            allDecoded.putAll(res.decodedStrings)
                            allSensitives.addAll(res.sensitiveApis)
                            res.detectedLibrary?.let { detectedLibs.add(it) }
                        }

                        output.add(TerminalLine("Classes được quét:       ${sampleClasses.size} / ${allClasses.size}", TerminalLineType.OUTPUT))
                        output.add(TerminalLine("Định danh hàm khôi phục: $totalRestoredMethods phương thức", TerminalLineType.SUCCESS))
                        output.add(TerminalLine("Định danh biến khôi phục: $totalRestoredFields trường dữ liệu", TerminalLineType.SUCCESS))
                        output.add(TerminalLine("Chuỗi ẩn đã giải mã:     ${allDecoded.size} chuỗi (Base64, Hex, XOR, Reverse)", TerminalLineType.CYAN_INFO))
                        allDecoded.values.take(15).forEach { dec ->
                            output.add(TerminalLine("  [${dec.algorithm}] \"${dec.decrypted}\"", TerminalLineType.SUCCESS))
                        }
                        if (allDecoded.size > 15) {
                            output.add(TerminalLine("  ... và ${allDecoded.size - 15} chuỗi khác", TerminalLineType.OUTPUT))
                        }
                        output.add(TerminalLine("Thư viện nhận diện được:  ${detectedLibs.joinToString(", ").ifEmpty { "Không có thư viện phổ biến" }}", TerminalLineType.OUTPUT))
                        if (allSensitives.isNotEmpty()) {
                            val grouped = allSensitives.groupBy { it.category }
                            output.add(TerminalLine("API nhạy cảm phát hiện:   ${grouped.map { "${it.key}: ${it.value.size}" }.joinToString(", ")}", TerminalLineType.WARNING))
                        }
                    }
                }
            }

            "cfg" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: cfg <tên_class> [tên_method]", TerminalLineType.WARNING))
                } else {
                    val query = args[0]
                    val clazz = project.dexFiles.flatMap { it.classes }.find {
                        it.type.contains(query, ignoreCase = true) || it.simpleName.equals(query, ignoreCase = true)
                    }
                    if (clazz == null) {
                        output.add(TerminalLine("Không tìm thấy class: $query", TerminalLineType.ERROR))
                    } else {
                        val methodQuery = args.getOrNull(1)
                        val targetMethod = if (methodQuery != null) {
                            clazz.methods.find { it.name.contains(methodQuery, ignoreCase = true) }
                        } else {
                            clazz.methods.firstOrNull { it.codeItem != null }
                        }

                        if (targetMethod == null || targetMethod.codeItem == null) {
                            output.add(TerminalLine("Không tìm thấy method có bytecode phù hợp", TerminalLineType.ERROR))
                        } else {
                            val cfgBuilder = com.example.decompiler.ControlFlowGraphBuilder()
                            val blocks = cfgBuilder.build(targetMethod.codeItem.instructions)
                            output.add(TerminalLine("=== CFG for ${clazz.simpleName}::${targetMethod.name} ===", TerminalLineType.CYAN_INFO))
                            output.add(TerminalLine("Tổng số Basic Blocks: ${blocks.size}", TerminalLineType.SUCCESS))
                            for (b in blocks) {
                                val preds = b.predecessors.joinToString(", ") { "B$it" }.ifEmpty { "Entry" }
                                val succs = b.successors.joinToString(", ") { "B$it" }.ifEmpty { "Exit/Return" }
                                output.add(TerminalLine("Block ${b.id} [Off: ${b.startOffset}..${b.endOffset}] | Preds: [$preds] -> Succs: [$succs] | Insns: ${b.instructions.size}", TerminalLineType.OUTPUT))
                            }
                        }
                    }
                }
            }

            "ast" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: ast <tên_class>", TerminalLineType.WARNING))
                } else {
                    val query = args[0]
                    val clazz = project.dexFiles.flatMap { it.classes }.find {
                        it.type.contains(query, ignoreCase = true) || it.simpleName.equals(query, ignoreCase = true)
                    }
                    if (clazz == null) {
                        output.add(TerminalLine("Không tìm thấy class: $query", TerminalLineType.ERROR))
                    } else {
                        output.add(TerminalLine("=== AST Decomposition for ${clazz.simpleName} ===", TerminalLineType.CYAN_INFO))
                        output.add(TerminalLine("Package: ${clazz.packageName} | Super: ${clazz.superType}", TerminalLineType.OUTPUT))
                        output.add(TerminalLine("Fields: ${clazz.fields.size} | Methods: ${clazz.methods.size}", TerminalLineType.OUTPUT))
                        val ti = com.example.decompiler.TypeInference()
                        for (m in clazz.methods) {
                            val code = m.codeItem
                            if (code != null) {
                                val vars = ti.inferVariables(clazz, m, code)
                                val paramList = vars.values.filter { it.isParameter }.joinToString(", ") { "${it.simpleType} ${it.varName}" }
                                val localCount = vars.values.count { !it.isParameter && !it.isThis }
                                output.add(TerminalLine("  Method: ${m.name}($paramList) -> Inferred Locals: $localCount, Insns: ${code.instructions.size}, Tries: ${code.tries.size}", TerminalLineType.SUCCESS))
                            }
                        }
                    }
                }
            }

            "strings" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else {
                    val filter = args.firstOrNull()
                    val allStrings = project.dexFiles.flatMap { it.strings }.distinct()
                    val filtered = if (filter != null) {
                        allStrings.filter { it.contains(filter, ignoreCase = true) }
                    } else {
                        allStrings.take(60)
                    }
                    filtered.forEach { output.add(TerminalLine("\"$it\"", TerminalLineType.OUTPUT)) }
                    output.add(TerminalLine("Hiển thị ${filtered.size} chuỗi / tổng số ${allStrings.size}", TerminalLineType.CYAN_INFO))
                }
            }

            "find", "grep" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: find <từ_khóa>", TerminalLineType.WARNING))
                } else {
                    val kw = args.joinToString(" ")
                    val results = searchEngine.search(project, kw)
                    if (results.isEmpty()) {
                        output.add(TerminalLine("Không tìm thấy kết quả nào cho: $kw", TerminalLineType.WARNING))
                    } else {
                        output.add(TerminalLine("Tìm thấy ${results.size} kết quả:", TerminalLineType.CYAN_INFO))
                        results.take(40).forEach { r ->
                            output.add(TerminalLine("[${r.category}] ${r.title} (${r.location})", TerminalLineType.OUTPUT))
                        }
                    }
                }
            }

            "tree" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else {
                    output.add(TerminalLine("Cấu trúc tệp APK:", TerminalLineType.CYAN_INFO))
                    project.info.entries.take(80).forEach { e ->
                        output.add(TerminalLine("  ${if (e.isDirectory) "[DIR] " else "      "}${e.path} (${e.size} B)", TerminalLineType.OUTPUT))
                    }
                    if (project.info.entries.size > 80) {
                        output.add(TerminalLine("... và ${project.info.entries.size - 80} tệp khác.", TerminalLineType.WARNING))
                    }
                }
            }

            "ls" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else {
                    val pathPrefix = args.firstOrNull()?.trim('/') ?: ""
                    val directChildren = project.info.entries.filter {
                        val p = it.path.trim('/')
                        if (pathPrefix.isEmpty()) !p.contains('/')
                        else p.startsWith(pathPrefix) && p != pathPrefix
                    }
                    directChildren.forEach {
                        output.add(TerminalLine("  ${it.path} (${it.size} B)", TerminalLineType.OUTPUT))
                    }
                }
            }

            "cat" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: cat <đường_dẫn_tệp>", TerminalLineType.WARNING))
                } else {
                    val target = args[0].trim('/')
                    val bytes = project.rawEntries[target]
                    if (bytes == null) {
                        output.add(TerminalLine("Không tìm thấy tệp trong bộ đệm cache: $target", TerminalLineType.ERROR))
                    } else {
                        val text = String(bytes, Charsets.UTF_8)
                        text.lines().take(60).forEach { output.add(TerminalLine(it, TerminalLineType.OUTPUT)) }
                    }
                }
            }

            "hexdump" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (args.isEmpty()) {
                    output.add(TerminalLine("Cú pháp: hexdump <tên_tệp> [offset] [length]", TerminalLineType.WARNING))
                } else {
                    val fileName = args[0]
                    val bytes = project.rawEntries[fileName] ?: project.rawEntries.entries.find { it.key.endsWith(fileName) }?.value
                    if (bytes == null) {
                        output.add(TerminalLine("Không tìm thấy tệp: $fileName", TerminalLineType.ERROR))
                    } else {
                        val off = args.getOrNull(1)?.toIntOrNull() ?: 0
                        val len = args.getOrNull(2)?.toIntOrNull() ?: 64
                        output.addAll(formatHexDump(bytes, off, len))
                    }
                }
            }

            "cert" -> {
                if (project == null) {
                    output.add(TerminalLine("Lỗi: Chưa mở APK nào.", TerminalLineType.ERROR))
                } else if (project.info.signatures.isEmpty()) {
                    output.add(TerminalLine("Không tìm thấy chữ ký APK v1 trong META-INF.", TerminalLineType.WARNING))
                } else {
                    project.info.signatures.forEach { sig ->
                        output.add(TerminalLine("Subject:   ${sig.subject}", TerminalLineType.CYAN_INFO))
                        output.add(TerminalLine("Issuer:    ${sig.issuer}", TerminalLineType.OUTPUT))
                        output.add(TerminalLine("Thuật toán: ${sig.algorithm}", TerminalLineType.OUTPUT))
                        output.add(TerminalLine("SHA-1:     ${sig.sha1}", TerminalLineType.OUTPUT))
                        output.add(TerminalLine("SHA-256:   ${sig.sha256}", TerminalLineType.SUCCESS))
                    }
                }
            }

            "export" -> {
                if (project == null || exporter == null) {
                    output.add(TerminalLine("Lỗi: Chưa có dự án để xuất.", TerminalLineType.ERROR))
                } else {
                    output.add(TerminalLine("Đang đóng gói và xuất toàn bộ dự án...", TerminalLineType.WARNING))
                    try {
                        val file = exporter.exportEverything(project)
                        output.add(TerminalLine("Đã xuất thành công: ${file.absolutePath} (${file.length()} bytes)", TerminalLineType.SUCCESS))
                    } catch (e: Exception) {
                        output.add(TerminalLine("Lỗi xuất: ${e.message}", TerminalLineType.ERROR))
                    }
                }
            }

            "sysinfo" -> {
                output.add(TerminalLine("OS:              Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", TerminalLineType.CYAN_INFO))
                output.add(TerminalLine("Thiết bị:        ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", TerminalLineType.OUTPUT))
                output.add(TerminalLine("Bộ nhớ khả dụng: ${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB / ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB", TerminalLineType.OUTPUT))
                output.add(TerminalLine("Architecture:    ${System.getProperty("os.arch")}", TerminalLineType.OUTPUT))
            }

            else -> {
                output.add(TerminalLine("Lệnh không hợp lệ: '$cmd'. Gõ 'help' để xem các lệnh khả dụng.", TerminalLineType.ERROR))
            }
        }

        return@withContext output
    }

    private fun formatHexDump(bytes: ByteArray, offset: Int, length: Int): List<TerminalLine> {
        val lines = mutableListOf<TerminalLine>()
        val start = offset.coerceIn(0, bytes.size)
        val end = (start + length).coerceIn(start, bytes.size)

        for (i in start until end step 16) {
            val chunkEnd = (i + 16).coerceAtMost(end)
            val chunk = bytes.sliceArray(i until chunkEnd)

            val hexPart = chunk.joinToString(" ") { "%02X".format(it) }.padEnd(48)
            val asciiPart = chunk.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")
            val line = "%08X  %s  |%s|".format(i, hexPart, asciiPart)
            lines.add(TerminalLine(line, TerminalLineType.OUTPUT))
        }

        return lines
    }
}
