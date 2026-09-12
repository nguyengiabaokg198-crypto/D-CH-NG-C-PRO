package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.UiState
import com.example.dex.DexClass
import com.example.resources.ResourceNode
import com.example.search.SearchCategory
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TerminalBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(
    uiState: UiState,
    onBackToHome: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onClassSelected: (DexClass) -> Unit,
    onResourceSelected: (ResourceNode) -> Unit,
    onSearch: (String, Boolean, SearchCategory) -> Unit,
    onTerminalInputChange: (String) -> Unit,
    onTerminalExecute: (String) -> Unit,
    onExportAll: () -> Unit,
    onExportReportOnly: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDeobfuscate: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val project = uiState.project ?: return

    val tabs = listOf(
        Pair("Tổng quan", Icons.Default.Info),
        Pair("Manifest", Icons.Default.Security),
        Pair("DEX & Smali", Icons.Default.Code),
        Pair("Java IR", Icons.Default.Description),
        Pair("Tài nguyên", Icons.Default.FolderZip),
        Pair("Tìm kiếm", Icons.Default.Search),
        Pair("Terminal", Icons.Default.Terminal),
        Pair("Xuất tệp", Icons.Default.Share)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = project.info.packageName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        text = "${project.info.fileName} | ${project.info.versionName} (SDK ${project.info.targetSdk})",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanPrimary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackToHome) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close APK", tint = CyanPrimary)
                }
            },
            actions = {
                IconButton(onClick = { onTabSelected(6) }) { // Terminal
                    Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = CyanPrimary)
                }
                IconButton(onClick = { onTabSelected(5) }) { // Search
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.LightGray)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
        )

        // Scrollable Tabs
        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTab,
            containerColor = DarkCard,
            contentColor = CyanPrimary,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                    color = CyanPrimary
                )
            }
        ) {
            tabs.forEachIndexed { index, (title, icon) ->
                Tab(
                    selected = uiState.selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    selectedContentColor = CyanPrimary,
                    unselectedContentColor = Color.Gray
                )
            }
        }

        // Active Tab Screen
        Box(modifier = Modifier.weight(1f)) {
            when (uiState.selectedTab) {
                0 -> OverviewTab(project = project, onNavigateTab = onTabSelected)
                1 -> ManifestTab(project = project)
                2 -> DexSmaliTab(
                    project = project,
                    selectedClass = uiState.selectedClass,
                    smaliCode = uiState.selectedSmaliCode,
                    onClassSelected = onClassSelected
                )
                3 -> JavaReconstructTab(
                    selectedClass = uiState.selectedClass,
                    javaCode = uiState.selectedJavaCode,
                    isDeobfuscateEnabled = uiState.isDeobfuscateEnabled,
                    onToggleDeobfuscate = onToggleDeobfuscate
                )
                4 -> ResourcesTab(
                    project = project,
                    resourceTree = uiState.resourceTree,
                    selectedNode = uiState.selectedResourceNode,
                    selectedContent = uiState.selectedResourceContent,
                    onSelectNode = onResourceSelected
                )
                5 -> SearchTab(
                    searchQuery = uiState.searchQuery,
                    searchResults = uiState.searchResults,
                    onSearch = onSearch
                )
                6 -> TerminalTab(
                    lines = uiState.terminalLines,
                    input = uiState.terminalInput,
                    onInputChange = onTerminalInputChange,
                    onExecute = onTerminalExecute
                )
                7 -> ExportTab(
                    project = project,
                    exportSuccessPath = uiState.exportSuccessPath,
                    onExportAll = onExportAll,
                    onExportReportOnly = onExportReportOnly
                )
            }
        }
    }
}
