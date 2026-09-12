package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.terminal.TerminalLine
import com.example.terminal.TerminalLineType
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkCard
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalYellow

@Composable
fun TerminalTab(
    lines: List<TerminalLine>,
    input: String,
    onInputChange: (String) -> Unit,
    onExecute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val quickCmds = listOf("help", "info", "manifest", "classes", "strings", "cert", "tree", "sysinfo", "clear")

    // Auto-scroll to bottom on new lines
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(8.dp)
    ) {
        // Terminal Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            color = DarkCard
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(TerminalRed, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(TerminalYellow, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(GreenAccent, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "apkdecompiler@android (terminal)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onExecute("clear") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Terminal Console Buffer
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(lines) { line ->
                    val textColor = when (line.type) {
                        TerminalLineType.PROMPT -> TerminalGreen
                        TerminalLineType.CYAN_INFO -> CyanPrimary
                        TerminalLineType.SUCCESS -> GreenAccent
                        TerminalLineType.WARNING -> TerminalYellow
                        TerminalLineType.ERROR -> TerminalRed
                        TerminalLineType.OUTPUT -> Color(0xFFECEFF1)
                    }

                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = textColor,
                        lineHeight = 16.sp,
                        fontWeight = if (line.type == TerminalLineType.PROMPT || line.type == TerminalLineType.CYAN_INFO) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Quick command chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCmds.forEach { cmd ->
                AssistChip(
                    onClick = { onExecute(cmd) },
                    label = {
                        Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CyanPrimary)
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = DarkCard)
                )
            }
        }

        // Input Field Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TerminalGreen,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp)
            )

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("Nhập lệnh (vd: help, info, smali MainActivity)...", color = Color.DarkGray, fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = Color(0xFF263238),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) onExecute(input)
                })
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (input.isNotBlank()) onExecute(input)
                },
                modifier = Modifier
                    .background(CyanPrimary, RoundedCornerShape(10.dp))
                    .size(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Run command", tint = Color.Black)
            }
        }
    }
}
