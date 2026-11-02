package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.SpeedDialEntity
import java.net.URL
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

// Helper for Base64 decoding
fun decodeBase64ToBitmap(b64: String): Bitmap? {
    return try {
        val base64Data = if (b64.contains("base64,")) {
            b64.substring(b64.indexOf("base64,") + 7)
        } else {
            b64
        }
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SpeedDialScreen(
    viewModel: SpeedDialViewModel,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items by viewModel.itemsState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val openFolderId by viewModel.openFolderId.collectAsStateWithLifecycle()

    var showEditLinkDialog by remember { mutableStateOf<SpeedDialEntity?>(null) }
    var showEditFolderDialog by remember { mutableStateOf<SpeedDialEntity?>(null) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var isCreatingLink by remember { mutableStateOf(false) }

    // Immersive custom background: dark radial glow matching the web app's background
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val radialBrush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1F2C), Color(0xFF080A0F)),
                    center = center,
                    radius = size.maxDimension / 1.2f
                )
                drawRect(brush = radialBrush)
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header with search & import/export
            HeaderSection(
                searchQuery = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onImportClick = onImportClick,
                onExportClick = onExportClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Grid
            if (items.isEmpty()) {
                EmptyStateSection {
                    viewModel.checkAndLoadDefaultData()
                }
            } else {
                val rootItems = remember(items) { items.filter { it.parentId == null }.sortedBy { it.orderIndex } }
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    rootItems.forEach { item ->
                        val isQueryMatch = searchQuery.isNotEmpty() && (
                                item.name.contains(searchQuery, ignoreCase = true) ||
                                (item.desc ?: "").contains(searchQuery, ignoreCase = true)
                        )

                        // Render main Card / Tile
                        item {
                            SpeedDialTile(
                                item = item,
                                childrenCount = if (item.type == "folder") {
                                    items.count { it.parentId == item.id }
                                } else 0,
                                folderIconUrls = if (item.type == "folder") {
                                    items.filter { it.parentId == item.id }.take(4).map { it.icon.orEmpty() }
                                } else emptyList(),
                                isQueryMatch = isQueryMatch,
                                onClick = {
                                    if (item.type == "link") {
                                        openUrl(context, item.url.orEmpty())
                                    } else {
                                        viewModel.toggleFolder(item.id)
                                    }
                                },
                                onEdit = {
                                    if (item.type == "link") {
                                        showEditLinkDialog = item
                                    } else {
                                        showEditFolderDialog = item
                                    }
                                },
                                onDelete = {
                                    viewModel.deleteItem(item.id)
                                },
                                onOpenAll = {
                                    if (item.type == "folder") {
                                        val children = items.filter { it.parentId == item.id }
                                        children.forEach { child ->
                                            openUrl(context, child.url.orEmpty())
                                        }
                                    }
                                }
                            )
                        }

                        // Inline folder expansion rendering
                        val children = items.filter { it.parentId == item.id }.sortedBy { it.orderIndex }
                        val hasChildrenQueryMatch = searchQuery.isNotEmpty() && children.any { 
                            it.name.contains(searchQuery, ignoreCase = true) 
                        }

                        if (item.type == "folder" && (item.id == openFolderId || (searchQuery.isNotEmpty() && hasChildrenQueryMatch))) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize(animationSpec = spring())
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(255, 255, 255, 10))
                                        .border(1.dp, Color(item.r, item.g, item.b, 60), RoundedCornerShape(20.dp))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            text = item.name.uppercase(),
                                            color = Color(item.r, item.g, item.b),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (children.isNotEmpty()) {
                                            Button(
                                                onClick = {
                                                    children.forEach { child ->
                                                        openUrl(context, child.url.orEmpty())
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(item.r, item.g, item.b, 25)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Launch,
                                                    contentDescription = null,
                                                    tint = Color(item.r, item.g, item.b),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "APRI TUTTI",
                                                    color = Color(item.r, item.g, item.b),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Render children in a beautiful FlowRow or manual grid columns
                                    // Let's lay out them in dynamic rows
                                    val columnsCount = 2
                                    val rows = children.chunked(columnsCount)
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rows.forEach { rowItems ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                rowItems.forEach { child ->
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        SpeedDialTile(
                                                            item = child,
                                                            childrenCount = 0,
                                                            folderIconUrls = emptyList(),
                                                            isQueryMatch = searchQuery.isNotEmpty() && child.name.contains(searchQuery, ignoreCase = true),
                                                            onClick = {
                                                                openUrl(context, child.url.orEmpty())
                                                            },
                                                            onEdit = {
                                                                showEditLinkDialog = child
                                                            },
                                                            onDelete = {
                                                                viewModel.deleteItem(child.id)
                                                            },
                                                            onOpenAll = {}
                                                        )
                                                    }
                                                }
                                                // Pad the remaining space in the search/grid row so it looks clean
                                                if (rowItems.size < columnsCount) {
                                                    repeat(columnsCount - rowItems.size) {
                                                        Box(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }

                                        // Quick add link button inside group
                                        TextButton(
                                            onClick = {
                                                showEditLinkDialog = SpeedDialEntity(
                                                    id = "",
                                                    type = "link",
                                                    name = "",
                                                    parentId = item.id
                                                )
                                            },
                                            modifier = Modifier.align(Alignment.CenterHorizontally)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddLink,
                                                contentDescription = null,
                                                tint = Color(item.r, item.g, item.b)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "AGGIUNGI LINK A QUESTO GRUPPO",
                                                color = Color(item.r, item.g, item.b),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom create quick action buttons (only if not searching)
                    if (searchQuery.isEmpty()) {
                        item {
                            AddTileButton(text = "+ LINK") {
                                isCreatingLink = true
                            }
                        }
                        item {
                            AddTileButton(text = "+ GRUPPO") {
                                isCreatingFolder = true
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS FOR EDITING / CREATING ---

    // 1. Link dialog (Create / Edit)
    if (isCreatingLink || showEditLinkDialog != null) {
        val sourceItem = showEditLinkDialog
        EditLinkDialog(
            item = sourceItem,
            allFolders = items.filter { it.type == "folder" },
            onDismiss = {
                isCreatingLink = false
                showEditLinkDialog = null
            },
            onSave = { name, url, desc, icon, r, g, b, pId ->
                viewModel.addOrUpdateLink(
                    id = sourceItem?.id?.takeIf { it.isNotEmpty() },
                    name = name,
                    url = url,
                    desc = desc,
                    icon = icon,
                    r = r,
                    g = g,
                    b = b,
                    parentId = pId
                )
                isCreatingLink = false
                showEditLinkDialog = null
            },
            viewModel = viewModel
        )
    }

    // 2. Folder dialog (Create / Edit)
    if (isCreatingFolder || showEditFolderDialog != null) {
        val sourceItem = showEditFolderDialog
        EditFolderDialog(
            item = sourceItem,
            onDismiss = {
                isCreatingFolder = false
                showEditFolderDialog = null
            },
            onSave = { name, r, g, b ->
                viewModel.addOrUpdateFolder(
                    id = sourceItem?.id?.takeIf { it.isNotEmpty() },
                    name = name,
                    r = r,
                    g = g,
                    b = b
                )
                isCreatingFolder = false
                showEditFolderDialog = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderSection(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Column {
        Text(
            text = "GI-SPEEDDIAL",
            color = Color(0xFF00F2FF),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 4.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "CYBER SPEED DIAL",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search field
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Cerca link, gruppi o descrizioni...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00F2FF)) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("search_field"),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(255, 255, 255, 8),
                    unfocusedContainerColor = Color(255, 255, 255, 5),
                    focusedIndicatorColor = Color(0xFF00F2FF),
                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.15f)
                ),
                singleLine = true
            )

            // Import button
            IconButton(
                onClick = onImportClick,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(255, 255, 255, 5))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Importa",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }

            // Export button
            IconButton(
                onClick = onExportClick,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(255, 255, 255, 5))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Esporta",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun SpeedDialTile(
    item: SpeedDialEntity,
    childrenCount: Int,
    folderIconUrls: List<String>,
    isQueryMatch: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenAll: () -> Unit
) {
    val rgbColor = Color(item.r, item.g, item.b)
    val cardBackground = rgbColor.copy(alpha = 0.08f)
    val cardBorderColor = if (isQueryMatch) Color(0xFF00F2FF) else rgbColor.copy(alpha = 0.25f)
    val cardBorderWidth = if (isQueryMatch) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .shadowAndGlow(color = rgbColor.copy(alpha = 0.15f), corners = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cardBackground)
            .border(cardBorderWidth, cardBorderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Top Bar: contains Type indicator/badge on Left, actions on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Type / count badge
                if (item.type == "folder") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(rgbColor.copy(alpha = 0.15f))
                            .border(0.5.dp, rgbColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${childrenCount} LINK",
                            color = rgbColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(rgbColor.copy(alpha = 0.6f))
                    )
                }

                // Right side: Action Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.type == "folder" && childrenCount > 0) {
                        // Open all shortcut
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onOpenAll() }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Apri tutti",
                                tint = Color(0xFF00F2FF),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Edit
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onEdit() }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Modifica",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Delete
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onDelete() }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Elimina",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Center area: much larger icon / preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (item.type == "link") {
                    // Glow & Icon
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Soft blurred glow circle underneath icon
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(50))
                                .background(rgbColor.copy(alpha = 0.25f))
                        )

                        // Actual Icon loaders
                        val currentIcon = item.icon.orEmpty()
                        if (currentIcon.startsWith("data:image")) {
                            val bitmap = remember(currentIcon) { decodeBase64ToBitmap(currentIcon) }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(38.dp)
                                )
                            } else {
                                Icon(Icons.Default.Language, contentDescription = null, tint = rgbColor, modifier = Modifier.size(32.dp))
                            }
                        } else {
                            AsyncImage(
                                model = currentIcon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                } else {
                    // Folder previews (2x2 grid representing children icons)
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (folderIconUrls.isEmpty()) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = rgbColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(38.dp)
                            )
                        } else {
                            // Lay out up to 4 mini-icons inside a bigger 54.dp preview container
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    repeat(2) { idx ->
                                        val icon = folderIconUrls.getOrNull(idx)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                        ) {
                                            if (icon != null) {
                                                MiniTileIcon(icon)
                                            }
                                        }
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    repeat(2) { idx ->
                                        val icon = folderIconUrls.getOrNull(idx + 2)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                        ) {
                                            if (icon != null) {
                                                MiniTileIcon(icon)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom heading text
            Text(
                text = item.name.uppercase(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
fun MiniTileIcon(iconUrl: String) {
    if (iconUrl.startsWith("data:image")) {
        val bitmap = remember(iconUrl) { decodeBase64ToBitmap(iconUrl) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun AddTileButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(18.dp))
            .border(
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun EmptyStateSection(onLoadDefault: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Launch,
            contentDescription = null,
            tint = Color(0xFF00F2FF).copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ancora nessun link salvato",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Crea un nuovo link o gruppo per riempire la griglia, oppure carica dei sani default.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(280.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onLoadDefault,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FF)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "CARICA DATI DEMO",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// Custom Modifier extension to add soft blurred background shadows
fun Modifier.shadowAndGlow(color: Color, corners: androidx.compose.ui.unit.Dp) = this.drawBehind {
    val transparentColor = color.copy(alpha = 0f)
    drawContext.canvas.save()
    
    // Draw generic back shadow inside constraints
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, transparentColor),
            center = center,
            radius = size.maxDimension / 2f
        ),
        alpha = 0.5f
    )
    drawContext.canvas.restore()
}

// Utility to open URL in external browser safely
fun openUrl(context: Context, url: String) {
    var cleanUrl = url.trim()
    if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
        cleanUrl = "https://$cleanUrl"
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Impossibile aprire l'URL: $cleanUrl", Toast.LENGTH_LONG).show()
    }
}

// -----------------------------------------------------
// EDIT LINK DIALOG (Modal identical styling)
// -----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLinkDialog(
    item: SpeedDialEntity?,
    allFolders: List<SpeedDialEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, desc: String, icon: String?, r: Int, g: Int, b: Int, pId: String?) -> Unit,
    viewModel: SpeedDialViewModel
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(item?.url.orEmpty()) }
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var desc by remember { mutableStateOf(item?.desc.orEmpty()) }
    var iconUrl by remember { mutableStateOf(item?.icon.orEmpty()) }
    
    var rChannel by remember { mutableFloatStateOf((item?.r ?: 0).toFloat()) }
    var gChannel by remember { mutableFloatStateOf((item?.g ?: 242).toFloat()) }
    var bChannel by remember { mutableFloatStateOf((item?.b ?: 255).toFloat()) }

    var selectedParentId by remember { mutableStateOf(item?.parentId) }
    var isFetchingMetadata by remember { mutableStateOf(false) }

    // Automatic metadata fetch when URL changes
    LaunchedEffect(url) {
        if (url.length > 5 && (item == null || item.id.isEmpty())) {
            viewModel.fetchMetadataForUrl(
                urlStr = url,
                onStart = { isFetchingMetadata = true },
                onCompleted = { fetchedTitle, fetchedFavicon, fr, fg, fb ->
                    isFetchingMetadata = false
                    if (name.isEmpty() && fetchedTitle != null) {
                        name = fetchedTitle
                    }
                    if (fetchedFavicon != null && iconUrl.isEmpty()) {
                        iconUrl = fetchedFavicon
                        rChannel = fr.toFloat()
                        gChannel = fg.toFloat()
                        bChannel = fb.toFloat()
                    }
                }
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .border(1.dp, Color(0xFF00F2FF), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (item == null || item.id.isEmpty()) "NUOVO LINK" else "MODIFICA LINK",
                    color = Color(0xFF00F2FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // URL Group
                Column {
                    Text("LINK (URL)", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("https://www.google.com", color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F2FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isFetchingMetadata) {
                        Text(
                            text = "Recupero informazioni metadati in corso...",
                            color = Color(0xFF00F2FF),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Name Group
                Column {
                    Text("NOME VISUALIZZATO", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("es: GOOGLE", color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F2FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Desc Group
                Column {
                    Text("DESCRIZIONE (OPZIONALE)", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        placeholder = { Text("Note extra...", color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F2FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Group drop down selector
                Column {
                    Text("GRUPPO (CONNETTI A)", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    var isGroupDropdownExpanded by remember { mutableStateOf(false) }
                    val currentGroupLabel = if (selectedParentId == null) "NESSUNO (ROOT)" else {
                        allFolders.find { it.id == selectedParentId }?.name ?: "NESSUNO (ROOT)"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isGroupDropdownExpanded = true }
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = currentGroupLabel, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = isGroupDropdownExpanded,
                            onDismissRequest = { isGroupDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF161B26)).border(1.dp, Color(0xFF00F2FF))
                        ) {
                            DropdownMenuItem(
                                text = { Text("NESSUNO (ROOT)", color = Color.White) },
                                onClick = {
                                    selectedParentId = null
                                    isGroupDropdownExpanded = false
                                }
                            )
                            allFolders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder.name, color = Color.White) },
                                    onClick = {
                                        selectedParentId = folder.id
                                        isGroupDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Icon Preview & Paste
                Column {
                    Text("ICONA (GOOGLE O APPUNTI)", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (iconUrl.startsWith("data:image")) {
                                val bitmap = remember(iconUrl) { decodeBase64ToBitmap(iconUrl) }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            } else if (iconUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = iconUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(42.dp)
                                )
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    val queryParam = if (name.isNotEmpty()) name else "logo"
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.google.com/search?q=${Uri.encode(queryParam)}+icon+logo+png&tbm=isch")
                                    )
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(255, 255, 255, 12)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cerca su Google", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    viewModel.processClipboardImage(
                                        context = context,
                                        onImageProcessed = { b64OrUrl, fetchedR, fetchedG, fetchedB ->
                                            iconUrl = b64OrUrl
                                            rChannel = fetchedR.toFloat()
                                            gChannel = fetchedG.toFloat()
                                            bChannel = fetchedB.toFloat()
                                            Toast.makeText(context, "Icona incollata con successo!", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { errorText ->
                                            Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(255, 255, 255, 12)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Incolla Immagine", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Palette Customizers (RGB values & Presets)
                Column {
                    Text("PALETTE DI COLORI PERSONALIZZABILE", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Color indicator badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(rChannel.toInt(), gChannel.toInt(), bChannel.toInt()))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Presets
                    Text("Preset neon:", color = Color.White.copy(0.4f), fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(
                            Triple(0, 242, 255),    // Neon Cyber Cyan
                            Triple(0, 255, 100),    // Matrix Green
                            Triple(255, 0, 150),    // Toxic Pink
                            Triple(255, 180, 0),    // Solar Gold
                            Triple(130, 80, 255)    // Deep Violet
                        )
                        presets.forEach { rgb ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(rgb.first, rgb.second, rgb.third))
                                    .clickable {
                                        rChannel = rgb.first.toFloat()
                                        gChannel = rgb.second.toFloat()
                                        bChannel = rgb.third.toFloat()
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Slider R
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("R: ${rChannel.toInt()}", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = rChannel,
                            onValueChange = { rChannel = it },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Red,
                                activeTrackColor = Color.Red.copy(0.5f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Slider G
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("G: ${gChannel.toInt()}", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = gChannel,
                            onValueChange = { gChannel = it },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Green,
                                activeTrackColor = Color.Green.copy(0.5f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Slider B
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("B: ${bChannel.toInt()}", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = bChannel,
                            onValueChange = { bChannel = it },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Blue,
                                activeTrackColor = Color.Blue.copy(0.5f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ANNULLA", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (name.trim().isEmpty() || url.trim().isEmpty()) {
                                Toast.makeText(context, "Nome e URL sono obbligatori!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSave(
                                name,
                                url,
                                desc,
                                iconUrl,
                                rChannel.toInt(),
                                gChannel.toInt(),
                                bChannel.toInt(),
                                selectedParentId
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SALVA", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// EDIT FOLDER DIALOG (Group creation modal style)
// -----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFolderDialog(
    item: SpeedDialEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, r: Int, g: Int, b: Int) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var rChannel by remember { mutableFloatStateOf((item?.r ?: 130).toFloat()) }
    var gChannel by remember { mutableFloatStateOf((item?.g ?: 100).toFloat()) }
    var bChannel by remember { mutableFloatStateOf((item?.b ?: 240).toFloat()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .border(1.dp, Color(0xFF00F2FF), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B26))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (item == null || item.id.isEmpty()) "NUOVO GRUPPO" else "MODIFICA GRUPPO",
                    color = Color(0xFF00F2FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Name Group
                Column {
                    Text("NOME GRUPPO", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("es: SOCIAL", color = Color.White.copy(0.3f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00F2FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Palette Customizers (RGB values & Presets)
                Column {
                    Text("COLORE DEL GRUPPO (SUGGERITO)", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Color indicator badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(rChannel.toInt(), gChannel.toInt(), bChannel.toInt()))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Presets
                    Text("Preset neon:", color = Color.White.copy(0.4f), fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(
                            Triple(0, 242, 255),    // Neon Cyber Cyan
                            Triple(0, 255, 100),    // Matrix Green
                            Triple(255, 0, 150),    // Toxic Pink
                            Triple(255, 180, 0),    // Solar Gold
                            Triple(130, 80, 255)    // Deep Violet
                        )
                        presets.forEach { rgb ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(rgb.first, rgb.second, rgb.third))
                                    .clickable {
                                        rChannel = rgb.first.toFloat()
                                        gChannel = rgb.second.toFloat()
                                        bChannel = rgb.third.toFloat()
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Slider R
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("R: ${rChannel.toInt()}", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = rChannel,
                            onValueChange = { rChannel = it },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Red,
                                activeTrackColor = Color.Red.copy(0.5f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Slider G
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("G: ${gChannel.toInt()}", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = gChannel,
                            onValueChange = { gChannel = it },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Green,
                                activeTrackColor = Color.Green.copy(0.5f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Slider B
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("B: ${bChannel.toInt()}", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                        Slider(
                            value = bChannel,
                            onValueChange = { bChannel = it },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Blue,
                                activeTrackColor = Color.Blue.copy(0.5f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.1f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ANNULLA", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) {
                                Toast.makeText(context, "Nome gruppo è obbligatorio!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSave(
                                name,
                                rChannel.toInt(),
                                gChannel.toInt(),
                                bChannel.toInt()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F2FF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SALVA", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
