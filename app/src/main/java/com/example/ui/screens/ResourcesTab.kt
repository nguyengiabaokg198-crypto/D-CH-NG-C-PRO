package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.ParsedApkProject
import com.example.resources.ResourceNode
import com.example.ui.components.CodeViewer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkCard
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalYellow

@Composable
fun ResourcesTab(
    project: ParsedApkProject,
    resourceTree: ResourceNode?,
    selectedNode: ResourceNode?,
    selectedContent: String?,
    onSelectNode: (ResourceNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(8.dp)
    ) {
        if (selectedNode != null && !selectedNode.isDirectory && selectedContent != null) {
            // View file content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedNode.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Chạm để quay lại danh sách",
                    style = MaterialTheme.typography.bodySmall,
                    color = TerminalYellow,
                    modifier = Modifier.clickable {
                        onSelectNode(resourceTree ?: ResourceNode("", "", true, 0))
                    }
                )
            }

            CodeViewer(
                code = selectedContent,
                language = selectedNode.extension,
                title = selectedNode.fullPath,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // File Explorer List
            Text(
                text = "TÀI NGUYÊN & ASSETS (${project.info.entries.size} tệp)",
                style = MaterialTheme.typography.labelMedium,
                color = CyanPrimary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(project.info.entries) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectNode(
                                    ResourceNode(
                                        name = entry.path.substringAfterLast('/'),
                                        fullPath = entry.path,
                                        isDirectory = entry.isDirectory,
                                        size = entry.size
                                    )
                                )
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when {
                                entry.isDirectory -> Icons.Default.Folder
                                entry.path.endsWith(".png") || entry.path.endsWith(".webp") || entry.path.endsWith(".jpg") -> Icons.Default.Image
                                else -> Icons.Default.Description
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (entry.isDirectory) TerminalYellow else CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${entry.size} bytes (nén: ${entry.compressedSize} bytes)",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
