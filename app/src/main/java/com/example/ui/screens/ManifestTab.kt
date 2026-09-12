package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apk.ParsedApkProject
import com.example.apk.PermissionLevel
import com.example.ui.components.CodeViewer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalYellow

@Composable
fun ManifestTab(
    project: ParsedApkProject,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableStateOf(0) } // 0: Reconstructed XML, 1: Components & Permissions
    val manifest = project.manifest

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
    ) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = DarkSurface,
            contentColor = CyanPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[subTab]),
                    color = CyanPrimary
                )
            }
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("XML Manifest") },
                icon = { Icon(Icons.Default.Code, contentDescription = null) }
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("Quyền & Thành phần") },
                icon = { Icon(Icons.Default.Security, contentDescription = null) }
            )
        }

        if (subTab == 0) {
            CodeViewer(
                code = manifest.xmlText,
                language = "xml",
                title = "AndroidManifest.xml (${manifest.xmlText.lines().size} lines)",
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Permissions
                item {
                    Text(
                        text = "QUYỀN HẠN YÊU CẦU (${manifest.components.permissions.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (manifest.components.permissions.isEmpty()) {
                    item {
                        Text("Không có quyền nào được yêu cầu.", color = Color.Gray)
                    }
                } else {
                    items(manifest.components.permissions) { perm ->
                        val isDanger = perm.level == PermissionLevel.DANGEROUS
                        val badgeColor = if (isDanger) TerminalRed else GreenAccent

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = perm.level.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = perm.name.substringAfterLast('.'),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = perm.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (perm.description.isNotEmpty()) {
                                        Text(
                                            text = perm.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.LightGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Activities
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ACTIVITIES (${manifest.components.activities.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                items(manifest.components.activities) { act ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = act.name.substringAfterLast('.'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = act.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (act.isExported) {
                                Box(
                                    modifier = Modifier
                                        .background(TerminalYellow.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("EXPORTED", fontSize = 10.sp, color = TerminalYellow, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Services & Receivers
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SERVICES (${manifest.components.services.size}) & RECEIVERS (${manifest.components.receivers.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                items(manifest.components.services) { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Service: ${s.name.substringAfterLast('.')}", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = s.name, style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                items(manifest.components.receivers) { r ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Receiver: ${r.name.substringAfterLast('.')}", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(text = r.name, style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
