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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.search.SearchCategory
import com.example.search.SearchResult
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkCard
import com.example.ui.theme.GreenAccent
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalYellow

@Composable
fun SearchTab(
    searchQuery: String,
    searchResults: List<SearchResult>,
    onSearch: (String, Boolean, SearchCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var queryText by remember { mutableStateOf(searchQuery) }
    var useRegex by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(SearchCategory.ALL) }

    val quickFilters = listOf("https://", "api.", "token", "secret", "password", "crypto", "WebView")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .padding(12.dp)
    ) {
        // Search Input
        OutlinedTextField(
            value = queryText,
            onValueChange = {
                queryText = it
                onSearch(it, useRegex, selectedCategory)
            },
            placeholder = { Text("Tìm kiếm class, method, chuỗi, API, URL...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanPrimary) },
            trailingIcon = {
                if (queryText.isNotEmpty()) {
                    IconButton(onClick = {
                        queryText = ""
                        onSearch("", useRegex, selectedCategory)
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
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

        Spacer(modifier = Modifier.height(6.dp))

        // Regex & Category selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = useRegex,
                onCheckedChange = {
                    useRegex = it
                    onSearch(queryText, it, selectedCategory)
                },
                colors = CheckboxDefaults.colors(checkedColor = CyanPrimary)
            )
            Text("Regex", color = Color.LightGray, fontSize = 13.sp)

            Spacer(modifier = Modifier.width(12.dp))

            // Horizontal Category Chips
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SearchCategory.values().forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = cat
                            onSearch(queryText, useRegex, cat)
                        },
                        label = { Text(cat.name.replace('_', ' '), fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        }

        // Quick shortcut chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickFilters.forEach { f ->
                AssistChip(
                    onClick = {
                        queryText = f
                        onSearch(f, useRegex, selectedCategory)
                    },
                    label = { Text(f, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CyanPrimary) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = DarkCard)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Results Header
        Text(
            text = "KẾT QUẢ TÌM KIẾM (${searchResults.size})",
            style = MaterialTheme.typography.labelSmall,
            color = CyanPrimary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (queryText.isEmpty()) "Nhập từ khóa tìm kiếm để bắt đầu tra cứu." else "Không tìm thấy kết quả phù hợp.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val catColor = when (res.category) {
                                    SearchCategory.URL_DOMAIN -> CyanPrimary
                                    SearchCategory.SECRET_KEY -> TerminalRed
                                    SearchCategory.PERMISSION -> TerminalYellow
                                    SearchCategory.API_CALL -> PurpleAccent
                                    else -> GreenAccent
                                }

                                Box(
                                    modifier = Modifier
                                        .background(catColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = res.category.name,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = catColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = res.location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = res.title,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            if (res.subtitle.isNotEmpty() && res.subtitle != res.title) {
                                Text(
                                    text = res.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
