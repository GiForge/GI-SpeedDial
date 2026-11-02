package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SpeedDialEntity
import com.example.data.SpeedDialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class SpeedDialViewModel(private val repository: SpeedDialRepository) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val openFolderId = MutableStateFlow<String?>(null)

    // Combines database items with UI search filtering
    val itemsState: StateFlow<List<SpeedDialEntity>> = combine(
        repository.allItems,
        searchQuery,
        openFolderId
    ) { all, query, openId ->
        if (all.isEmpty()) {
            return@combine emptyList<SpeedDialEntity>()
        }
        all
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Normal direct list from repository for JSON export, etc.
    val rawItems = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleFolder(folderId: String) {
        if (openFolderId.value == folderId) {
            openFolderId.value = null
        } else {
            openFolderId.value = folderId
        }
    }

    fun closeOpenFolder() {
        openFolderId.value = null
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItemById(id)
            if (openFolderId.value == id) {
                openFolderId.value = null
            }
        }
    }

    fun addOrUpdateLink(
        id: String?,
        name: String,
        url: String,
        desc: String?,
        icon: String?,
        r: Int,
        g: Int,
        b: Int,
        parentId: String? = null
    ) {
        viewModelScope.launch {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }

            val itemId = id ?: ("t" + System.currentTimeMillis())
            val orderIndex = if (id == null) {
                // Approximate append order
                (rawItems.value.filter { it.parentId == parentId }.maxOfOrNull { it.orderIndex } ?: -1) + 1
            } else {
                rawItems.value.find { it.id == id }?.orderIndex ?: 0
            }

            val entity = SpeedDialEntity(
                id = itemId,
                type = "link",
                name = name.uppercase(),
                url = cleanUrl,
                desc = desc,
                icon = icon ?: getFaviconPlaceholder(cleanUrl),
                r = r,
                g = g,
                b = b,
                parentId = parentId,
                orderIndex = orderIndex
            )
            repository.insertItem(entity)
            
            // If inside a folder, update folder average color
            if (parentId != null) {
                updateFolderAverageColor(parentId)
            }
        }
    }

    fun addOrUpdateFolder(
        id: String?,
        name: String,
        r: Int,
        g: Int,
        b: Int
    ) {
        viewModelScope.launch {
            val itemId = id ?: ("f" + System.currentTimeMillis())
            val orderIndex = if (id == null) {
                (rawItems.value.filter { it.parentId == null }.maxOfOrNull { it.orderIndex } ?: -1) + 1
            } else {
                rawItems.value.find { it.id == id }?.orderIndex ?: 0
            }

            val entity = SpeedDialEntity(
                id = itemId,
                type = "folder",
                name = name.uppercase(),
                r = r,
                g = g,
                b = b,
                parentId = null,
                orderIndex = orderIndex
            )
            repository.insertItem(entity)
        }
    }

    private suspend fun updateFolderAverageColor(folderId: String) {
        val all = rawItems.value
        val folder = all.find { it.id == folderId } ?: return
        val children = all.filter { it.parentId == folderId }
        if (children.isEmpty()) return

        var sumR = 0
        var sumG = 0
        var sumB = 0
        children.forEach {
            sumR += it.r
            sumG += it.g
            sumB += it.b
        }
        val avgR = sumR / children.size
        val avgG = sumG / children.size
        val avgB = sumB / children.size

        repository.insertItem(folder.copy(r = avgR, g = avgG, b = avgB))
    }

    fun getFaviconPlaceholder(url: String): String {
        return try {
            val host = URL(url).host
            "https://www.google.com/s2/favicons?sz=128&domain=$host"
        } catch (e: Exception) {
            "https://www.google.com/s2/favicons?sz=128&domain=google.com"
        }
    }

    // Move link from root/other folders to parent folder, or extract out
    fun moveItem(itemId: String, targetFolderId: String?) {
        viewModelScope.launch {
            val all = rawItems.value
            val item = all.find { it.id == itemId } ?: return@launch
            val originalParentId = item.parentId

            if (targetFolderId == itemId) return@launch // Can't drop onto itself

            val newOrder = (all.filter { it.parentId == targetFolderId }.maxOfOrNull { it.orderIndex } ?: -1) + 1

            if (targetFolderId == null) {
                // Move to root
                repository.insertItem(item.copy(parentId = null, orderIndex = newOrder))
            } else {
                val targetItem = all.find { it.id == targetFolderId } ?: return@launch
                if (targetItem.type == "link") {
                    // Combine target & dragged links to create a new folder!
                    val folderId = "f" + System.currentTimeMillis()
                    val folderOrder = targetItem.orderIndex
                    
                    // 1. Create Folder
                    val newFolder = SpeedDialEntity(
                        id = folderId,
                        type = "folder",
                        name = "NUOVO GRUPPO",
                        r = (targetItem.r + item.r) / 2,
                        g = (targetItem.g + item.g) / 2,
                        b = (targetItem.b + item.b) / 2,
                        parentId = null,
                        orderIndex = folderOrder
                    )
                    repository.insertItem(newFolder)

                    // 2. Put elements inside
                    repository.insertItem(targetItem.copy(parentId = folderId, orderIndex = 0))
                    repository.insertItem(item.copy(parentId = folderId, orderIndex = 1))
                } else {
                    // Put item inside existing folder
                    repository.insertItem(item.copy(parentId = targetFolderId, orderIndex = newOrder))
                    updateFolderAverageColor(targetFolderId)
                }
            }

            if (originalParentId != null) {
                updateFolderAverageColor(originalParentId)
                // Clean up empty folders
                val fChildren = all.filter { it.parentId == originalParentId && it.id != itemId }
                if (fChildren.isEmpty()) {
                    repository.deleteItemById(originalParentId)
                }
            }
        }
    }

    // Fetches metadata asynchronously from current URL inputs
    fun fetchMetadataForUrl(
        urlStr: String,
        onStart: () -> Unit,
        onCompleted: (title: String?, faviconUrl: String?, r: Int, g: Int, b: Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onStart() }

            val cleanUrl = if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                "https://$urlStr"
            } else {
                urlStr
            }

            var faviconUrl = getFaviconPlaceholder(cleanUrl)
            var title: String? = null
            var finalR = 100
            var finalG = 100
            var finalB = 100

            try {
                // Fetch page title
                val client = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder().url(cleanUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        val titleRegex = Regex("<title>\\s*([^<]*)\\s*</title>", RegexOption.IGNORE_CASE)
                        val match = titleRegex.find(html)
                        title = match?.groups?.get(1)?.value?.trim()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Let's attempt to download favicon to compute dynamic color palette!
            try {
                val favUrl = URL(faviconUrl)
                val connection = favUrl.openConnection()
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    val colors = calculateAverageColor(bitmap)
                    finalR = colors.first
                    finalG = colors.second
                    finalB = colors.third
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                onCompleted(title, faviconUrl, finalR, finalG, finalB)
            }
        }
    }

    // Decodes base64 string or URI in clipboard and does dynamic color analysis
    fun processClipboardImage(
        context: Context,
        onImageProcessed: (base64OrUrl: String, r: Int, g: Int, b: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipboard == null) {
                withContext(Dispatchers.Main) { onError("Servizio appunti non disponibile") }
                return@launch
            }
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                withContext(Dispatchers.Main) { onError("Nessun elemento negli appunti") }
                return@launch
            }

            val item = clip.getItemAt(0)
            
            // Option A: Clipboard containing a direct image URI
            val uri = item.uri
            if (uri != null) {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/png"
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val b64String = "data:$mimeType;base64,$b64"
                        
                        // Analyse Bitmap
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val colors = if (bitmap != null) calculateAverageColor(bitmap) else Triple(100, 100, 100)

                        withContext(Dispatchers.Main) {
                            onImageProcessed(b64String, colors.first, colors.second, colors.third)
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Option B: Clipboard containing text (Data URI or standard Image URL)
            val text = item.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                if (text.startsWith("data:image/") && text.contains("base64,")) {
                    try {
                        val base64Data = text.substring(text.indexOf("base64,") + 7)
                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val colors = if (bitmap != null) calculateAverageColor(bitmap) else Triple(100, 100, 100)

                        withContext(Dispatchers.Main) {
                            onImageProcessed(text, colors.first, colors.second, colors.third)
                        }
                        return@launch
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (text.startsWith("http://") || text.startsWith("https://")) {
                    try {
                        val url = URL(text)
                        val connection = url.openConnection()
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        val inputStream = connection.getInputStream()
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        val colors = if (bitmap != null) calculateAverageColor(bitmap) else Triple(100, 100, 100)

                        withContext(Dispatchers.Main) {
                            onImageProcessed(text, colors.first, colors.second, colors.third)
                        }
                        return@launch
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onError("Copia prima un'icona o un'immagine da Google!")
            }
        }
    }

    fun calculateAverageColor(bitmap: Bitmap): Triple<Int, Int, Int> {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (pixel in pixels) {
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha > 150) { // filter highly transparent pixels
                r += android.graphics.Color.red(pixel)
                g += android.graphics.Color.green(pixel)
                b += android.graphics.Color.blue(pixel)
                count++
            }
        }
        return if (count == 0) {
            Triple(100, 100, 100)
        } else {
            Triple((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
        }
    }

    fun importJson(jsonContent: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.importJson(jsonContent)
            if (success) {
                openFolderId.value = null
                onSuccess()
            } else {
                onError("File JSON non valido o corrotto.")
            }
        }
    }

    fun exportJson(): String {
        return repository.exportJson(rawItems.value)
    }

    fun checkAndLoadDefaultData() {
        viewModelScope.launch {
            val currentItems = repository.allItems.first()
            if (currentItems.isEmpty()) {
                repository.loadDefaultData()
            }
        }
    }
}

class SpeedDialViewModelFactory(private val repository: SpeedDialRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpeedDialViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpeedDialViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
