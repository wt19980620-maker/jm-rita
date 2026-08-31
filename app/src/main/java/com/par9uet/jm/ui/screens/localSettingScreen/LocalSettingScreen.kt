package com.par9uet.jm.ui.screens.localSettingScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.par9uet.jm.config.SOURCE_NAME
import com.par9uet.jm.config.SOURCE_WEBSITE
import com.par9uet.jm.ui.components.CommonScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSettingScreen() {
    CommonScaffold(
        title = "设置"
    ) {
        Column {
            ListItem(
                headlineContent = { Text("内容来源") },
                supportingContent = {
                    Text("$SOURCE_NAME（$SOURCE_WEBSITE）\n原生接口模式，不加载网页广告")
                }
            )
            ThemeSettingListItem()
            ApiSettingListItem()
        }
    }
}
