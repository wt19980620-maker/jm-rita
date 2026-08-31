package com.par9uet.jm.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ComicSearchHistoryTag(
    label: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    InputChip(
        selected = false,
        border = null,
        onClick = onClick,
        label = {
            Text(label)
        },
        trailingIcon = {
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = onDelete
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除搜索记录：$label",
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}
