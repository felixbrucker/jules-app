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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.felixbrucker.jules.network.api.model.PlanStep
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.ui.components.AnimatedMovingDotsIcon
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
                    onDelete = { viewModel.deleteSession(onBackClick) }
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
    onDelete: () -> Unit
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
                onDelete = onDelete
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
        session.repo?.let { repo ->
            Row(
                modifier = with(sharedTransitionScope) {
                    Modifier
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "source_${session.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
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
    onDelete: () -> Unit
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
                AssistChip(
                    modifier = Modifier.height(28.dp),
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, pr.url.toUri())) },
                    label = { Text("PR #${pr.number ?: ""}", style = MaterialTheme.typography.labelSmall) }
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
                DiffAdditionsDeletionsBadges(additions = cs.totalAdditions, deletions = cs.totalDeletions)
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
            if (splitRatio > 0.0f) {
                Box(modifier = Modifier.weight(splitRatio.coerceAtLeast(0.01f))) {
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

            if (splitRatio > 0.0f && splitRatio < 1.0f) {
                SplitDividerHandle(
                    containerWidthPx = containerWidthPx,
                    splitRatio = splitRatio,
                    onSplitRatioChange = onSplitRatioChange
                )
            }

            if (splitRatio < 1.0f) {
                Box(modifier = Modifier.weight((1.0f - splitRatio).coerceAtLeast(0.01f))) {
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

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(20.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(containerWidthPx) {
                var currentRatio = splitRatio
                detectHorizontalDragGestures(
                    onDragStart = { currentRatio = splitRatio },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (containerWidthPx > 0) {
                            currentRatio += dragAmount / containerWidthPx.toFloat()
                            currentOnSplitRatioChange(currentRatio)
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

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
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

            item(key = "prompt_top") {
                PromptActivityBubble(prompt = session.prompt)
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
                AgentThinkingBubble()
            }
        }
        is Activity.ProgressUpdated -> ProgressUpdatedBubble(
            title = activity.title,
            description = activity.description
        )
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
private fun AgentThinkingBubble() {
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
                    text = "Agent is thinking",
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                title?.let {
                    FormattedMarkdownText(
                        markdown = it,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                if (title != null && description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
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
                PlanStepItemRow(stepIndex = step.index ?: (idx + 1), step = step)
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
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (hasDesc && isExpanded) {
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
                    DiffAdditionsDeletionsBadges(additions = it.totalAdditions, deletions = it.totalDeletions)
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

    if (currentDiff == null || currentDiff.parsedFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No changeset diff available", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(currentDiff.parsedFiles.size) { index ->
                val file = currentDiff.parsedFiles[index]
                val isExpanded = expandedCards[file.path] ?: false
                FileDiffCard(
                    file = file,
                    unidiffPatch = currentDiff.unidiffPatch,
                    isExpanded = isExpanded,
                    onToggleExpand = { expandedCards[file.path] = !isExpanded }
                )
            }
        }

        if (isFullScreen) {
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
                    onSelectFile = { path ->
                        expandedCards[path] = true
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
                DiffAdditionsDeletionsBadges(additions = file.additions, deletions = file.deletions)
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                ColorCodedDiffBody(file = file, unidiffPatch = unidiffPatch)
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

@Composable
private fun ColorCodedDiffBody(file: ParsedFile, unidiffPatch: String) {
    val fileLines = extractFileDiffLines(file.path, unidiffPatch)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0D1117)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            fileLines.forEach { line ->
                val (bgColor, textColor) = when {
                    line.startsWith("+") && !line.startsWith("+++") -> Color(0xFF163B21) to Color(0xFF7EE787)
                    line.startsWith("-") && !line.startsWith("---") -> Color(0xFF3C1E1E) to Color(0xFFFF7B72)
                    line.startsWith("@@") -> Color(0xFF161B22) to Color(0xFF79C0FF)
                    else -> Color.Transparent to Color(0xFFC9D1D9)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = line,
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

private fun extractFileDiffLines(path: String, unidiffPatch: String): List<String> {
    val blocks = unidiffPatch.split(Regex("^diff --git ", RegexOption.MULTILINE))
    for (block in blocks) {
        if (block.contains("a/$path") || block.contains("b/$path")) {
            return block.lines().take(300)
        }
    }
    return emptyList()
}

@Composable
private fun ScrollableFileTree(
    parsedFiles: List<ParsedFile>,
    expandedFolders: Map<String, Boolean>,
    onToggleFolder: (String) -> Unit,
    onSelectFile: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "File Tree",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(parsedFiles.size) { idx ->
                val file = parsedFiles[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onSelectFile(file.path) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = "File",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = file.path,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffAdditionsDeletionsBadges(additions: Int, deletions: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (additions > 0) {
            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF234C28)) {
                Text(
                    text = "+$additions",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color(0xFF6CF098),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                )
            }
        }
        if (deletions > 0) {
            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF4C2323)) {
                Text(
                    text = "-$deletions",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color(0xFFFF8585),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun FormattedMarkdownText(
    markdown: String,
    style: androidx.compose.ui.text.TextStyle
) {
    val annotatedString = remember(markdown) {
        parseSimpleMarkdown(markdown)
    }
    Text(
        text = annotatedString,
        style = style
    )
}

private fun parseSimpleMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            } else if (text.startsWith("`", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2D3748))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}
