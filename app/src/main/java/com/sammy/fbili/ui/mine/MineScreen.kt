package com.sammy.fbili.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sammy.fbili.data.Account
import com.sammy.fbili.net.normUrl

@Composable
fun MineScreen(
    onLogin: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onFav: () -> Unit,
    onDownloads: () -> Unit,
    onFollowing: () -> Unit,
    onUser: (Long) -> Unit,
) {
    val user by Account.user.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp)
                .clickable { user?.mid?.let { if (it > 0) onUser(it) } ?: onLogin() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (user != null) {
                AsyncImage(model = user!!.face.normUrl(), contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CircleShape))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(user!!.uname, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("Lv${user!!.levelInfo?.currentLevel ?: 1}" +
                        (if ((user!!.vip?.status ?: 0) == 1) " · 大会员" else ""),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(Modifier.size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.width(14.dp))
                Text("点击登录", fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        MenuRow("历史记录", onHistory)
        MenuRow("我的收藏", onFav)
        MenuRow("我的关注", onFollowing)
        MenuRow("离线缓存", onDownloads)
        MenuRow("设置（含深色模式）", onSettings)
    }
}

@Composable
private fun MenuRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(2.dp))
}
