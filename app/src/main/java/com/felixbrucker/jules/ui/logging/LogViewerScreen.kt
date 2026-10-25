package com.felixbrucker.jules.ui.logging

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.felixbrucker.jules.data.database.LogEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

@Composable
fun LogViewerScreen(
    modifier: Modifier = Modifier,
    viewModel: LogViewerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val pagedLogs = viewModel.logsPaged.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPriorities by viewModel.selectedPriorities.collectAsState()
    val filteredCount by viewModel.filteredCount.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()

    BackHandler {
        onNavigateBack()
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var topControlsHeightPx by remember { mutableFloatStateOf(0f) }
    var topControlsOffsetPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (topControlsHeightPx <= 0f) return Offset.Zero

                val oldOffset = topControlsOffsetPx
                val newOffset = (oldOffset + delta).coerceIn(-topControlsHeightPx, 0f)
                val consumedY = newOffset - oldOffset
                topControlsOffsetPx = newOffset

                return Offset(0f, consumedY)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex == 0 && currentOffset == 0) {
                    topControlsOffsetPx = 0f
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LogViewerTopBar(
                onNavigateBack = onNavigateBack,
                onClearLogs = { showClearDialog = true }
            )
        },
        containerColor = LogColors.SurfaceDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .nestedScroll(nestedScrollConnection)
        ) {
            LogViewerTopControls(
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                selectedPriorities = selectedPriorities,
                onTogglePriority = { viewModel.togglePriority(it) },
                filteredLogsCount = filteredCount,
                totalLogsCount = totalCount,
                onJumpToBottom = {
                    if (pagedLogs.itemCount > 0) {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                },
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val measuredHeight = placeable.height
                        if (measuredHeight > 0 && topControlsHeightPx != measuredHeight.toFloat()) {
                            topControlsHeightPx = measuredHeight.toFloat()
                            topControlsOffsetPx = topControlsOffsetPx.coerceIn(-topControlsHeightPx, 0f)
                        }

                        val layoutHeight = (measuredHeight + topControlsOffsetPx.roundToInt()).coerceAtLeast(0)

                        layout(placeable.width, layoutHeight) {
                            placeable.placeRelative(0, topControlsOffsetPx.roundToInt())
                        }
                    }
                    .clipToBounds()
            )

            if (pagedLogs.itemCount == 0) {
                LogEmptyView(isLogsEmpty = totalCount == 0)
            } else {
                LogViewerList(
                    pagedLogs = pagedLogs,
                    listState = listState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showClearDialog) {
        ClearLogsDialog(
            onConfirm = {
                viewModel.clearLogs()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerTopBar(
    onNavigateBack: () -> Unit,
    onClearLogs: () -> Unit
) {
    TopAppBar(
        title = { Text("App Log Viewer", color = LogColors.TextPrimary, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = LogColors.TextPrimary)
            }
        },
        actions = {
            IconButton(onClick = onClearLogs, modifier = Modifier.testTag("clear_logs_button")) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = LogColors.ErrorPink)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = LogColors.SurfaceDark)
    )
}

@Composable
fun LogViewerSearchInput(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("Filter logs by tag or text...", fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LogColors.TextSubtle) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = LogColors.TextSubtle)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().testTag("log_search_input"),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LogColors.PurpleLight,
            unfocusedBorderColor = LogColors.OutlineVariant,
            focusedContainerColor = LogColors.SurfaceContainer,
            unfocusedContainerColor = LogColors.SurfaceContainer,
            focusedTextColor = LogColors.TextPrimary,
            unfocusedTextColor = LogColors.TextPrimary
        )
    )
}

@Composable
fun LogViewerPriorityFilterChip(
    priorityVal: Int,
    label: String,
    isSelected: Boolean,
    onSelectPriority: (Int) -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = { onSelectPriority(priorityVal) },
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LogColors.PurpleLight,
            selectedLabelColor = LogColors.SurfacePurple,
            containerColor = LogColors.SurfaceContainer,
            labelColor = LogColors.TextSubtle
        )
    )
}

@Composable
fun LogViewerPriorityChips(
    selectedPriorities: Set<Int>,
    onTogglePriority: (Int) -> Unit
) {
    val priorities = remember {
        listOf(
            -1 to "All",
            Log.VERBOSE to "Verbose",
            Log.DEBUG to "Debug",
            Log.INFO to "Info",
            Log.WARN to "Warn",
            Log.ERROR to "Error"
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        priorities.forEach { (priorityVal, label) ->
            val isSelected = if (priorityVal == -1) selectedPriorities.isEmpty() else selectedPriorities.contains(priorityVal)
            LogViewerPriorityFilterChip(
                priorityVal = priorityVal,
                label = label,
                isSelected = isSelected,
                onSelectPriority = onTogglePriority
            )
        }
    }
}

@Composable
fun LogViewerCountHeader(
    filteredLogsCount: Int,
    totalLogsCount: Int,
    onJumpToBottom: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Showing $filteredLogsCount of $totalLogsCount log entries",
            color = LogColors.TextSubtle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        if (filteredLogsCount > 0) {
            TextButton(
                onClick = onJumpToBottom,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Jump to Bottom", fontSize = 11.sp, color = LogColors.PurpleLight)
            }
        }
    }
}

@Composable
fun LogViewerTopControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedPriorities: Set<Int>,
    onTogglePriority: (Int) -> Unit,
    filteredLogsCount: Int,
    totalLogsCount: Int,
    onJumpToBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        LogViewerSearchInput(searchQuery = searchQuery, onSearchQueryChange = onSearchQueryChange)
        LogViewerPriorityChips(selectedPriorities = selectedPriorities, onTogglePriority = onTogglePriority)
        LogViewerCountHeader(
            filteredLogsCount = filteredLogsCount,
            totalLogsCount = totalLogsCount,
            onJumpToBottom = onJumpToBottom
        )
    }
}

@Composable
fun LogEmptyView(isLogsEmpty: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = LogColors.OutlineGrey, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isLogsEmpty) "No log entries recorded yet" else "No matching log entries found",
                color = LogColors.TextSubtle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StreamingDot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(LogColors.PurpleLight.copy(alpha = alpha))
    )
}

@Composable
fun StreamingDotsAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "streaming_dots")
    val alpha1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                1.0f at 200
                0.3f at 600
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                0.3f at 300
                1.0f at 500
                0.3f at 900
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                0.3f at 600
                1.0f at 800
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamingDot(alpha = alpha1)
            StreamingDot(alpha = alpha2)
            StreamingDot(alpha = alpha3)
        }
    }
}

@Composable
fun LogViewerList(
    pagedLogs: LazyPagingItems<LogEntity>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.testTag("log_entries_list")
    ) {
        item(key = "streaming_dots") {
            StreamingDotsAnimation()
        }
        items(
            count = pagedLogs.itemCount,
            key = pagedLogs.itemKey { entry -> entry.id }
        ) { index ->
            val entry = pagedLogs[index]
            if (entry != null) {
                LogEntryCard(
                    modifier = Modifier.animateItem(),
                    entry = entry
                )
            }
        }
    }
}

@Composable
fun LogBadge(
    priorityLabel: String,
    badgeBg: Color,
    badgeFg: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = badgeBg
    ) {
        Text(
            text = priorityLabel,
            color = badgeFg,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun LogEntryHeader(
    entry: LogEntity,
    formattedTime: String,
    badgeBg: Color,
    badgeFg: Color,
    isExpandable: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LogBadge(priorityLabel = entry.priorityLabel(), badgeBg = badgeBg, badgeFg = badgeFg)
            Text(
                text = entry.tag ?: "Jules",
                color = LogColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formattedTime,
                color = LogColors.TextGrey,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            if (isExpandable) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = LogColors.TextSubtle
                    )
                }
            }
        }
    }
}

@Composable
fun LogEntryMessage(
    message: String,
    isLong: Boolean,
    isExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val displayText = remember(message, isExpanded, isLong) {
        if (!isExpanded && isLong) message.take(300) + "…" else message
    }

    SelectionContainer(modifier = modifier) {
        Text(
            text = displayText,
            color = LogColors.TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun LogEntryStackTrace(
    stackTrace: String,
    isLong: Boolean,
    isExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val displayText = remember(stackTrace, isExpanded, isLong) {
        if (!isExpanded && isLong) stackTrace.take(300) + "…" else stackTrace
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = LogColors.SurfaceDark,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
    ) {
        SelectionContainer(modifier = Modifier.padding(8.dp)) {
            Text(
                text = displayText,
                color = LogColors.ErrorPink,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun LogEntryCard(
    entry: LogEntity,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val isMessageLong = remember(entry.message) {
        entry.message.length > 300 || entry.message.count { it == '\n' } > 5
    }

    val isStackTraceLong = remember(entry.throwableStackTrace) {
        entry.throwableStackTrace?.run { length > 300 || count { it == '\n' } > 5 } == true
    }

    val isExpandable = isMessageLong || isStackTraceLong

    val (badgeBg, badgeFg) = remember(entry.priority) {
        when (entry.priority) {
            Log.ERROR -> LogColors.SurfaceRedDeep to LogColors.ErrorPink
            Log.WARN -> LogColors.SurfaceYellowDark to LogColors.YellowWarningLight
            Log.INFO -> LogColors.SurfaceDarkGreen to LogColors.GreenSuccess
            Log.DEBUG -> LogColors.SurfaceVariantDark to LogColors.PurpleLight
            else -> LogColors.SurfaceGreyDark to LogColors.TextSubtle
        }
    }

    val formattedTime = remember(entry.timestamp) {
        try {
            logTimeFormatter.format(Instant.ofEpochMilli(entry.timestamp))
        } catch (_: Exception) {
            entry.timestamp.toString()
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = LogColors.SurfaceContainer,
        border = BorderStroke(1.dp, LogColors.SurfaceVariantGrey),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isExpandable) {
                    Modifier.clickable { isExpanded = !isExpanded }
                } else {
                    Modifier
                }
            )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            LogEntryHeader(
                entry = entry,
                formattedTime = formattedTime,
                badgeBg = badgeBg,
                badgeFg = badgeFg,
                isExpandable = isExpandable,
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded }
            )
            Spacer(modifier = Modifier.height(6.dp))
            LogEntryMessage(
                message = entry.message,
                isLong = isMessageLong,
                isExpanded = isExpanded
            )
            if (!entry.throwableStackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                LogEntryStackTrace(
                    stackTrace = entry.throwableStackTrace,
                    isLong = isStackTraceLong,
                    isExpanded = isExpanded
                )
            }
        }
    }
}

@Composable
fun ClearLogsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear All Logs", fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to delete all stored diagnostic logs? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
