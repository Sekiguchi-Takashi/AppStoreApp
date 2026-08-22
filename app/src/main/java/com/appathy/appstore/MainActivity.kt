package com.appathy.appstore

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accent = Color(0xFF7BD88F)
private val Scheme = darkColorScheme(
    primary = Accent,
    background = Color(0xFF101418),
    surface = Color(0xFF181E24),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                StoreScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", 0) }

    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var showSettings by remember { mutableStateOf(token.isBlank()) }
    var loading by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(listOf<StoreApp>()) }
    var profiles by remember { mutableStateOf(listOf<Profile>()) }
    var showProfiles by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    var showOrder by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var showOpen by remember { mutableStateOf(Settings.showOpenButton(context)) }
    var diagText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf("") }
    var conflict by remember { mutableStateOf<Triple<StoreApp, LatestRelease, String>?>(null) }
    var resumeInstall by remember { mutableStateOf<Pair<String, String>?>(null) }
    val latest = remember { mutableStateMapOf<String, LatestRelease?>() }
    val states = remember { mutableStateMapOf<String, InstallState>() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun reload() {
        if (token.isBlank()) return
        scope.launch {
            loading = true
            try {
                val (list, profs) = withContext(Dispatchers.IO) { Catalog.fetch(token) }
                apps = list
                profiles = profs
                InstallLog.prune(context, list)
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.coroutineScope {
                    list.map { a ->
                        async {
                            val l = runCatching { Catalog.latestFor(a, a.defaultChannel, token) }.getOrNull()
                            latest[a.id] = l
                            states[a.id] = Catalog.installState(context, a, l)
                        }
                    }.awaitAll()
                    }
                    val keep = mutableMapOf<String, String>()
                    for (a in list) {
                        latest[a.id]?.let { keep[a.id] = it.tag }
                    }
                    Installer.pruneCache(context, keep)
                }
                WidgetCache.save(context, list, states)
                StoreWidget.notifyChanged(context)
            } catch (e: Exception) {
                toast("カタログ取得に失敗: ${e.message}")
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val activity = context as? android.app.Activity
    LaunchedEffect(apps.size) {
        val want = activity?.intent?.getStringExtra(StoreWidget.EXTRA_AUTO_INSTALL) ?: return@LaunchedEffect
        activity.intent?.removeExtra(StoreWidget.EXTRA_AUTO_INSTALL)
        val a = apps.firstOrNull { it.id == want } ?: return@LaunchedEffect
        val l = latest[a.id] ?: return@LaunchedEffect
        busy[a.id] = true
        try {
            withContext(Dispatchers.IO) { Installer.downloadAndInstall(context, a, l, token) }
        } catch (e: Exception) {
            toast("${a.name}: ${e.message}")
        }
        busy[a.id] = false
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, intent: android.content.Intent) {
                val status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999)
                val label = intent.getStringExtra("label") ?: ""
                if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    val confirm = if (android.os.Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(android.content.Intent.EXTRA_INTENT)
                    confirm?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (confirm != null) c.startActivity(confirm)
                    return
                }
                val msg = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE)
                toast(SessionInstaller.statusText(status, msg, label))
                if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) {
                    val appId = intent.getStringExtra("appId")
                    val tag = intent.getStringExtra("tag")
                    if (appId != null && tag != null) {
                        val wasInstalled = InstallLog.tagOf(c, appId) != null
                        InstallLog.record(c, appId, tag)
                        History.add(c, label, if (wasInstalled) "更新" else "インストール", tag)
                    }
                }
                InstallLog.resolvePending(c, apps)
                for (a in apps) {
                    states[a.id] = Catalog.installState(c, a, latest[a.id])
                }
            }
        }
        val filter = android.content.IntentFilter(SessionInstaller.ACTION_RESULT)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && apps.isNotEmpty()) {
                InstallLog.resolvePending(context, apps)
                InstallLog.prune(context, apps)
                val pend = resumeInstall
                if (pend != null) {
                    val app = apps.firstOrNull { it.id == pend.first }
                    val rel = latest[pend.first]
                    if (app != null && rel != null && Catalog.installedPackage(context, app) == null) {
                        resumeInstall = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    Installer.downloadAndInstall(context, app, rel, token)
                                }
                            } catch (e: Exception) {
                                toast("${app.name}: ${e.message}")
                            }
                        }
                    }
                }
                for (a in apps) {
                    states[a.id] = Catalog.installState(context, a, latest[a.id])
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appathy Store") },
                actions = {
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        TextButton(onClick = { menu = true }) { Text("メニュー") }
                        androidx.compose.material3.DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("まとめてインストール") },
                                onClick = { menu = false; showProfiles = true })
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("一覧を再読み込み") },
                                onClick = { menu = false; reload() })
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("並び順") },
                                onClick = { menu = false; showOrder = true })
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("ステータス") },
                                onClick = { menu = false; showManage = true })
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("履歴") },
                                onClick = { menu = false; showHistory = true })
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (showOpen) "「開く」ボタンを隠す" else "「開く」ボタンを表示") },
                                onClick = {
                                    menu = false
                                    showOpen = !showOpen
                                    Settings.setShowOpenButton(context, showOpen)
                                })
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Release診断（報告用）") },
                                onClick = { menu = false; showDiag = true })
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("トークン設定") },
                                onClick = { menu = false; showSettings = true })
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (bulk.isNotBlank()) {
                Text(bulk, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                     style = MaterialTheme.typography.bodySmall)
            }
            if (loading) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            val shown = apps
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                items(shown, key = { it.id }) { a ->
                    AppRow(
                        app = a,
                        state = states[a.id] ?: InstallState.UNKNOWN,
                        latest = latest[a.id],
                        busy = busy[a.id] == true,
                        showOpen = showOpen,
                        onOpen = {
                            val pkg = Catalog.installedPackage(context, a)?.packageName
                            val launch = if (pkg == null) null
                                else context.packageManager.getLaunchIntentForPackage(pkg)
                            if (launch != null) context.startActivity(launch)
                            else toast("${a.name}: 起動できません")
                        },
                        onReinstall = {
                            InstallLog.forget(context, a.id)
                            states[a.id] = InstallState.UPDATE_AVAILABLE
                            toast("${a.name}: 再インストールできます")
                        },
                        onInstall = {
                            val l = latest[a.id] ?: return@AppRow
                            scope.launch {
                                busy[a.id] = true
                                try {
                                    var problem: String? = null
                                    val reason = withContext(Dispatchers.IO) {
                                        var apk = Installer.download(context, a, l, token)
                                        problem = SignatureCheck.apkProblem(context, apk)
                                        if (problem != null) {
                                            apk.delete()
                                            apk = Installer.download(context, a, l, token)
                                            problem = SignatureCheck.apkProblem(context, apk)
                                        }
                                        if (problem != null) null
                                        else SignatureCheck.blockingReason(context, a, apk)
                                    }
                                    if (problem != null) {
                                        toast("${a.name}: ${problem}")
                                    } else if (reason != null) {
                                        conflict = Triple(a, l, reason)
                                    } else {
                                        withContext(Dispatchers.IO) {
                                            Installer.downloadAndInstall(context, a, l, token)
                                        }
                                    }
                                } catch (e: Exception) {
                                    toast("${a.name}: ${e.message}")
                                }
                                busy[a.id] = false
                                states[a.id] = Catalog.installState(context, a, latest[a.id])
                            }
                        }
                    )
                }
            }
        }
    }

    conflict?.let { (app, rel, reason) ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("署名が違うため上書きできません") },
            text = {
                Column {
                    Text("${app.name} は端末に入っている版と署名が異なります。Android は署名の違うアプリを上書きできないため、一度削除してから入れ直す必要があります。")
                    Text("削除するとそのアプリのデータも消えます。", Modifier.padding(top = 8.dp))
                    Text(reason, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pkg = Catalog.installedPackage(context, app)?.packageName
                    conflict = null
                    if (pkg != null) {
                        resumeInstall = app.id to rel.tag
                        Installer.uninstall(context, pkg)
                    }
                }) { Text("削除して入れ直す") }
            },
            dismissButton = {
                TextButton(onClick = { conflict = null }) { Text("やめる") }
            }
        )
    }

    if (showProfiles) {
        fun runQueue(queue: List<StoreApp>) {
            showProfiles = false
            if (queue.isEmpty()) {
                toast("対象がありません")
                return
            }
            scope.launch {
                for ((i, a) in queue.withIndex()) {
                    val l = latest[a.id] ?: continue
                    bulk = "${i + 1}/${queue.size} ${a.name} を準備中"
                    try {
                        withContext(Dispatchers.IO) {
                            Installer.downloadAndInstall(context, a, l, token)
                        }
                    } catch (e: Exception) {
                        toast("${a.name}: ${e.message}")
                    }
                    kotlinx.coroutines.delay(2500)
                }
                bulk = ""
                reload()
            }
        }
        val updatable = apps.filter { states[it.id] == InstallState.UPDATE_AVAILABLE && latest[it.id] != null }
        val notInstalled = apps.filter { states[it.id] == InstallState.NOT_INSTALLED && latest[it.id] != null }
        AlertDialog(
            onDismissRequest = { showProfiles = false },
            title = { Text("まとめてインストール") },
            text = {
                Column {
                    Text("1本ごとに OS の確認画面が出ます。")
                    TextButton(
                        onClick = { runQueue(updatable) },
                        enabled = updatable.isNotEmpty()
                    ) { Text("更新があるものだけ（${updatable.size} 本）") }
                    TextButton(
                        onClick = { runQueue(notInstalled) },
                        enabled = notInstalled.isNotEmpty()
                    ) { Text("未インストールのみ（${notInstalled.size} 本）") }
                    for (p in profiles) {
                        val target = apps.filter {
                            it.id in p.appIds && latest[it.id] != null &&
                            (states[it.id] == InstallState.NOT_INSTALLED || states[it.id] == InstallState.UPDATE_AVAILABLE)
                        }
                        TextButton(
                            onClick = { runQueue(target) },
                            enabled = target.isNotEmpty()
                        ) { Text("${p.name}（対象 ${target.size} 本）") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProfiles = false }) { Text("閉じる") } }
        )
    }

    if (showManage) {
        val edits = remember(apps) {
            mutableStateMapOf<String, Pair<String, String>>().apply {
                apps.forEach { put(it.id, it.status to it.memo) }
            }
        }
        val statuses = listOf("", "テスト中", "修正中", "最終版", "停止中")
        AlertDialog(
            onDismissRequest = { showManage = false },
            title = { Text("ステータス") },
            text = {
                LazyColumn {
                    items(apps, key = { it.id }) { a ->
                        val cur = edits[a.id] ?: ("" to "")
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    val i = statuses.indexOf(cur.first).let { if (it < 0) 0 else it }
                                    edits[a.id] = statuses[(i + 1) % statuses.size] to cur.second
                                }) { Text(if (cur.first.isBlank()) "なし" else cur.first) }
                            }
                            OutlinedTextField(
                                value = cur.second,
                                onValueChange = { edits[a.id] = cur.first to it.take(20) },
                                singleLine = true,
                                label = { Text("メモ (20文字まで)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    val payload = edits.toMap()
                    scope.launch {
                        try {
                            val msg = withContext(Dispatchers.IO) { CatalogWriter.saveStatus(token, payload) }
                            toast(msg)
                            showManage = false
                            reload()
                        } catch (e: Exception) {
                            toast("保存に失敗: ${e.message}")
                        }
                        saving = false
                    }
                }) { Text(if (saving) "保存中..." else "保存") }
            },
            dismissButton = { TextButton(onClick = { showManage = false }) { Text("閉じる") } }
        )
    }

    if (showOrder) {
        val slots = remember(apps) {
            mutableStateMapOf<Int, String>().apply {
                apps.filter { it.order in 1..50 }.forEach { put(it.order, it.id) }
            }
        }
        var picking by remember { mutableStateOf<Int?>(null) }
        var confirmReset by remember { mutableStateOf(false) }
        val nameOf = apps.associate { it.id to it.name }

        AlertDialog(
            onDismissRequest = { showOrder = false },
            title = { Text("並び順") },
            text = {
                Column {
                    Text("番号をタップしてアプリを選びます。選ばなかったアプリは最後尾になります。",
                        style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.padding(top = 8.dp)) {
                        items((1..50).toList()) { n ->
                            val id = slots[n]
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$n", Modifier.width(36.dp), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { picking = n }, modifier = Modifier.weight(1f)) {
                                    Text(if (id == null) "（未選択）" else nameOf[id] ?: id)
                                }
                                if (id != null) {
                                    TextButton(onClick = { slots.remove(n) }) { Text("解除") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    val payload = slots.entries.associate { (n, id) -> id to n }
                    scope.launch {
                        try {
                            val msg = withContext(Dispatchers.IO) { CatalogWriter.saveOrder(token, payload) }
                            toast(msg)
                            showOrder = false
                            reload()
                        } catch (e: Exception) {
                            toast("保存に失敗: ${e.message}")
                        }
                        saving = false
                    }
                }) { Text(if (saving) "保存中..." else "保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmReset = true }) { Text("リセット") }
                    TextButton(onClick = { showOrder = false }) { Text("閉じる") }
                }
            }
        )

        picking?.let { slot ->
            val used = slots.values.toSet()
            val choices = apps.filter { it.id !in used }
            AlertDialog(
                onDismissRequest = { picking = null },
                title = { Text("$slot 番に入れるアプリ") },
                text = {
                    if (choices.isEmpty()) {
                        Text("選べるアプリがありません")
                    } else {
                        LazyColumn {
                            items(choices, key = { it.id }) { a ->
                                TextButton(onClick = {
                                    slots[slot] = a.id
                                    picking = null
                                }, modifier = Modifier.fillMaxWidth()) { Text(a.name) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = null }) { Text("閉じる") } }
            )
        }

        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("並び順をリセットしますか？") },
                text = { Text("すべての番号の割り当てを解除します。保存を押すまでカタログには反映されません。") },
                confirmButton = {
                    TextButton(onClick = {
                        slots.clear()
                        confirmReset = false
                    }) { Text("リセットする") }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("やめる") } }
            )
        }
    }

    if (showHistory) {
        val entries = remember(showHistory) { History.list(context) }
        val fmt = remember { java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.JAPAN) }
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("履歴（最大100件・1か月）") },
            text = {
                if (entries.isEmpty()) {
                    Text("まだ履歴がありません")
                } else {
                    LazyColumn {
                        items(entries) { e ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text("${e.name} ・ ${e.action}", style = MaterialTheme.typography.bodyMedium)
                                Text("${fmt.format(java.util.Date(e.time))} ・ ${e.tag}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("閉じる") } },
            dismissButton = {
                TextButton(onClick = {
                    History.clear(context)
                    showHistory = false
                }) { Text("全消去") }
            }
        )
    }

    if (showDiag) {
        var picked by remember { mutableStateOf<StoreApp?>(null) }
        if (picked == null) {
            AlertDialog(
                onDismissRequest = { showDiag = false },
                title = { Text("どのアプリを調べますか") },
                text = {
                    LazyColumn {
                        items(apps, key = { it.id }) { a ->
                            TextButton(
                                onClick = { picked = a; diagText = "" },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(a.name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDiag = false }) { Text("閉じる") } }
            )
        } else {
            val target = picked!!
            LaunchedEffect(target.id) {
                diagText = "診断中..."
                diagText = withContext(Dispatchers.IO) {
                    runCatching { Diagnostics.reportOne(context, target, token) }
                        .getOrElse { "診断に失敗しました: ${it.message}" }
                }
            }
            AlertDialog(
                onDismissRequest = { showDiag = false; picked = null; diagText = "" },
                title = { Text(target.name + " の診断") },
                text = {
                    Column {
                        Text("コピーしてチャットに貼ると原因を特定できます。",
                            style = MaterialTheme.typography.bodySmall)
                        LazyColumn(Modifier.padding(top = 8.dp)) {
                            items(diagText.split("\n")) { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("diag", diagText))
                        toast("コピーしました")
                    }) { Text("コピー") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { picked = null; diagText = "" }) { Text("別のアプリ") }
                        TextButton(onClick = { showDiag = false; picked = null; diagText = "" }) { Text("閉じる") }
                    }
                }
            )
        }
    }

    if (showSettings) {
        var input by remember { mutableStateOf("") }
        var editing by remember { mutableStateOf(token.isBlank()) }
        AlertDialog(
            onDismissRequest = { if (token.isNotBlank()) showSettings = false },
            title = { Text("GitHub トークン") },
            text = {
                Column {
                    if (editing) {
                        Text("private リポジトリの読み取り権限を持つトークンを入力。端末内にのみ保存されます。")
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    } else {
                        Text("保存済み: " + masked(token))
                        Text("トークンは端末内にのみ保存されています。")
                        TextButton(onClick = { input = ""; editing = true }) { Text("変更する") }
                    }
                }
            },
            confirmButton = {
                if (editing) {
                    TextButton(onClick = {
                        if (input.isNotBlank()) {
                            token = input.trim()
                            prefs.edit().putString("token", token).apply()
                            reload()
                        }
                        showSettings = false
                    }) { Text("保存") }
                } else {
                    TextButton(onClick = { showSettings = false }) { Text("閉じる") }
                }
            }
        )
    }
}

private fun masked(t: String): String =
    if (t.length <= 8) "********" else t.take(4) + "*".repeat(8) + t.takeLast(4)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: StoreApp,
    state: InstallState,
    latest: LatestRelease?,
    busy: Boolean,
    onInstall: () -> Unit,
    onReinstall: () -> Unit = {},
    onOpen: () -> Unit = {},
    showOpen: Boolean = false,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .combinedClickable(onClick = {}, onLongClick = onReinstall)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = AppIcons.of(androidx.compose.ui.platform.LocalContext.current, app)
            if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Box(
                    Modifier.size(44.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) { Text(app.name.take(1), style = MaterialTheme.typography.titleLarge) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium)
                    if (app.status.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text(app.status, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    "${app.category} ・ ${statusLabel(state, latest)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (app.memo.isNotBlank()) {
                    Text(app.memo, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (showOpen && !busy &&
                (state == InstallState.UP_TO_DATE || state == InstallState.UPDATE_AVAILABLE)
            ) {
                TextButton(onClick = onOpen) { Text("開く") }
            }
            when {
                busy -> CircularProgressIndicator(Modifier.padding(8.dp))
                state == InstallState.NOT_INSTALLED ->
                    Button(onClick = onInstall) { Text("インストール") }
                state == InstallState.UPDATE_AVAILABLE ->
                    Button(onClick = onInstall) { Text("更新") }
                state == InstallState.UP_TO_DATE ->
                    Button(onClick = {}, enabled = false) { Text("最新") }
                else ->
                    Button(onClick = {}, enabled = false) { Text("配布なし") }
            }
        }
    }
}

private fun statusLabel(state: InstallState, latest: LatestRelease?): String = when (state) {
    InstallState.NOT_INSTALLED -> "未インストール ・ ${latest?.tag ?: ""}"
    InstallState.UP_TO_DATE -> "最新 ${latest?.tag ?: ""}"
    InstallState.UPDATE_AVAILABLE -> "更新あり → ${latest?.tag}"
    InstallState.NO_RELEASE -> "リリース未作成"
    InstallState.UNKNOWN -> "情報不足"
}
