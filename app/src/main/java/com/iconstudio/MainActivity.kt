package com.iconstudio

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path as NativePath
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

private enum class Tool { PEN, LINE, RECT, CIRCLE, ERASER }
private data class Mark(val points: List<Offset>, val color: Color, val width: Float, val tool: Tool)
private data class Layer(val name: String, val marks: List<Mark>, val visible: Boolean = true)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IconStudioApp() }
    }
}

@Composable
private fun IconStudioApp() {
    val context = LocalContext.current
    val layers = remember { mutableStateListOf(Layer("Camada 1", emptyList())) }
    LaunchedEffect(Unit) { loadProject(context)?.let { layers.clear(); layers.addAll(it) } }
    var activeLayer by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(Color(0xFF20242B)) }
    var brushSize by remember { mutableFloatStateOf(8f) }
    var activeMark by remember { mutableStateOf<Mark?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showLayers by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    val undo = remember { mutableStateListOf<List<Layer>>() }
    val redo = remember { mutableStateListOf<List<Layer>>() }
    val palette = listOf(Color(0xFF20242B), Color(0xFFFFFFFF), Color(0xFF2563EB), Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF8B5CF6), Color(0xFFEC4899))

    fun snapshot() { undo.add(layers.toList()); if (undo.size > 40) undo.removeAt(0); redo.clear() }
    fun finishMark() {
        val m = activeMark ?: return
        snapshot()
        val old = layers[activeLayer]
        layers[activeLayer] = old.copy(marks = old.marks + m)
        activeMark = null
    }
    fun saveProject() {
        val root = JSONObject()
        val layerArray = JSONArray()
        layers.forEach { layer ->
            val lj = JSONObject().put("name", layer.name).put("visible", layer.visible)
            val marks = JSONArray()
            layer.marks.forEach { mark ->
                val mj = JSONObject().put("color", mark.color.toArgb()).put("width", mark.width).put("tool", mark.tool.name)
                val pts = JSONArray()
                mark.points.forEach { pts.put(JSONArray().put(it.x).put(it.y)) }
                mj.put("points", pts); marks.put(mj)
            }
            lj.put("marks", marks); layerArray.put(lj)
        }
        root.put("layers", layerArray)
        context.getSharedPreferences("icon_studio", 0).edit().putString("project", root.toString()).apply()
        Toast.makeText(context, "Projeto guardado neste dispositivo", Toast.LENGTH_SHORT).show()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2563EB), background = Color(0xFFF3F5F8), surface = Color.White)) {
        Column(Modifier.fillMaxSize().background(Color(0xFFF3F5F8))) {
            Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ICON STUDIO", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color(0xFF182230), letterSpacing = 1.4.sp)
                    Text("Editor vetorial", fontSize = 11.sp, color = Color(0xFF7A8493))
                }
                IconButton(onClick = { if (undo.isNotEmpty()) { redo.add(layers.toList()); val s = undo.removeAt(undo.lastIndex); layers.clear(); layers.addAll(s); activeLayer = activeLayer.coerceIn(0, layers.lastIndex) } }) { Icon(Icons.Default.Undo, "Desfazer") }
                IconButton(onClick = { if (redo.isNotEmpty()) { undo.add(layers.toList()); val s = redo.removeAt(redo.lastIndex); layers.clear(); layers.addAll(s); activeLayer = activeLayer.coerceIn(0, layers.lastIndex) } }) { Icon(Icons.Default.Redo, "Refazer") }
                IconButton(onClick = { saveProject() }) { Icon(Icons.Default.Save, "Guardar") }
                IconButton(onClick = { showExport = true }) { Icon(Icons.Default.FileDownload, "Exportar") }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tool.entries.forEach { t ->
                    val selected = tool == t
                    FilledTonalButton(onClick = { tool = t }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) Color(0xFFDBEAFE) else Color.White, contentColor = if (selected) Color(0xFF1D4ED8) else Color(0xFF475467))) {
                        Icon(when(t) { Tool.PEN -> Icons.Default.Edit; Tool.LINE -> Icons.Default.ShowChart; Tool.RECT -> Icons.Default.Rectangle; Tool.CIRCLE -> Icons.Default.Circle; Tool.ERASER -> Icons.Default.AutoFixNormal }, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp)); Text(when(t) { Tool.PEN -> "Traço"; Tool.LINE -> "Linha"; Tool.RECT -> "Retângulo"; Tool.CIRCLE -> "Círculo"; Tool.ERASER -> "Borracha" }, fontSize = 11.sp)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).background(Color(0xFFE6EAF0), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(14.dp).background(Color.White, RoundedCornerShape(5.dp)).pointerInput(tool, color, brushSize, activeLayer, zoom) {
                    detectDragGestures(onDragStart = { p ->
                        val q = Offset((p.x - size.width/2f)/zoom + size.width/2f, (p.y - size.height/2f)/zoom + size.height/2f)
                        activeMark = Mark(listOf(q), if (tool == Tool.ERASER) Color.White else color, if (tool == Tool.ERASER) brushSize*2.5f else brushSize, tool)
                    }, onDrag = { change, _ ->
                        val q = Offset((change.position.x - size.width/2f)/zoom + size.width/2f, (change.position.y - size.height/2f)/zoom + size.height/2f)
                        activeMark = activeMark?.let { it.copy(points = it.points + q) }
                    }, onDragEnd = { finishMark() })
                }) {
                    drawRect(Color.White)
                    val center = Offset(size.width/2f, size.height/2f)
                    withTransform({ translate(center.x, center.y); scale(zoom, zoom, Offset.Zero); translate(-center.x, -center.y) }) {
                        layers.filter { it.visible }.forEach { layer -> layer.marks.forEach { drawMark(it) } }
                        activeMark?.let { drawMark(it) }
                    }
                }
                Column(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalAlignment = Alignment.End) {
                    FilledTonalIconButton(onClick = { zoom = (zoom * 1.2f).coerceAtMost(4f) }) { Icon(Icons.Default.Add, "Aumentar zoom") }
                    Spacer(Modifier.height(5.dp))
                    FilledTonalIconButton(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(.25f) }) { Icon(Icons.Default.Remove, "Diminuir zoom") }
                    Text("${(zoom*100).toInt()}%", fontSize = 10.sp, color = Color.DarkGray)
                }
            }
            Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("COR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF667085))
                    Spacer(Modifier.width(10.dp))
                    palette.forEach { c -> Box(Modifier.padding(end = 8.dp).size(25.dp).background(c, CircleShape).border(if (color == c) 2.dp else 1.dp, if (color == c) Color(0xFF2563EB) else Color(0xFFD0D5DD), CircleShape).clickable { color = c }) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showLayers = true }) { Icon(Icons.Default.Layers, null, Modifier.size(18.dp)); Text("Camadas") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Espessura", fontSize = 11.sp, color = Color(0xFF667085))
                    Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 1f..36f, modifier = Modifier.weight(1f))
                    Text("${brushSize.toInt()} px", fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { c -> Box(Modifier.size(1.dp)) }
                }
            }
        }
        // Palette controls are separate so each swatch remains an actual action.
        if (showLayers) AlertDialog(onDismissRequest = { showLayers = false }, title = { Text("Camadas") }, text = {
            Column {
                layers.forEachIndexed { i, layer ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = activeLayer == i, onClick = { activeLayer = i })
                        Text(layer.name, Modifier.weight(1f))
                        IconButton(onClick = { layers[i] = layer.copy(visible = !layer.visible) }) { Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Visibilidade") }
                        IconButton(onClick = { if (layers.size > 1) { layers.removeAt(i); activeLayer = activeLayer.coerceAtMost(layers.lastIndex) } }) { Icon(Icons.Default.Delete, "Eliminar camada") }
                    }
                }
                TextButton(onClick = { snapshot(); layers.add(Layer("Camada ${layers.size + 1}", emptyList())); activeLayer = layers.lastIndex }) { Icon(Icons.Default.Add, null); Text("Nova camada") }
            }
        }, confirmButton = { TextButton(onClick = { showLayers = false }) { Text("Fechar") } })
        if (showExport) AlertDialog(onDismissRequest = { showExport = false }, title = { Text("Exportar ícone") }, text = { Text("Exporta o desenho visível como PNG quadrado de 1024 × 1024 píxeis para a galeria.") }, confirmButton = {
            TextButton(onClick = { exportPng(context, layers.toList()); showExport = false }) { Text("Exportar PNG") }
        }, dismissButton = { TextButton(onClick = { showExport = false }) { Text("Cancelar") } })
    }
}


private fun loadProject(context: android.content.Context): List<Layer>? {
    return try {
        val raw = context.getSharedPreferences("icon_studio", 0).getString("project", null) ?: return null
        val arr = JSONObject(raw).getJSONArray("layers")
        buildList {
            for (i in 0 until arr.length()) {
                val lj = arr.getJSONObject(i)
                val marksJson = lj.getJSONArray("marks")
                val marks = buildList {
                    for (j in 0 until marksJson.length()) {
                        val mj = marksJson.getJSONObject(j)
                        val pointsJson = mj.getJSONArray("points")
                        val points = buildList {
                            for (k in 0 until pointsJson.length()) {
                                val point = pointsJson.getJSONArray(k)
                                add(Offset(point.getDouble(0).toFloat(), point.getDouble(1).toFloat()))
                            }
                        }
                        add(Mark(points, Color(mj.getInt("color")), mj.getDouble("width").toFloat(), Tool.valueOf(mj.getString("tool"))))
                    }
                }
                add(Layer(lj.getString("name"), marks, lj.optBoolean("visible", true)))
            }
        }.ifEmpty { listOf(Layer("Camada 1", emptyList())) }
    } catch (_: Exception) { null }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMark(mark: Mark) {
    if (mark.points.isEmpty()) return
    val color = mark.color
    val stroke = DrawStroke(width = mark.width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    val start = mark.points.first()
    when (mark.tool) {
        Tool.PEN, Tool.ERASER -> {
            val p = Path().apply { moveTo(start.x, start.y); mark.points.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(p, color, style = stroke)
            if (mark.points.size == 1) drawCircle(color, mark.width/2f, start)
        }
        Tool.LINE -> if (mark.points.size > 1) drawLine(color, start, mark.points.last(), mark.width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        Tool.RECT -> if (mark.points.size > 1) {
            val end = mark.points.last()
            drawRect(color, Offset(min(start.x,end.x), min(start.y,end.y)), Size(kotlin.math.abs(end.x-start.x), kotlin.math.abs(end.y-start.y)), style = stroke)
        }
        Tool.CIRCLE -> if (mark.points.size > 1) {
            val end = mark.points.last(); val r = (start-end).getDistance()
            drawCircle(color, r, start, style = stroke)
        }
    }
}

private fun exportPng(context: android.content.Context, layers: List<Layer>) {
    try {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val scale = 1024f / 512f
        layers.filter { it.visible }.forEach { layer -> layer.marks.forEach { mark ->
            if (mark.points.isEmpty()) return@forEach
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (mark.tool == Tool.ERASER) android.graphics.Color.WHITE else mark.color.toArgb()
                strokeWidth = mark.width * scale; style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val path = NativePath()
            val pts = mark.points.map { Offset((it.x - 0f)*scale, (it.y - 0f)*scale) }
            path.moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { path.lineTo(it.x, it.y) }
            when(mark.tool) {
                Tool.RECT -> if (pts.size > 1) path.addRect(pts.first().x, pts.first().y, pts.last().x, pts.last().y, NativePath.Direction.CW)
                Tool.CIRCLE -> if (pts.size > 1) { val a=pts.first(); val b=pts.last(); val r=kotlin.math.hypot(a.x-b.x,a.y-b.y); path.addCircle(a.x,a.y,r,NativePath.Direction.CW) }
                else -> Unit
            }
            canvas.drawPath(path, paint)
            if (pts.size == 1 && mark.tool == Tool.PEN) canvas.drawCircle(pts[0].x, pts[0].y, paint.strokeWidth/2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color=paint.color })
        } }
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "IconStudio-${System.currentTimeMillis()}.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/IconStudio") }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Não foi possível criar o ficheiro")
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Toast.makeText(context, "PNG exportado para Pictures/IconStudio", Toast.LENGTH_LONG).show()
    } catch (e: Exception) { Toast.makeText(context, "Falha ao exportar: ${e.message}", Toast.LENGTH_LONG).show() }
}
