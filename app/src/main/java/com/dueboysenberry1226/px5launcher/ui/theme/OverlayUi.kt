package com.dueboysenberry1226.px5launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.data.LaunchableApp

@Composable
fun HomeOverlays(
    searchOpen: Boolean,
    searchQuery: String,
    searchResults: List<LaunchableApp>,

    menuOpen: Boolean,
    isPinned: Boolean,

    canUninstall: Boolean,
    canDisable: Boolean,

    onSearchChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onSearchLaunch: (LaunchableApp) -> Unit,

    onTogglePin: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDisable: () -> Unit,

    onMenuClose: () -> Unit
) {
    // ===== MENU =====
    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = onMenuClose
    ) {
        DropdownMenuItem(
            text = { Text(if (isPinned) "Kitűzés eltávolítása" else "Kitűzés") },
            onClick = { onTogglePin() } // ✅ NEM zárunk itt menüt!
        )

        DropdownMenuItem(
            text = { Text("App infó") },
            onClick = { onAppInfo() } // ✅ NEM zárunk itt menüt!
        )

        if (canUninstall) {
            DropdownMenuItem(
                text = { Text("Törlés (uninstall)") },
                onClick = { onUninstall() } // ✅ NEM zárunk itt menüt!
            )
        } else if (canDisable) {
            DropdownMenuItem(
                text = { Text("Letiltás") },
                onClick = { onDisable() } // ✅ NEM zárunk itt menüt!
            )
        }
    }

    // ===== SEARCH =====
    if (searchOpen) {
        OverlayCard(title = "Keresés", onClose = onSearchClose) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                placeholder = { Text("Írd be az app nevét…") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.35f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                )
            )

            Spacer(Modifier.height(12.dp))

            if (searchResults.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchResults.size) { i ->
                        val a = searchResults[i]
                        AssistChip(
                            onClick = { onSearchClose(); onSearchLaunch(a) },
                            label = { Text(a.label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.10f),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            } else {
                Text(
                    "Találatok itt fognak megjelenni.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun OverlayCard(
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
                .fillMaxWidth(0.76f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1422)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onClose) {
                        Text("Bezár", color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}
