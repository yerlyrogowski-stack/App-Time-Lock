package dev.local.apptimelock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.local.apptimelock.core.formatRemaining
import dev.local.apptimelock.data.AppState
import dev.local.apptimelock.data.InstalledApp

@Composable
fun TimeLockApp(
    state: AppState,
    apps: List<InstalledApp>,
    appsLoaded: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onEnable: () -> Unit,
    onAppSettings: () -> Unit,
    onCountdownToggle: (Boolean) -> Unit = {},
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var showConsent by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                listOf("首页" to Icons.Outlined.HourglassEmpty, "应用" to Icons.Outlined.Apps, "设置" to Icons.Outlined.Tune).forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = page == index, onClick = { page = index },
                        icon = { Icon(item.second, null) }, label = { Text(item.first) },
                        modifier = Modifier.testTag("tab_$index"),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.error != null) Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(state.error, Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
            if (!state.ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else when (page) {
                0 -> HomeScreen(state, apps, onManage = { page = 1 }, onEnable = { showConsent = true })
                1 -> AppsScreen(state, apps, appsLoaded,
                    onToggle = { pkg, enabled ->
                        onToggle(pkg, enabled)
                        if (enabled && !state.serviceConnected && state.timer.sessions.isEmpty()) showConsent = true
                    },
                    onEnable = { showConsent = true },
                )
                else -> SettingsScreen(state, onEnable = { showConsent = true }, onHelp = { showHelp = true }, onAppSettings, onCountdownToggle)
            }
        }
    }
    if (showConsent) AlertDialog(
        onDismissRequest = { showConsent = false },
        icon = { Icon(Icons.Outlined.PrivacyTip, null) },
        title = { Text("开启使用提醒") },
        text = { Text("应用时间锁需要无障碍服务，以识别当前应用、显示倒计时，并在时间用完时覆盖提醒。\n\n系统会授予窗口访问能力；本应用只处理应用与窗口状态，不读取聊天文字、控件内容或截图，不上传数据。\n\n你可以随时在系统设置中关闭。关闭后，自动计时和拦截将停止。") },
        confirmButton = { TextButton(onClick = { showConsent = false; onEnable() }) { Text("了解并去设置") } },
        dismissButton = { TextButton(onClick = { showConsent = false }) { Text("稍后再说") } },
    )
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false }, title = { Text("小米 13 · 后台运行帮助") },
        text = { Text("在系统“应用信息”中完成以下设置：\n\n1. 开启“自启动 / 后台自启动”。\n2. 进入“省电策略”，选择“无限制”。智能限制后台运行可能让提醒停止响应。\n3. 若经常一键清理后台，可在最近任务中锁定本应用。\n\n然后检查“应用时间锁 · 使用提醒”无障碍服务是否仍开启，并打开一个受控应用确认有时长选择或倒计时。系统版本不同，入口名称可能不同。\n\n自启动不能代替无障碍授权，也不能保证系统永不关闭服务。关闭权限、强行停止或卸载会停止提醒。若提示“受限设置”，请在应用信息页按系统提示处理。") },
        confirmButton = { TextButton(onClick = { showHelp = false; onAppSettings() }) { Text("打开应用信息") } },
        dismissButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } },
    )
}

@Composable private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 20.dp, bottom = 10.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun HomeScreen(state: AppState, apps: List<InstalledApp>, onManage: () -> Unit, onEnable: () -> Unit) {
    val controlled = apps.filter { it.packageName in state.timer.sessions }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.HourglassEmpty, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text("应用时间锁", Modifier.padding(start = 8.dp), fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("慢一点，也很好", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("把时间\n留给自己。", Modifier.padding(top = 12.dp), fontSize = 38.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold)
            Text("打开前，先决定这一段用多久。", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (state.serviceConnected) Icons.Outlined.VerifiedUser else Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary)
                        Text(if (state.serviceConnected) "提醒服务已连接" else "保护未开启", Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    }
                    Text(if (state.serviceConnected) "${controlled.size} 个应用已加入提醒。\n离开时暂停，回来后继续。" else "先选好应用，再开启使用提醒。\n每一次继续，都由你来决定。", lineHeight = 23.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = if (state.serviceConnected) onManage else onEnable, shape = RoundedCornerShape(14.dp)) {
                        Text(if (state.serviceConnected) "管理受控应用" else "开启使用提醒")
                        Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("我的受控应用", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onManage) { Text("管理") }
            }
        }
        if (controlled.isEmpty()) item {
            OutlinedCard(onClick = onManage, shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AddCircleOutline, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("添加第一个应用", fontWeight = FontWeight.Medium)
                        Text("从最容易忘记时间的那个开始", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        items(controlled, key = { it.packageName }) { app ->
            val session = state.timer.sessions.getValue(app.packageName)
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(when { session.remainingMs > 0 -> "下次打开继续"; session.hasExpired -> "时间已到 · 下次重新选择"; else -> "打开时选择使用时长" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                    }
                    if (session.remainingMs > 0) Text(formatRemaining(session.remainingMs), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                }
            }
        }
        item { Text("10 / 20 / 30 分钟，也可以自己定。", Modifier.padding(bottom = 18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun AppIcon(app: InstalledApp) {
    val bitmap = remember(app.packageName, app.icon) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Image(bitmap, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
}

@Composable private fun AppsScreen(state: AppState, apps: List<InstalledApp>, loaded: Boolean, onToggle: (String, Boolean) -> Unit, onEnable: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        PageHeading("选择应用", "给容易忘记时间的应用，多一次停下来的机会。")
        if (!state.serviceConnected) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("保护尚未生效", fontWeight = FontWeight.SemiBold)
                    Text("勾选只保存名单。开启无障碍服务后，打开应用才会弹出时长选择。", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onEnable, contentPadding = PaddingValues(0.dp)) { Text("去开启使用提醒") }
                }
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("搜索应用") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).testTag("app_search"), shape = RoundedCornerShape(18.dp))
        Text("已选择 ${state.timer.sessions.size} 个 · 取消后清除剩余时间", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (!loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (loaded && filtered.isEmpty()) item { Text(if (query.isBlank()) "没有可添加的应用" else "没有找到匹配的应用", Modifier.padding(vertical = 24.dp)) }
            items(filtered, key = { it.packageName }) { app ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app)
                        Text(app.label, Modifier.weight(1f).padding(horizontal = 14.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Switch(checked = app.packageName in state.timer.sessions, onCheckedChange = { onToggle(app.packageName, it) }, modifier = Modifier.testTag("toggle_${app.packageName}"))
                    }
                }
            }
            item { Text("电话、系统设置、桌面和本工具保留自由访问。", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun SettingsScreen(state: AppState, onEnable: () -> Unit, onHelp: () -> Unit, onAppSettings: () -> Unit, onCountdownToggle: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("安心使用", "所有选择和剩余时间，只留在这台手机上。") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("倒计时侧边栏", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(checked = state.showCountdown, onCheckedChange = onCountdownToggle, modifier = Modifier.testTag("countdown_switch"))
                    }
                    Text("默认隐藏在屏幕边缘，不显示时间。点击边缘触发条滑出倒计时，5 秒后自动滑回。关闭仅隐藏侧边栏，计时和到时提醒照常运行。", color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 23.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("无障碍服务", fontWeight = FontWeight.SemiBold)
                    Text(if (state.serviceConnected) "服务已连接，自动提醒正在运行。" else "服务尚未连接，自动计时与拦截未生效。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onEnable) { Text("检查系统授权") }
                }
            }
        }
        item { SettingCard("后台运行", "小米手机建议开启自启动，并将省电策略设为“无限制”。", "查看设置步骤", onHelp) }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("你的使用规则", fontWeight = FontWeight.SemiBold)
                    Text("• 每个应用独立计时\n• 普通切后台或锁屏暂停，回来继续\n• 移除后台任务后重开，重新选时长\n• 到时轻震，可重新选择时长\n• 无法确认任务变化时保留余额", lineHeight = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SettingCard("隐私与数据", "不读取聊天内容、不截图、不联网。无账号、无使用历史。清除应用数据会同时清除全部设置和余额。", "系统应用信息", onAppSettings) }
        item { Text("应用时间锁 1.1.5\n为每一次使用，留一个清楚的开始。", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp) }
    }
}

@Composable private fun SettingCard(title: String, text: String, action: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 23.sp)
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action) }
        }
    }
}
