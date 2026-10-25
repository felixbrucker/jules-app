package com.felixbrucker.jules.ui.sessions

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.net.toUri
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.felixbrucker.jules.R
import com.felixbrucker.jules.data.database.EmbeddedPullRequest
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.network.api.model.Activity
import com.felixbrucker.jules.network.api.model.FileChangeType
import com.felixbrucker.jules.network.api.model.ParsedFile
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.network.api.model.PlanStep
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.ui.components.AnimatedMovingDotsIcon
import com.felixbrucker.jules.ui.components.DiffBadges
import com.felixbrucker.jules.ui.components.FormattedMarkdownText
import com.felixbrucker.jules.ui.components.PullRequestChip
import com.felixbrucker.jules.ui.components.SessionStateBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBackClick: () -> Unit
) {
    val sessionWithPr by viewModel.sessionWithPullRequestFlow.collectAsState(initial = null)
    val currentDiff by viewModel.currentChangesetDiffFlow.collectAsState(initial = null)
    val activities = viewModel.activitiesPagingDataFlow.collectAsLazyPagingItems()
    val splitRatio by viewModel.splitRatio.collectAsState()
    val isSendingMessage by viewModel.isSendingMessage.collectAsState()
    val isApprovingPlan by viewModel.isApprovingPlan.collectAsState()
    val dispatchState by viewModel.dispatchDialogState.collectAsState()
    val errorEvent by viewModel.errorEvents.collectAsState(initial = null)

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.startPolling()
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
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
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
                    activities = activities,
                    currentDiff = currentDiff,
                    splitRatio = splitRatio,
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SessionDetailTopBar(
    sessionWithPr: SessionWithPullRequest,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBackClick: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onSelectWorkflow: (DispatchableWorkflowEntity) -> Unit
) {
    val session = sessionWithPr.session
    var menuExpanded by remember { mutableStateOf(false) }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SessionTopBarLeftSection(
                session = session,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onBackClick = onBackClick
            )
            SessionTopBarRightSection(
                sessionWithPr = sessionWithPr,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                menuExpanded = menuExpanded,
                onMenuToggle = { menuExpanded = it },
                onArchive = onArchive,
                onUnarchive = onUnarchive,
                onDelete = onDelete,
                onSelectWorkflow = onSelectWorkflow
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RowScope.SessionTopBarLeftSection(
    session: SessionEntity,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBackClick: () -> Unit
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
            val context = LocalContext.current
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SessionTopBarRightSection(
    sessionWithPr: SessionWithPullRequest,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    menuExpanded: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onSelectWorkflow: (DispatchableWorkflowEntity) -> Unit
) {
    val session = sessionWithPr.session
    val context = LocalContext.current

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
        session.changeSet?.let { cs ->
            Box(
                modifier = with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = "changes_${session.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            ) {
                DiffBadges(additions = cs.totalAdditions, deletions = cs.totalDeletions)
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
            IconButton(onClick = { onMenuToggle(true) }, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Actions")
            }
            SessionActionDropdown(
                expanded = menuExpanded,
                isArchived = session.isArchived,
                onDismiss = { onMenuToggle(false) },
                onArchive = onArchive,
                onUnarchive = onUnarchive,
                onDelete = onDelete
            )
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
    activities: LazyPagingItems<ActivityUiItem>,
    currentDiff: CurrentChangesetDiff?,
    splitRatio: Float,
    isSendingMessage: Boolean,
    isApprovingPlan: Boolean,
    onSplitRatioChange: (Float) -> Unit,
    onSendMessage: (String) -> Unit,
    onApprovePlan: () -> Unit
) {
    var containerWidthPx by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { containerWidthPx = it.size.width }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(splitRatio.coerceAtLeast(0.0001f))) {
                if (splitRatio > 0.0f) {
                    ChatWindowView(
                        session = session,
                        activities = activities,
                        isSendingMessage = isSendingMessage,
                        isApprovingPlan = isApprovingPlan,
                        onSendMessage = onSendMessage,
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
                        currentDiff = currentDiff,
                        isFullScreen = (splitRatio == 0.0f)
                    )
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
    activities: LazyPagingItems<ActivityUiItem>,
    isSendingMessage: Boolean,
    isApprovingPlan: Boolean,
    onSendMessage: (String) -> Unit,
    onApprovePlan: () -> Unit
) {
    val listState = rememberLazyListState()
    var initialScrolledSessionId by remember { mutableStateOf<String?>(null) }

    val latestItemId = if (activities.itemCount > 0) activities[0]?.id else null

    LaunchedEffect(session.id, latestItemId) {
        if (activities.itemCount > 0) {
            if (initialScrolledSessionId != session.id) {
                initialScrolledSessionId = session.id
                listState.scrollToItem(0)
            } else if (listState.firstVisibleItemIndex <= 2) {
                listState.animateScrollToItem(0)
            }
        }
    }

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
                items(
                    count = activities.itemCount,
                    key = activities.itemKey { it.id }
                ) { index ->
                    val item = activities[index]
                    if (item != null) {
                        ChatActivityRow(
                            item = item,
                            isBottomMost = (index == 0),
                            session = session
                        )
                    }
                }
            }
        }

        if (session.state == SessionState.AWAITING_PLAN_APPROVAL) {
            ApprovePlanBottomBar(
                isApprovingPlan = isApprovingPlan,
                onApprovePlan = onApprovePlan
            )
        }

        ChatInputBottomBar(
            isSending = isSendingMessage,
            onSendMessage = onSendMessage
        )
    }
}

@Composable
private fun ChatActivityRow(
    item: ActivityUiItem,
    isBottomMost: Boolean,
    session: SessionEntity
) {
    when (item) {
        is ActivityUiItem.RelativeTimeDivider -> {
            CenteredTimeDivider(timeText = item.relativeTimeText)
        }
        is ActivityUiItem.ActivityItem -> {
            ActivityBubbleContainer(
                activity = item.activity,
                isBottomMost = isBottomMost,
                session = session
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
private fun PromptActivityBubble(prompt: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = prompt,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ActivityBubbleContainer(
    activity: Activity,
    isBottomMost: Boolean,
    session: SessionEntity
) {
    when (activity) {
        is Activity.UserMessaged -> UserMessageBubble(message = activity.userMessage)
        is Activity.AgentMessaged -> {
            if (isBottomMost) {
                AgentStatusBubble(label = "Thinking")
            }
        }
        is Activity.ProgressUpdated -> {
            if (!activity.title.isNullOrBlank() || !activity.description.isNullOrBlank()) {
                ProgressUpdatedBubble(
                    title = activity.title,
                    description = activity.description
                )
            } else if (isBottomMost) {
                AgentStatusBubble(label = "Working")
            }
        }
        is Activity.PlanGenerated -> PlanGeneratedCard(planSteps = activity.plan.steps)
        is Activity.PlanApproved -> PlanApprovedPill()
        is Activity.SessionFailed -> SessionFailedBox(reason = activity.reason)
        is Activity.SessionCompleted -> SessionCompletedSummaryBox(
            session = session,
            activity = activity
        )
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
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
                            FormattedMarkdownText(
                                markdown = title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
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
                            FormattedMarkdownText(
                                markdown = description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    title?.let {
                        FormattedMarkdownText(
                            markdown = it,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    description?.let {
                        FormattedMarkdownText(
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
                FormattedMarkdownText(
                    markdown = step.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
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
                        FormattedMarkdownText(
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
    activity: Activity.SessionCompleted
) {
    val changeSet = activity.artifacts.firstNotNullOfOrNull { it.changeSet }
    val patch = changeSet?.gitPatch

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
                changeSet?.summary?.let {
                    DiffBadges(additions = it.totalAdditions, deletions = it.totalDeletions)
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
                            painter = painterResource(id = R.drawable.ic_github_pr_open),
                            contentDescription = "Branch",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF3FB950)
                        )
                        Text(
                            text = pr.headRef ?: pr.baseRef ?: "pr-branch",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            patch?.suggestedCommitMessage?.let { commitMsg ->
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
    isSending: Boolean,
    onSendMessage: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

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
                onValueChange = { textInput = it },
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
                        textInput = ""
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

@Composable
private fun FileDiffView(
    currentDiff: CurrentChangesetDiff?,
    isFullScreen: Boolean
) {
    val expandedCards = remember { mutableStateMapOf<String, Boolean>() }
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    if (currentDiff == null || currentDiff.parsedFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No changeset diff available", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val filteredFiles = remember(currentDiff.parsedFiles, currentDiff.unidiffPatch, searchQuery) {
        if (searchQuery.isBlank()) {
            currentDiff.parsedFiles
        } else {
            currentDiff.parsedFiles.filter { file ->
                file.path.contains(searchQuery, ignoreCase = true) ||
                    extractFileDiffLines(file.path, currentDiff.unidiffPatch).any { line ->
                        line.content.contains(searchQuery, ignoreCase = true)
                    }
            }
        }
    }

    LaunchedEffect(selectedFilePath) {
        val path = selectedFilePath ?: return@LaunchedEffect
        val fileIndex = filteredFiles.indexOfFirst { it.path == path }
        if (fileIndex >= 0) {
            kotlinx.coroutines.yield()
            listState.animateScrollToItem(fileIndex)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    placeholder = { Text("Search files or diffs...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(8.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            filteredFiles.forEach { file ->
                                expandedCards[file.path] = true
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Expand All Cards",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            filteredFiles.forEach { file ->
                                expandedCards[file.path] = false
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UnfoldLess,
                            contentDescription = "Collapse All Cards",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (filteredFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No files match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles.size, key = { filteredFiles[it].path }) { index ->
                        val file = filteredFiles[index]
                        val isExpanded = expandedCards[file.path] ?: false
                        FileDiffCard(
                            file = file,
                            unidiffPatch = currentDiff.unidiffPatch,
                            isExpanded = isExpanded,
                            onToggleExpand = { expandedCards[file.path] = !isExpanded }
                        )
                    }
                }
            }
        }

        if (isFullScreen) {
            val allFolderPaths = remember(currentDiff.parsedFiles) {
                fun collectFolders(nodes: List<FileTreeNode>): List<String> {
                    val result = mutableListOf<String>()
                    for (node in nodes) {
                        if (node is FileTreeNode.Directory) {
                            result.add(node.fullPath)
                            result.addAll(collectFolders(node.children))
                        }
                    }
                    return result
                }
                collectFolders(buildFileTree(currentDiff.parsedFiles))
            }

            Box(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(8.dp)
            ) {
                ScrollableFileTree(
                    parsedFiles = currentDiff.parsedFiles,
                    expandedFolders = expandedFolders,
                    onToggleFolder = { folder ->
                        expandedFolders[folder] = !(expandedFolders[folder] ?: true)
                    },
                    onExpandAllFolders = {
                        allFolderPaths.forEach { expandedFolders[it] = true }
                    },
                    onCollapseAllFolders = {
                        allFolderPaths.forEach { expandedFolders[it] = false }
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
private fun FileDiffCard(
    file: ParsedFile,
    unidiffPatch: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(10.dp)
        ) {
            Row(
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

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    ColorCodedDiffBody(file = file, unidiffPatch = unidiffPatch)
                }
            }
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

enum class DiffLineType {
    CONTEXT,
    ADDITION,
    DELETION
}

data class ParsedDiffLine(
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val content: String,
    val type: DiffLineType
)

@Composable
private fun ColorCodedDiffBody(file: ParsedFile, unidiffPatch: String) {
    val fileLines = remember(file.path, unidiffPatch) {
        extractFileDiffLines(file.path, unidiffPatch)
    }

    SelectionContainer {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF0D1117)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                fileLines.forEach { line ->
                    val (bgColor, textColor) = when (line.type) {
                        DiffLineType.ADDITION -> Color(0xFF163B21) to Color(0xFF7EE787)
                        DiffLineType.DELETION -> Color(0xFF3C1E1E) to Color(0xFFFF7B72)
                        DiffLineType.CONTEXT -> Color.Transparent to Color(0xFFC9D1D9)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = line.oldLineNumber?.toString() ?: "",
                            modifier = Modifier.width(32.dp).padding(end = 4.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            ),
                            color = Color(0xFF6E7681)
                        )
                        Text(
                            text = line.newLineNumber?.toString() ?: "",
                            modifier = Modifier.width(32.dp).padding(end = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            ),
                            color = Color(0xFF6E7681)
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
        }
    }
}

private fun extractFileDiffLines(path: String, unidiffPatch: String): List<ParsedDiffLine> {
    val blocks = unidiffPatch.split(Regex("^diff --git ", RegexOption.MULTILINE))
    val result = mutableListOf<ParsedDiffLine>()

    for (block in blocks) {
        if (block.contains("a/$path") || block.contains("b/$path")) {
            val lines = block.lines()
            var oldLine = 1
            var newLine = 1
            var inHunk = false

            for (line in lines) {
                if (line.startsWith("@@ ")) {
                    inHunk = true
                    val match = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""").find(line)
                    if (match != null) {
                        oldLine = match.groupValues[1].toInt()
                        newLine = match.groupValues[2].toInt()
                    }
                    continue
                }

                if (!inHunk) continue

                when {
                    line.startsWith("+") && !line.startsWith("+++") -> {
                        result.add(
                            ParsedDiffLine(
                                oldLineNumber = null,
                                newLineNumber = newLine,
                                content = line,
                                type = DiffLineType.ADDITION
                            )
                        )
                        newLine++
                    }
                    line.startsWith("-") && !line.startsWith("---") -> {
                        result.add(
                            ParsedDiffLine(
                                oldLineNumber = oldLine,
                                newLineNumber = null,
                                content = line,
                                type = DiffLineType.DELETION
                            )
                        )
                        oldLine++
                    }
                    line.startsWith(" ") || line.isEmpty() -> {
                        result.add(
                            ParsedDiffLine(
                                oldLineNumber = oldLine,
                                newLineNumber = newLine,
                                content = line,
                                type = DiffLineType.CONTEXT
                            )
                        )
                        oldLine++
                        newLine++
                    }
                }
            }
            return result.take(300)
        }
    }
    return emptyList()
}

private sealed interface FileTreeNode {
    val name: String
    val fullPath: String

    data class Directory(
        override val name: String,
        override val fullPath: String,
        val children: List<FileTreeNode>
    ) : FileTreeNode

    data class File(
        override val name: String,
        override val fullPath: String,
        val parsedFile: ParsedFile
    ) : FileTreeNode
}

private fun buildFileTree(parsedFiles: List<ParsedFile>): List<FileTreeNode> {
    class DirBuilder(val name: String, val fullPath: String) {
        val subDirs = mutableMapOf<String, DirBuilder>()
        val files = mutableListOf<ParsedFile>()

        fun toTreeNode(): FileTreeNode.Directory {
            val childrenList = mutableListOf<FileTreeNode>()
            subDirs.values.sortedBy { it.name }.forEach { dir ->
                childrenList.add(dir.toTreeNode())
            }
            files.sortedBy { it.path.substringAfterLast('/') }.forEach { file ->
                childrenList.add(
                    FileTreeNode.File(
                        name = file.path.substringAfterLast('/'),
                        fullPath = file.path,
                        parsedFile = file
                    )
                )
            }
            return FileTreeNode.Directory(name, fullPath, childrenList)
        }
    }

    val rootDirs = mutableMapOf<String, DirBuilder>()
    val rootFiles = mutableListOf<ParsedFile>()

    for (file in parsedFiles) {
        val parts = file.path.split('/')
        if (parts.size == 1) {
            rootFiles.add(file)
        } else {
            val topLevelDirName = parts[0]
            val topLevelDir = rootDirs.getOrPut(topLevelDirName) {
                DirBuilder(topLevelDirName, topLevelDirName)
            }
            var currentDir = topLevelDir
            var currentPath = topLevelDirName
            for (i in 1 until parts.size - 1) {
                val dirName = parts[i]
                currentPath = "$currentPath/$dirName"
                currentDir = currentDir.subDirs.getOrPut(dirName) {
                    DirBuilder(dirName, currentPath)
                }
            }
            currentDir.files.add(file)
        }
    }

    val result = mutableListOf<FileTreeNode>()
    rootDirs.values.sortedBy { it.name }.forEach { dir ->
        result.add(dir.toTreeNode())
    }
    rootFiles.sortedBy { it.path }.forEach { file ->
        result.add(
            FileTreeNode.File(
                name = file.path,
                fullPath = file.path,
                parsedFile = file
            )
        )
    }
    return result
}

@Composable
private fun ScrollableFileTree(
    parsedFiles: List<ParsedFile>,
    expandedFolders: Map<String, Boolean>,
    onToggleFolder: (String) -> Unit,
    onExpandAllFolders: () -> Unit,
    onCollapseAllFolders: () -> Unit,
    onSelectFile: (String) -> Unit
) {
    val rootNodes = remember(parsedFiles) { buildFileTree(parsedFiles) }

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
            Row {
                IconButton(onClick = onExpandAllFolders, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Expand All Folders",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onCollapseAllFolders, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.UnfoldLess,
                        contentDescription = "Collapse All Folders",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            rootNodes.forEach { rootNode ->
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
