package com.dueboysenberry1226.px5launcher.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dueboysenberry1226.px5launcher.R
import com.dueboysenberry1226.px5launcher.data.LaunchableApp
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.yield

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
    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = onMenuClose
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    if (isPinned) {
                        stringResource(R.string.menu_unpin)
                    } else {
                        stringResource(R.string.menu_pin)
                    }
                )
            },
            onClick = { onTogglePin() }
        )

        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_app_info)) },
            onClick = { onAppInfo() }
        )

        if (canUninstall) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_uninstall)) },
                onClick = { onUninstall() }
            )
        } else if (canDisable) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_disable)) },
                onClick = { onDisable() }
            )
        }
    }

    if (searchOpen) {
        val searchFieldFR = remember { FocusRequester() }

        val filteredApps = remember(searchQuery, searchResults) {
            filterAppsForKeyboardSearch(
                apps = searchResults,
                query = searchQuery
            )
        }

        LaunchedEffect(searchOpen) {
            if (searchOpen) {
                yield()
                yield()
                searchFieldFR.requestFocus()
            }
        }

        OverlayCard(
            title = stringResource(R.string.search_title),
            onClose = onSearchClose
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    SearchDisplayField(
                        value = searchQuery,
                        placeholder = stringResource(R.string.search_placeholder),
                        focusRequester = searchFieldFR
                    )

                    Spacer(Modifier.height(14.dp))

                    if (filteredApps.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = filteredApps,
                                key = { it.packageName }
                            ) { app ->
                                SearchResultCard(
                                    app = app,
                                    onClick = {
                                        onSearchClose()
                                        onSearchLaunch(app)
                                    }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.search_no_results),
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .widthIn(min = 340.dp)
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    BuiltInKeyboard(
                        onChar = { char ->
                            onSearchChange(searchQuery + char)
                        },
                        onBackspace = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchChange(searchQuery.dropLast(1))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchDisplayField(
    value: String,
    placeholder: String,
    focusRequester: FocusRequester
) {
    val shape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .background(
                if (isFocused) Color.White.copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.42f)
                else Color.White.copy(alpha = 0.16f),
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 15.sp
            )
        } else {
            Text(
                text = value,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    app: LaunchableApp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (isFocused) Color.White.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.10f)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.95f)
                else Color.White.copy(alpha = 0.14f),
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                val nk = event.nativeKeyEvent
                if (nk.action != android.view.KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (nk.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                        android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                            onClick()
                            true
                        }

                        else -> false
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconBmp = app.iconBitmap

        if (iconBmp != null) {
            Image(
                bitmap = iconBmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .height(40.dp)
                    .width(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .width(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.label.take(1).ifBlank { "?" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = app.label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BuiltInKeyboard(
    onChar: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val panelShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 460.dp)
                .clip(panelShape)
                .background(Color(0xFF171C24))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = panelShape
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyboardRow(
                keys = listOf(
                    KeyboardItem.Letter("Q"),
                    KeyboardItem.Letter("W"),
                    KeyboardItem.Letter("E"),
                    KeyboardItem.Letter("R"),
                    KeyboardItem.Letter("T"),
                    KeyboardItem.Letter("Y"),
                    KeyboardItem.Letter("U"),
                    KeyboardItem.Letter("I"),
                    KeyboardItem.Letter("O"),
                    KeyboardItem.Letter("P")
                ),
                onChar = onChar,
                onBackspace = onBackspace
            )

            KeyboardRow(
                keys = listOf(
                    KeyboardItem.Dummy,
                    KeyboardItem.Letter("A"),
                    KeyboardItem.Letter("S"),
                    KeyboardItem.Letter("D"),
                    KeyboardItem.Letter("F"),
                    KeyboardItem.Letter("G"),
                    KeyboardItem.Letter("H"),
                    KeyboardItem.Letter("J"),
                    KeyboardItem.Letter("K"),
                    KeyboardItem.Letter("L")
                ),
                onChar = onChar,
                onBackspace = onBackspace
            )

            KeyboardRow(
                keys = listOf(
                    KeyboardItem.Dummy,
                    KeyboardItem.Letter("Z"),
                    KeyboardItem.Letter("X"),
                    KeyboardItem.Letter("C"),
                    KeyboardItem.Letter("V"),
                    KeyboardItem.Letter("B"),
                    KeyboardItem.Letter("N"),
                    KeyboardItem.Letter("M"),
                    KeyboardItem.Dummy,
                    KeyboardItem.Backspace
                ),
                onChar = onChar,
                onBackspace = onBackspace
            )
        }
    }
}

@Composable
private fun KeyboardRow(
    keys: List<KeyboardItem>,
    onChar: (String) -> Unit,
    onBackspace: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { key ->
            when (key) {
                is KeyboardItem.Letter -> {
                    ActiveKeyboardKey(
                        label = key.label,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        onClick = { onChar(key.label) }
                    )
                }

                KeyboardItem.Backspace -> {
                    ActiveKeyboardKey(
                        label = "⌫",
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        onClick = onBackspace
                    )
                }

                KeyboardItem.Dummy -> {
                    DummyKeyboardKey(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveKeyboardKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (isFocused) Color(0xFF3C4757) else Color(0xFF232A34)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.30f)
                else Color.White.copy(alpha = 0.05f),
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                val nk = event.nativeKeyEvent
                if (nk.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                when (nk.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                    android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                        onClick()
                        true
                    }

                    else -> false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFFEAF1FF),
            fontSize = if (label == "⌫") 16.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun DummyKeyboardKey(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF232A34).copy(alpha = 0.55f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.03f),
                shape = RoundedCornerShape(10.dp)
            )
            .alpha(0.9f)
    )
}

private sealed interface KeyboardItem {
    data class Letter(val label: String) : KeyboardItem
    data object Backspace : KeyboardItem
    data object Dummy : KeyboardItem
}

private fun filterAppsForKeyboardSearch(
    apps: List<LaunchableApp>,
    query: String
): List<LaunchableApp> {
    if (query.isBlank()) return emptyList()

    val normalizedQuery = normalizeSearchText(query)
    val queryCounts = buildCharCounts(normalizedQuery)

    if (queryCounts.isEmpty()) return emptyList()

    return apps
        .filter { app ->
            val normalizedLabel = normalizeSearchText(app.label)
            val labelCounts = buildCharCounts(normalizedLabel)
            queryCounts.all { (char, needed) ->
                (labelCounts[char] ?: 0) >= needed
            }
        }
        .sortedBy { it.label.lowercase(Locale.ROOT) }
}

private fun normalizeSearchText(text: String): String {
    val lowered = text.lowercase(Locale.ROOT)
    val normalized = Normalizer.normalize(lowered, Normalizer.Form.NFD)
    return normalized.replace("\\p{Mn}+".toRegex(), "")
}

private fun buildCharCounts(text: String): Map<Char, Int> {
    val counts = LinkedHashMap<Char, Int>()

    text.forEach { char ->
        if (!char.isWhitespace()) {
            counts[char] = (counts[char] ?: 0) + 1
        }
    }

    return counts
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
            .background(Color.Black.copy(alpha = 0.01f))
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 0.dp)
                .fillMaxWidth(1f)
                .fillMaxHeight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1422)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )

                    TextButton(onClick = onClose) {
                        Text(
                            text = stringResource(R.string.common_close),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                content()
            }
        }
    }
}