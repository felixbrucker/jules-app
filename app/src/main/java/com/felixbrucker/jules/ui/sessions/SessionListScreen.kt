package com.felixbrucker.jules.ui.sessions

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RateReview
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.felixbrucker.jules.data.database.SessionDraftWithSource
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
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
    val drafts by viewModel.drafts.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedChips by viewModel.selectedChips.collectAsState()
    val includeArchived by viewModel.includeArchived.collectAsState()
    val includeDrafts by viewModel.includeDrafts.collectAsState()
    val dailyCount by viewModel.dailySessionCount.collectAsState()
    val dailyLimit by viewModel.dailySessionLimit.collectAsState()

    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val activeDraft by viewModel.activeDraft.collectAsState()
    val isCreatingSession by viewModel.isCreatingSession.collectAsState()
    val createSessionError by viewModel.createSessionError.collectAsState()
    val dispatchState by viewModel.dispatchDialogState.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }

    if (showCreateDialog && activeDraft != null) {
        val activeDraftEntity = activeDraft!!
        val isPersistedDraft = remember(activeDraftEntity.id, drafts) {
            drafts.any { it.draft.id == activeDraftEntity.id }
        }
        CreateSessionDialog(
            draft = activeDraftEntity,
            sources = sources,
            isCreating = isCreatingSession,
            isPersistedDraft = isPersistedDraft,
            errorMessage = createSessionError,
            onDismiss = { viewModel.dismissCreateDialog() },
            onSaveDraft = { viewModel.saveDraft(it) },
            onCreateSession = { viewModel.createSessionFromDraft(it) }
        )
    }

    dispatchState?.let { state ->
        val source = remember(state.session.sourceId, sources) {
            sources.find { it.id == state.session.sourceId }
        }
        DispatchWorkflowDialog(
            session = state.session,
            workflow = state.workflow,
            source = source,
            isDispatching = state.isDispatching,
            errorMessage = state.errorMessage,
            onDismiss = { viewModel.dismissDispatchDialog() },
            onConfirmDispatch = { branch, inputs ->
                viewModel.dispatchWorkflow(state.session, state.workflow, branch, inputs)
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SessionTopBar(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                dailyCount = dailyCount,
                dailyLimit = dailyLimit,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSearchActiveChange = { isSearchActive = it },
                onSettingsClick = onSettingsClick
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openCreateDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create Session")
            }
        }
    ) { innerPadding ->
        SessionListContent(
            modifier = Modifier.padding(innerPadding),
            viewModel = viewModel,
            selectedChips = selectedChips,
            includeArchived = includeArchived,
            includeDrafts = includeDrafts,
            drafts = drafts,
            sessions = sessions
        )
    }
}

@Composable
private fun SessionTopBar(
    isSearchActive: Boolean,
    searchQuery: String,
    dailyCount: Int,
    dailyLimit: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    SessionTopAppBar(
        isSearchActive = isSearchActive,
        searchQuery = searchQuery,
        dailyCount = dailyCount,
        dailyLimit = dailyLimit,
        onSearchQueryChange = onSearchQueryChange,
        onSearchActiveChange = onSearchActiveChange,
        onSettingsClick = onSettingsClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopAppBar(
    isSearchActive: Boolean,
    searchQuery: String,
    dailyCount: Int,
    dailyLimit: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            TopAppBarTitleContent(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onSearchActiveChange = onSearchActiveChange
            )
        },
        actions = {
            if (!isSearchActive) {
                DailySessionLimitIndicator(
                    dailyCount = dailyCount,
                    dailyLimit = dailyLimit
                )
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
private fun DailySessionLimitIndicator(
    dailyCount: Int,
    dailyLimit: Int
) {
    val progress = (dailyCount.toFloat() / dailyLimit.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .width(90.dp)
            .padding(end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            drawStopIndicator = {}
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Limit ($dailyCount/$dailyLimit)",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopAppBarTitleContent(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit
) {
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
    selectedChips: Set<SessionFilterChip>,
    includeArchived: Boolean,
    includeDrafts: Boolean,
    drafts: List<SessionDraftWithSource>,
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
                selectedChips = selectedChips,
                includeArchived = includeArchived,
                includeDrafts = includeDrafts,
                onToggleChip = { viewModel.toggleFilterChip(it) },
                onToggleArchived = { viewModel.toggleIncludeArchived(it) },
                onToggleDrafts = { viewModel.toggleIncludeDrafts(it) }
            )
            SessionLazyColumn(
                listState = listState,
                drafts = drafts,
                sessions = sessions,
                onCardClickDraft = { viewModel.openCreateDialog(it.draft) },
                onDeleteDraft = { viewModel.deleteDraft(it) },
                onDelete = { viewModel.deleteSession(it) },
                onArchive = { viewModel.archiveSession(it) },
                onUnarchive = { viewModel.unarchiveSession(it) },
                onSelectWorkflow = { session, workflow -> viewModel.openDispatchDialog(session, workflow) }
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
    selectedChips: Set<SessionFilterChip>,
    includeArchived: Boolean,
    includeDrafts: Boolean,
    onToggleChip: (SessionFilterChip) -> Unit,
    onToggleArchived: (Boolean) -> Unit,
    onToggleDrafts: (Boolean) -> Unit
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
        selectedChips = selectedChips,
        includeArchived = includeArchived,
        includeDrafts = includeDrafts,
        onToggleChip = onToggleChip,
        onToggleArchived = onToggleArchived,
        onToggleDrafts = onToggleDrafts
    )
}

@Composable
private fun FilterChipsRow(
    modifier: Modifier = Modifier,
    selectedChips: Set<SessionFilterChip>,
    includeArchived: Boolean,
    includeDrafts: Boolean,
    onToggleChip: (SessionFilterChip) -> Unit,
    onToggleArchived: (Boolean) -> Unit,
    onToggleDrafts: (Boolean) -> Unit
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SessionFilterChip.entries) { chip ->
            FilterChip(
                selected = selectedChips.contains(chip),
                onClick = { onToggleChip(chip) },
                label = { Text(chip.label) },
                leadingIcon = { FilterChipLeadingIcon(chip = chip) }
            )
        }
        item {
            FilterChip(
                selected = includeDrafts,
                onClick = { onToggleDrafts(!includeDrafts) },
                label = { Text("Drafts") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        item {
            FilterChip(
                selected = includeArchived,
                onClick = { onToggleArchived(!includeArchived) },
                label = { Text("Archived") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun FilterChipLeadingIcon(chip: SessionFilterChip) {
    when (chip) {
        SessionFilterChip.IN_PROGRESS -> Icon(
            imageVector = Icons.Default.HourglassTop,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        SessionFilterChip.AWAITING_FEEDBACK -> Icon(
            imageVector = Icons.Default.RateReview,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        SessionFilterChip.COMPLETED -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SessionLazyColumn(
    listState: LazyListState,
    drafts: List<SessionDraftWithSource>,
    sessions: List<SessionWithPullRequest>,
    onCardClickDraft: (SessionDraftWithSource) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onDelete: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onSelectWorkflow: (SessionEntity, DispatchableWorkflowEntity) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = drafts,
            key = { "draft_${it.draft.id}" }
        ) { draftItem ->
            SessionDraftCard(
                modifier = Modifier.animateItem(),
                draftWithSource = draftItem,
                onCardClick = { onCardClickDraft(draftItem) },
                onDeleteDraft = { onDeleteDraft(draftItem.draft.id) }
            )
        }
        items(
            items = sessions,
            key = { it.session.id }
        ) { item ->
            SessionCard(
                modifier = Modifier.animateItem(),
                sessionWithPullRequest = item,
                onDelete = onDelete,
                onArchive = onArchive,
                onUnarchive = onUnarchive,
                onSelectWorkflow = onSelectWorkflow
            )
        }
    }
}

@Composable
private fun SessionCard(
    modifier: Modifier = Modifier,
    sessionWithPullRequest: SessionWithPullRequest,
    onDelete: (String) -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onSelectWorkflow: (SessionEntity, DispatchableWorkflowEntity) -> Unit
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

    Card(modifier = modifier.fillMaxWidth()) {
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
                pullRequestState = sessionWithPullRequest.pullRequestState,
                workflows = sessionWithPullRequest.workflows,
                onSelectWorkflow = { wf -> onSelectWorkflow(session, wf) }
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
            SessionStateBadge(state = session.state)
            if (session.isArchived) {
                ArchivedBadge()
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
private fun SessionStateBadge(state: SessionState) {
    val style = getSessionStateStyle(state)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = style.containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (style.isAnimatedDots) {
                AnimatedMovingDotsIcon(color = style.contentColor)
            } else style.icon?.let { iconVec ->
                Icon(
                    imageVector = iconVec,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = style.contentColor
                )
            }
            Text(
                text = state.label,
                color = style.contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ArchivedBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF374151)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Archive,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color(0xFFD1D5DB)
            )
            Text(
                text = "Archived",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

data class StateBadgeStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector? = null,
    val isAnimatedDots: Boolean = false
)

private fun getSessionStateStyle(state: SessionState): StateBadgeStyle {
    return when (state) {
        SessionState.IN_PROGRESS -> StateBadgeStyle(
            containerColor = Color(0xFF1E3A8A),
            contentColor = Color(0xFF93C5FD),
            isAnimatedDots = true
        )
        SessionState.AWAITING_PLAN_APPROVAL, SessionState.AWAITING_USER_FEEDBACK -> StateBadgeStyle(
            containerColor = Color(0xFF78350F),
            contentColor = Color(0xFFFDE68A),
            icon = Icons.Default.RateReview
        )
        SessionState.COMPLETED -> StateBadgeStyle(
            containerColor = Color(0xFF14532D),
            contentColor = Color(0xFF86EFAC),
            icon = Icons.Default.CheckCircle
        )
        SessionState.FAILED -> StateBadgeStyle(
            containerColor = Color(0xFF7F1D1D),
            contentColor = Color(0xFFFCA5A5),
            icon = Icons.Default.Error
        )
        SessionState.QUEUED, SessionState.PLANNING -> StateBadgeStyle(
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF94A3B8),
            icon = Icons.Default.HourglassEmpty
        )
        SessionState.PAUSED -> StateBadgeStyle(
            containerColor = Color(0xFF374151),
            contentColor = Color(0xFFD1D5DB),
            icon = Icons.Default.PauseCircle
        )
        else -> StateBadgeStyle(
            containerColor = Color(0xFF374151),
            contentColor = Color(0xFFD1D5DB),
            icon = Icons.AutoMirrored.Filled.Help
        )
    }
}

@Composable
fun AnimatedMovingDotsIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DotsTransition")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
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
        label = "Dot1Alpha"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
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
        label = "Dot2Alpha"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
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
        label = "Dot3Alpha"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer { alpha = dot1Alpha }
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer { alpha = dot2Alpha }
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer { alpha = dot3Alpha }
                .background(color, CircleShape)
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCardFooter(
    session: SessionEntity,
    pullRequestState: PullRequestState?,
    workflows: List<DispatchableWorkflowEntity>,
    onSelectWorkflow: (DispatchableWorkflowEntity) -> Unit
) {
    val context = LocalContext.current
    val timeToFormat = session.updateTime ?: session.createTime
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        timeToFormat.toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
    val durationStr = formatDuration(session.duration.toMillis())

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = Int.MAX_VALUE
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
                    workflows = workflows,
                    onPrClick = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                    onWorkflowSelected = onSelectWorkflow
                )
            }
        }
    }
}

@Composable
private fun PullRequestChip(
    pr: EmbeddedPullRequest,
    pullRequestState: PullRequestState?,
    workflows: List<DispatchableWorkflowEntity>,
    onPrClick: (String) -> Unit,
    onWorkflowSelected: (DispatchableWorkflowEntity) -> Unit
) {
    if (workflows.isEmpty()) {
        SimplePullRequestChip(
            pr = pr,
            pullRequestState = pullRequestState,
            onPrClick = onPrClick
        )
    } else {
        CompoundPullRequestWorkflowButton(
            pr = pr,
            pullRequestState = pullRequestState,
            workflows = workflows,
            onPrClick = onPrClick,
            onWorkflowSelected = onWorkflowSelected
        )
    }
}

@Composable
private fun SimplePullRequestChip(
    pr: EmbeddedPullRequest,
    pullRequestState: PullRequestState?,
    onPrClick: (String) -> Unit
) {
    val (iconRes, iconColor) = getPullRequestIconInfo(pullRequestState)
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
private fun CompoundPullRequestWorkflowButton(
    pr: EmbeddedPullRequest,
    pullRequestState: PullRequestState?,
    workflows: List<DispatchableWorkflowEntity>,
    onPrClick: (String) -> Unit,
    onWorkflowSelected: (DispatchableWorkflowEntity) -> Unit
) {
    val (iconRes, iconColor) = getPullRequestIconInfo(pullRequestState)
    val prNumberText = pr.number?.let { " (#$it)" } ?: ""
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onPrClick(pr.url) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "GitHub PR",
                    modifier = Modifier.size(14.dp),
                    tint = iconColor
                )
                Text(
                    text = "View PR$prNumberText",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Workflow Dispatches",
                    modifier = Modifier.size(18.dp)
                )
                WorkflowDropdownMenu(
                    expanded = menuExpanded,
                    workflows = workflows,
                    onDismiss = { menuExpanded = false },
                    onWorkflowSelected = onWorkflowSelected
                )
            }
        }
    }
}

@Composable
private fun WorkflowDropdownMenu(
    expanded: Boolean,
    workflows: List<DispatchableWorkflowEntity>,
    onDismiss: () -> Unit,
    onWorkflowSelected: (DispatchableWorkflowEntity) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        workflows.forEach { workflow ->
            DropdownMenuItem(
                text = { Text(workflow.name) },
                onClick = {
                    onDismiss()
                    onWorkflowSelected(workflow)
                }
            )
        }
    }
}

private fun getPullRequestIconInfo(pullRequestState: PullRequestState?): Pair<Int, Color> {
    return when (pullRequestState) {
        PullRequestState.MERGED -> R.drawable.ic_github_pr_merged to Color(0xFFA371F7)
        PullRequestState.CLOSED -> R.drawable.ic_github_pr_closed to Color(0xFFF85149)
        PullRequestState.OPEN, null -> R.drawable.ic_github_pr_open to Color(0xFF3FB950)
    }
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
