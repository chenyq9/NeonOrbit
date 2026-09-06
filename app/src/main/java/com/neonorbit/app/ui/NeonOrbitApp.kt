package com.neonorbit.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.neonorbit.app.R
import com.neonorbit.app.audio.FeedbackPlayer
import com.neonorbit.app.data.PreferencesStore
import com.neonorbit.app.data.UserSettings
import com.neonorbit.app.game.EnergyShard
import com.neonorbit.app.game.GameEngine
import com.neonorbit.app.game.GameEvent
import com.neonorbit.app.game.GameSnapshot
import com.neonorbit.app.game.GameStatus
import com.neonorbit.app.game.Hazard
import com.neonorbit.app.game.OrbitLane
import com.neonorbit.app.game.ParticleKind
import com.neonorbit.app.game.PLAYER_ANGLE
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun NeonOrbitApp() {
    val context = LocalContext.current
    val gameAreaDescription = stringResource(R.string.game_area_description)
    val store = remember { PreferencesStore(context) }
    val engine = remember { GameEngine(store.bestScore()) }
    val feedback = remember { FeedbackPlayer(context) }
    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var settings by remember { mutableStateOf(store.settings()) }
    var showSettings by remember { mutableStateOf(false) }
    var tutorialSeen by remember { mutableStateOf(false) }

    DisposableEffect(feedback) {
        onDispose { feedback.close() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentStatus by rememberUpdatedState(snapshot.status)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && currentStatus == GameStatus.RUNNING) {
                engine.pause()
                snapshot = engine.snapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snapshot.status) {
        if (snapshot.status != GameStatus.RUNNING) return@LaunchedEffect
        var previous = 0L
        while (isActive && engine.snapshot().status == GameStatus.RUNNING) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    val dt = (now - previous) / 1_000_000_000f
                    val events = engine.tick(dt)
                    val updated = engine.snapshot()
                    events.forEach { feedback.onEvent(it, settings.sound, settings.haptics) }
                    if (GameEvent.NewBest in events) store.saveBestScore(updated.bestScore)
                    snapshot = updated
                }
                previous = now
            }
        }
    }

    fun handleEvents(events: List<GameEvent>) {
        events.forEach { feedback.onEvent(it, settings.sound, settings.haptics) }
        snapshot = engine.snapshot()
    }

    BackHandler(enabled = snapshot.status != GameStatus.READY && !showSettings) {
        when (snapshot.status) {
            GameStatus.RUNNING -> engine.pause()
            GameStatus.PAUSED, GameStatus.GAME_OVER -> engine.goHome()
            GameStatus.READY -> Unit
        }
        snapshot = engine.snapshot()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .clickable(
                enabled = snapshot.status == GameStatus.RUNNING,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { handleEvents(engine.switchLane()) },
            )
            .semantics { contentDescription = gameAreaDescription }
    ) {
        GameWorld(snapshot = snapshot, reducedFx = settings.reducedFx)

        if (snapshot.status == GameStatus.RUNNING || snapshot.status == GameStatus.PAUSED) {
            GameHud(
                snapshot = snapshot,
                onPause = {
                    if (snapshot.status == GameStatus.RUNNING) engine.pause() else engine.resume()
                    snapshot = engine.snapshot()
                },
            )
        }

        when (snapshot.status) {
            GameStatus.READY -> StartOverlay(
                bestScore = snapshot.bestScore,
                onPlay = {
                    tutorialSeen = false
                    handleEvents(engine.start())
                },
                onSettings = { showSettings = true },
            )
            GameStatus.PAUSED -> PauseOverlay(
                onResume = {
                    engine.resume()
                    snapshot = engine.snapshot()
                },
                onHome = {
                    engine.goHome()
                    snapshot = engine.snapshot()
                },
            )
            GameStatus.GAME_OVER -> GameOverOverlay(
                snapshot = snapshot,
                onRetry = { handleEvents(engine.restart()) },
                onHome = {
                    engine.goHome()
                    snapshot = engine.snapshot()
                },
            )
            GameStatus.RUNNING -> if (!tutorialSeen && snapshot.elapsedSeconds < 3.2f) {
                TutorialHint(
                    onDismiss = { tutorialSeen = true },
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onChange = {
                settings = it
                store.saveSettings(it)
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun GameWorld(snapshot: GameSnapshot, reducedFx: Boolean) {
    val laneTarget = if (snapshot.lane == OrbitLane.INNER) 0.64f else 0.86f
    val playerLane by animateFloatAsState(
        targetValue = laneTarget,
        animationSpec = tween(durationMillis = 95),
        label = "playerLane",
    )

    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.54f
        val orbitBase = min(size.width, size.height * 0.78f) * 0.43f
        val shakePx = if (reducedFx) 0f else snapshot.shake * 10f
        val sx = sin(snapshot.elapsedSeconds * 64f) * shakePx
        val sy = cos(snapshot.elapsedSeconds * 51f) * shakePx * 0.7f

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF15112A),
                    SpaceBlack,
                    Color(0xFF02030A),
                ),
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                radius = max(size.width, size.height) * 0.72f,
            )
        )

        translate(sx, sy) {
            drawStarfield(snapshot.elapsedSeconds, reducedFx)
            drawCore(cx, cy, orbitBase, snapshot)
            drawOrbits(cx, cy, orbitBase, snapshot)
            snapshot.shards.forEach { drawShard(it, cx, cy, orbitBase, snapshot.elapsedSeconds) }
            snapshot.hazards.forEach { drawHazard(it, cx, cy, orbitBase, snapshot) }
            if (!reducedFx) drawParticles(snapshot, cx, cy, orbitBase)
            drawPlayer(cx, cy, orbitBase, playerLane, snapshot)
        }
    }
}

private fun DrawScope.drawStarfield(time: Float, reducedFx: Boolean) {
    val count = if (reducedFx) 34 else 62
    repeat(count) { i ->
        val seed = i * 97 + 41
        val x = ((seed * 37) % 997) / 997f * size.width
        val yBase = ((seed * 71) % 991) / 991f * size.height
        val y = (yBase + (time * (4f + i % 3) / size.height)) % 1f * size.height
        val twinkle = if (reducedFx) 0.34f else 0.18f + 0.26f * (0.5f + 0.5f * sin(time * 2f + i))
        drawCircle(
            color = SoftWhite.copy(alpha = twinkle),
            radius = if (i % 9 == 0) 1.6f else 0.9f,
            center = androidx.compose.ui.geometry.Offset(x, y),
        )
    }
}

private fun DrawScope.drawCore(cx: Float, cy: Float, orbitBase: Float, snapshot: GameSnapshot) {
    val center = androidx.compose.ui.geometry.Offset(cx, cy)
    val pulse = 1f + sin(snapshot.elapsedSeconds * if (snapshot.surgeActive) 8f else 2.2f) * 0.035f
    val base = orbitBase * 0.22f * pulse
    val accent = if (snapshot.surgeActive) NeonViolet else NeonCyan

    drawCircle(accent.copy(alpha = 0.055f), base * 2.2f, center)
    drawCircle(accent.copy(alpha = 0.09f), base * 1.6f, center)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(SoftWhite, accent, Color(0xFF171C33), SpaceBlack),
            center = center,
            radius = base,
        ),
        radius = base,
        center = center,
    )
    drawCircle(SoftWhite.copy(alpha = 0.78f), base * 0.11f, center)

    val energySweep = if (snapshot.surgeActive) 360f else snapshot.energy * 3.6f
    drawArc(
        color = accent.copy(alpha = 0.28f),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(cx - base * 1.36f, cy - base * 1.36f),
        size = androidx.compose.ui.geometry.Size(base * 2.72f, base * 2.72f),
        style = Stroke(width = max(2f, orbitBase * 0.011f), cap = StrokeCap.Round),
    )
    drawArc(
        color = accent,
        startAngle = -90f,
        sweepAngle = energySweep,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(cx - base * 1.36f, cy - base * 1.36f),
        size = androidx.compose.ui.geometry.Size(base * 2.72f, base * 2.72f),
        style = Stroke(width = max(2f, orbitBase * 0.018f), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawOrbits(cx: Float, cy: Float, orbitBase: Float, snapshot: GameSnapshot) {
    val accent = if (snapshot.surgeActive) NeonViolet else NeonCyan
    listOf(0.64f, 0.86f).forEachIndexed { index, lane ->
        val radius = orbitBase * lane
        drawCircle(
            color = SoftWhite.copy(alpha = 0.05f),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = Stroke(width = orbitBase * 0.035f),
        )
        drawCircle(
            color = (if (index == 0) NeonViolet else NeonCyan).copy(alpha = 0.20f),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = Stroke(width = max(1.6f, orbitBase * 0.008f)),
        )
    }

    val activeRadius = orbitBase * if (snapshot.lane == OrbitLane.INNER) 0.64f else 0.86f
    drawArc(
        color = accent.copy(alpha = 0.7f),
        startAngle = -107f,
        sweepAngle = 34f,
        useCenter = false,
        topLeft = androidx.compose.ui.geometry.Offset(cx - activeRadius, cy - activeRadius),
        size = androidx.compose.ui.geometry.Size(activeRadius * 2f, activeRadius * 2f),
        style = Stroke(width = orbitBase * 0.018f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawPlayer(
    cx: Float,
    cy: Float,
    orbitBase: Float,
    laneRadius: Float,
    snapshot: GameSnapshot,
) {
    val r = orbitBase * laneRadius
    val x = cx + cos(PLAYER_ANGLE) * r
    val y = cy + sin(PLAYER_ANGLE) * r
    val accent = if (snapshot.surgeActive) NeonViolet else NeonCyan
    val ship = orbitBase * 0.052f

    drawCircle(accent.copy(alpha = 0.10f), ship * 3.4f, androidx.compose.ui.geometry.Offset(x, y))
    drawCircle(accent.copy(alpha = 0.20f), ship * 2.2f, androidx.compose.ui.geometry.Offset(x, y))

    rotate(degrees = 0f, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
        val path = Path().apply {
            moveTo(x, y - ship * 1.15f)
            lineTo(x + ship * 0.92f, y + ship * 0.9f)
            lineTo(x, y + ship * 0.52f)
            lineTo(x - ship * 0.92f, y + ship * 0.9f)
            close()
        }
        drawPath(path, accent)
        val core = Path().apply {
            moveTo(x, y - ship * 0.62f)
            lineTo(x + ship * 0.42f, y + ship * 0.46f)
            lineTo(x - ship * 0.42f, y + ship * 0.46f)
            close()
        }
        drawPath(core, SoftWhite)
    }

    if (snapshot.surgeActive) {
        drawCircle(
            color = SoftWhite.copy(alpha = 0.45f + 0.2f * sin(snapshot.elapsedSeconds * 12f)),
            radius = ship * 1.55f,
            center = androidx.compose.ui.geometry.Offset(x, y),
            style = Stroke(width = ship * 0.16f),
        )
    }
}

private fun DrawScope.drawHazard(hazard: Hazard, cx: Float, cy: Float, orbitBase: Float, snapshot: GameSnapshot) {
    val lane = if (hazard.lane == OrbitLane.INNER) 0.64f else 0.86f
    val radius = orbitBase * lane
    val angle = PLAYER_ANGLE + hazard.distance
    val x = cx + cos(angle) * radius
    val y = cy + sin(angle) * radius
    val s = orbitBase * (0.052f + hazard.variant * 0.004f)
    val rotation = angle * 180f / PI.toFloat() + 90f + snapshot.elapsedSeconds * (18f + hazard.variant * 8f)

    drawCircle(DangerRed.copy(alpha = 0.08f), s * 3f, androidx.compose.ui.geometry.Offset(x, y))
    drawCircle(DangerRed.copy(alpha = 0.17f), s * 1.9f, androidx.compose.ui.geometry.Offset(x, y))
    rotate(rotation, androidx.compose.ui.geometry.Offset(x, y)) {
        val path = Path().apply {
            moveTo(x, y - s * 1.15f)
            lineTo(x + s * 0.78f, y)
            lineTo(x, y + s * 1.15f)
            lineTo(x - s * 0.78f, y)
            close()
        }
        drawPath(path, if (snapshot.surgeActive) DangerRed.copy(alpha = 0.62f) else DangerRed)
        drawPath(path, SoftWhite.copy(alpha = 0.38f), style = Stroke(width = max(1f, s * 0.09f)))
    }
}

private fun DrawScope.drawShard(shard: EnergyShard, cx: Float, cy: Float, orbitBase: Float, time: Float) {
    val lane = if (shard.lane == OrbitLane.INNER) 0.64f else 0.86f
    val radius = orbitBase * lane
    val angle = PLAYER_ANGLE + shard.distance
    val x = cx + cos(angle) * radius
    val y = cy + sin(angle) * radius
    val s = orbitBase * 0.03f * (1f + 0.08f * sin(time * 8f + shard.id))
    drawCircle(NeonCyan.copy(alpha = 0.09f), s * 3.4f, androidx.compose.ui.geometry.Offset(x, y))
    drawCircle(NeonCyan.copy(alpha = 0.2f), s * 2.1f, androidx.compose.ui.geometry.Offset(x, y))
    rotate(45f + time * 70f, androidx.compose.ui.geometry.Offset(x, y)) {
        drawRect(
            brush = Brush.linearGradient(listOf(SoftWhite, NeonCyan, NeonViolet)),
            topLeft = androidx.compose.ui.geometry.Offset(x - s, y - s),
            size = androidx.compose.ui.geometry.Size(s * 2f, s * 2f),
        )
    }
}

private fun DrawScope.drawParticles(snapshot: GameSnapshot, cx: Float, cy: Float, orbitBase: Float) {
    snapshot.particles.forEach { p ->
        val life = (1f - p.age / p.ttl).coerceIn(0f, 1f)
        val r = orbitBase * p.laneRadius
        val x = cx + cos(p.angle) * r
        val y = cy + sin(p.angle) * r
        val color = when (p.kind) {
            ParticleKind.CYAN -> NeonCyan
            ParticleKind.VIOLET -> NeonViolet
            ParticleKind.WHITE -> SoftWhite
            ParticleKind.DANGER -> DangerRed
        }
        drawCircle(color.copy(alpha = life * 0.8f), p.size * life, androidx.compose.ui.geometry.Offset(x, y))
    }
}

@Composable
private fun GameHud(snapshot: GameSnapshot, onPause: () -> Unit) {
    val pauseDescription = stringResource(R.string.pause)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Metric(label = stringResource(R.string.score), value = snapshot.score.toString())
            IconButton(
                modifier = Modifier
                    .size(48.dp)
                    .background(SoftWhite.copy(alpha = 0.06f), CircleShape)
                    .semantics { contentDescription = pauseDescription },
                onClick = onPause,
            ) {
                Text(if (snapshot.status == GameStatus.PAUSED) "▶" else "Ⅱ", color = SoftWhite, fontSize = 20.sp)
            }
            Metric(label = stringResource(R.string.best), value = snapshot.bestScore.toString(), alignEnd = true)
        }
        AnimatedVisibility(snapshot.surgeActive) {
            Text(
                text = stringResource(R.string.overload),
                color = NeonViolet,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(value, color = SoftWhite, fontSize = 23.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun StartOverlay(bestScore: Int, onPlay: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NeonMark()
        Spacer(Modifier.height(18.dp))
        Text(
            text = "NEON\nORBIT",
            color = SoftWhite,
            fontWeight = FontWeight.Black,
            fontSize = 48.sp,
            lineHeight = 42.sp,
            textAlign = TextAlign.Center,
            letterSpacing = (-1).sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tagline),
            color = NeonCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.4.sp,
        )
        if (bestScore > 0) {
            Spacer(Modifier.height(18.dp))
            Text("${stringResource(R.string.best)}  $bestScore", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBlack),
        ) {
            Text(stringResource(R.string.play), fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(stringResource(R.string.settings_button), color = SoftWhite)
        }
    }
}

@Composable
private fun NeonMark() {
    Canvas(Modifier.size(84.dp)) {
        val c = center
        drawCircle(NeonCyan.copy(alpha = 0.12f), size.minDimension * 0.5f, c)
        drawCircle(NeonCyan, size.minDimension * 0.31f, c, style = Stroke(size.minDimension * 0.055f))
        drawCircle(NeonViolet, size.minDimension * 0.055f, c)
        drawCircle(SoftWhite, size.minDimension * 0.022f, c)
    }
}

@Composable
private fun TutorialHint(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 66.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = PanelBlack,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clickable(onClick = onDismiss),
        ) {
            Text(
                stringResource(R.string.tap_hint),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                color = SoftWhite,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onHome: () -> Unit) {
    OverlayCard(
        title = stringResource(R.string.paused),
        primaryLabel = stringResource(R.string.resume),
        onPrimary = onResume,
        secondaryLabel = stringResource(R.string.home),
        onSecondary = onHome,
    )
}

@Composable
private fun GameOverOverlay(snapshot: GameSnapshot, onRetry: () -> Unit, onHome: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = PanelBlack,
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.padding(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.game_over), color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(14.dp))
                Text(snapshot.score.toString(), color = SoftWhite, fontSize = 54.sp, fontWeight = FontWeight.Black)
                Text("${stringResource(R.string.best)} ${snapshot.bestScore}", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBlack),
                    shape = RoundedCornerShape(17.dp),
                ) { Text(stringResource(R.string.retry), fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(17.dp)) {
                    Text(stringResource(R.string.home), color = SoftWhite)
                }
            }
        }
    }
}

@Composable
private fun OverlayCard(
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = PanelBlack, shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(32.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = SoftWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = SpaceBlack),
                    shape = RoundedCornerShape(17.dp),
                ) { Text(primaryLabel, fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(17.dp)) {
                    Text(secondaryLabel, color = SoftWhite)
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(settings: UserSettings, onChange: (UserSettings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingRow(stringResource(R.string.sound), settings.sound) { onChange(settings.copy(sound = it)) }
                SettingRow(stringResource(R.string.haptics), settings.haptics) { onChange(settings.copy(haptics = it)) }
                SettingRow(stringResource(R.string.reduced_fx), settings.reducedFx) { onChange(settings.copy(reducedFx = it)) }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        containerColor = Color(0xFF111622),
    )
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = SoftWhite)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
