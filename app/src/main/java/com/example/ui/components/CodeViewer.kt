package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkCard
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalYellow

@Composable
fun CodeViewer(
    code: String,
    language: String = "smali",
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lines = code.lines()
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TerminalBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkCard,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title ?: "${language.uppercase()} (${lines.size} lines)",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                            Toast.makeText(context, "Đã sao chép mã", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = Color.White
                        )
                    }
                }
            }

            // Code lines
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        ) {
                            // Line Number
                            Text(
                                text = (index + 1).toString().padStart(4, ' '),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF546E7A),
                                modifier = Modifier.width(36.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Highlighted Line
                            Text(
                                text = highlightLine(line, language),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun highlightLine(line: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        val trimmed = line.trimStart()

        when {
            // Comments
            trimmed.startsWith("#") || trimmed.startsWith("//") -> {
                pushStyle(SpanStyle(color = Color(0xFF78909C)))
                append(line)
                pop()
            }

            // Smali directives
            trimmed.startsWith(".") -> {
                val directive = trimmed.substringBefore(" ")
                val rest = line.substring(line.indexOf(directive) + directive.length)
                pushStyle(SpanStyle(color = CyanPrimary, fontWeight = FontWeight.Bold))
                append(line.substring(0, line.indexOf(directive) + directive.length))
                pop()
                pushStyle(SpanStyle(color = Color(0xFFECEFF1)))
                append(rest)
                pop()
            }

            // Java Keywords
            language == "java" && (
                    trimmed.startsWith("package ") || trimmed.startsWith("public ") ||
                            trimmed.startsWith("class ") || trimmed.startsWith("extends ") ||
                            trimmed.startsWith("implements ") || trimmed.startsWith("private ") ||
                            trimmed.startsWith("protected ") || trimmed.startsWith("static ") ||
                            trimmed.startsWith("final ") || trimmed.startsWith("return") ||
                            trimmed.startsWith("if ") || trimmed.startsWith("while ") ||
                            trimmed.startsWith("for ") || trimmed.startsWith("throw ")
                    ) -> {
                pushStyle(SpanStyle(color = PurpleAccent, fontWeight = FontWeight.Bold))
                append(line)
                pop()
            }

            // String literals
            line.contains("\"") -> {
                pushStyle(SpanStyle(color = TerminalYellow))
                append(line)
                pop()
            }

            // Labels
            trimmed.startsWith(":") -> {
                pushStyle(SpanStyle(color = GreenAccent, fontWeight = FontWeight.SemiBold))
                append(line)
                pop()
            }

            else -> {
                pushStyle(SpanStyle(color = Color(0xFFCFD8DC)))
                append(line)
                pop()
            }
        }
    }
}
