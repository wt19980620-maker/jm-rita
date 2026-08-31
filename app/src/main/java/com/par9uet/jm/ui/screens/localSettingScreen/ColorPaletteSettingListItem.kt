package com.par9uet.jm.ui.screens.localSettingScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.par9uet.jm.store.LocalSettingManager
import com.par9uet.jm.ui.theme.normalizeThemePalette
import com.par9uet.jm.ui.theme.themePaletteLabel
import com.par9uet.jm.ui.theme.themePaletteOptions
import org.koin.compose.getKoin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteSettingListItem(
    localSettingManager: LocalSettingManager = getKoin().get()
) {
    val localSetting by localSettingManager.localSettingState.collectAsState()
    val selectedPalette = normalizeThemePalette(localSetting.colorPalette)
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable { expanded = true },
        headlineContent = { Text("界面配色") },
        supportingContent = { Text(themePaletteLabel(localSetting.colorPalette)) },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                themePaletteOptions.first { it.id == selectedPalette }.previewColor
                            )
                    )
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "展开配色设置"
                    )
                }
                ExposedDropdownMenu(
                    modifier = Modifier.width(200.dp),
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    themePaletteOptions.forEach { palette ->
                        DropdownMenuItem(
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(palette.previewColor)
                                )
                            },
                            trailingIcon = {
                                if (selectedPalette == palette.id) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选中${palette.label}"
                                    )
                                }
                            },
                            text = { Text(palette.label) },
                            onClick = {
                                localSettingManager.updateColorPalette(palette.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}
