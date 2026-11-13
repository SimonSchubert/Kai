@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package com.kainext.duo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kainext.duo.data.Goal
import com.kainext.duo.data.GoalPlan
import com.kainext.duo.data.GoalPlanStatus
import com.kainext.duo.data.GoalPriority
import com.kainext.duo.data.GoalStats
import com.kainext.duo.data.GoalStatus
import com.kainext.duo.data.toGoalStats
import com.kainext.duo.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_goals_add
import kai.composeapp.generated.resources.settings_goals_add_plan
import kai.composeapp.generated.resources.settings_goals_cancel
import kai.composeapp.generated.resources.settings_goals_category_label
import kai.composeapp.generated.resources.settings_goals_delete
import kai.composeapp.generated.resources.settings_goals_description
import kai.composeapp.generated.resources.settings_goals_description_label
import kai.composeapp.generated.resources.settings_goals_edit
import kai.composeapp.generated.resources.settings_goals_empty_description
import kai.composeapp.generated.resources.settings_goals_empty_title
import kai.composeapp.generated.resources.settings_goals_overdue
import kai.composeapp.generated.resources.settings_goals_plan_label
import kai.composeapp.generated.resources.settings_goals_plan_status_done
import kai.composeapp.generated.resources.settings_goals_plan_status_in_progress
import kai.composeapp.generated.resources.settings_goals_plan_status_pending
import kai.composeapp.generated.resources.settings_goals_plan_status_skipped
import kai.composeapp.generated.resources.settings_goals_plans_title
import kai.composeapp.generated.resources.settings_goals_priority_high
import kai.composeapp.generated.resources.settings_goals_priority_label
import kai.composeapp.generated.resources.settings_goals_priority_low
import kai.composeapp.generated.resources.settings_goals_priority_medium
import kai.composeapp.generated.resources.settings_goals_priority_urgent
import kai.composeapp.generated.resources.settings_goals_progress
import kai.composeapp.generated.resources.settings_goals_progress_detail
import kai.composeapp.generated.resources.settings_goals_save
import kai.composeapp.generated.resources.settings_goals_section_goals
import kai.composeapp.generated.resources.settings_goals_section_stats
import kai.composeapp.generated.resources.settings_goals_stats_active
import kai.composeapp.generated.resources.settings_goals_stats_completed
import kai.composeapp.generated.resources.settings_goals_stats_overdue
import kai.composeapp.generated.resources.settings_goals_stats_progress
import kai.composeapp.generated.resources.settings_goals_stats_total
import kai.composeapp.generated.resources.settings_goals_status_active
import kai.composeapp.generated.resources.settings_goals_status_archived
import kai.composeapp.generated.resources.settings_goals_status_done
import kai.composeapp.generated.resources.settings_goals_status_label
import kai.composeapp.generated.resources.settings_goals_status_paused
import kai.composeapp.generated.resources.settings_goals_title
import kai.composeapp.generated.resources.settings_goals_title_label
import kai.composeapp.generated.resources.settings_goals_title_required
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Settings → Goals. Local-first CRUD over the goal list; the assistant reads open plan steps
 * through the same store, so what you see here is exactly what it plans against.
 */
@Composable
internal fun GoalsContent(
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    var editing by remember { mutableStateOf<Goal?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.settings_goals_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.settings_goals_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { editing = null; showEditor = true },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_goals_add))
                }
            }
        }

        if (uiState.goals.isEmpty()) {
            SettingsCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_goals_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.settings_goals_empty_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val stats = remember(uiState.goals) { buildGoalStats(uiState.goals) }
            GoalStatsCard(stats = stats)

            GoalsColumn(
                title = stringResource(Res.string.settings_goals_section_goals),
            ) {
                uiState.goals.forEach { goal ->
                    GoalCard(
                        goal = goal,
                        onEdit = { editing = goal; showEditor = true },
                        onDelete = { actions.onDeleteGoal(goal.id) },
                        onTogglePlan = { planId ->
                            actions.onUpdateGoal(togglePlan(goal, planId))
                        },
                        onChangeStatus = { status ->
                            actions.onUpdateGoal(goal.copy(status = status))
                        },
                    )
                }
            }
        }
    }

    if (showEditor) {
        GoalEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false; editing = null },
            onSave = { goal ->
                if (editing == null) actions.onAddGoal(goal) else actions.onUpdateGoal(goal)
                showEditor = false
                editing = null
            },
        )
    }
}

@Composable
private fun GoalsColumn(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        content()
    }
}

@Composable
private fun GoalStatsCard(stats: GoalStats) {
    SettingsCard {
        Text(
            text = stringResource(Res.string.settings_goals_section_stats),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatCell(stringResource(Res.string.settings_goals_stats_total), stats.totalGoals.toString())
            StatCell(stringResource(Res.string.settings_goals_stats_active), stats.activeGoals.toString())
            StatCell(stringResource(Res.string.settings_goals_stats_completed), stats.completedGoals.toString())
            StatCell(stringResource(Res.string.settings_goals_stats_overdue), stats.overdueGoals.toString())
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.settings_goals_stats_progress),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { stats.overallCompletionPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                Res.string.settings_goals_progress_detail,
                stats.completedPlans,
                stats.totalPlans,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePlan: (String) -> Unit,
    onChangeStatus: (GoalStatus) -> Unit,
) {
    SettingsCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (goal.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = goal.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onEdit, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.settings_goals_edit))
                }
                TextButton(onClick = onDelete, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.settings_goals_delete))
                }
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalStatus.entries.filter { it != GoalStatus.ARCHIVED }.forEach { status ->
                    FilterChip(
                        selected = goal.status == status,
                        onClick = { onChangeStatus(status) },
                        label = { Text(goalStatusLabel(status)) },
                        modifier = Modifier.handCursor(),
                    )
                }
                if (goal.isOverdue) {
                    Text(
                        text = stringResource(Res.string.settings_goals_overdue),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { goal.completionFraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    text = stringResource(Res.string.settings_goals_progress, goal.completionPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        Res.string.settings_goals_progress_detail,
                        goal.completedPlanCount,
                        goal.totalPlanCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (goal.plans.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.settings_goals_plans_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                goal.plans.forEach { plan ->
                    GoalPlanRow(plan = plan, onToggle = { onTogglePlan(plan.id) })
                }
            }
        }
    }
}

@Composable
private fun GoalPlanRow(
    plan: GoalPlan,
    onToggle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = plan.status == GoalPlanStatus.DONE,
            onCheckedChange = { onToggle() },
            modifier = Modifier.handCursor(),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (plan.status == GoalPlanStatus.DONE) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
            if (plan.description.isNotBlank()) {
                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = planStatusLabel(plan.status),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalEditorDialog(
    initial: Goal?,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
) {
    val isNew = initial == null
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var priority by remember { mutableStateOf(initial?.priority ?: GoalPriority.MEDIUM) }
    var status by remember { mutableStateOf(initial?.status ?: GoalStatus.ACTIVE) }
    var plans by remember { mutableStateOf(initial?.plans.orEmpty()) }
    var newPlan by remember { mutableStateOf("") }
    var showTitleError by remember { mutableStateOf(false) }
    // Deadline is entered as a number of days from today. A full date picker would need
    // material3's experimental DatePicker and a locale-formatted display; a day offset keeps the
    // dialog compact and unambiguous, which is what the field is used for ("due in a week").
    var targetDateDays by remember {
        mutableStateOf(
            initial?.targetDateEpochMs?.let { deadline ->
                val days = (deadline - Clock.System.now().toEpochMilliseconds()) / MILLIS_PER_DAY
                if (days > 0) days.toString() else ""
            }.orEmpty(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isNew) Res.string.settings_goals_add else Res.string.settings_goals_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; showTitleError = false },
                    label = { Text(stringResource(Res.string.settings_goals_title_label)) },
                    singleLine = true,
                    isError = showTitleError,
                    supportingText = if (showTitleError) {
                        { Text(stringResource(Res.string.settings_goals_title_required)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.settings_goals_description_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(Res.string.settings_goals_category_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = targetDateDays,
                    onValueChange = { input -> targetDateDays = input.filter { it.isDigit() }.take(5) },
                    label = { Text("Target date") },
                    placeholder = { Text("No deadline") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(Res.string.settings_goals_priority_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalPriority.entries.forEach { option ->
                        FilterChip(
                            selected = priority == option,
                            onClick = { priority = option },
                            label = { Text(priorityLabel(option)) },
                            modifier = Modifier.handCursor(),
                        )
                    }
                }

                Text(
                    text = stringResource(Res.string.settings_goals_status_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalStatus.entries.forEach { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
                            label = { Text(goalStatusLabel(option)) },
                            modifier = Modifier.handCursor(),
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.settings_goals_plans_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                plans.forEachIndexed { index, plan ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { plans = plans.filterIndexed { i, _ -> i != index } },
                            modifier = Modifier.size(32.dp).handCursor(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete step",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPlan,
                        onValueChange = { newPlan = it },
                        label = { Text(stringResource(Res.string.settings_goals_plan_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newPlan.isNotBlank()) {
                                    plans = plans + GoalPlan(
                                        id = Uuid.random().toString(),
                                        title = newPlan.trim(),
                                        createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                                    )
                                    newPlan = ""
                                }
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (newPlan.isNotBlank()) {
                                plans = plans + GoalPlan(
                                    id = Uuid.random().toString(),
                                    title = newPlan.trim(),
                                    createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                                )
                                newPlan = ""
                            }
                        },
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(stringResource(Res.string.settings_goals_add_plan))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) {
                        showTitleError = true
                    } else {
                        onSave(
                            Goal(
                                id = initial?.id ?: Uuid.random().toString(),
                                title = title.trim(),
                                description = description.trim(),
                                category = category.trim(),
                                priority = priority,
                                status = status,
                                createdAtEpochMs = initial?.createdAtEpochMs
                                    ?: Clock.System.now().toEpochMilliseconds(),
                                targetDateEpochMs = targetDateDays.toLongOrNull()
                                    ?.takeIf { it > 0 }
                                    ?.let { Clock.System.now().toEpochMilliseconds() + it * MILLIS_PER_DAY },
                                plans = plans,
                            ),
                        )
                    }
                },
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_goals_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.settings_goals_cancel))
            }
        },
    )
}

private fun togglePlan(goal: Goal, planId: String): Goal {
    val now = Clock.System.now().toEpochMilliseconds()
    return goal.copy(
        plans = goal.plans.map { plan ->
            if (plan.id != planId) {
                plan
            } else if (plan.status == GoalPlanStatus.DONE) {
                plan.copy(status = GoalPlanStatus.PENDING, completedAtEpochMs = null)
            } else {
                plan.copy(status = GoalPlanStatus.DONE, completedAtEpochMs = now)
            }
        },
    )
}

private fun buildGoalStats(goals: List<Goal>): GoalStats = goals.toGoalStats()

@Composable
private fun goalStatusLabel(status: GoalStatus): String = stringResource(
    when (status) {
        GoalStatus.ACTIVE -> Res.string.settings_goals_status_active
        GoalStatus.PAUSED -> Res.string.settings_goals_status_paused
        GoalStatus.DONE -> Res.string.settings_goals_status_done
        GoalStatus.ARCHIVED -> Res.string.settings_goals_status_archived
    },
)

@Composable
private fun priorityLabel(priority: GoalPriority): String = stringResource(
    when (priority) {
        GoalPriority.LOW -> Res.string.settings_goals_priority_low
        GoalPriority.MEDIUM -> Res.string.settings_goals_priority_medium
        GoalPriority.HIGH -> Res.string.settings_goals_priority_high
        GoalPriority.URGENT -> Res.string.settings_goals_priority_urgent
    },
)

@Composable
private fun planStatusLabel(status: GoalPlanStatus): String = stringResource(
    when (status) {
        GoalPlanStatus.PENDING -> Res.string.settings_goals_plan_status_pending
        GoalPlanStatus.IN_PROGRESS -> Res.string.settings_goals_plan_status_in_progress
        GoalPlanStatus.DONE -> Res.string.settings_goals_plan_status_done
        GoalPlanStatus.SKIPPED -> Res.string.settings_goals_plan_status_skipped
    },
)

/** Days -> milliseconds, for the goal deadline field. */
private const val MILLIS_PER_DAY = 86_400_000L
