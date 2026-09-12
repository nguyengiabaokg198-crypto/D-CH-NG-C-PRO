package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.apk.ApkArchiveParser
import com.example.apk.ParsedApkProject
import com.example.decompiler.JavaReconstructor
import com.example.dex.DexClass
import com.example.export.ProjectExporter
import com.example.resources.ResourceExplorer
import com.example.resources.ResourceNode
import com.example.sample.SampleApkProvider
import com.example.search.CodeSearchEngine
import com.example.search.SearchCategory
import com.example.search.SearchResult
import com.example.smali.SmaliGenerator
import com.example.terminal.EmbeddedTerminalEngine
import com.example.terminal.TerminalLine
import com.example.terminal.TerminalLineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val project: ParsedApkProject? = null,
    val selectedTab: Int = 0, // 0: Overview, 1: Manifest, 2: DEX & Smali, 3: Java IR, 4: Resources, 5: Search, 6: Terminal, 7: Export
    val selectedClass: DexClass? = null,
    val selectedSmaliCode: String = "",
    val selectedJavaCode: String = "",
    val resourceTree: ResourceNode? = null,
    val selectedResourceNode: ResourceNode? = null,
    val selectedResourceContent: String? = null,
    val searchQuery: String = "",
    val searchUseRegex: Boolean = false,
    val searchCategory: SearchCategory = SearchCategory.ALL,
    val searchResults: List<SearchResult> = emptyList(),
    val terminalLines: List<TerminalLine> = listOf(
        TerminalLine("APK Decompiler Pro Embedded Terminal v1.0", TerminalLineType.CYAN_INFO),
        TerminalLine("Gõ 'help' để xem danh sách câu lệnh được hỗ trợ.", TerminalLineType.OUTPUT)
    ),
    val terminalInput: String = "",
    val recentFiles: List<String> = emptyList(),
    val isDeobfuscateEnabled: Boolean = false,
    val appLanguage: String = "vi", // "vi" or "en"
    val themeMode: String = "dark", // "dark", "light", "system"
    val exportSuccessPath: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val archiveParser = ApkArchiveParser()
    private val smaliGenerator = SmaliGenerator()
    private val javaReconstructor = JavaReconstructor()
    private val resourceExplorer = ResourceExplorer()
    private val searchEngine = CodeSearchEngine()
    private val exporter = ProjectExporter(application)
    private val terminalEngine = EmbeddedTerminalEngine(searchEngine, smaliGenerator, javaReconstructor)

    init {
        // Load recent files from prefs if available
        loadRecentFiles()
    }

    private fun loadRecentFiles() {
        val prefs = getApplication<Application>().getSharedPreferences("apk_decompiler_prefs", Context.MODE_PRIVATE)
        val recents = prefs.getStringSet("recent_files", emptySet())?.toList() ?: emptyList()
        val lang = prefs.getString("language", "vi") ?: "vi"
        val theme = prefs.getString("theme", "dark") ?: "dark"
        _uiState.update { it.copy(recentFiles = recents, appLanguage = lang, themeMode = theme) }
    }

    fun saveRecentFile(name: String) {
        val prefs = getApplication<Application>().getSharedPreferences("apk_decompiler_prefs", Context.MODE_PRIVATE)
        val current = _uiState.value.recentFiles.toMutableList()
        current.remove(name)
        current.add(0, name)
        val updated = current.take(8)
        prefs.edit().putStringSet("recent_files", updated.toSet()).apply()
        _uiState.update { it.copy(recentFiles = updated) }
    }

    fun setLanguage(lang: String) {
        val prefs = getApplication<Application>().getSharedPreferences("apk_decompiler_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("language", lang).apply()
        _uiState.update { it.copy(appLanguage = lang) }
    }

    fun setThemeMode(mode: String) {
        val prefs = getApplication<Application>().getSharedPreferences("apk_decompiler_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("theme", mode).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun loadSampleApk() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Đang tạo mẫu APK Demo...", errorMessage = null) }
            try {
                val bytes = withContext(Dispatchers.Default) {
                    SampleApkProvider.createSampleApkBytes()
                }
                processApkBytes(bytes, "DemoSecurityTarget.apk")
                saveRecentFile("DemoSecurityTarget.apk (Sample)")
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Lỗi tạo mẫu Demo: ${e.message}") }
            }
        }
    }

    fun openApkFromUri(uri: Uri, name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Đang đọc tệp $name...", errorMessage = null) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    stream?.use { it.readBytes() } ?: throw IllegalArgumentException("Không thể đọc tệp")
                }
                processApkBytes(bytes, name)
                saveRecentFile(name)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Lỗi đọc tệp: ${e.localizedMessage}") }
            }
        }
    }

    fun openApkFromBytes(bytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Đang phân tích $fileName...", errorMessage = null) }
            processApkBytes(bytes, fileName)
            saveRecentFile(fileName)
        }
    }

    private suspend fun processApkBytes(bytes: ByteArray, fileName: String) {
        withContext(Dispatchers.Default) {
            try {
                _uiState.update { it.copy(statusMessage = "Đang phân tích cấu trúc archive & DEX...") }
                val project = archiveParser.parse(bytes, fileName)

                _uiState.update { it.copy(statusMessage = "Đang xây dựng cây tài nguyên...") }
                val tree = resourceExplorer.buildTree(project.info.entries)

                val firstClass = project.dexFiles.firstOrNull()?.classes?.firstOrNull()
                val smali = firstClass?.let { smaliGenerator.generate(it) } ?: ""
                val java = firstClass?.let { javaReconstructor.reconstruct(it) } ?: ""

                val termWelcome = listOf(
                    TerminalLine("Đã mở tệp: ${project.info.fileName} (${project.info.packageName})", TerminalLineType.SUCCESS),
                    TerminalLine("Tổng số Class: ${project.info.classesCount}, Methods: ${project.info.methodsCount}", TerminalLineType.CYAN_INFO),
                    TerminalLine("Gõ 'help' để xem danh sách câu lệnh phân tích.", TerminalLineType.OUTPUT)
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "",
                        errorMessage = null,
                        project = project,
                        selectedTab = 0,
                        selectedClass = firstClass,
                        selectedSmaliCode = smali,
                        selectedJavaCode = java,
                        resourceTree = tree,
                        terminalLines = it.terminalLines + termWelcome
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không thể phân tích tệp: ${e.message ?: "Lỗi định dạng không hợp lệ"}"
                    )
                }
            }
        }
    }

    fun selectClass(clazz: DexClass) {
        viewModelScope.launch(Dispatchers.Default) {
            val smali = smaliGenerator.generate(clazz)
            val java = javaReconstructor.reconstruct(clazz, deobfuscate = _uiState.value.isDeobfuscateEnabled)
            _uiState.update {
                it.copy(
                    selectedClass = clazz,
                    selectedSmaliCode = smali,
                    selectedJavaCode = java
                )
            }
        }
    }

    fun toggleDeobfuscation(enabled: Boolean) {
        _uiState.update { it.copy(isDeobfuscateEnabled = enabled) }
        val clazz = _uiState.value.selectedClass ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val java = javaReconstructor.reconstruct(clazz, deobfuscate = enabled)
            _uiState.update { it.copy(selectedJavaCode = java) }
        }
    }

    fun selectResource(node: ResourceNode) {
        val proj = _uiState.value.project ?: return
        val raw = proj.rawEntries[node.fullPath]
        val content = if (raw != null) {
            if (node.fullPath.endsWith(".png") || node.fullPath.endsWith(".webp") || node.fullPath.endsWith(".jpg")) {
                "IMAGE_BINARY:${node.fullPath}"
            } else {
                try {
                    String(raw, Charsets.UTF_8)
                } catch (e: Exception) {
                    "[Binary file: ${raw.size} bytes]"
                }
            }
        } else {
            "Tệp không có trong bộ đệm xem trước hoặc nằm trong archive chưa trích xuất."
        }

        _uiState.update {
            it.copy(
                selectedResourceNode = node,
                selectedResourceContent = content
            )
        }
    }

    fun performSearch(query: String, useRegex: Boolean = false, category: SearchCategory = SearchCategory.ALL) {
        val proj = _uiState.value.project ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(searchQuery = query, searchUseRegex = useRegex, searchCategory = category, isLoading = true) }
            val results = searchEngine.search(proj, query, useRegex, category)
            _uiState.update { it.copy(searchResults = results, isLoading = false) }
        }
    }

    fun updateTerminalInput(text: String) {
        _uiState.update { it.copy(terminalInput = text) }
    }

    fun executeTerminalCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.equals("clear", ignoreCase = true)) {
            _uiState.update { it.copy(terminalLines = emptyList(), terminalInput = "") }
            return
        }

        val proj = _uiState.value.project
        viewModelScope.launch {
            val output = terminalEngine.execute(trimmed, proj, exporter)
            _uiState.update {
                it.copy(
                    terminalLines = (it.terminalLines + output).takeLast(500),
                    terminalInput = ""
                )
            }
        }
    }

    fun exportProject(exportAll: Boolean) {
        val proj = _uiState.value.project ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Đang đóng gói dữ liệu xuất...") }
            try {
                val file = if (exportAll) exporter.exportEverything(proj) else exporter.exportReportOnly(proj)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        exportSuccessPath = file.absolutePath,
                        terminalLines = it.terminalLines + TerminalLine("Đã xuất tệp thành công: ${file.name}", TerminalLineType.SUCCESS)
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Lỗi xuất dự án: ${e.message}") }
            }
        }
    }

    fun closeProject() {
        _uiState.update {
            it.copy(
                project = null,
                selectedClass = null,
                selectedSmaliCode = "",
                selectedJavaCode = "",
                resourceTree = null,
                selectedResourceNode = null,
                selectedResourceContent = null,
                selectedTab = 0
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissExportSuccess() {
        _uiState.update { it.copy(exportSuccessPath = null) }
    }
}
