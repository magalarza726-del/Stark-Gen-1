package com.maverick.airboard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.maverick.airboard.camera.AirCameraController
import com.maverick.airboard.tracking.BlueCapAnalyzer
import com.maverick.airboard.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private enum class AppScreen { SPLASH, HOME, CALIBRATION, DRAW, TUTORIAL }
private enum class DrawTool { SELECT, DRAW, ERASE }

private data class TrackingUi(
    val x: Float = .5f,
    val y: Float = .5f,
    val confidence: Float = 0f,
    val aspect: Float = 4f / 3f,
    val detected: Boolean = false,
)

private data class StrokeData(
    val points: SnapshotStateList<PointF> = mutableStateListOf(),
)

@Composable
fun StarkApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("stark_gen1", 0) }
    var screen by remember { mutableStateOf(AppScreen.SPLASH) }
    var calibrated by remember { mutableStateOf(prefs.getBoolean("calibrated", false)) }
    var hue by remember { mutableFloatStateOf(prefs.getFloat("hue", 220f)) }
    var area by remember { mutableFloatStateOf(prefs.getFloat("area", 0f)) }

    AppBackground {
        when (screen) {
            AppScreen.SPLASH -> Splash(
                onStart = { screen = AppScreen.HOME },
                onTutorial = { screen = AppScreen.TUTORIAL },
            )
            AppScreen.HOME -> Home(
                calibrated = calibrated,
                onNew = { screen = if (calibrated) AppScreen.DRAW else AppScreen.CALIBRATION },
                onCalibration = { screen = AppScreen.CALIBRATION },
                onTutorial = { screen = AppScreen.TUTORIAL },
            )
            AppScreen.CALIBRATION -> CameraWorkspace(
                calibrationMode = true,
                initialHue = hue,
                initialArea = area,
                onBack = { screen = AppScreen.HOME },
                onCalibrated = { newHue, newArea ->
                    hue = newHue
                    area = newArea
                    calibrated = true
                    prefs.edit().putBoolean("calibrated", true)
                        .putFloat("hue", newHue)
                        .putFloat("area", newArea)
                        .apply()
                },
                onContinue = { screen = AppScreen.DRAW },
            )
            AppScreen.DRAW -> CameraWorkspace(
                calibrationMode = false,
                initialHue = hue,
                initialArea = area,
                onBack = { screen = AppScreen.HOME },
                onCalibrated = { _, _ -> },
                onContinue = {},
            )
            AppScreen.TUTORIAL -> Tutorial(
                onBack = { screen = AppScreen.HOME },
                onBegin = { screen = if (calibrated) AppScreen.DRAW else AppScreen.CALIBRATION },
            )
        }
    }
}

@Composable
private fun AppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(StarkBackground, Color(0xFF06152B), Color(0xFF130B2C))
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                StarkCyan.copy(alpha = .10f),
                radius = size.minDimension * .75f,
                center = Offset(-size.width * .15f, size.height * .05f),
            )
            drawCircle(
                StarkViolet.copy(alpha = .10f),
                radius = size.minDimension * .72f,
                center = Offset(size.width * 1.08f, size.height * .90f),
            )
        }
        content()
    }
}

@Composable
private fun Splash(onStart: () -> Unit, onTutorial: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StarkMark(170.dp)
        Spacer(Modifier.height(18.dp))
        Text("STARK GEN 1", color = StarkText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("A I R B O A R D   D R A W", color = StarkCyan, fontSize = 15.sp)
        Spacer(Modifier.height(34.dp))
        Text(
            "Seguimiento de tapa azul sobre fondos blancos o uniformes",
            color = StarkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        PrimaryButton("Comenzar", onStart)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onTutorial,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text("Tutorial")
        }
        Spacer(Modifier.height(34.dp))
        Text("GEN 1", color = StarkMuted)
    }
}

@Composable
private fun Home(
    calibrated: Boolean,
    onNew: () -> Unit,
    onCalibration: () -> Unit,
    onTutorial: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header()
        MenuItem("＋", "Nueva sesión", "Comenzar un dibujo AirBoard", true, onNew)
        MenuItem("◎", "Calibración", "Alinear seguimiento de tapa azul", false, onCalibration)
        MenuItem("?", "Tutorial", "Ver el flujo de Gen 1", false, onTutorial)
        GlassCard {
            Text("Estado", color = StarkText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (calibrated) "● Tapa azul calibrada" else "○ Falta calibrar",
                color = if (calibrated) StarkSuccess else StarkMuted,
            )
        }
        Text(
            "GEN 1 · AZUL + FONDO UNIFORME",
            color = StarkMuted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun CameraWorkspace(
    calibrationMode: Boolean,
    initialHue: Float,
    initialArea: Float,
    onBack: () -> Unit,
    onCalibrated: (Float, Float) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tracking by remember { mutableStateOf(TrackingUi()) }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var calibrating by remember { mutableStateOf(false) }
    var calibratedNow by remember { mutableStateOf(initialArea > 0f) }
    var tolerance by remember { mutableFloatStateOf(28f) }
    var tool by remember { mutableStateOf(DrawTool.DRAW) }
    val strokes = remember { mutableStateListOf<StrokeData>() }
    var activeStroke by remember { mutableStateOf<StrokeData?>(null) }

    val analyzer = remember {
        BlueCapAnalyzer { result ->
            ContextCompat.getMainExecutor(context).execute {
                tracking = TrackingUi(
                    result.x,
                    result.y,
                    result.confidence,
                    result.frameAspectRatio,
                    result.detected,
                )
            }
        }.also {
            it.hueCenter = initialHue
            it.calibratedArea = initialArea
        }
    }
    LaunchedEffect(tolerance) { analyzer.hueTolerance = tolerance }
    val controller = remember { AirCameraController(context.applicationContext, analyzer) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(preview, hasPermission, lifecycleOwner) {
        if (hasPermission) preview?.let { controller.bind(lifecycleOwner, it) }
        onDispose { controller.unbind() }
    }

    DisposableEffect(Unit) {
        onDispose { controller.close() }
    }

    LaunchedEffect(tracking.detected, tracking.x, tracking.y, tool, calibrationMode) {
        if (calibrationMode) return@LaunchedEffect
        if (!tracking.detected || tracking.confidence < .48f) {
            activeStroke = null
            return@LaunchedEffect
        }

        val point = PointF(tracking.x, tracking.y)
        when (tool) {
            DrawTool.SELECT -> activeStroke = null
            DrawTool.DRAW -> {
                val stroke = activeStroke ?: StrokeData().also {
                    strokes.add(it)
                    activeStroke = it
                }
                val last = stroke.points.lastOrNull()
                if (last == null || abs(last.x - point.x) + abs(last.y - point.y) > .0025f) {
                    stroke.points.add(point)
                }
            }
            DrawTool.ERASE -> {
                activeStroke = null
                val it = strokes.iterator()
                while (it.hasNext()) {
                    val stroke = it.next()
                    if (stroke.points.any { p ->
                            dist(p.x, p.y, point.x, point.y) < .06f
                        }) {
                        it.remove()
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Volver", color = StarkCyan) }
            Spacer(Modifier.weight(1f))
            if (!calibrationMode) {
                StatusPill(tracking.detected, tracking.confidence)
            }
        }

        if (calibrationMode) Header(compact = true)

        CameraPanel(
            modifier = Modifier.fillMaxWidth().weight(1f),
            tracking = tracking,
            strokes = if (calibrationMode) emptyList() else strokes,
            calibrationMode = calibrationMode,
            tool = tool,
            focusPoint = focusPoint,
            onPreview = { preview = it },
            onFocus = { p ->
                focusPoint = p
                preview?.let { controller.focusAt(it, p.x, p.y) }
            },
        )

        if (!hasPermission) {
            GlassCard {
                Text("Se necesita permiso de cámara.", color = StarkText)
            }
        } else if (calibrationMode) {
            GlassCard {
                Text("Calibración de tapa azul", color = StarkText, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Spacer(Modifier.height(6.dp))
                Text("Coloca la tapa azul dentro del círculo sobre un fondo blanco o uniforme.", color = StarkMuted)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Confianza: " + (tracking.confidence * 100).toInt() + "%",
                    color = if (tracking.detected) StarkSuccess else StarkMuted,
                )
                Spacer(Modifier.height(10.dp))

                if (calibrating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Midiendo azul…", color = StarkText)
                    }
                } else {
                    PrimaryButton(if (calibratedNow) "Recalibrar" else "Calibrar") {
                        calibrating = true
                        preview?.let { controller.focusCenter(it) }
                        analyzer.startCalibration { success, newHue, newArea ->
                            ContextCompat.getMainExecutor(context).execute {
                                calibrating = false
                                if (success) {
                                    calibratedNow = true
                                    onCalibrated(newHue, newArea)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { preview?.let { controller.focusCenter(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("◎ Reenfocar")
                }

                if (calibratedNow) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continuar a dibujar")
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ToolButton("Selección", tool == DrawTool.SELECT, Modifier.weight(1f)) {
                    tool = DrawTool.SELECT
                    activeStroke = null
                }
                ToolButton("Dibujar", tool == DrawTool.DRAW, Modifier.weight(1f)) {
                    tool = DrawTool.DRAW
                    activeStroke = null
                }
                ToolButton("Borrar", tool == DrawTool.ERASE, Modifier.weight(1f)) {
                    tool = DrawTool.ERASE
                    activeStroke = null
                }
                ToolButton("Limpiar", false, Modifier.weight(1f)) {
                    strokes.clear()
                    activeStroke = null
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sensibilidad", color = StarkText, fontSize = 12.sp)
                Slider(
                    value = tolerance,
                    onValueChange = { tolerance = it },
                    valueRange = 14f..42f,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                OutlinedButton(
                    onClick = { preview?.let { controller.focusCenter(it) } }
                ) {
                    Text("Reenfocar")
                }
            }

            Text(
                "Gen 1 funciona solo con tapas azules sobre fondos blancos o uniformes.",
                color = StarkMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }
}

@Composable
private fun CameraPanel(
    modifier: Modifier,
    tracking: TrackingUi,
    strokes: List<StrokeData>,
    calibrationMode: Boolean,
    tool: DrawTool,
    focusPoint: Offset?,
    onPreview: (PreviewView) -> Unit,
    onFocus: (Offset) -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(24.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(StarkCyan, StarkViolet)),
                RoundedCornerShape(24.dp),
            )
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { onFocus(it) }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    onPreview(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(Modifier.fillMaxSize()) {
            val rect = contentRect(size, tracking.aspect)

            for (stroke in strokes) {
                if (stroke.points.size < 2) continue
                val path = Path()
                val p0 = mapPoint(stroke.points.first(), rect)
                path.moveTo(p0.x, p0.y)
                for (i in 1 until stroke.points.size) {
                    val p = mapPoint(stroke.points[i], rect)
                    path.lineTo(p.x, p.y)
                }
                drawPath(
                    path,
                    color = if (tool == DrawTool.ERASE) Color(0xFF70B7FF) else StarkBlue,
                    style = Stroke(width = 8f, cap = StrokeCap.Round),
                )
            }

            if (tracking.detected) {
                val p = Offset(
                    rect.left + tracking.x * rect.width,
                    rect.top + tracking.y * rect.height,
                )
                drawCircle(StarkCyan.copy(alpha = .20f), 26f, p)
                drawCircle(StarkBlue, 11f, p)
                drawCircle(StarkCyan, 18f, p, style = Stroke(3f))
            }

            if (calibrationMode) {
                val c = center
                drawCircle(Color.White, 72f, c, style = Stroke(4f))
                drawLine(Color.White, c - Offset(100f, 0f), c - Offset(46f, 0f), 4f)
                drawLine(Color.White, c + Offset(46f, 0f), c + Offset(100f, 0f), 4f)
                drawLine(Color.White, c - Offset(0f, 100f), c - Offset(0f, 46f), 4f)
                drawLine(Color.White, c + Offset(0f, 46f), c + Offset(0f, 100f), 4f)
            }

            focusPoint?.let { p ->
                drawCircle(StarkCyan, 32f, p, style = Stroke(3f))
            }
        }
    }
}

private fun contentRect(size: Size, aspect: Float): Rect {
    val safeAspect = if (aspect > 0f) aspect else 4f / 3f
    val viewAspect = size.width / max(1f, size.height)
    return if (viewAspect > safeAspect) {
        val w = size.height * safeAspect
        val left = (size.width - w) / 2f
        Rect(left, 0f, left + w, size.height)
    } else {
        val h = size.width / safeAspect
        val top = (size.height - h) / 2f
        Rect(0f, top, size.width, top + h)
    }
}

private fun mapPoint(p: PointF, rect: Rect) = Offset(
    rect.left + p.x * rect.width,
    rect.top + p.y * rect.height,
)

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
}

@Composable
private fun Tutorial(onBack: () -> Unit, onBegin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StarkMark(110.dp)
        Text("STARK GEN 1", color = StarkText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("AIRBOARD DRAW", color = StarkCyan)
        Spacer(Modifier.height(20.dp))
        Text("Tutorial rápido", color = StarkText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        StepCard("1", "Usa una tapa azul", "Debe verse claramente y estar cerca del punto de escritura.")
        StepCard("2", "Apunta a un fondo blanco", "También sirve un fondo uniforme sin otros objetos azules.")
        StepCard("3", "Calibra y dibuja", "Centra la tapa, calibra y cambia al modo Dibujar.")
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Comenzar", onBegin)
        TextButton(onClick = onBack) { Text("Volver") }
    }
}

@Composable
private fun StepCard(number: String, title: String, body: String) {
    GlassCard(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(StarkCyan, StarkViolet))),
                contentAlignment = Alignment.Center,
            ) {
                Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = StarkText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(body, color = StarkMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Header(compact: Boolean = false) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarkMark(if (compact) 58.dp else 70.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "STARK GEN 1",
                    color = StarkText,
                    fontSize = if (compact) 19.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("AIRBOARD DRAW", color = StarkCyan, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StarkMark(markSize: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(markSize)) {
        val w = size.width
        val h = size.height
        drawOval(
            StarkCyan,
            topLeft = Offset(w * .08f, h * .48f),
            size = Size(w * .70f, h * .27f),
            style = Stroke(w * .045f),
        )
        drawOval(
            StarkViolet,
            topLeft = Offset(w * .24f, h * .51f),
            size = Size(w * .67f, h * .21f),
            style = Stroke(w * .04f),
        )
        drawCircle(StarkCyan, w * .055f, Offset(w * .53f, h * .27f))
        drawLine(
            StarkCyan,
            Offset(w * .49f, h * .35f),
            Offset(w * .73f, h * .20f),
            w * .05f,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.White, w * .025f, Offset(w * .77f, h * .17f))
    }
}

@Composable
private fun MenuItem(
    symbol: String,
    title: String,
    subtitle: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val background = if (primary) {
        Brush.horizontalGradient(
            listOf(StarkCyan.copy(alpha = .30f), StarkBlue.copy(alpha = .42f), StarkViolet.copy(alpha = .42f))
        )
    } else {
        Brush.linearGradient(listOf(StarkSurfaceHigh, StarkSurface))
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(background)
            .border(
                1.dp,
                if (primary) StarkCyan else StarkViolet.copy(alpha = .55f),
                RoundedCornerShape(26.dp),
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(17.dp))
                .background(StarkCyan.copy(alpha = .12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = StarkText, fontSize = 29.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = StarkText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(subtitle, color = StarkMuted, fontSize = 13.sp)
        }
        Text("›", color = StarkText, fontSize = 30.sp)
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(StarkSurface.copy(alpha = .94f))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(StarkCyan.copy(alpha = .60f), StarkViolet.copy(alpha = .60f))
                ),
                RoundedCornerShape(22.dp),
            )
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = StarkBlue),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun ToolButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(17.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(
                        listOf(StarkCyan.copy(alpha = .75f), StarkViolet.copy(alpha = .75f))
                    )
                } else {
                    Brush.linearGradient(listOf(StarkSurface, StarkSurfaceHigh))
                }
            )
            .border(
                1.dp,
                if (selected) StarkCyan else StarkViolet.copy(alpha = .50f),
                RoundedCornerShape(17.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = StarkText, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatusPill(detected: Boolean, confidence: Float) {
    val color = if (detected) StarkSuccess else Color(0xFFFFB45A)
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = .12f))
            .border(1.dp, color.copy(alpha = .70f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            if (detected) "Seguimiento " + (confidence * 100).toInt() + "%" else "Buscando tapa",
            color = StarkText,
            fontSize = 11.sp,
        )
    }
}
