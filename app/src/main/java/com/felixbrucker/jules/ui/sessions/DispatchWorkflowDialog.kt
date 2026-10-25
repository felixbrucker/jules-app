package com.felixbrucker.jules.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SourceEntity
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.network.api.model.WorkflowInput
import com.felixbrucker.jules.ui.components.LoadingStateButton

@Composable
fun DispatchWorkflowDialog(
    session: SessionEntity,
    workflow: DispatchableWorkflowEntity,
    source: SourceEntity?,
    isDispatching: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmDispatch: (branch: String, inputs: Map<String, String>) -> Unit
) {
    val branchOptions = remember(session, source) {
        buildBranchOptions(session = session, source = source)
    }

    val defaultBranch = remember(session, source, branchOptions) {
        determineDefaultBranch(session = session, source = source, branchOptions = branchOptions)
    }

    var selectedBranch by remember(defaultBranch) { mutableStateOf(defaultBranch) }

    val inputValues = remember(workflow) {
        mutableStateMapOf<String, String>().apply {
            workflow.inputs.forEach { input ->
                put(input.name, input.default ?: if (input.type == "boolean") "false" else "")
            }
        }
    }

    val isFormValid = remember(selectedBranch, workflow.inputs, inputValues.toMap()) {
        selectedBranch.isNotBlank() && workflow.inputs.all { input ->
            if (input.required) {
                !inputValues[input.name].isNullOrBlank()
            } else true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Dispatch Workflow: ${workflow.name}")
        },
        text = {
            DialogContentBody(
                branchOptions = branchOptions,
                selectedBranch = selectedBranch,
                onBranchSelected = { selectedBranch = it },
                inputs = workflow.inputs,
                inputValues = inputValues,
                errorMessage = errorMessage
            )
        },
        confirmButton = {
            LoadingStateButton(
                label = "Dispatch",
                loadingLabel = "Dispatching...",
                isLoading = isDispatching,
                enabled = isFormValid,
                onClick = { onConfirmDispatch(selectedBranch, inputValues.toMap()) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDispatching) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DialogContentBody(
    branchOptions: List<String>,
    selectedBranch: String,
    onBranchSelected: (String) -> Unit,
    inputs: List<WorkflowInput>,
    inputValues: MutableMap<String, String>,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BranchSelectionDropdown(
            options = branchOptions,
            selectedOption = selectedBranch,
            onOptionSelected = onBranchSelected
        )

        if (inputs.isNotEmpty()) {
            Text(
                text = "Workflow Inputs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            inputs.forEach { input ->
                WorkflowInputField(
                    input = input,
                    currentValue = inputValues[input.name] ?: "",
                    onValueChange = { newValue -> inputValues[input.name] = newValue }
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun BranchSelectionDropdown(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Branch",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        androidx.compose.foundation.layout.Box {
            Surface(
                modifier = Modifier.clickable { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedOption.ifBlank { "Select Branch" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select branch"
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowInputField(
    input: WorkflowInput,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    when {
        input.type == "choice" && !input.options.isNullOrEmpty() -> {
            ChoiceInputField(input = input, currentValue = currentValue, onValueChange = onValueChange)
        }
        input.type == "boolean" -> {
            BooleanInputField(input = input, currentValue = currentValue, onValueChange = onValueChange)
        }
        input.type == "number" -> {
            NumberInputField(input = input, currentValue = currentValue, onValueChange = onValueChange)
        }
        else -> {
            TextInputField(input = input, currentValue = currentValue, onValueChange = onValueChange)
        }
    }
}

@Composable
private fun NumberInputField(
    input: WorkflowInput,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    val labelText = if (input.required) "${input.name} *" else input.name
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = labelText, style = MaterialTheme.typography.labelMedium)
            if (!input.description.isNullOrBlank()) {
                Text(
                    text = input.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = currentValue,
            onValueChange = onValueChange,
            modifier = Modifier.width(130.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        )
    }
}

@Composable
private fun ChoiceInputField(
    input: WorkflowInput,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val labelText = if (input.required) "${input.name} *" else input.name

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = labelText, style = MaterialTheme.typography.labelMedium)
            if (!input.description.isNullOrBlank()) {
                Text(
                    text = input.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        androidx.compose.foundation.layout.Box {
            Surface(
                modifier = Modifier.clickable { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentValue.ifBlank { "Select" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                input.options.orEmpty().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BooleanInputField(
    input: WorkflowInput,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    val isChecked = currentValue.equals("true", ignoreCase = true)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = input.name, style = MaterialTheme.typography.labelMedium)
            if (!input.description.isNullOrBlank()) {
                Text(text = input.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = { checked -> onValueChange(checked.toString()) }
        )
    }
}

@Composable
private fun TextInputField(
    input: WorkflowInput,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    val labelText = if (input.required) "${input.name} *" else input.name
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = labelText, style = MaterialTheme.typography.labelMedium)
            if (!input.description.isNullOrBlank()) {
                Text(
                    text = input.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = currentValue,
            onValueChange = onValueChange,
            modifier = Modifier.width(160.dp),
            singleLine = true
        )
    }
}

private fun buildBranchOptions(
    session: SessionEntity,
    source: SourceEntity?
): List<String> {
    val branches = mutableSetOf<String>()
    session.pullRequest?.baseRef?.takeIf { it.isNotBlank() }?.let { branches.add(it) }
    session.startingBranch?.takeIf { it.isNotBlank() }?.let { branches.add(it) }
    session.pullRequest?.headRef?.takeIf { it.isNotBlank() }?.let { branches.add(it) }
    source?.githubRepo?.branches?.forEach { b ->
        if (b.displayName.isNotBlank()) branches.add(b.displayName)
    }
    source?.githubRepo?.defaultBranchName?.takeIf { it.isNotBlank() }?.let { branches.add(it) }

    return branches.toList().ifEmpty { listOf("main") }
}

private fun determineDefaultBranch(
    session: SessionEntity,
    source: SourceEntity?,
    branchOptions: List<String>
): String {
    val headRef = session.pullRequest?.headRef
    if (!headRef.isNullOrBlank()) return headRef

    val baseRef = session.pullRequest?.baseRef
    if (!baseRef.isNullOrBlank()) return baseRef

    val startingBranch = session.startingBranch
    if (!startingBranch.isNullOrBlank()) return startingBranch

    val defaultBranch = source?.githubRepo?.defaultBranchName
    if (!defaultBranch.isNullOrBlank()) return defaultBranch

    return branchOptions.firstOrNull() ?: "main"
}
