package com.sammy.fbili.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sammy.fbili.data.Account
import com.sammy.fbili.data.Settings
import com.sammy.fbili.data.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val theme by Settings.theme.collectAsState()
    val maxQ by Settings.maxQuality.collectAsState()
    val dmOn by Settings.danmakuOn.collectAsState()
    val user by Account.user.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("外观", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
        listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色").forEach { (m, name) ->
            Row(
                Modifier.fillMaxWidth().clickable { scope.launch { Settings.setTheme(ctx, m) } }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = theme == m, onClick = { scope.launch { Settings.setTheme(ctx, m) } })
                Text(name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Text("播放", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
        listOf(16 to "360P（最省电）", 32 to "480P", 64 to "720P", 80 to "1080P（登录）", 116 to "1080P 高帧率（大会员）", 127 to "1080P 超高清（大会员）").forEach { (qn, name) ->
            Row(
                Modifier.fillMaxWidth().clickable { scope.launch { Settings.setQuality(ctx, qn) } }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = maxQ == qn, onClick = { scope.launch { Settings.setQuality(ctx, qn) } })
                Text(name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("默认显示弹幕", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.weight(1f))
            Switch(checked = dmOn, onCheckedChange = { v -> scope.launch { Settings.setDanmaku(ctx, v) } })
        }

        Text("账号", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
        if (user != null) {
            Row(Modifier.fillMaxWidth().clickable {
                scope.launch { Account.logout() }
            }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("退出登录（${user!!.uname}）", fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text("未登录", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "fbili v1.0.1\n原生 Kotlin 实现，独立于任何既有客户端。\n弹幕为本地显示（不发服务器），发送弹幕请以官方 App 为准。",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(30.dp))
    }
}
