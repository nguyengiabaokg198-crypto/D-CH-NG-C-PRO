package com.example

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.screens.AnalyzerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsDialog
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TerminalBg

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            var showSettingsDialog by remember { mutableStateOf(false) }

            // File Picker Launcher
            val openDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    val fileName = queryFileName(uri) ?: "app.apk"
                    viewModel.openApkFromUri(uri, fileName)
                }
            }

            MyApplicationTheme(darkTheme = uiState.themeMode != "light") {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TerminalBg)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (uiState.project == null) {
                            HomeScreen(
                                uiState = uiState,
                                onPickFileClick = {
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "application/vnd.android.package-archive",
                                            "application/zip",
                                            "application/java-archive",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                onLoadSampleClick = {
                                    viewModel.loadSampleApk()
                                },
                                onSettingsClick = {
                                    showSettingsDialog = true
                                }
                            )
                        } else {
                            AnalyzerScreen(
                                uiState = uiState,
                                onBackToHome = {
                                    viewModel.dismissExportSuccess()
                                    viewModel.closeProject()
                                },
                                onTabSelected = { viewModel.setTab(it) },
                                onClassSelected = { viewModel.selectClass(it) },
                                onResourceSelected = { viewModel.selectResource(it) },
                                onSearch = { q, regex, cat -> viewModel.performSearch(q, regex, cat) },
                                onTerminalInputChange = { viewModel.updateTerminalInput(it) },
                                onTerminalExecute = { viewModel.executeTerminalCommand(it) },
                                onExportAll = { viewModel.exportProject(true) },
                                onExportReportOnly = { viewModel.exportProject(false) },
                                onOpenSettings = { showSettingsDialog = true },
                                onToggleDeobfuscate = { viewModel.toggleDeobfuscation(it) }
                            )
                        }

                        // Loading overlay
                        if (uiState.isLoading) {
                            Dialog(onDismissRequest = {}) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = DarkSurface,
                                    tonalElevation = 8.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(color = CyanPrimary)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = uiState.statusMessage.ifEmpty { "Đang xử lý..." },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Error dialog
                        uiState.errorMessage?.let { err ->
                            AlertDialog(
                                onDismissRequest = { viewModel.dismissError() },
                                title = { Text("Thông báo", color = Color.White) },
                                text = { Text(err, color = Color.LightGray) },
                                confirmButton = {
                                    TextButton(onClick = { viewModel.dismissError() }) {
                                        Text("Đồng ý", color = CyanPrimary)
                                    }
                                },
                                containerColor = DarkSurface
                            )
                        }

                        // Settings Dialog
                        if (showSettingsDialog) {
                            SettingsDialog(
                                currentLanguage = uiState.appLanguage,
                                onLanguageSelected = { lang ->
                                    viewModel.setLanguage(lang)
                                },
                                onDismiss = { showSettingsDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = it.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }
}
