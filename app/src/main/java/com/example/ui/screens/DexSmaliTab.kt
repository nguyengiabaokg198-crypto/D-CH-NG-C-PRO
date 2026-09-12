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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.dex.DexClass
import com.example.ui.components.CodeViewer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.TerminalBg

@Composable
fun DexSmaliTab(
    project: ParsedApkProject,
    selectedClass: DexClass?,
    smaliCode: String,
    onClassSelected: (DexClass) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterText by remember { mutableStateOf("") }
    var inspectingClass by remember { mutableStateOf(selectedClass != null) }

    val allClasses = remember(project) {
        project.dexFiles.flatMap { dex -> dex.classes.map { clazz -> Pair(dex.name, clazz) } }
    }

    val filtered = remember(filterText, allClasses) {
        if (filterText.isBlank()) allClasses
        else allClasses.filter {
            it.second.type.contains(filterText, ignoreCase = true) ||
                    it.second.simpleName.contains(filterText, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
    ) {
        if (inspectingClass && selectedClass != null) {
            // View Smali Code for selected class
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { inspectingClass = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to classes", tint = CyanPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedClass.simpleName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = selectedClass.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            CodeViewer(
                code = smaliCode,
                language = "smali",
                title = "${selectedClass.simpleName}.smali",
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        } else {
            // Class List Explorer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("Lọc class name, package...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanPrimary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard,
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "DANH SÁCH CLASS (${filtered.size} / ${allClasses.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { (dexName, clazz) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onClassSelected(clazz)
                                    inspectingClass = true
                                },
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
                                        .background(PurpleAccent.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = dexName,
                                        fontSize = 10.sp,
                                        color = PurpleAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = clazz.simpleName,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = clazz.type,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${clazz.methods.size} methods | ${clazz.fields.size} fields",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CyanPrimary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
