package com.appathy.appstore

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
    var bulk by remember { mutableStateOf("") }
    var conflict by remember { mutableStateOf<Triple<StoreApp, LatestRelease, String>?>(null) }
    var resumeInstall by remember { mutableStateOf<Pair<String, String>?>(null) }
    var category by remember { mutableStateOf("すべて") }
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
                            InstallLog.baseline(context, a, l?.tag)
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
            } catch (e: Exception) {
                toast("カタログ取得に失敗: ${e.message}")
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

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
                    TextButton(onClick = { showProfiles = true }) { Text("まとめて") }
                    TextButton(onClick = { reload() }) { Text("更新") }
                    TextButton(onClick = { showSettings = true }) { Text("設定") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val cats = listOf("すべて") + apps.map { it.category }.distinct().sorted()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                items(cats) { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                }
            }
            if (bulk.isNotBlank()) {
                Text(bulk, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                     style = MaterialTheme.typography.bodySmall)
            }
            if (loading) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            val shown = apps.filter { category == "すべて" || it.category == category }
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
        AlertDialog(
            onDismissRequest = { showProfiles = false },
            title = { Text("まとめてインストール") },
            text = {
                Column {
                    Text("未インストールのアプリだけを順に入れます。1本ごとに確認画面が出ます。")
                    for (p in profiles) {
                        val n = p.appIds.count { id ->
                            states[id] == InstallState.NOT_INSTALLED || states[id] == InstallState.UPDATE_AVAILABLE
                        }
                        TextButton(onClick = {
                            showProfiles = false
                            val queue = apps.filter {
                                it.id in p.appIds &&
                                (states[it.id] == InstallState.NOT_INSTALLED || states[it.id] == InstallState.UPDATE_AVAILABLE)
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
                        }) { Text("${p.name}（対象 ${n} 本）") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProfiles = false }) { Text("閉じる") } }
        )
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
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .combinedClickable(onClick = {}, onLongClick = onReinstall)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${app.category} ・ ${statusLabel(state, latest)}",
                    style = MaterialTheme.typography.bodySmall
                )
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
