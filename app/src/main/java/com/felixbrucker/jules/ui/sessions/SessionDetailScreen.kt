package com.felixbrucker.jules.ui.sessions

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.felixbrucker.jules.R
import com.felixbrucker.jules.data.database.ActivityBasedStateEntity
import com.felixbrucker.jules.data.database.AgentState
import com.felixbrucker.jules.data.database.ChatElement
import com.felixbrucker.jules.data.database.ChatElementEntity
import com.felixbrucker.jules.data.database.ChatStateEntity
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.network.api.model.FileChangeType
import com.felixbrucker.jules.network.api.model.ParsedFile
import com.felixbrucker.jules.network.api.model.PlanStep
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.ui.components.AnimatedMovingDotsIcon
import com.felixbrucker.jules.ui.components.ChatMarkdown
import com.felixbrucker.jules.ui.components.DiffBadges
import com.felixbrucker.jules.ui.components.PullRequestChip
import com.felixbrucker.jules.ui.components.SessionStateBadge
import com.felixbrucker.jules.ui.icon.ChatIcon
import com.felixbrucker.jules.ui.icon.DifferenceIcon

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBackClick: () -> Unit
) {
    val sessionWithPr by viewModel.sessionWithPullRequestFlow.collectAsState(initial = null)
    val chatState by viewModel.chatStateFlow.collectAsState()
    val activityBasedState by viewModel.activityBasedStateFlow.collectAsState()
    val fileTree by viewModel.fileTreeFlow.collectAsState()
    val fileDiffLinesMap by viewModel.fileDiffLinesMapFlow.collectAsState()
    val chatElements = viewModel.chatElementsPagingDataFlow.collectAsLazyPagingItems()
    val splitRatio by viewModel.splitRatio.collectAsState()
    val isInitialSyncing by viewModel.isInitialSyncing.collectAsState()
    val isSendingMessage by viewModel.isSendingMessage.collectAsState()
    val isApprovingPlan by viewModel.isApprovingPlan.collectAsState()
    val dispatchState by viewModel.dispatchDialogState.collectAsState()
    val errorEvent by viewModel.errorEvents.collectAsState(initial = null)

    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.startPolling()
    }

    LaunchedEffect(isPhone) {
        if (isPhone) {
            viewModel.updateSplitRatio(1.0f)
        }
    }

    LaunchedEffect(errorEvent) {
        errorEvent?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val session = sessionWithPr?.session

    dispatchState?.let { state ->
        DispatchWorkflowDialog(
            session = state.session,
            workflow = state.workflow,
            source = null,
            isDispatching = state.isDispatching,
            errorMessage = state.errorMessage,
            onDismiss = { viewModel.dismissDispatchDialog() },
            onConfirmDispatch = { branch, inputs ->
                viewModel.dispatchWorkflow(state.session, state.workflow, branch, inputs)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            session?.let {
                SessionDetailTopBar(
                    sessionWithPr = sessionWithPr!!,
                    activityBasedState = activityBasedState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    isPhone = isPhone,
                    splitRatio = splitRatio,
                    onToggleViewMode = {
                        val nextRatio = if (splitRatio > 0.0f) 0.0f else 1.0f
                        viewModel.updateSplitRatio(nextRatio)
                    },
                    onBackClick = onBackClick,
                    onArchive = { viewModel.archiveSession() },
                    onUnarchive = { viewModel.unarchiveSession() },
                    onDelete = { viewModel.deleteSession(onBackClick) },
                    onSelectWorkflow = { wf -> viewModel.openDispatchDialog(it, wf) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            session?.let {
                SessionDetailSplitContent(
                    session = session,
                    chatElements = chatElements,
                    chatState = chatState,
                    activityBasedState = activityBasedState,
                    fileTree = fileTree,
                    fileDiffLinesMap = fileDiffLinesMap,
                    splitRatio = splitRatio,
                    isPhone = isPhone,
                    isInitialSyncing = isInitialSyncing,
                    isSendingMessage = isSendingMessage,
                    isApprovingPlan = isApprovingPlan,
                    onSplitRatioChange = { viewModel.updateSplitRatio(it) },
                    onSendMessage = { viewModel.sendMessage(it) },
                    onApprovePlan = { viewModel.approvePlan() }
                )
            }
        }
    }
}


@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SessionDetailTopBar(
    sessionWithPr: SessionWithPullRequest,
    activityBasedState: ActivityBasedStateEntity?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isPhone: Boolean,
    splitRatio: Float,
    onToggleViewMode: () -> Unit,
    onBackClick: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onSelectWorkflow: (DispatchableWorkflowEntity) -> Unit
) {
    val session = sessionWithPr.session
    var menuExpanded by remember { mutableStateOf(false) }

    val parsedFiles = activityBasedState?.latestDiff?.parsedFilesMap?.values
    val totalAdditions = parsedFiles?.sumOf { it.additions } ?: session.changeSet?.totalAdditions ?: 0
    val totalDeletions = parsedFiles?.sumOf { it.deletions } ?: session.changeSet?.totalDeletions ?: 0
    val context = LocalContext.current

    Surface(
        modifier = with(sharedTransitionScope) {
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "container_${session.id}"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
        },
        tonalElevation = 2.dp
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                SessionStateBadge(
                    state = session.state,
                    sessionId = session.id,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )

                session.repo?.let { repo ->
                    Row(
                        modifier = with(sharedTransitionScope) {
                            Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "source_${session.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                        }.clickable {
                            val repoUrl = "https://github.com/${repo.owner}/${repo.name}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, repoUrl.toUri()))
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub Repo",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = repo.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = session.title ?: "Untitled",
                    modifier = with(sharedTransitionScope) {
                        Modifier
                            .weight(1f, fill = false)
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(key = "title_${session.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                session.pullRequest?.let { pr ->
                    Box(
                        modifier = with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = "pr_badge_${session.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    ) {
                        PullRequestChip(
                            pr = pr,
                            pullRequestState = sessionWithPr.pullRequestState,
                            workflows = sessionWithPr.workflows,
                            onPrClick = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                            onWorkflowSelected = onSelectWorkflow
                        )
                    }
                }

                if (totalAdditions > 0 || totalDeletions > 0) {
                    Box(
                        modifier = with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = "changes_${session.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    ) {
                        DiffBadges(additions = totalAdditions, deletions = totalDeletions)
                    }
                }

                if (isPhone) {
                    IconButton(
                        onClick = onToggleViewMode,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (splitRatio > 0.0f) DifferenceIcon else ChatIcon,
                            contentDescription = if (splitRatio > 0.0f) "Show diffs" else "Show chat"
                        )
                    }
                }

                Box(
                    modifier = with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "menu_${session.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                ) {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                    SessionActionDropdown(
                        expanded = menuExpanded,
                        isArchived = session.isArchived,
                        onDismiss = { menuExpanded = false },
                        onArchive = onArchive,
                        onUnarchive = onUnarchive,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionActionDropdown(
    expanded: Boolean,
    isArchived: Boolean,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (isArchived) {
            DropdownMenuItem(
                text = { Text("Unarchive") },
                leadingIcon = { Icon(imageVector = Icons.Default.Unarchive, contentDescription = null) },
                onClick = { onDismiss(); onUnarchive() }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Archive") },
                leadingIcon = { Icon(imageVector = Icons.Default.Archive, contentDescription = null) },
                onClick = { onDismiss(); onArchive() }
            )
        }
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

@Composable
private fun SessionDetailSplitContent(
    session: SessionEntity,
    chatElements: LazyPagingItems<ChatElementUiItem>,
    chatState: ChatStateEntity?,
    activityBasedState: ActivityBasedStateEntity?,
    fileTree: List<FileTreeNode>,
    fileDiffLinesMap: Map<String, List<ParsedDiffLine>>,
    splitRatio: Float,
    isPhone: Boolean,
    isInitialSyncing: Boolean,
    isSendingMessage: Boolean,
    isApprovingPlan: Boolean,
    onSplitRatioChange: (Float) -> Unit,
    onSendMessage: (String) -> Unit,
    onApprovePlan: () -> Unit
) {
    var containerWidthPx by remember { mutableStateOf(0) }
    var chatInputText by rememberSaveable(session.id) { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerWidthPx = it.size.width }
    ) {
        if (isPhone) {
            if (splitRatio > 0.0f) {
                ChatWindowView(
                    session = session,
                    chatElements = chatElements,
                    chatState = chatState,
                    activityBasedState = activityBasedState,
                    chatInputText = chatInputText,
                    onChatInputTextChange = { chatInputText = it },
                    isInitialSyncing = isInitialSyncing,
                    isSendingMessage = isSendingMessage,
                    isApprovingPlan = isApprovingPlan,
                    onSendMessage = {
                        onSendMessage(it)
                        chatInputText = ""
                    },
                    onApprovePlan = onApprovePlan
                )
            } else {
                FileDiffView(
                    activityBasedState = activityBasedState,
                    fileTree = fileTree,
                    fileDiffLinesMap = fileDiffLinesMap,
                    isFullScreen = false
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(splitRatio.coerceAtLeast(0.0001f))) {
                    if (splitRatio > 0.0f) {
                        ChatWindowView(
                            session = session,
                            chatElements = chatElements,
                            chatState = chatState,
                            activityBasedState = activityBasedState,
                            chatInputText = chatInputText,
                            onChatInputTextChange = { chatInputText = it },
                            isInitialSyncing = isInitialSyncing,
                            isSendingMessage = isSendingMessage,
                            isApprovingPlan = isApprovingPlan,
                            onSendMessage = {
                                onSendMessage(it)
                                chatInputText = ""
                            },
                            onApprovePlan = onApprovePlan
                        )
                    }
                }

                SplitDividerHandle(
                    containerWidthPx = containerWidthPx,
                    splitRatio = splitRatio,
                    onSplitRatioChange = onSplitRatioChange
                )

                Box(modifier = Modifier.weight((1.0f - splitRatio).coerceAtLeast(0.0001f))) {
                    if (splitRatio < 1.0f) {
                        FileDiffView(
                            activityBasedState = activityBasedState,
                            fileTree = fileTree,
                            fileDiffLinesMap = fileDiffLinesMap,
                            isFullScreen = (splitRatio == 0.0f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitDividerHandle(
    containerWidthPx: Int,
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit
) {
    val currentOnSplitRatioChange by rememberUpdatedState(onSplitRatioChange)
    val currentSplitRatio by rememberUpdatedState(splitRatio)

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(20.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(Unit) {
                var rawDragRatio = 0f
                detectHorizontalDragGestures(
                    onDragStart = { rawDragRatio = currentSplitRatio },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (containerWidthPx > 0) {
                            rawDragRatio = (rawDragRatio + dragAmount / containerWidthPx.toFloat()).coerceIn(0f, 1f)
                            currentOnSplitRatioChange(rawDragRatio)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun ChatWindowView(
    session: SessionEntity,
    chatElements: LazyPagingItems<ChatElementUiItem>,
    chatState: ChatStateEntity?,
    activityBasedState: ActivityBasedStateEntity?,
    chatInputText: String,
    onChatInputTextChange: (String) -> Unit,
    isInitialSyncing: Boolean,
    isSendingMessage: Boolean,
    isApprovingPlan: Boolean,
    onSendMessage: (String) -> Unit,
    onApprovePlan: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var initialScrolledSessionId by remember { mutableStateOf<String?>(null) }

    val latestItemId = chatElements.itemSnapshotList.items.firstOrNull()?.id
    val itemCount = chatElements.itemCount

    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 3
        }
    }

    var hasUnreadNewElements by remember { mutableStateOf(false) }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            hasUnreadNewElements = false
        }
    }

    LaunchedEffect(session.id, latestItemId, itemCount) {
        if (itemCount > 0) {
            if (initialScrolledSessionId != session.id) {
                initialScrolledSessionId = session.id
                hasUnreadNewElements = false
                listState.scrollToItem(0)
            } else if (latestItemId != null) {
                if (isAtBottom) {
                    listState.animateScrollToItem(0)
                } else {
                    hasUnreadNewElements = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (chatState?.agentState == AgentState.Thinking) {
                        item(key = "agent_thinking_indicator") {
                            AgentStatusBubble(label = "Thinking")
                        }
                    } else if (chatState?.agentState == AgentState.Working) {
                        item(key = "agent_working_indicator") {
                            AgentStatusBubble(label = "Working")
                        }
                    }

                    items(
                        count = chatElements.itemCount,
                        key = chatElements.itemKey { it.id }
                    ) { index ->
                        val item = chatElements[index]
                        if (item != null) {
                            ChatElementRow(
                                item = item,
                                session = session,
                                activityBasedState = activityBasedState
                            )
                        }
                    }
                }
            }

            val latestElement = chatElements.itemSnapshotList.items
                .filterIsInstance<ChatElementUiItem.ElementItem>()
                .firstOrNull()
                ?.elementEntity
                ?.element

            val showApprovePlan = latestElement is ChatElement.PlanElement && chatState?.hasApprovedPlan != true

            if (showApprovePlan) {
                ApprovePlanBottomBar(
                    isApprovingPlan = isApprovingPlan,
                    onApprovePlan = onApprovePlan
                )
            }

            ChatInputBottomBar(
                textInput = chatInputText,
                onTextInputChange = onChatInputTextChange,
                isSending = isSendingMessage,
                onSendMessage = onSendMessage
            )
        }

        AnimatedVisibility(
            visible = hasUnreadNewElements && !isAtBottom,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                            hasUnreadNewElements = false
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "New messages",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (isInitialSyncing) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading session activities...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatElementRow(
    item: ChatElementUiItem,
    session: SessionEntity,
    activityBasedState: ActivityBasedStateEntity?
) {
    when (item) {
        is ChatElementUiItem.RelativeTimeDivider -> {
            CenteredTimeDivider(timeText = item.relativeTimeText)
        }
        is ChatElementUiItem.ElementItem -> {
            ChatElementBubbleContainer(
                element = item.elementEntity.element,
                session = session,
                activityBasedState = activityBasedState
            )
        }
    }
}

@Composable
private fun CenteredTimeDivider(timeText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChatElementBubbleContainer(
    element: ChatElement,
    session: SessionEntity,
    activityBasedState: ActivityBasedStateEntity?
) {
    SelectionContainer {
        when (element) {
            is ChatElement.UserMessageElement -> UserMessageBubble(message = element.message)
            is ChatElement.AgentMessageBlockElement -> {
                ProgressUpdatedBubble(
                    title = element.titleMarkdown,
                    description = element.detailMarkdown
                )
            }
            is ChatElement.AgentThoughtElement -> {
                AgentThoughtBubble(
                    thoughts = element.thoughts,
                    durationText = "${element.thoughtDuration.inWholeSeconds}s"
                )
            }
            is ChatElement.ChangedFilesElement -> {
                ChangedFilesBubble(changedFiles = element.changedFiles)
            }
            is ChatElement.PlanElement -> PlanGeneratedCard(planSteps = element.steps)
            is ChatElement.PlanApprovedElement -> PlanApprovedPill()
            is ChatElement.SessionFailedElement -> SessionFailedBox(reason = element.reason)
            is ChatElement.SessionCompletedElement -> SessionCompletedSummaryBox(
                session = session,
                element = element
            )
        }
    }
}

@Composable
private fun UserMessageBubble(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                ChatMarkdown(
                    markdown = message,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun AgentThoughtBubble(thoughts: String, durationText: String) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                .clickable { isExpanded = !isExpanded }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Thought for $durationText",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        ChatMarkdown(markdown = thoughts)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangedFilesBubble(changedFiles: List<String>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "Changed files (${changedFiles.size})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            changedFiles.forEach { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
private fun AgentStatusBubble(label: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedMovingDotsIcon(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProgressUpdatedBubble(
    title: String?,
    description: String?
) {
    val hasBoth = !title.isNullOrBlank() && !description.isNullOrBlank()
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = if (hasBoth) {
                Modifier
                    .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                    .clickable { isExpanded = !isExpanded }
            } else Modifier
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (hasBoth) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ChatMarkdown(
                                markdown = title ?: "",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                onClick = { isExpanded = !isExpanded }
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            ChatMarkdown(
                                markdown = description ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    title?.let {
                        ChatMarkdown(
                            markdown = it,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    description?.let {
                        ChatMarkdown(
                            markdown = it,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanGeneratedCard(planSteps: List<PlanStep>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Plan (${planSteps.size} steps)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            planSteps.forEachIndexed { idx, step ->
                PlanStepItemRow(stepIndex = idx + 1, step = step)
            }
        }
    }
}

@Composable
private fun PlanStepItemRow(stepIndex: Int, step: PlanStep) {
    var isExpanded by remember { mutableStateOf(false) }
    val hasDesc = !step.description.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = hasDesc) { isExpanded = !isExpanded }
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$stepIndex",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                ChatMarkdown(
                    markdown = step.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    onClick = if (hasDesc) { { isExpanded = !isExpanded } } else null
                )
            }
            if (hasDesc) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (hasDesc) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.padding(start = 32.dp)) {
                        ChatMarkdown(
                            markdown = step.description.orEmpty(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanApprovedPill() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = "Plan approved 🎉",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun SessionFailedBox(reason: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Failed",
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Session failed: $reason",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SessionCompletedSummaryBox(
    session: SessionEntity,
    element: ChatElement.SessionCompletedElement
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ready for review 🎉",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF86EFAC)
                )
                if (element.totalAdditions > 0 || element.totalDeletions > 0) {
                    DiffBadges(additions = element.totalAdditions, deletions = element.totalDeletions)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            session.pullRequest?.let { pr ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github_branch),
                            contentDescription = "Branch",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = pr.headRef ?: pr.baseRef ?: "pr-branch",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            element.suggestedCommitMessage?.let { commitMsg ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = commitMsg,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovePlanBottomBar(
    isApprovingPlan: Boolean,
    onApprovePlan: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onApprovePlan,
                enabled = !isApprovingPlan,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isApprovingPlan) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Approving...")
                } else {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve plan")
                }
            }
        }
    }
}

@Composable
private fun ChatInputBottomBar(
    textInput: String,
    onTextInputChange: (String) -> Unit,
    isSending: Boolean,
    onSendMessage: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = textInput,
                onValueChange = onTextInputChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (textInput.isEmpty()) {
                            Text("Type a message...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (textInput.isNotBlank() && !isSending) {
                        onSendMessage(textInput)
                    }
                },
                enabled = textInput.isNotBlank() && !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (textInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val diffAdditionBg = Color(0xFF163B21)
private val diffAdditionFg = Color(0xFF7EE787)
private val diffDeletionBg = Color(0xFF3C1E1E)
private val diffDeletionFg = Color(0xFFFF7B72)
private val diffContextBg = Color.Transparent
private val diffContextFg = Color(0xFFC9D1D9)
private val diffLineNumFg = Color(0xFF6E7681)
private val diffCodeDarkBg = Color(0xFF0D1117)

@Composable
private fun FileDiffView(
    activityBasedState: ActivityBasedStateEntity?,
    fileTree: List<FileTreeNode>,
    fileDiffLinesMap: Map<String, List<ParsedDiffLine>>,
    isFullScreen: Boolean
) {
    val expandedCards = remember { mutableStateMapOf<String, Boolean>() }
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    var selectedFilePath by remember { mutableStateOf<String?>(null) }

    val parsedFiles = activityBasedState?.latestDiff?.parsedFilesMap?.values?.toList() ?: emptyList()

    if (parsedFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No changeset diff available", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LaunchedEffect(selectedFilePath) {
        val path = selectedFilePath ?: return@LaunchedEffect
        var targetIndex = 0
        for (file in parsedFiles) {
            if (file.path == path) break
            targetIndex += 1
            if (expandedCards[file.path] == true) {
                targetIndex += (fileDiffLinesMap[file.path]?.size ?: 0)
            }
        }
        kotlinx.coroutines.yield()
        listState.animateScrollToItem(targetIndex)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                parsedFiles.forEach { file ->
                    val isExpanded = expandedCards[file.path] ?: false
                    val lines = fileDiffLinesMap[file.path] ?: emptyList()

                    item(key = "header_${file.path}") {
                        Box(modifier = Modifier.padding(top = 8.dp)) {
                            FileDiffHeaderCard(
                                file = file,
                                isExpanded = isExpanded,
                                onToggleExpand = { expandedCards[file.path] = !isExpanded }
                            )
                        }
                    }

                    if (isExpanded && lines.isNotEmpty()) {
                        items(
                            count = lines.size,
                            key = { index -> "line_${file.path}_$index" }
                        ) { index ->
                            DiffLineRow(
                                line = lines[index],
                                isLast = index == lines.size - 1
                            )
                        }
                    }
                }
            }
        }

        if (isFullScreen) {
            val hasExpandedDiffs = parsedFiles.any { expandedCards[it.path] == true }
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(8.dp)
            ) {
                ScrollableFileTree(
                    fileTree = fileTree,
                    expandedFolders = expandedFolders,
                    hasExpandedDiffs = hasExpandedDiffs,
                    onToggleFolder = { folder ->
                        expandedFolders[folder] = !(expandedFolders[folder] ?: true)
                    },
                    onToggleAllCards = {
                        val targetState = !hasExpandedDiffs
                        parsedFiles.forEach { file ->
                            expandedCards[file.path] = targetState
                        }
                    },
                    onSelectFile = { path ->
                        expandedCards[path] = true
                        selectedFilePath = path
                    }
                )
            }
        }
    }
}

@Composable
private fun FileDiffHeaderCard(
    file: ParsedFile,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val shape = if (isExpanded) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    } else {
        RoundedCornerShape(12.dp)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChangeTypeBadge(changeType = file.changeType)
            Text(
                text = file.path,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DiffBadges(additions = file.additions, deletions = file.deletions)
        }
    }
}

@Composable
private fun ChangeTypeBadge(changeType: FileChangeType) {
    val (label, bg, fg) = when (changeType) {
        FileChangeType.CREATED -> Triple("A", Color(0xFF14532D), Color(0xFF86EFAC))
        FileChangeType.MODIFIED -> Triple("M", Color(0xFF78350F), Color(0xFFFDE68A))
        FileChangeType.DELETED -> Triple("D", Color(0xFF7F1D1D), Color(0xFFFCA5A5))
    }

    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DiffLineRow(
    line: ParsedDiffLine,
    isLast: Boolean
) {
    val (bgColor, textColor) = when (line.type) {
        DiffLineType.ADDITION -> diffAdditionBg to diffAdditionFg
        DiffLineType.DELETION -> diffDeletionBg to diffDeletionFg
        DiffLineType.CONTEXT -> diffContextBg to diffContextFg
    }

    val shape = if (isLast) RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(0.dp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = diffCodeDarkBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(horizontal = 6.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = line.oldLineNumber?.toString().orEmpty(),
                modifier = Modifier.widthIn(min = 40.dp).padding(end = 4.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End
                ),
                color = diffLineNumFg
            )
            Text(
                text = line.newLineNumber?.toString().orEmpty(),
                modifier = Modifier.widthIn(min = 40.dp).padding(end = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End
                ),
                color = diffLineNumFg
            )
            Text(
                text = line.content,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = textColor
            )
        }
    }
}

@Composable
private fun ScrollableFileTree(
    fileTree: List<FileTreeNode>,
    expandedFolders: Map<String, Boolean>,
    hasExpandedDiffs: Boolean,
    onToggleFolder: (String) -> Unit,
    onToggleAllCards: () -> Unit,
    onSelectFile: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "File Tree",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = onToggleAllCards,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (hasExpandedDiffs) "Collapse All" else "Expand All",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            fileTree.forEach { rootNode ->
                FileTreeNodeItem(
                    node = rootNode,
                    depth = 0,
                    expandedFolders = expandedFolders,
                    onToggleFolder = onToggleFolder,
                    onSelectFile = onSelectFile
                )
            }
        }
    }
}

@Composable
private fun FileTreeNodeItem(
    node: FileTreeNode,
    depth: Int,
    expandedFolders: Map<String, Boolean>,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (String) -> Unit
) {
    val indent = (depth * 12).dp
    when (node) {
        is FileTreeNode.Directory -> {
            val isExpanded = expandedFolders[node.fullPath] ?: true
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = indent)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onToggleFolder(node.fullPath) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                        contentDescription = "Folder",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = node.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Folder",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        node.children.forEach { childNode ->
                            FileTreeNodeItem(
                                node = childNode,
                                depth = depth + 1,
                                expandedFolders = expandedFolders,
                                onToggleFolder = onToggleFolder,
                                onSelectFile = onSelectFile
                            )
                        }
                    }
                }
            }
        }
        is FileTreeNode.File -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = indent)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelectFile(node.fullPath) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "File",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = node.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
