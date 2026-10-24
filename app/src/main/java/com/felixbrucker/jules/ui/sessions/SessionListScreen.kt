package com.felixbrucker.jules.ui.sessions

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.jules.R
import com.felixbrucker.jules.data.database.EmbeddedChangeSet
import com.felixbrucker.jules.data.database.EmbeddedPullRequest
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.SessionState
import kotlin.math.roundToInt
import androidx.core.net.toUri

@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onSettingsClick: () -> Unit
) {
    val sessions by viewModel.sessions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStates by viewModel.selectedStates.collectAsState()
    val includeArchived by viewModel.includeArchived.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SessionTopBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSearchActiveChange = { isSearchActive = it },
                onSettingsClick = onSettingsClick
            )
        }
    ) { innerPadding ->
        SessionListContent(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel,
            selectedStates = selectedStates,
            includeArchived = includeArchived,
            sessions = sessions
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                                (slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "TopBarSearchTransition"
            ) { active ->
                if (active) {
                    TopBarSearchField(
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onCloseSearch = {
                            onSearchQueryChange("")
                            onSearchActiveChange(false)
                        }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_jules),
                            contentDescription = "Jules Logo",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Jules")
                    }
                }
            }
        },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    )
}

@Composable
private fun TopBarSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BackHandler { onCloseSearch() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp)
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        TopBarSearchFieldContent(
            searchQuery = searchQuery,
            focusRequester = focusRequester,
            onSearchQueryChange = onSearchQueryChange,
            onCloseSearch = onCloseSearch,
            onSearchKeyboard = {
                keyboardController?.hide()
                if (searchQuery.isBlank()) {
                    onCloseSearch()
                }
            }
        )
    }
}

@Composable
private fun TopBarSearchFieldContent(
    searchQuery: String,
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onSearchKeyboard: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCloseSearch, modifier = Modifier.size(28.dp)) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchKeyboard() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(text = "Search title, prompt, repo...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    innerTextField()
                }
            }
        )
        IconButton(
            onClick = {
                if (searchQuery.isNotEmpty()) {
                    onSearchQueryChange("")
                } else {
                    onCloseSearch()
                }
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear or Close",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SessionListContent(
    modifier: Modifier = Modifier,
    viewModel: SessionListViewModel,
    selectedStates: Set<SessionState>,
    includeArchived: Boolean,
    sessions: List<SessionWithPullRequest>
) {
    val listState = rememberLazyListState()
    var filterBarHeightPx by remember { mutableFloatStateOf(0f) }
    var filterBarOffsetPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (filterBarHeightPx <= 0f) return Offset.Zero
                val oldOffset = filterBarOffsetPx
                val newOffset = (oldOffset + delta).coerceIn(-filterBarHeightPx, 0f)
                val consumedY = newOffset - oldOffset
                filterBarOffsetPx = newOffset
                return Offset(0f, consumedY)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index == 0 && offset == 0) {
                    filterBarOffsetPx = 0f
                }
            }
    }

    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = viewModel.isRefreshing,
        onRefresh = { viewModel.syncSessions() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            CollapsingFilterBar(
                filterBarHeightPx = filterBarHeightPx,
                filterBarOffsetPx = filterBarOffsetPx,
                onHeightMeasured = { filterBarHeightPx = it },
                onOffsetClamped = { filterBarOffsetPx = it },
                selectedStates = selectedStates,
                includeArchived = includeArchived,
                onToggleState = { viewModel.toggleStateFilter(it) },
                onToggleArchived = { viewModel.toggleIncludeArchived(it) }
            )
            SessionLazyColumn(
                listState = listState,
                sessions = sessions,
                onDelete = { viewModel.deleteSession(it) },
                onArchive = { viewModel.archiveSession(it) },
                onUnarchive = { viewModel.unarchiveSession(it) }
            )
        }
    }
}

@Composable
private fun CollapsingFilterBar(
    filterBarHeightPx: Float,
    filterBarOffsetPx: Float,
    onHeightMeasured: (Float) -> Unit,
    onOffsetClamped: (Float) -> Unit,
    selectedStates: Set<SessionState>,
    includeArchived: Boolean,
    onToggleState: (SessionState) -> Unit,
    onToggleArchived: (Boolean) -> Unit
) {
    FilterChipsRow(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val measuredHeight = placeable.height
                if (measuredHeight > 0 && filterBarHeightPx != measuredHeight.toFloat()) {
                    onHeightMeasured(measuredHeight.toFloat())
                    onOffsetClamped(filterBarOffsetPx.coerceIn(-measuredHeight.toFloat(), 0f))
                }
                val layoutHeight = (measuredHeight + filterBarOffsetPx.roundToInt()).coerceAtLeast(0)
                layout(placeable.width, layoutHeight) {
                    placeable.placeRelative(0, filterBarOffsetPx.roundToInt())
                }
            }
            .clipToBounds(),
        selectedStates = selectedStates,
        includeArchived = includeArchived,
        onToggleState = onToggleState,
        onToggleArchived = onToggleArchived
    )
}

@Composable
private fun FilterChipsRow(
    modifier: Modifier = Modifier,
    selectedStates: Set<SessionState>,
    includeArchived: Boolean,
    onToggleState: (SessionState) -> Unit,
    onToggleArchived: (Boolean) -> Unit
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SessionState.entries) { state ->
            FilterChip(
                selected = selectedStates.contains(state),
                onClick = { onToggleState(state) },
                label = { Text(state.label) }
            )
        }
        item {
            FilterChip(
                selected = includeArchived,
                onClick = { onToggleArchived(!includeArchived) },
                label = { Text("Archived") }
            )
        }
    }
}

@Composable
private fun SessionLazyColumn(
    listState: LazyListState,
    sessions: List<SessionWithPullRequest>,
    onDelete: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = sessions,
            key = { it.session.id }
        ) { item ->
            SessionCard(
                sessionWithPullRequest = item,
                onDelete = onDelete,
                onArchive = onArchive,
                onUnarchive = onUnarchive
            )
        }
    }
}

@Composable
private fun SessionCard(
    sessionWithPullRequest: SessionWithPullRequest,
    onDelete: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit
) {
    val session = sessionWithPullRequest.session
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            onConfirm = {
                showDeleteDialog = false
                onDelete(session.id)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            SessionCardHeader(session = session)
            Spacer(modifier = Modifier.height(8.dp))
            SessionCardPromptRow(
                prompt = session.prompt,
                isArchived = session.isArchived,
                onRequestDelete = { showDeleteDialog = true },
                onArchive = { onArchive(session.id) },
                onUnarchive = { onUnarchive(session.id) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SessionCardFooter(
                session = session,
                pullRequestState = sessionWithPullRequest.pullRequestState
            )
        }
    }
}

@Composable
private fun SessionCardPromptRow(
    prompt: String,
    isArchived: Boolean,
    onRequestDelete: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = prompt,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Box(modifier = Modifier.offset(x = 12.dp)) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Actions")
            }
            SessionActionDropdownMenu(
                expanded = menuExpanded,
                isArchived = isArchived,
                onDismiss = { menuExpanded = false },
                onRequestDelete = onRequestDelete,
                onArchive = onArchive,
                onUnarchive = onUnarchive
            )
        }
    }
}

@Composable
private fun SessionCardHeader(
    session: SessionEntity
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundedBadge(text = session.state.label, color = MaterialTheme.colorScheme.primaryContainer)
            if (session.isArchived) {
                RoundedBadge(text = "Archived", color = MaterialTheme.colorScheme.secondaryContainer)
            }
            Text(
                text = session.title ?: "Untitled Session",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        session.changeSet?.let { cs ->
            DiffBadges(changeSet = cs)
        }
    }
}

@Composable
private fun SessionActionDropdownMenu(
    expanded: Boolean,
    isArchived: Boolean,
    onDismiss: () -> Unit,
    onRequestDelete: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (isArchived) {
            DropdownMenuItem(
                text = { Text("Unarchive") },
                leadingIcon = { Icon(imageVector = Icons.Default.Unarchive, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onUnarchive()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Archive") },
                leadingIcon = { Icon(imageVector = Icons.Default.Archive, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onArchive()
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
            onClick = {
                onDismiss()
                onRequestDelete()
            }
        )
    }
}

@Composable
private fun DiffBadges(changeSet: EmbeddedChangeSet) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (changeSet.totalAdditions > 0) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF234C28)
            ) {
                Text(
                    text = "+${changeSet.totalAdditions}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFF6CF098),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        if (changeSet.totalDeletions > 0) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF4C2323)
            ) {
                Text(
                    text = "-${changeSet.totalDeletions}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFFFF8585),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun RoundedBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SessionCardFooter(
    session: SessionEntity,
    pullRequestState: PullRequestState?
) {
    val context = LocalContext.current
    val timeToFormat = session.updateTime ?: session.createTime
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        timeToFormat.toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val durationStr = formatDuration(session.duration.toMillis())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            session.repo?.let { repo ->
                val repoUrl = "https://github.com/${repo.owner}/${repo.name}"
                AssistChip(
                    modifier = Modifier.height(28.dp),
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, repoUrl.toUri())) },
                    label = { Text(repo.label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub Repo",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
            Text(text = "Updated $relativeTime", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Duration: $durationStr", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            session.pullRequest?.let { pr ->
                PullRequestChip(
                    pr = pr,
                    pullRequestState = pullRequestState,
                    onPrClick = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                )
            }
        }
    }
}

@Composable
private fun PullRequestChip(
    pr: EmbeddedPullRequest,
    pullRequestState: PullRequestState?,
    onPrClick: (String) -> Unit
) {
    val (iconRes, iconColor) = when (pullRequestState) {
        PullRequestState.MERGED -> R.drawable.ic_github_pr_merged to Color(0xFFA371F7)
        PullRequestState.CLOSED -> R.drawable.ic_github_pr_closed to Color(0xFFF85149)
        PullRequestState.OPEN, null -> R.drawable.ic_github_pr_open to Color(0xFF3FB950)
    }

    val prNumberText = pr.number?.let { " (#$it)" } ?: ""

    AssistChip(
        modifier = Modifier.height(28.dp),
        onClick = { onPrClick(pr.url) },
        label = { Text("View PR$prNumberText", style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = "GitHub PR",
                modifier = Modifier.size(14.dp),
                tint = iconColor
            )
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Session") },
        text = { Text("Are you sure you want to delete this session? This action cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "$hours h ${minutes % 60} m"
        minutes > 0 -> "$minutes m ${seconds % 60} s"
        else -> "$seconds s"
    }
}
