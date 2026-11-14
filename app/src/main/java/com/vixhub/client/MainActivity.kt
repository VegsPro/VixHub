package com.vixhub.client
// BuildConfig generated

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vixhub.client.ui.theme.VixHubTheme
import android.os.Build
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VixHubTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VixHubApp()
                }
            }
        }
    }
}

data class MenuTile(val screen: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

val MenuTiles = listOf(
    MenuTile("storage", "Файлы", Icons.Filled.Folder, Color(0xFF00D4FF)),
    MenuTile("database", "Базы", Icons.Filled.Storage, Color(0xFF80CBC4)),
    MenuTile("scripts", "Скрипты", Icons.Filled.Code, Color(0xFFFFB74D)),
    MenuTile("sites", "Сайты", Icons.Filled.Language, Color(0xFFCE93D8)),
    MenuTile("server", "Сервер", Icons.Filled.CloudUpload, Color(0xFFEF9A9A))
)


class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPrefs(app)
    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _status = MutableStateFlow("Загрузка...")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _health = MutableStateFlow("")
    val health: StateFlow<String> = _health.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // Storage
    private val _groups = MutableStateFlow<List<ApiClient.GroupInfo>>(emptyList())
    val groups: StateFlow<List<ApiClient.GroupInfo>> = _groups.asStateFlow()

    private val _files = MutableStateFlow<List<ApiClient.FileInfo>>(emptyList())
    val files: StateFlow<List<ApiClient.FileInfo>> = _files.asStateFlow()

    private val _currentFolder = MutableStateFlow<String?>(null)
    val currentFolder: StateFlow<String?> = _currentFolder.asStateFlow()

    // folder name -> password (session)
    // passwords via AppPrefs

    private val _dbResult = MutableStateFlow("")
    val dbResult: StateFlow<String> = _dbResult.asStateFlow()

    private val _scriptLogs = MutableStateFlow("")
    val scriptLogs: StateFlow<String> = _scriptLogs.asStateFlow()

    init {
        refreshUrlAndHealth()
    }

    fun clearMessage() { _message.value = null }
    fun showMessage(msg: String) { _message.value = msg }

    fun refreshUrlAndHealth() {
        viewModelScope.launch {
            _loading.value = true
            _status.value = "Получение URL..."
            ApiClient.fetchBaseUrl()
                .onSuccess { url ->
                    _baseUrl.value = url
                    _status.value = "URL получен"
                    checkHealth()
                }
                .onFailure {
                    _status.value = "Ошибка URL: ${it.message}"
                    _loading.value = false
                }
        }
    }

    private suspend fun checkHealth() {
        _status.value = "Проверка health..."
        ApiClient.health()
            .onSuccess {
                _health.value = it
                _status.value = "Онлайн"
            }
            .onFailure {
                _health.value = it.message ?: "Ошибка"
                _status.value = "Оффлайн / ошибка"
            }
        _loading.value = false
    }

    // ── Storage ──

    fun loadGroups() {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.listGroups()
                .onSuccess {
                    _groups.value = it
                    showMessage("Папок: ${it.size}")
                }
                .onFailure { showMessage("Ошибка: ${it.message}") }
            _loading.value = false
        }
    }

    fun createGroup(name: String, password: String?) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.createGroup(name, password)
                .onSuccess {
                    showMessage(it)
                    if (!password.isNullOrBlank()) prefs.setFolderKey(name, password)
                    loadGroups()
                }
                .onFailure { showMessage("Ошибка: ${it.message}") }
            _loading.value = false
        }
    }

    fun deleteGroup(name: String) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.deleteGroup(name)
                .onSuccess {
                    showMessage(it)
                    prefs.forgetFolderKey(name)
                    if (_currentFolder.value == name) {
                        _currentFolder.value = null
                        _files.value = emptyList()
                    }
                    loadGroups()
                }
                .onFailure { showMessage("Ошибка: ${it.message}") }
            _loading.value = false
        }
    }

    fun openFolder(name: String, password: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            val key = password?.takeIf { it.isNotBlank() } ?: prefs.getFolderKey(name)
            ApiClient.listFiles(name, key)
                .onSuccess {
                    if (!key.isNullOrBlank()) prefs.setFolderKey(name, key)
                    _currentFolder.value = name
                    _files.value = it
                    showMessage("Файлов: ${it.size}")
                }
                .onFailure {
                    showMessage("Ошибка: ${it.message}")
                    // если пароль неверный — не открываем
                }
            _loading.value = false
        }
    }

    fun closeFolder() {
        _currentFolder.value = null
        _files.value = emptyList()
    }

    fun refreshFiles() {
        val folder = _currentFolder.value ?: return
        openFolder(folder, folderKey(folder))
    }

    fun uploadToFolder(filename: String, bytes: ByteArray) {
        val folder = _currentFolder.value ?: run {
            showMessage("Папка не открыта")
            return
        }
        viewModelScope.launch {
            _loading.value = true
            ApiClient.uploadFile(folder, filename, bytes, folderKey(folder))
                .onSuccess {
                    showMessage(it)
                    refreshFiles()
                }
                .onFailure { showMessage("Ошибка загрузки: ${it.message}") }
            _loading.value = false
        }
    }

    fun deleteFile(filename: String) {
        val folder = _currentFolder.value ?: return
        viewModelScope.launch {
            _loading.value = true
            ApiClient.deleteFile(folder, filename, folderKey(folder))
                .onSuccess {
                    showMessage(it)
                    _files.value = _files.value.filter { f -> f.name != filename }
                    refreshFiles()
                }
                .onFailure { showMessage("Ошибка: ${it.message}") }
            _loading.value = false
        }
    }

    fun folderKey(folder: String?): String? = folder?.let { prefs.getFolderKey(it) }

    fun forgetPassword(folder: String) {
        prefs.forgetFolderKey(folder)
        showMessage("Пароль для «$folder» забыт")
    }

    fun getViewMode(folder: String) = prefs.getViewMode(folder)
    fun setViewMode(folder: String, mode: String) = prefs.setViewMode(folder, mode)
    fun getServerSecret() = prefs.getServerSecret()
    fun setServerSecret(s: String) = prefs.setServerSecret(s)
    fun getSites() = prefs.getSites()
    fun addSite(id: String) = prefs.addSite(id)
    fun removeSiteLocal(id: String) = prefs.removeSite(id)
    fun getRecentDbs() = prefs.getRecentDbs()
    fun addRecentDb(n: String) = prefs.addRecentDb(n)
    fun getRecentScripts() = prefs.getRecentScripts()
    fun addRecentScript(id: String) = prefs.addRecentScript(id)

    fun deleteFilesBulk(names: List<String>) {
        val folder = _currentFolder.value ?: return
        viewModelScope.launch {
            _loading.value = true
            ApiClient.deleteFiles(folder, names, folderKey(folder))
                .onSuccess { (ok, fail) ->
                    showMessage("Удалено: $ok, ошибок: $fail")
                    refreshFiles()
                }
                .onFailure { showMessage("Ошибка: ${it.message}") }
            _loading.value = false
        }
    }

    fun uploadSite(projectId: String, files: List<Pair<String, ByteArray>>, update: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.uploadWebsite(projectId, files, update)
                .onSuccess {
                    prefs.addSite(projectId)
                    val url = it.optString("url", "/web/$projectId/")
                    showMessage(it.optString("message", "OK") + " · " + url)
                }
                .onFailure { showMessage("Ошибка: ${it.message}") }
            _loading.value = false
        }
    }


    // ── DB ──

    fun dbGet(name: String, path: String) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.dbGet(name, path)
                .onSuccess { _dbResult.value = prettyJson(it) }
                .onFailure { _dbResult.value = "Ошибка: ${it.message}" }
            _loading.value = false
        }
    }

    fun dbWrite(name: String, path: String, value: String, merge: Boolean, force: Boolean) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.dbWrite(name, path, value, merge, force)
                .onSuccess {
                    _dbResult.value = "OK: $it"
                    showMessage("Записано")
                }
                .onFailure {
                    _dbResult.value = "Ошибка: ${it.message}"
                    showMessage("Ошибка записи")
                }
            _loading.value = false
        }
    }

    fun dbDelete(name: String, path: String) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.dbDelete(name, path)
                .onSuccess {
                    _dbResult.value = "Удалено: $it"
                    showMessage("Удалено")
                }
                .onFailure { _dbResult.value = "Ошибка: ${it.message}" }
            _loading.value = false
        }
    }

    fun loadScript(id: String, bytes: ByteArray) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.loadScript(id, bytes)
                .onSuccess {
                    showMessage("Скрипт $id запущен")
                    _scriptLogs.value = it
                }
                .onFailure { showMessage("Ошибка: ${it.message}") }
            _loading.value = false
        }
    }

    fun stopScript(id: String) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.stopScript(id)
                .onSuccess { showMessage("Остановлен: $it") }
                .onFailure { showMessage("Ошибка: ${it.message}") }
            _loading.value = false
        }
    }

    fun fetchLogs(id: String) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.scriptLogs(id)
                .onSuccess { _scriptLogs.value = it }
                .onFailure { _scriptLogs.value = "Ошибка: ${it.message}" }
            _loading.value = false
        }
    }

    fun updateServer(secret: String, bytes: ByteArray, filename: String) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.updateServer(secret, bytes)
                .onSuccess { msg ->
                    showMessage(msg)
                    kotlinx.coroutines.delay(2500)
                    refreshUrlAndHealth()
                }
                .onFailure { showMessage("Ошибка обновления: ${it.message}") }
            _loading.value = false
        }
    }

    private fun prettyJson(raw: String): String {
        return try {
            when {
                raw.trimStart().startsWith("{") -> org.json.JSONObject(raw).toString(2)
                raw.trimStart().startsWith("[") -> org.json.JSONArray(raw).toString(2)
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VixHubApp(vm: AppViewModel = viewModel()) {
    var screen by remember { mutableStateOf("home") }
    BackHandler(enabled = screen != "home") {
        screen = "home"
    }
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val status by vm.status.collectAsState()
    val baseUrl by vm.baseUrl.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    var updateInfo by remember { mutableStateOf<RemoteRelease?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }
    var showInstallCached by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(Unit) {
        val cached = UpdateManager.cachedApk(context)
        val pendingVer = AppPrefs(context).pendingApkVersion()
        if (cached != null && pendingVer > BuildConfig.VERSION_CODE) {
            showInstallCached = cached
        }
        UpdateManager.checkLatest()
            .onSuccess { rel ->
                if (rel != null && rel.versionCode > BuildConfig.VERSION_CODE) {
                    updateInfo = rel
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VixHub", fontWeight = FontWeight.Bold)
                        Text(
                            when (screen) {
                                "home" -> "Клиент сервера"
                                "storage" -> "Файлы"
                                "database" -> "Базы данных"
                                "scripts" -> "Скрипты"
                                "sites" -> "Сайты"
                                "server" -> "Обновление сервера"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (screen != "home") {
                        IconButton(onClick = { screen = "home" }) {
                            Icon(Icons.Default.ArrowBack, "Назад")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                actions = {
                    IconButton(onClick = { vm.refreshUrlAndHealth() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
    ) { padding ->
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = {
                val forward = targetState != "home" && initialState == "home"
                val back = targetState == "home" && initialState != "home"
                if (forward) {
                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(200)) { -it / 5 } + fadeOut(tween(180)))
                } else if (back) {
                    (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(200)) { it / 3 } + fadeOut(tween(180)))
                } else {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.96f, animationSpec = tween(150)))
                }
            },
            label = "screen"
        ) { s ->
            when (s) {
                "home" -> HomeScreen(
                    status = status,
                    baseUrl = baseUrl,
                    onOpen = { screen = it },
                    onRefresh = { vm.refreshUrlAndHealth() }
                )
                "storage" -> StorageScreen(vm)
                "database" -> DatabaseScreen(vm)
                "scripts" -> ScriptsScreen(vm)
                "sites" -> SitesScreen(vm)
                "server" -> ServerUpdateScreen(vm)
            }
        }
    }

    if (updateInfo != null && !updateDownloading) {
        val rel = updateInfo!!
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Доступно обновление") },
            text = {
                Text("Версия ${rel.versionName} (code ${rel.versionCode}). Сейчас: ${BuildConfig.VERSION_NAME}. Скачать в фоне?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val rel2 = rel
                    updateInfo = null
                    updateDownloading = true
                    kotlinx.coroutines.MainScope().launch {
                        UpdateManager.notifyProgress(context, "VixHub · обновление", "Скачивание…", 0, 0, true)
                        UpdateManager.downloadApk(context, rel2.apkUrl, rel2.versionCode) { d, t ->
                            if (t > 0) {
                                updateProgress = d.toFloat() / t
                                UpdateManager.notifyProgress(
                                    context, "VixHub · обновление",
                                    "${d / 1024 / 1024}/${t / 1024 / 1024} МБ",
                                    (d * 100 / t).toInt(), 100, true
                                )
                            }
                        }.onSuccess { file ->
                            updateDownloading = false
                            UpdateManager.notifyDone(context, "Готово к установке")
                            showInstallCached = file
                        }.onFailure {
                            updateDownloading = false
                            UpdateManager.cancelNotif(context)
                            vm.showMessage("Ошибка обновления: ${it.message}")
                        }
                    }
                }) { Text("Скачать") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) { Text("Позже") }
            }
        )
    }

    if (showInstallCached != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Установить обновление?") },
            text = { Text("APK уже в кэше. Можно установить поверх текущей версии.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        UpdateManager.installApk(context, showInstallCached!!)
                    } catch (e: Exception) {
                        vm.showMessage("Разрешите установку приложений: ${e.message}")
                    }
                }) { Text("Установить") }
            },
            dismissButton = {
                TextButton(onClick = { showInstallCached = null }) { Text("Позже") }
            }
        )
    }
}

@Composable
fun HomeScreen(
    status: String,
    baseUrl: String,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Разделы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        // 2-column tiles
        val tiles = MenuTiles
        for (row in tiles.chunked(2)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (tile in row) {
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale = if (pressed) 0.94f else 1f
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.15f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable(
                                interactionSource = interaction,
                                indication = null
                            ) { onOpen(tile.screen) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (pressed) 2.dp else 6.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .background(tile.color.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(tile.icon, null, tint = tile.color, modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(tile.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}


@Composable
fun StatusScreen(vm: AppViewModel) {
    val baseUrl by vm.baseUrl.collectAsState()
    val status by vm.status.collectAsState()
    val health by vm.health.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(
            title = "Состояние",
            value = status,
            icon = if (status.contains("Онлайн")) Icons.Default.CheckCircle else Icons.Default.Error,
            color = if (status.contains("Онлайн")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Базовый URL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = baseUrl.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ответ /health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = health.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        OutlinedButton(onClick = { vm.refreshUrlAndHealth() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Обновить URL и статус")
        }
    }
}

@Composable
fun StatusCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(40.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorageScreen(vm: AppViewModel) {
    val groups by vm.groups.collectAsState()
    val files by vm.files.collectAsState()
    val currentFolder by vm.currentFolder.collectAsState()
    val context = LocalContext.current

    var showCreate by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var createPass by remember { mutableStateOf("") }

    var passwordPromptFolder by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }

    var deleteGroupName by remember { mutableStateOf<String?>(null) }
    var fileMenu by remember { mutableStateOf<ApiClient.FileInfo?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }

    // Системная «Назад»: выбор → папка → не выходим из приложения (обрабатывает родитель)
    BackHandler(enabled = selectMode || currentFolder != null) {
        when {
            selectMode -> {
                selectMode = false
                selectedFiles = emptySet()
            }
            currentFolder != null -> vm.closeFolder()
        }
    }

    // list | gallery — per-folder preference
    var viewMode by remember { mutableStateOf("list") }
    LaunchedEffect(currentFolder) {
        if (currentFolder != null) {
            viewMode = vm.getViewMode(currentFolder!!)
        }
    }

    // previews
    var previewImage by remember { mutableStateOf<String?>(null) }
    var previewVideo by remember { mutableStateOf<String?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val folder = currentFolder
        if (folder.isNullOrBlank()) {
            vm.showMessage("Папка не открыта")
            return@rememberLauncherForActivityResult
        }
        UploadService.start(context, folder, vm.folderKey(folder), uris)
        vm.showMessage("Загрузка ${uris.size} файл(ов) в фоне…")
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (groups.isEmpty()) vm.loadGroups()
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var apkInstallName by remember { mutableStateOf<String?>(null) }
    var apkProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    fun openFile(f: ApiClient.FileInfo) {
        val ext = FileType.ext(f.name)
        when {
            ext == "apk" -> apkInstallName = f.name
            FileType.kind(f.name) == FileKind.IMAGE -> previewImage = f.name
            FileType.kind(f.name) == FileKind.VIDEO -> previewVideo = f.name
            FileType.kind(f.name) == FileKind.TEXT || FileType.kind(f.name) == FileKind.CODE -> previewText = f.name
            else -> fileMenu = f
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (currentFolder == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CreateNewFolder, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Создать")
                }
                OutlinedButton(onClick = { vm.loadGroups() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Обновить")
                }
            }
            Spacer(Modifier.height(12.dp))

            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.FolderOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Нет папок", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.name }) { g ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (g.protected && vm.folderKey(g.name) == null) {
                                            passwordPromptFolder = g.name
                                            passwordInput = ""
                                        } else {
                                            vm.openFolder(g.name)
                                        }
                                    },
                                    onLongClick = { deleteGroupName = g.name }
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (g.protected) Icons.Default.Lock else Icons.Default.Folder,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (g.protected) "Защищена паролем" else "Открытая",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (g.protected && vm.folderKey(g.name) != null) {
                                    TextButton(onClick = { vm.forgetPassword(g.name) }) {
                                        Text("Забыть", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (selectMode) {
                        selectMode = false
                        selectedFiles = emptySet()
                    } else vm.closeFolder()
                }) {
                    Icon(Icons.Default.ArrowBack, "Назад")
                }
                Column(Modifier.weight(1f)) {
                    Text(currentFolder!!, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (selectMode) "Выбрано: ${selectedFiles.size}" else "${files.size} файл(ов)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectMode) {
                    IconButton(onClick = {
                        selectedFiles = if (selectedFiles.size == files.size) emptySet() else files.map { it.name }.toSet()
                    }) {
                        Icon(Icons.Default.DoneAll, null)
                    }
                    IconButton(
                        onClick = {
                            if (selectedFiles.isNotEmpty()) {
                                vm.deleteFilesBulk(selectedFiles.toList())
                                selectMode = false
                                selectedFiles = emptySet()
                            }
                        },
                        enabled = selectedFiles.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(onClick = { selectMode = true }) {
                        Icon(Icons.Default.DoneAll, null)
                    }
                    IconButton(onClick = {
                        val next = if (viewMode == "list") "gallery" else "list"
                        viewMode = next
                        currentFolder?.let { vm.setViewMode(it, next) }
                    }) {
                        Icon(
                            if (viewMode == "list") Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "Вид"
                        )
                    }
                    IconButton(onClick = { vm.refreshFiles() }) {
                        Icon(Icons.Default.Refresh, null)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { pickFiles.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text("Загрузить файлы (до 200 МБ)")
            }
            Text(
                "Несколько файлов — очередь в фоне. Тап: открыть · ⋮ / долгое: меню",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val up by UploadProgress.state.collectAsState()
            if (up.active || (up.done && up.total > 0)) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (up.active) "Загрузка в «${up.folder}»: ${up.current}/${up.total}"
                            else up.message,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (up.active) {
                            LinearProgressIndicator(
                                progress = { if (up.total == 0) 0f else up.current.toFloat() / up.total },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(up.currentName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Успешно: ${up.ok} · Ошибки: ${up.fail}", style = MaterialTheme.typography.labelSmall)
                        } else {
                            TextButton(onClick = { UploadProgress.clear(); vm.refreshFiles() }) {
                                Text("Скрыть и обновить список")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Папка пуста", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (viewMode == "gallery") {
                val media = files.filter { FileType.isMedia(it.name) }
                val other = files.filter { !FileType.isMedia(it.name) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(media, key = { "m-" + it.name }) { f ->
                        val url = ApiClient.downloadUrl(currentFolder!!, f.name, vm.folderKey(currentFolder))
                        val selected = f.name in selectedFiles
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (selectMode) {
                                            selectedFiles = if (selected) selectedFiles - f.name else selectedFiles + f.name
                                        } else openFile(f)
                                    },
                                    onLongClick = {
                                        if (!selectMode) {
                                            selectMode = true
                                            selectedFiles = setOf(f.name)
                                        } else fileMenu = f
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box {
                                if (FileType.kind(f.name) == FileKind.VIDEO) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF1A2740)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayCircle, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(url)
                                            .addHeader("Authorization", "Bearer xQ9_mL2pR5tY_7vB3nM8kL1jH4gF6dS2a")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = f.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Box(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        f.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (selectMode) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            selectedFiles = if (it) selectedFiles + f.name else selectedFiles - f.name
                                        },
                                        modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                                    )
                                }
                            }
                        }
                    }
                    // non-media as small cards
                    gridItems(other, key = { "o-" + it.name }) { f ->
                        val selected = f.name in selectedFiles
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (selectMode) {
                                            selectedFiles = if (selected) selectedFiles - f.name else selectedFiles + f.name
                                        } else openFile(f)
                                    },
                                    onLongClick = {
                                        if (!selectMode) {
                                            selectMode = true
                                            selectedFiles = setOf(f.name)
                                        } else fileMenu = f
                                    }
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                Column(
                                    Modifier.fillMaxSize().padding(8.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(iconForKind(FileType.kind(f.name)), null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(6.dp))
                                    Text(f.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (selectMode) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            selectedFiles = if (it) selectedFiles + f.name else selectedFiles - f.name
                                        },
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(files, key = { it.name }) { f ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                Modifier
                                    .combinedClickable(
                                        onClick = {
                                            if (selectMode) {
                                                selectedFiles = if (f.name in selectedFiles) selectedFiles - f.name else selectedFiles + f.name
                                            } else openFile(f)
                                        },
                                        onLongClick = {
                                            if (!selectMode) {
                                                selectMode = true
                                                selectedFiles = setOf(f.name)
                                            } else fileMenu = f
                                        }
                                    )
                                    .padding(14.dp)
                                    .then(
                                        if (f.name in selectedFiles) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                        else Modifier
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectMode) {
                                    Checkbox(
                                        checked = f.name in selectedFiles,
                                        onCheckedChange = {
                                            selectedFiles = if (it) selectedFiles + f.name else selectedFiles - f.name
                                        }
                                    )
                                }
                                Icon(iconForKind(FileType.kind(f.name)), null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val sub = buildString {
                                        append(FileType.kind(f.name).name.lowercase())
                                        f.size?.let { append(" · "); append(formatSize(it)) }
                                    }
                                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { fileMenu = f }) {
                                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // dialogs: create / password / delete group — same as before
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Новая папка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' } },
                        label = { Text("Имя (a-z, 0-9, _, -)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = createPass,
                        onValueChange = { createPass = it },
                        label = { Text("Пароль (необязательно)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (createName.isBlank()) vm.showMessage("Укажите имя")
                    else {
                        showCreate = false
                        vm.createGroup(createName, createPass.ifBlank { null })
                        createName = ""; createPass = ""
                    }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена") } }
        )
    }

    if (passwordPromptFolder != null) {
        AlertDialog(
            onDismissRequest = { passwordPromptFolder = null },
            title = { Text("Пароль: ${passwordPromptFolder}") },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("X-Folder-Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = passwordPromptFolder!!
                    passwordPromptFolder = null
                    vm.openFolder(name, passwordInput)
                }) { Text("Открыть") }
            },
            dismissButton = { TextButton(onClick = { passwordPromptFolder = null }) { Text("Отмена") } }
        )
    }

    if (deleteGroupName != null) {
        AlertDialog(
            onDismissRequest = { deleteGroupName = null },
            title = { Text("Удалить папку?") },
            text = { Text("«${deleteGroupName}» и всё содержимое будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = deleteGroupName!!
                        deleteGroupName = null
                        vm.deleteGroup(n)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteGroupName = null }) { Text("Отмена") } }
        )
    }

    if (fileMenu != null && currentFolder != null) {
        val f = fileMenu!!
        val folder = currentFolder!!
        AlertDialog(
            onDismissRequest = { fileMenu = null },
            title = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (FileType.kind(f.name) in listOf(FileKind.IMAGE, FileKind.VIDEO, FileKind.TEXT)) {
                        TextButton(onClick = { fileMenu = null; openFile(f) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Visibility, null)
                            Spacer(Modifier.width(12.dp))
                            Text("Открыть", Modifier.weight(1f))
                        }
                    }
                    TextButton(
                        onClick = {
                            val url = ApiClient.downloadUrl(folder, f.name, vm.folderKey(folder))
                            try {
                                val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
                                val req = DownloadManager.Request(Uri.parse(url))
                                    .setTitle(f.name)
                                    .setDescription("VixHub")
                                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, f.name)
                                    .addRequestHeader("Authorization", "Bearer xQ9_mL2pR5tY_7vB3nM8kL1jH4gF6dS2a")
                                dm.enqueue(req)
                                vm.showMessage("Скачивание начато")
                            } catch (e: Exception) {
                                vm.showMessage("Ошибка: ${e.message}")
                            }
                            fileMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Скачать", Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            val key = vm.folderKey(folder)
                            val url = ApiClient.publicDownloadUrl(folder, f.name, key)
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("url", url))
                            if (!key.isNullOrBlank()) {
                                vm.showMessage("Ссылка скопирована (vix). ВНИМАНИЕ: в URL есть пароль папки (?key=…)")
                            } else {
                                vm.showMessage("Ссылка скопирована (vix)")
                            }
                            fileMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Скопировать ссылку", Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = { fileMenu = null; vm.deleteFile(f.name) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Удалить", Modifier.weight(1f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fileMenu = null }) { Text("Закрыть") } }
        )
    }


    // APK download + install
    if (apkInstallName != null && currentFolder != null) {
        val name = apkInstallName!!
        AlertDialog(
            onDismissRequest = { if (apkProgress == null) apkInstallName = null },
            title = { Text("Установка APK") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name)
                    if (apkProgress != null) {
                        val (d, t) = apkProgress!!
                        val p = if (t > 0) d.toFloat() / t else 0f
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        Text(
                            if (t > 0) "${d / 1024 / 1024} / ${t / 1024 / 1024} МБ" else "Скачивание…",
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        Text("Файл будет скачан во внутренний кэш и предложен к установке.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = apkProgress == null,
                    onClick = {
                        val folder = currentFolder!!
                        val key = vm.folderKey(folder)
                        val scope = kotlinx.coroutines.MainScope()
                        scope.launch {
                            apkProgress = 0L to -1L
                            ApiClient.downloadBytes(folder, name, key)
                                .onSuccess { bytes ->
                                    apkProgress = bytes.size.toLong() to bytes.size.toLong()
                                    val dir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
                                    val file = java.io.File(dir, name)
                                    file.writeBytes(bytes)
                                    AppPrefs(context).setPendingApkPath(file.absolutePath)
                                    apkProgress = null
                                    apkInstallName = null
                                    try {
                                        UpdateManager.installApk(context, file)
                                    } catch (e: Exception) {
                                        vm.showMessage("Разрешите установку из этого источника: ${e.message}")
                                    }
                                }
                                .onFailure {
                                    apkProgress = null
                                    vm.showMessage("Ошибка: ${it.message}")
                                }
                        }
                    }
                ) { Text("Скачать и установить") }
            },
            dismissButton = {
                TextButton(onClick = { apkInstallName = null; apkProgress = null }) { Text("Отмена") }
            }
        )
    }

    // Image preview
    if (previewImage != null && currentFolder != null) {
        val url = ApiClient.downloadUrl(currentFolder!!, previewImage!!, vm.folderKey(currentFolder))
        AlertDialog(
            onDismissRequest = { previewImage = null },
            title = { Text(previewImage!!, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .addHeader("Authorization", "Bearer xQ9_mL2pR5tY_7vB3nM8kL1jH4gF6dS2a")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = { TextButton(onClick = { previewImage = null }) { Text("Закрыть") } },
            dismissButton = {
                TextButton(onClick = {
                    fileMenu = files.find { it.name == previewImage }
                    previewImage = null
                }) { Text("Меню") }
            }
        )
    }

    // Video preview
    if (previewVideo != null && currentFolder != null) {
        VideoPlayerDialog(
            url = ApiClient.downloadUrl(currentFolder!!, previewVideo!!, vm.folderKey(currentFolder)),
            title = previewVideo!!,
            onDismiss = { previewVideo = null },
            onMenu = {
                fileMenu = files.find { it.name == previewVideo }
                previewVideo = null
            }
        )
    }

    // Text editor
    if (previewText != null && currentFolder != null) {
        TextEditorDialog(
            folder = currentFolder!!,
            filename = previewText!!,
            folderKey = vm.folderKey(currentFolder),
            onDismiss = { previewText = null },
            onSaved = {
                vm.showMessage("Сохранено")
                vm.refreshFiles()
            },
            showMessage = { vm.showMessage(it) }
        )
    }
}

@Composable
private fun iconForKind(kind: FileKind) = when (kind) {
    FileKind.IMAGE -> Icons.Default.Image
    FileKind.VIDEO -> Icons.Default.Movie
    FileKind.AUDIO -> Icons.Default.Audiotrack
    FileKind.TEXT, FileKind.CODE -> Icons.Default.Description
    FileKind.ARCHIVE -> Icons.Default.Folder
    FileKind.OTHER -> Icons.Default.InsertDriveFile
}

@Composable
fun VideoPlayerDialog(url: String, title: String, onDismiss: () -> Unit, onMenu: () -> Unit = {}) {
    val context = LocalContext.current
    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf("Authorization" to "Bearer xQ9_mL2pR5tY_7vB3nM8kL1jH4gF6dS2a")
            )
            .setAllowCrossProtocolRedirects(true)
        val mediaSource = ProgressiveMediaSource.Factory(httpFactory)
            .createMediaSource(MediaItem.fromUri(url))
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = { TextButton(onClick = onMenu) { Text("Меню") } }
    )
}

@Composable
fun TextEditorDialog(
    folder: String,
    filename: String,
    folderKey: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    showMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("Загрузка…") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(folder, filename) {
        loading = true
        ApiClient.downloadText(folder, filename, folderKey)
            .onSuccess { text = it; loading = false }
            .onFailure {
                text = ""
                loading = false
                showMessage("Ошибка чтения: ${it.message}")
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(filename, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 420.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        ApiClient.uploadFile(folder, filename, text.toByteArray(Charsets.UTF_8), folderKey)
                            .onSuccess { onSaved(); onDismiss() }
                            .onFailure { showMessage("Ошибка сохранения: ${it.message}") }
                        saving = false
                    }
                }
            ) { Text(if (saving) "…" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}


private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

@Composable
fun DatabaseScreen(vm: AppViewModel) {
    var dbName by remember { mutableStateOf("base") }
    var path by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var merge by remember { mutableStateOf(false) }
    var force by remember { mutableStateOf(false) }
    val result by vm.dbResult.collectAsState()

    val recentDbs = remember { vm.getRecentDbs() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (recentDbs.isNotEmpty()) {
            Text("Недавние базы", fontWeight = FontWeight.SemiBold)
            recentDbs.take(8).forEach { name ->
                SuggestionChip(
                    onClick = { dbName = name },
                    label = { Text(name) }
                )
            }
        }
        OutlinedTextField(value = dbName, onValueChange = { dbName = it }, label = { Text("Имя базы") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Путь") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Значение") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = merge, onCheckedChange = { merge = it })
            Text("Merge")
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = force, onCheckedChange = { force = it })
            Text("Force")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { vm.addRecentDb(dbName); vm.dbGet(dbName, path) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Читать")
            }
            Button(onClick = { vm.dbWrite(dbName, path, value, merge, force) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Записать")
            }
        }
        OutlinedButton(
            onClick = { vm.dbDelete(dbName, path) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Delete, null)
            Spacer(Modifier.width(8.dp))
            Text("Удалить")
        }
        if (result.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(result, Modifier.padding(12.dp).fillMaxWidth(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ScriptsScreen(vm: AppViewModel) {
    var scriptId by remember { mutableStateOf("main") }
    val logs by vm.scriptLogs.collectAsState()
    val context = LocalContext.current

    val pickScript = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        if (scriptId.isBlank()) {
            vm.showMessage("Укажите ID скрипта")
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                vm.addRecentScript(scriptId.trim()); vm.loadScript(scriptId.trim(), stream.readBytes())
            }
        } catch (e: Exception) {
            vm.showMessage("Ошибка файла: ${e.message}")
        }
    }

    val recentScripts = remember { vm.getRecentScripts() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recentScripts.isNotEmpty()) {
            Text("Недавние скрипты", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recentScripts.take(6).forEach { id ->
                    SuggestionChip(onClick = { scriptId = id }, label = { Text(id) })
                }
            }
        }
        OutlinedTextField(
            value = scriptId,
            onValueChange = { scriptId = it },
            label = { Text("ID скрипта") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { pickScript.launch("*/*") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(4.dp))
                Text("Загрузить .py")
            }
            OutlinedButton(
                onClick = { if (scriptId.isNotBlank()) vm.stopScript(scriptId.trim()) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.width(4.dp))
                Text("Стоп")
            }
        }
        Button(
            onClick = { if (scriptId.isNotBlank()) vm.fetchLogs(scriptId.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Article, null)
            Spacer(Modifier.width(8.dp))
            Text("Получить логи")
        }
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp).fillMaxSize()) {
                Text("Логи", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(
                        logs.ifEmpty { "Логи появятся здесь..." },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (logs.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}



@Composable
fun SitesScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val baseUrl by vm.baseUrl.collectAsState()
    var sites by remember { mutableStateOf(vm.getSites()) }
    fun siteLink(id: String): String = ApiClient.siteUrl(id)
    var projectId by remember { mutableStateOf("") }
    var isUpdate by remember { mutableStateOf(false) }
    var pendingFiles by remember { mutableStateOf<List<Pair<String, ByteArray>>>(emptyList()) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val list = mutableListOf<Pair<String, ByteArray>>()
        for (uri in uris) {
            try {
                val name = FileUtils.displayName(context, uri)
                val bytes = FileUtils.readBytes(context, uri)
                list.add(name to bytes)
            } catch (e: Exception) {
                vm.showMessage("Ошибка: ${e.message}")
            }
        }
        pendingFiles = list
    }

    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val name = FileUtils.displayName(context, uri)
            val bytes = FileUtils.readBytes(context, uri)
            pendingFiles = listOf(name to bytes)
        } catch (e: Exception) {
            vm.showMessage("Ошибка: ${e.message}")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Сайты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Загрузите ZIP или файлы (нужен index.html). Обновление заменяет все файлы проекта.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = projectId,
            onValueChange = { projectId = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' } },
            label = { Text("ID сайта (my_site)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isUpdate, onCheckedChange = { isUpdate = it })
            Text("Обновить существующий (/up_web)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { pickZip.launch("application/zip") }, modifier = Modifier.weight(1f)) {
                Text("ZIP")
            }
            OutlinedButton(onClick = { pickFiles.launch("*/*") }, modifier = Modifier.weight(1f)) {
                Text("Файлы")
            }
        }
        if (pendingFiles.isNotEmpty()) {
            Text("Выбрано: ${pendingFiles.joinToString { it.first }}", style = MaterialTheme.typography.labelSmall)
        }
        Button(
            onClick = {
                if (projectId.isBlank()) {
                    vm.showMessage("Укажите ID сайта")
                    return@Button
                }
                if (pendingFiles.isEmpty()) {
                    vm.showMessage("Выберите файлы или ZIP")
                    return@Button
                }
                vm.uploadSite(projectId.trim(), pendingFiles, isUpdate)
                sites = listOf(projectId.trim()) + sites.filter { it != projectId.trim() }
                pendingFiles = emptyList()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = projectId.isNotBlank() && pendingFiles.isNotEmpty()
        ) {
            Icon(Icons.Default.CloudUpload, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isUpdate) "Обновить сайт" else "Создать сайт")
        }

        HorizontalDivider()
        Text("Ваши сайты", fontWeight = FontWeight.SemiBold)
        if (sites.isEmpty()) {
            Text("Пока нет сохранённых сайтов", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            sites.forEach { id ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(id, fontWeight = FontWeight.SemiBold)
                            Text(
                                siteLink(id).ifEmpty { "/web/$id/" },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = {
                            val url = siteLink(id)
                            if (url.isNotBlank()) {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            Uri.parse(url)
                                        )
                                    )
                                } catch (e: Exception) {
                                    vm.showMessage(e.message ?: "Ошибка")
                                }
                            } else vm.showMessage("URL ещё не загружен — обновите статус")
                        }) {
                            Icon(Icons.Default.OpenInNew, null)
                        }
                        IconButton(onClick = {
                            val url = siteLink(id)
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("site", url))
                            vm.showMessage("Ссылка скопирована")
                        }) {
                            Icon(Icons.Default.Link, null)
                        }
                        IconButton(onClick = {
                            projectId = id
                            isUpdate = true
                            vm.showMessage("ID подставлен — выберите ZIP/файлы для обновления")
                        }) {
                            Icon(Icons.Default.Edit, null)
                        }
                        IconButton(onClick = {
                            vm.removeSiteLocal(id)
                            sites = vm.getSites()
                        }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerUpdateScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var secret by remember { mutableStateOf(vm.getServerSecret()) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var selectedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var confirmOpen by remember { mutableStateOf(false) }

    val pickPy = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                selectedBytes = stream.readBytes()
                selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "server.py"
            }
        } catch (e: Exception) {
            vm.showMessage("Ошибка файла: ${e.message}")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Обновление server.py", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "POST /update_server?secret=… + Bearer. Сервер проверяет compile(), делает .bak и перезапускается через ~1.5 с.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            label = { Text("Secret-код") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = {
            vm.setServerSecret(secret.trim())
            vm.showMessage("Secret сохранён")
        }) { Text("Сохранить secret") }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Файл server.py", fontWeight = FontWeight.SemiBold)
                Text(
                    selectedName?.let { "$it (${selectedBytes?.size ?: 0} байт)" } ?: "Файл не выбран",
                    color = if (selectedName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
                OutlinedButton(onClick = { pickPy.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Выбрать .py")
                }
            }
        }
        Button(
            onClick = {
                when {
                    secret.isBlank() -> vm.showMessage("Укажите secret")
                    selectedBytes == null -> vm.showMessage("Выберите файл")
                    else -> confirmOpen = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = secret.isNotBlank() && selectedBytes != null
        ) {
            Icon(Icons.Default.CloudUpload, null)
            Spacer(Modifier.width(8.dp))
            Text("Отправить и перезапустить")
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Подтвердите обновление") },
            text = { Text("Сервер перезапустится с «${selectedName}». Продолжить?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    vm.setServerSecret(secret.trim()); vm.updateServer(secret.trim(), selectedBytes!!, selectedName ?: "server.py")
                }) { Text("Обновить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Отмена") }
            }
        )
    }
}
