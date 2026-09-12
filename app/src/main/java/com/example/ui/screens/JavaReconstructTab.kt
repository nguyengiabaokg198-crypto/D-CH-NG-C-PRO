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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dex.DexClass
import com.example.ui.components.CodeViewer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkCard
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalYellow

@Composable
fun JavaReconstructTab(
    selectedClass: DexClass?,
    javaCode: String,
    isDeobfuscateEnabled: Boolean = false,
    onToggleDeobfuscate: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(8.dp)
    ) {
        // Engine controls and notice banner
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Engine JADX-Mobile v2.0",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[AST + CFG + SSA IR]",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalGreen,
                            fontSize = 10.sp
                        )
                    }

                    // De-obfuscation Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isDeobfuscateEnabled) "De-obf ON" else "De-obf OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDeobfuscateEnabled) TerminalGreen else Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isDeobfuscateEnabled,
                            onCheckedChange = onToggleDeobfuscate,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyanPrimary,
                                checkedTrackColor = CyanPrimary.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.size(width = 44.dp, height = 24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = TerminalYellow,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDeobfuscateEnabled) {
                            "Đang áp dụng: Giải mã chuỗi Base64 + Phục hồi định danh có nghĩa theo chữ ký phương thức."
                        } else {
                            "Tái cấu trúc luồng điều khiển (if-else, while, try-catch, switch) và suy luận kiểu biến tự động."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (selectedClass != null && javaCode.isNotEmpty()) {
            val titleSuffix = if (isDeobfuscateEnabled) "(De-obfuscated)" else "(Reconstructed IR)"
            CodeViewer(
                code = javaCode,
                language = "java",
                title = "${selectedClass.simpleName}.java $titleSuffix",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Vui lòng chọn một Class từ tab 'DEX & Smali' để tái tạo mã Java.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
