package com.appathy.appstore

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
                val list = withContext(Dispatchers.IO) { Catalog.fetch(token) }
                apps = list
                withContext(Dispatchers.IO) {
                    for (a in list) {
                        val l = runCatching { Catalog.latestFor(a, a.defaultChannel, token) }.getOrNull()
                        latest[a.id] = l
                        states[a.id] = Catalog.installState(context, a, l)
                    }
                }
            } catch (e: Exception) {
                toast("カタログ取得に失敗: ${e.message}")
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appathy Store") },
                actions = {
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
                        onInstall = {
                            val l = latest[a.id] ?: return@AppRow
                            scope.launch {
                                busy[a.id] = true
                                try {
                                    withContext(Dispatchers.IO) {
                                        Installer.downloadAndInstall(context, a, l, token)
                                    }
                                } catch (e: Exception) {
                                    toast("${a.name}: ${e.message}")
                                }
                                busy[a.id] = false
                            }
                        }
                    )
                }
            }
        }
    }

    if (showSettings) {
        var input by remember { mutableStateOf(token) }
        AlertDialog(
            onDismissRequest = { if (token.isNotBlank()) showSettings = false },
            title = { Text("GitHub トークン") },
            text = {
                Column {
                    Text("private リポジトリの読み取り権限を持つ PAT を入力。端末内にのみ保存されます。")
                    OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    token = input.trim()
                    prefs.edit().putString("token", token).apply()
                    showSettings = false
                    reload()
                }) { Text("保存") }
            }
        )
    }
}

@Composable
fun AppRow(app: StoreApp, state: InstallState, latest: LatestRelease?, busy: Boolean, onInstall: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
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
                else -> {}
            }
        }
    }
}

private fun statusLabel(state: InstallState, latest: LatestRelease?): String = when (state) {
    InstallState.NOT_INSTALLED -> latest?.tag ?: ""
    InstallState.UP_TO_DATE -> "最新 (${latest?.tag ?: "リリースなし"})"
    InstallState.UPDATE_AVAILABLE -> "更新あり → ${latest?.tag}"
    InstallState.NO_RELEASE -> "リリース未作成"
    InstallState.UNKNOWN -> "情報不足"
}
