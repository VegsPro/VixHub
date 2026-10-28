package com.vixhub.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vixhub.app.ui.theme.VixHubTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

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

sealed class Tab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Status : Tab("Статус", Icons.Filled.MonitorHeart)
    data object Photos : Tab("Фото", Icons.Filled.PhotoLibrary)
    data object Database : Tab("База", Icons.Filled.Storage)
    data object Scripts : Tab("Скрипты", Icons.Filled.Code)
}

class AppViewModel : ViewModel() {
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

    // Photos
    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images: StateFlow<List<ImageItem>> = _images.asStateFlow()

    // DB
    private val _dbResult = MutableStateFlow("")
    val dbResult: StateFlow<String> = _dbResult.asStateFlow()

    // Scripts
    private val _scriptLogs = MutableStateFlow("")
    val scriptLogs: StateFlow<String> = _scriptLogs.asStateFlow()

    data class ImageItem(val id: String, val filename: String)

    init {
        refreshUrlAndHealth()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(msg: String) {
        _message.value = msg
    }

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

    fun loadImages() {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.listImages()
                .onSuccess { pairs ->
                    val list = pairs.map { (id, name) -> ImageItem(id, name) }
                    _images.value = list
                    showMessage("Загружено ${list.size} фото")
                }
                .onFailure {
                    showMessage("Ошибка списка: ${it.message}")
                }
            _loading.value = false
        }
    }

    fun uploadPhoto(bytes: ByteArray, name: String) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.uploadPhoto(bytes, name)
                .onSuccess {
                    showMessage("Загружено: ${it.optString("id", it.toString())}")
                    loadImages()
                }
                .onFailure {
                    showMessage("Ошибка загрузки: ${it.message}")
                }
            _loading.value = false
        }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch {
            _loading.value = true
            ApiClient.deleteImage(id)
                .onSuccess {
                    showMessage("Удалено")
                    // убираем из локального списка сразу
                    _images.value = _images.value.filter { it.id != id }
                    loadImages()
                }
                .onFailure {
                    showMessage("Удаление не поддерживается сервером: ${it.message}")
                }
            _loading.value = false
        }
    }

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
                .onFailure {
                    _dbResult.value = "Ошибка: ${it.message}"
                }
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
                .onFailure {
                    showMessage("Ошибка: ${it.message}")
                }
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

    private fun prettyJson(raw: String): String {
        return try {
            when {
                raw.trimStart().startsWith("{") -> JSONObject(raw).toString(2)
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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(Tab.Status, Tab.Photos, Tab.Database, Tab.Scripts)
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VixHub", fontWeight = FontWeight.Bold)
                        Text(
                            "Клиент сервера",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> StatusScreen(vm)
                1 -> PhotosScreen(vm)
                2 -> DatabaseScreen(vm)
                3 -> ScriptsScreen(vm)
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

        OutlinedButton(
            onClick = { vm.refreshUrlAndHealth() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Обновить URL и статус")
        }

        Text(
            "Токен авторизации встроен. Все запросы (кроме фото и health) используют Bearer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
fun PhotosScreen(vm: AppViewModel) {
    val images by vm.images.collectAsState()
    val context = LocalContext.current
    var previewId by remember { mutableStateOf<String?>(null) }
    var menuItem by remember { mutableStateOf<AppViewModel.ImageItem?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "photo_${System.currentTimeMillis()}.jpg"
                vm.uploadPhoto(bytes, name)
            }
        } catch (e: Exception) {
            vm.showMessage("Ошибка чтения файла: ${e.message}")
        }
    }

    fun copyLink(item: AppViewModel.ImageItem) {
        val url = ApiClient.imageUrl(item.id)
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("photo_url", url))
        vm.showMessage("Ссылка скопирована")
    }

    fun downloadPhoto(item: AppViewModel.ImageItem) {
        val url = ApiClient.imageUrl(item.id)
        if (url.isBlank()) {
            vm.showMessage("URL пустой")
            return
        }
        try {
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(item.filename)
                .setDescription("Загрузка фото из VixHub")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.filename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            dm.enqueue(request)
            vm.showMessage("Скачивание начато")
        } catch (e: Exception) {
            vm.showMessage("Ошибка скачивания: ${e.message}")
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { pickImage.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Upload, null)
                Spacer(Modifier.width(8.dp))
                Text("Загрузить")
            }
            OutlinedButton(
                onClick = { vm.loadImages() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Список")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Нет фото. Нажмите «Список» или загрузите.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Долгое нажатие — меню действий", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(images, key = { it.id }) { item ->
                    Box {
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = { previewId = item.id },
                                    onLongClick = { menuItem = item }
                                ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(ApiClient.imageUrl(item.id))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.filename,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        item.filename,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Меню по долгому нажатию
    if (menuItem != null) {
        val item = menuItem!!
        AlertDialog(
            onDismissRequest = { menuItem = null },
            title = {
                Text(
                    item.filename,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            downloadPhoto(item)
                            menuItem = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Скачать", modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            copyLink(item)
                            menuItem = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Скопировать ссылку", modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            vm.deletePhoto(item.id)
                            menuItem = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Удалить", modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuItem = null }) { Text("Отмена") }
            }
        )
    }

    // Превью по обычному тапу
    if (previewId != null) {
        AlertDialog(
            onDismissRequest = { previewId = null },
            title = { Text("Фото") },
            text = {
                AsyncImage(
                    model = ApiClient.imageUrl(previewId!!),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = { previewId = null }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
fun DatabaseScreen(vm: AppViewModel) {
    var dbName by remember { mutableStateOf("base") }
    var path by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var merge by remember { mutableStateOf(false) }
    var force by remember { mutableStateOf(false) }
    val result by vm.dbResult.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = dbName,
            onValueChange = { dbName = it },
            label = { Text("Имя базы") },
            placeholder = { Text("base / users / ...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Путь (опционально)") },
            placeholder = { Text("admin/name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Значение (JSON / строка / число)") },
            placeholder = { Text("\"Ivan\" или {\"a\":1}") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = merge, onCheckedChange = { merge = it })
            Text("Merge")
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = force, onCheckedChange = { force = it })
            Text("Force")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { vm.dbGet(dbName, path) }, modifier = Modifier.weight(1f)) {
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
                Text(
                    result,
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
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
                val bytes = stream.readBytes()
                vm.loadScript(scriptId.trim(), bytes)
            }
        } catch (e: Exception) {
            vm.showMessage("Ошибка файла: ${e.message}")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = scriptId,
            onValueChange = { scriptId = it },
            label = { Text("ID скрипта") },
            placeholder = { Text("main / bot / ...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { pickScript.launch("*/*") },
                modifier = Modifier.weight(1f)
            ) {
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
            Text("Получить логи (50 строк)")
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp).fillMaxSize()) {
                Text("Логи", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logs.ifEmpty { "Логи появятся здесь..." },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (logs.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
