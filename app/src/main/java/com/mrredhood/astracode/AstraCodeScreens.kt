package com.mrredhood.astracode

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AstraNavy = Color(0xFF050B18)
private val AstraPanel = Color(0xFF0A1628)
private val AstraPanelRaised = Color(0xFF101E35)
private val AstraCyan = Color(0xFF42D9FF)
private val AstraBlue = Color(0xFF398BFF)
private val AstraPurple = Color(0xFF9A67FF)
private val AstraPink = Color(0xFFF36DDB)
private val AstraGold = Color(0xFFFFBF5A)
private val AstraGreen = Color(0xFF35E0B2)
private val AstraMuted = Color(0xFFA8B9D4)

private fun accentColor(name: String): Color = when (name) {
    "Cyan" -> AstraCyan
    "Purple" -> AstraPurple
    "Pink" -> AstraPink
    "Gold" -> AstraGold
    "Green" -> AstraGreen
    else -> AstraBlue
}

/** Deeper accent shades retain readable contrast on the Light theme. */
private fun lightAccentColor(name: String): Color = when (name) {
    "Cyan" -> Color(0xFF087FA8)
    "Purple" -> Color(0xFF7040CE)
    "Pink" -> Color(0xFFB52D91)
    "Gold" -> Color(0xFF9A5A00)
    "Green" -> Color(0xFF007A64)
    else -> Color(0xFF245DDB)
}

/** Typography uses the system sans/mono fallbacks so no missing font binaries are referenced. */
@Composable
internal fun AstraCodeTheme(
    mode: String,
    accent: String,
    content: @Composable () -> Unit
) {
    val hostActivity = LocalContext.current as? Activity
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (mode) {
        "Light" -> false
        "System" -> systemDark
        else -> true
    }
    val primary = accentColor(accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = primary,
            secondary = AstraPurple,
            tertiary = AstraPink,
            background = AstraNavy,
            surface = Color(0xFF081324),
            surfaceVariant = AstraPanelRaised,
            onPrimary = Color.White,
            onBackground = Color(0xFFEAF3FF),
            onSurface = Color(0xFFEAF3FF),
            onSurfaceVariant = AstraMuted,
            outline = Color(0xFF263B5D),
            error = Color(0xFFFF647C)
        )
    } else {
        lightColorScheme(
            primary = lightAccentColor(accent),
            secondary = Color(0xFF7442CF),
            tertiary = Color(0xFFB52D91),
            background = Color(0xFFF4F7FF),
            surface = Color.White,
            surfaceVariant = Color(0xFFE8EEF9),
            onBackground = Color(0xFF12203B),
            onSurface = Color(0xFF12203B),
            onSurfaceVariant = Color(0xFF52627F),
            outline = Color(0xFFB9C7DE)
        )
    }
    SideEffect {
        hostActivity?.window?.let { window ->
            val barColor = if (dark) Color(0xFF050B18) else Color(0xFFF4F7FF)
            window.statusBarColor = barColor.toArgb()
            window.navigationBarColor = barColor.toArgb()
            val lightBarFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val currentFlags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (dark) {
                currentFlags and lightBarFlags.inv()
            } else {
                currentFlags or lightBarFlags
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AstraCodeTypography.Scale, content = content)
}

@Composable
internal fun AstraPageHero(
    title: String,
    description: String,
    icon: String = "more",
    mascot: Boolean = false,
    height: Dp = 154.dp,
    trailing: (@Composable () -> Unit)? = null
) {
    val dark = MaterialTheme.colorScheme.background == AstraNavy
    val heroTitleColor = if (dark) Color(0xFFF2F6FF) else Color(0xFF12203B)
    val heroBodyColor = if (dark) Color(0xFFD0DDF8) else Color(0xFF52627F)
    AstraPanel(modifier = Modifier.fillMaxWidth(), gradient = true) {
        Image(
            painter = painterResource(id = R.drawable.astracode_splash_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(22.dp))
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(height).background(
                Brush.horizontalGradient(listOf(Color(0xF0050B18), Color(0xC7071023), Color(0x65221547))),
                RoundedCornerShape(22.dp)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = heroTitleColor, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = heroBodyColor)
                if (trailing != null) {
                    Spacer(Modifier.height(2.dp))
                    trailing()
                }
            }
            if (mascot) {
                AstraRobotIllustration()
            } else {
                Box(
                    modifier = Modifier.size(54.dp).background(
                        Brush.linearGradient(listOf(AstraBlue.copy(alpha = .42f), AstraPurple.copy(alpha = .48f))),
                        CircleShape
                    ).border(1.dp, AstraCyan.copy(alpha = .7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { AstraIcon(icon, size = 30.dp, description = null) }
            }
        }
    }
}

@Composable
internal fun AstraPanel(
    modifier: Modifier = Modifier,
    gradient: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = MaterialTheme.colorScheme.background == AstraNavy
    val fill = if (gradient && dark) {
        Brush.linearGradient(listOf(Color(0xFF101F3C), Color(0xFF10152D), Color(0xFF17142F)))
    } else if (gradient) {
        Brush.linearGradient(listOf(Color(0xFFE7F0FF), Color(0xFFF0EAFE), Color(0xFFF9EFFF)))
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
    }
    Box(
        modifier = modifier
            .background(fill, RoundedCornerShape(22.dp))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(AstraCyan.copy(alpha = .65f), AstraBlue.copy(alpha = .45f), AstraPurple.copy(alpha = .65f)))
                ),
                RoundedCornerShape(22.dp)
            )
    ) { content() }
}

@Composable
internal fun AstraRobotIllustration(modifier: Modifier = Modifier.size(width = 98.dp, height = 116.dp)) {
    val motion = rememberInfiniteTransition(label = "astracode-robot-motion")
    val bob by motion.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "robot float"
    )
    val tilt by motion.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "robot tilt"
    )
    val eyeOpen = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_250)
            eyeOpen.animateTo(0.12f, tween(85))
            delay(100)
            eyeOpen.animateTo(1f, tween(115))
            delay(70)
            eyeOpen.animateTo(0.18f, tween(65))
            eyeOpen.animateTo(1f, tween(135))
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val fitScale = minOf(maxWidth.value / 98f, maxHeight.value / 116f).coerceIn(0.08f, 1.5f)
        Box(
            modifier = Modifier.requiredSize(width = 98.dp, height = 116.dp).graphicsLayer {
                scaleX = fitScale
                scaleY = fitScale
                translationY = bob * fitScale
                rotationZ = tilt
            },
            contentAlignment = Alignment.Center
        ) {
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = (-1).dp)
                .width(57.dp).height(5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0x002ADFFF), Color(0xAA4B7CFF), Color(0x00D34BFF))
                    ),
                    CircleShape
                )
        )
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-8).dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(width = 13.dp, height = 17.dp).background(
                Brush.verticalGradient(listOf(Color(0xFFB6DFFF), Color(0xFF4B74C9))),
                RoundedCornerShape(6.dp)
            ))
            Box(Modifier.size(width = 38.dp, height = 28.dp).background(
                Brush.verticalGradient(listOf(Color(0xFF244D9B), Color(0xFF101C43))),
                RoundedCornerShape(10.dp)
            ).border(1.dp, Color(0xFF4EBEFF), RoundedCornerShape(10.dp)))
            Box(Modifier.size(width = 13.dp, height = 17.dp).background(
                Brush.verticalGradient(listOf(Color(0xFFE0B9FF), Color(0xFF7045CE))),
                RoundedCornerShape(6.dp)
            ))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = (-29).dp).size(width = 38.dp, height = 26.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF25447C), Color(0xFF101932))), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF6687D9), RoundedCornerShape(10.dp))
        )
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = 3.dp).width(4.dp).height(17.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF4BE7FF), Color(0xFF8760FF))), CircleShape)
        )
        Box(
            Modifier.align(Alignment.TopCenter).size(9.dp)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFF42E6FF), Color(0xFF655DFF))), CircleShape)
        )
        Row(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                Modifier.size(width = 11.dp, height = 24.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF7CB9FF), Color(0xFF233A78))), RoundedCornerShape(7.dp))
                    .border(1.dp, Color(0xFF587EDD), RoundedCornerShape(7.dp))
            )
            Box(
                Modifier.size(width = 76.dp, height = 58.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFDAF1FF), Color(0xFF658FD9), Color(0xFF253C7A), Color(0xFFA37AFF))),
                        RoundedCornerShape(22.dp)
                    )
                    .border(1.2.dp, Color(0xFF7DDFFF), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(width = 64.dp, height = 43.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF030918), Color(0xFF07152F), Color(0xFF020714))),
                            RoundedCornerShape(18.dp)
                        )
                        .border(1.dp, Color(0xFF365AA0), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(Color(0xFF53F5FF), Color(0xFF18C5FF)).forEach { eye ->
                            Box(
                                Modifier.width(8.dp).height(16.dp).graphicsLayer { scaleY = eyeOpen.value }
                                    .background(Brush.verticalGradient(listOf(Color.White, eye, Color(0xFF247CFF))), CircleShape)
                            )
                        }
                    }
                }
            }
            Box(
                Modifier.size(width = 11.dp, height = 24.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF9DBBFF), Color(0xFF4B49A8))), RoundedCornerShape(7.dp))
                    .border(1.dp, Color(0xFF7877EB), RoundedCornerShape(7.dp))
            )
        }
        }
    }
}

@Composable
internal fun AstraPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Color(0xFF7650FF), Color(0xFF3F70FF), Color(0xFF08B9FF)))
                else Brush.horizontalGradient(listOf(Color(0xFF27334D), Color(0xFF1A2941))),
                shape
            )
            .border(1.dp, Color(0xFF8B9CFF).copy(alpha = if (enabled) .55f else .18f), shape),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color(0xFF8794AD)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp, pressedElevation = 0.dp, focusedElevation = 0.dp, hoveredElevation = 0.dp
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) { Text(label, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
internal fun AstraSectionTitle(
    title: String,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (trailing != null && onTrailing != null) {
            TextButton(onClick = onTrailing) { Text(trailing, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun AstraFeatureCard(
    title: String,
    summary: String,
    icon: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = .11f)),
        border = BorderStroke(1.dp, tint.copy(alpha = .55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(tint.copy(alpha = .16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { AstraIcon(icon, size = 27.dp, description = null) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier.size(30.dp).background(Color(0x332A9FFF), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("→", color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp) }
        }
    }
}

@Composable
private fun AstraQuickAction(
    title: String,
    icon: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = .45f)),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = .08f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 3.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AstraIcon(icon, size = 24.dp, description = null)
            Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun HomeDashboardScreen(
    onOpenProjects: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBuild: () -> Unit,
    onCreateProject: () -> Unit,
    onOpenExecution: () -> Unit,
    onOpenGit: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { WorkspaceRepository(context) }
    val workspace = remember(context) { repository.savedTreeUri() }
    var recentProjects by remember { mutableStateOf<List<RecentWorkspace>>(emptyList()) }

    LaunchedEffect(repository) {
        recentProjects = withContext(Dispatchers.IO) {
            runCatching { repository.recentWorkspaces() }.getOrDefault(emptyList())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        AstraPageHero(
            title = "Good Morning,\nLet's Build Something Amazing!",
            description = "Turn your ideas into real projects with the power of AI.",
            icon = "code",
            mascot = true,
            height = 178.dp,
            trailing = {
                AstraPrimaryButton("＋  New Project", onClick = onCreateProject, modifier = Modifier.fillMaxWidth())
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AstraFeatureCard("AI Chat", "Get help & build with AI", "chat", AstraPurple, Modifier.weight(1f), onOpenChat)
            AstraFeatureCard("Terminal", "Run safe commands", "terminal", AstraCyan, Modifier.weight(1f), onOpenTerminal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AstraFeatureCard("Projects", "Manage files & workspace", "files", AstraGreen, Modifier.weight(1f), onOpenProjects)
            AstraFeatureCard("Build & Run", "Build, test and run your app", "build", AstraGold, Modifier.weight(1f), onOpenBuild)
        }

        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AstraSectionTitle(
                    "Recent Projects",
                    trailing = "View all ›",
                    onTrailing = onOpenProjects
                )
                if (recentProjects.isEmpty()) {
                    Text(
                        if (workspace == null) "No recent projects yet. Open Projects to select a workspace."
                        else "Your selected workspace is saved. Reopen Projects to browse its files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                        AstraIcon("files", size = 19.dp, description = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (workspace == null) "Open Project" else "Open Current Workspace")
                    }
                } else {
                    recentProjects.forEach { project ->
                        Card(
                            onClick = {
                                repository.saveTreeUri(project.uri)
                                onOpenProjects()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .82f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(42.dp).background(
                                        Brush.linearGradient(listOf(AstraBlue.copy(alpha = .28f), AstraPurple.copy(alpha = .25f))),
                                        RoundedCornerShape(12.dp)
                                    ),
                                    contentAlignment = Alignment.Center
                                ) { AstraIcon("files", size = 25.dp, description = null) }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(project.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Saved workspace · tap to open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = when (project.kind) {
                                        "Flutter" -> AstraBlue.copy(alpha = .15f)
                                        "Android" -> AstraGreen.copy(alpha = .15f)
                                        "Python" -> AstraGold.copy(alpha = .17f)
                                        else -> AstraPurple.copy(alpha = .15f)
                                    }
                                ) {
                                    Text(project.kind, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 19.sp)
                            }
                        }
                    }
                }
            }
        }

        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AstraSectionTitle("Quick Actions")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    AstraQuickAction("Create File", "code", AstraBlue, Modifier.weight(1f), onOpenProjects)
                    AstraQuickAction("Create Folder", "files", AstraCyan, Modifier.weight(1f), onOpenProjects)
                    AstraQuickAction("Open Project", "files", AstraPurple, Modifier.weight(1f), onOpenProjects)
                    AstraQuickAction("Clone from Git", "git", AstraPink, Modifier.weight(1f), onOpenGit)
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AstraSectionTitle("AI Workflow", trailing = "Execution ›", onTrailing = onOpenExecution)
                Text(
                    "Chat with your configured cloud model. Review file changes and approve supported workspace actions before they run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun CreateProjectScreen(
    onOpenProjects: () -> Unit
) {
    var framework by rememberSaveable { mutableStateOf("Flutter") }
    var projectName by rememberSaveable { mutableStateOf("MyApp") }
    var description by rememberSaveable { mutableStateOf("") }
    var template by rememberSaveable { mutableStateOf("Basic app") }
    var includeExample by rememberSaveable { mutableStateOf(true) }
    var initializeGit by rememberSaveable { mutableStateOf(true) }
    var configureLints by rememberSaveable { mutableStateOf(true) }
    var useMaterialTheme by rememberSaveable { mutableStateOf(true) }
    var addRecommendedPackages by rememberSaveable { mutableStateOf(true) }
    var createReadme by rememberSaveable { mutableStateOf(true) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val frameworks = listOf("Flutter", "Android", "React Native", "Web (HTML)", "Next.js", "Node.js", "Python", "Java", "Empty")
    val templates = listOf("Basic app", "Bottom navigation", "State management", "API starter")
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        AstraPageHero("Create a new project", "Choose a starter direction and configure your project details.", "build", mascot = true)
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("1 · Choose a framework")
                frameworks.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { item ->
                            val selected = framework == item
                            val icon = when (item) {
                                "Flutter" -> "code"
                                "Android" -> "build"
                                "React Native" -> "more"
                                "Web (HTML)" -> "preview"
                                "Next.js" -> "code"
                                "Node.js" -> "terminal"
                                "Python" -> "code"
                                "Java" -> "build"
                                else -> "files"
                            }
                            Card(
                                onClick = { framework = item; message = null },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(15.dp),
                                border = BorderStroke(1.dp, if (selected) AstraPurple else MaterialTheme.colorScheme.outline.copy(alpha = .8f)),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) AstraPurple.copy(alpha = .18f) else MaterialTheme.colorScheme.surface.copy(alpha = .83f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().height(94.dp).padding(7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(34.dp).background(
                                            Brush.linearGradient(
                                                listOf(
                                                    when (item) {
                                                        "Flutter" -> AstraBlue.copy(alpha = .35f)
                                                        "Android" -> AstraGreen.copy(alpha = .35f)
                                                        "Python" -> AstraGold.copy(alpha = .35f)
                                                        "Java" -> Color(0xFFE34B64).copy(alpha = .25f)
                                                        else -> AstraPurple.copy(alpha = .28f)
                                                    },
                                                    AstraPanelRaised
                                                )
                                            ),
                                            RoundedCornerShape(11.dp)
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) { AstraIcon(icon, size = 24.dp, description = null) }
                                    Text(item, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    if (selected) Text("✓ Selected", style = MaterialTheme.typography.labelSmall, color = AstraCyan)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("2 · Project configuration")
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Project name") },
                    singleLine = true,
                    isError = projectName.isNotBlank() && !projectName.matches(Regex("[A-Za-z][A-Za-z0-9_-]{1,39}"))
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                    AstraIcon("files", size = 19.dp, description = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Choose project location")
                }
                Text("Project generation is not enabled yet. The destination selector opens the existing SAF workspace; AstraCode will never write outside that granted folder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("3 · Starter template")
                templates.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            val selected = template == item
                            Card(
                                onClick = { template = item },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) AstraPurple.copy(alpha = .18f) else MaterialTheme.colorScheme.surface.copy(alpha = .86f)
                                ),
                                border = BorderStroke(1.dp, if (selected) AstraPurple else MaterialTheme.colorScheme.outline),
                                shape = RoundedCornerShape(15.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().height(108.dp).padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AstraIcon(
                                            when (item) {
                                                "Basic app" -> "code"
                                                "Bottom navigation" -> "more"
                                                "State management" -> "storage"
                                                else -> "preview"
                                            },
                                            size = 23.dp
                                        )
                                        Spacer(Modifier.weight(1f))
                                        if (selected) Text("✓", color = AstraCyan, fontWeight = FontWeight.Bold)
                                    }
                                    Text(item, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when (item) {
                                            "Bottom navigation" -> "Multiple screens and navigation"
                                            "State management" -> "Organized application state"
                                            "API starter" -> "HTTP client starter structure"
                                            else -> "Clean starter project"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AstraSectionTitle("4 · Additional options")
                SettingToggle("Include example code", "Starter examples when a generator is available", includeExample) { includeExample = it }
                SettingToggle("Configure lints", "Add framework lint defaults when supported", configureLints) { configureLints = it }
                SettingToggle("Initialize Git", "Create a local repository when supported", initializeGit) { initializeGit = it }
                SettingToggle("Set up theme", "Use Material 3 where the framework supports it", useMaterialTheme) { useMaterialTheme = it }
                SettingToggle("Add recommended packages", "Include starter dependencies when a generator is available", addRecommendedPackages) { addRecommendedPackages = it }
                SettingToggle("Create README", "Prepare a project overview when generation is supported", createReadme) { createReadme = it }
            }
        }
        if (message != null) {
            AstraNotice(message!!, isError = true)
        }
        AstraPrimaryButton(
            "Create project",
            onClick = {
                message = if (!projectName.matches(Regex("[A-Za-z][A-Za-z0-9_-]{1,39}"))) {
                    "Use a project name starting with a letter and containing 2–40 letters, numbers, underscores, or hyphens."
                } else {
                    "Project scaffolding is not enabled yet. No files were created. Open Projects to select the destination and manage files safely."
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
            AstraIcon("files", size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text("Open Projects workspace")
        }
    }
}

@Composable
internal fun TerminalScreen() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { WorkspaceRepository(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf("") }
    var output by rememberSaveable {
        mutableStateOf("AstraCode Safe Terminal\nOnly built-in workspace commands are supported.\nType help to see available commands.\n")
    }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun executeCommand() {
        val command = prompt.trim()
        if (command.isEmpty() || busy) return
        prompt = ""
        if (command == "clear") {
            output = ""
            error = null
            return
        }
        if (command == "help") {
            output += "\n> help\nhelp   Show supported commands\npwd    Show selected workspace status\nls     List root workspace items\nclear  Clear terminal output\n\nArbitrary shell, Git, package-manager and build commands are disabled.\n"
            error = null
            return
        }
        if (command != "pwd" && command != "ls") {
            output += "\n> " + command + "\nBlocked: this terminal currently supports only help, pwd, ls and clear. No shell process was started.\n"
            error = "Command not executed"
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = repository.savedTreeUri()
                    if (uri == null) return@runCatching "No workspace selected. Open Projects and choose a folder first."
                    if (command == "pwd") return@runCatching "workspace://selected-root (scoped SAF folder)"
                    val rootId = repository.rootDocumentId(uri)
                    val children = repository.listChildren(uri, rootId)
                    if (children.isEmpty()) "(empty workspace)"
                    else children.joinToString("\n") { entry -> if (entry.isDirectory) entry.displayName + "/" else entry.displayName }
                }.getOrElse { "Workspace query failed: " + (it.message ?: "provider error") }
            }
            output += "\n> " + command + "\n" + result + "\n"
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AstraPageHero("Terminal", "Run workspace commands from your phone.", "terminal", mascot = true)
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            FilterChip(selected = true, onClick = { prompt = "" }, label = { Text("▣  bash") })
            FilterChip(selected = false, onClick = {
                prompt = "flutter run"
                error = "Flutter execution is not enabled. No shell process was started."
            }, label = { Text("▶  flutter run") })
            FilterChip(selected = false, onClick = {
                prompt = "git status"
                error = "Git commands are not enabled in this terminal. No process was started."
            }, label = { Text("⑂  git") })
            FilterChip(selected = false, onClick = {
                prompt = "python"
                error = "Python execution is not enabled. No process was started."
            }, label = { Text("Py  python") })
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AstraIcon("folder", size = 18.dp, description = null)
                    Text("Working directory", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text("SAF workspace", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (repository.savedTreeUri() == null) "No workspace selected" else "Selected project root",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(AstraGreen, CircleShape))
                    Text("Scoped terminal · built-ins only", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text(if (busy) "Working…" else "Ready", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    Modifier.fillMaxWidth()
                        .background(Color(0xFF030812), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF233A5A), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        output.takeLast(7500),
                        color = Color(0xFFD7E5FF),
                        fontFamily = AstraCodeTypography.CodeFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter a safe command") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AstraPrimaryButton("Run built-in", onClick = ::executeCommand, modifier = Modifier.weight(1f), enabled = !busy && prompt.isNotBlank())
                    OutlinedButton(onClick = { output = ""; error = null }, modifier = Modifier.weight(1f)) { Text("Clear output") }
                }
                AstraNotice(
                    "AstraCode does not launch an unrestricted shell. Flutter, Git, Python and build commands remain disabled until a scoped execution and approval design is implemented.",
                    isError = false
                )
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AstraSectionTitle("Quick Commands", trailing = "View all ›", onTrailing = { prompt = "help" })
                listOf(
                    Triple("pwd", "Show workspace", "files"),
                    Triple("ls", "List root items", "files"),
                    Triple("help", "Command guide", "code"),
                    Triple("clear", "Clear output", "more"),
                    Triple("flutter build apk", "Build debug APK · unavailable", "build"),
                    Triple("git status", "Check Git · unavailable", "git")
                ).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (command, label, icon) ->
                            Card(
                                onClick = {
                                    prompt = command
                                    if (command == "flutter build apk" || command == "git status") {
                                        error = "$command is not enabled in this build. It will be refused without launching a shell."
                                    } else error = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .8f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .88f))
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().height(78.dp).padding(7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    AstraIcon(icon, size = 22.dp, description = null)
                                    Text(command, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun BuildRunScreen() {
    var target by rememberSaveable { mutableStateOf("Debug APK") }
    var mode by rememberSaveable { mutableStateOf("Debug") }
    var platform by rememberSaveable { mutableStateOf("Android") }
    var installAfter by rememberSaveable { mutableStateOf(true) }
    var verboseLogs by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var outputTab by rememberSaveable { mutableStateOf("Logs") }
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        AstraPageHero("Build & Run", "Choose a target and review settings before building.", "build", mascot = true)
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("1 · Select target")
                listOf("Run app", "Debug APK", "Release APK", "Release AAB").chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            val selected = target == option
                            Card(
                                onClick = { target = option; notice = null },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surface.copy(alpha = .84f)
                                )
                            ) {
                                Column(Modifier.fillMaxWidth().height(104.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AstraIcon(if (option == "Run app") "preview" else if (option == "Release AAB") "artifact" else "build", size = 28.dp)
                                        Spacer(Modifier.weight(1f))
                                        if (selected) Text("✓", color = AstraCyan, fontWeight = FontWeight.Bold)
                                    }
                                    Text(option, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        when (option) {
                                            "Run app" -> "On device"
                                            "Debug APK" -> "Test package"
                                            "Release APK" -> "Installable release"
                                            else -> "Store bundle"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraSectionTitle("2 · Build configuration")
                Text("Build mode", style = MaterialTheme.typography.titleSmall)
                AstraChoiceRow(listOf("Debug", "Profile", "Release"), mode) { mode = it }
                Text("Target platform", style = MaterialTheme.typography.titleSmall)
                AstraChoiceRow(listOf("Android", "iOS", "All"), platform) { platform = it }
                SettingToggle("Build & install", "Installation follows a successful build", installAfter) { installAfter = it }
                SettingToggle("Verbose logs", "Show detailed toolchain output when available", verboseLogs) { verboseLogs = it }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraSectionTitle("3 · Start build")
                AstraNotice("Build execution is not wired into this build yet. No build will start from this screen.", isError = false)
                AstraPrimaryButton("Start build", onClick = {
                    notice = "No build was started. The safe UI and options are ready; an audited local/hosted build runner is still required."
                }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { notice = "Clean is unavailable until a scoped build runner is implemented." }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clean project")
                }
                if (notice != null) AstraNotice(notice!!, isError = true)
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AstraSectionTitle("Build Output")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Logs", "Problems", "Artifacts").forEach { tab ->
                        FilterChip(
                            selected = outputTab == tab,
                            onClick = { outputTab = tab },
                            label = { Text(tab) }
                        )
                    }
                }
                when (outputTab) {
                    "Logs" -> Text(
                        "No build has been run from AstraCode in this session. Build logs will appear after a real build runner is connected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = AstraCodeTypography.CodeFont
                    )
                    "Problems" -> Text(
                        "No compiler or test report is available because this screen has not run a build.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        "No APK or AAB artifacts were produced from this screen. Artifacts will only be listed after verified build output exists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiExecutionScreen(onOpenChat: () -> Unit) {
    val steps = listOf("Plan", "Analyze", "Edit Files", "Run & Test", "Verify", "Complete")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AstraPageHero(
            "AI Execution",
            "Observe an AI task from planning through verification.",
            "execution",
            mascot = true
        )
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(34.dp).background(AstraPurple.copy(alpha = .2f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        AstraIcon("execution", size = 24.dp, description = null)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("No active AI task", style = MaterialTheme.typography.titleMedium)
                        Text("Start from AI Chat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(50), color = AstraGold.copy(alpha = .13f)) {
                        Text("Idle", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = AstraGold)
                    }
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("Execution Progress")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    steps.forEachIndexed { index, label ->
                        Column(
                            modifier = Modifier.width(76.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier.size(31.dp).background(Color(0xFF08172A), CircleShape)
                                    .border(1.5.dp, if (index == 0) AstraPurple else MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text((index + 1).toString(), color = if (index == 0) AstraCyan else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
                            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                AstraPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        AstraIcon("code", size = 29.dp, description = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Current step", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Waiting for a task", style = MaterialTheme.typography.titleSmall)
                            Text("No files are being modified.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                AstraNotice("Autonomous multi-step execution is not enabled. This screen never fabricates progress; supported individual workspace actions are proposed and approved in AI Chat.", isError = false)
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                AstraSectionTitle("Generated Files", trailing = "0 files")
                Text("Files created by approved workspace actions will appear in chat history and in your selected workspace. No task files to show yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AstraSectionTitle("Execution Logs")
                Text(
                    "No execution has been recorded. Once a supported action runs, AstraCode reports its actual result and verification evidence.",
                    fontFamily = AstraCodeTypography.CodeFont,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Ⅱ  Pause") }
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("■  Stop") }
                    OutlinedButton(onClick = onOpenChat, modifier = Modifier.weight(1.3f)) { Text("Open AI Chat") }
                }
            }
        }
    }
}

@Composable
internal fun AstraSettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    accent: String,
    onAccentChange: (String) -> Unit,
    codeFontSize: Int,
    onCodeFontSizeChange: (Int) -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBuild: () -> Unit
) {
    val accents = listOf("Cyan", "Blue", "Purple", "Pink", "Gold", "Green")
    val context = LocalContext.current
    fun openExternal(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        AstraPageHero("Settings", "Tune AstraCode's appearance and coding workflow.", "settings")
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                AstraSectionTitle("Appearance")
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                AstraChoiceRow(listOf("Dark", "Light", "System"), themeMode, onThemeModeChange)
                Text("Accent color", style = MaterialTheme.typography.titleSmall)
                accents.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { name ->
                            val color = accentColor(name)
                            Card(
                                onClick = { onAccentChange(name) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (name == accent) color else MaterialTheme.colorScheme.outline),
                                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (name == accent) .24f else .08f))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(21.dp).background(color, CircleShape))
                                    Text(name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraSectionTitle("Editor preferences")
                Text("Code font size · $codeFontSize sp", style = MaterialTheme.typography.titleSmall)
                AstraChoiceRow(
                    options = listOf("11", "12", "13", "14", "16", "18"),
                    selected = codeFontSize.toString(),
                    onSelect = { it.toIntOrNull()?.let(onCodeFontSizeChange) }
                )
                Text(
                    "Used by the code editor and structural inspection. JetBrains Mono is the target family; system monospace is used until licensed font files are bundled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                    Text("Open editor and workspace")
                }
            }
        }
        SettingsSection(
            "Editor Preferences",
            "Font size, line numbers, tabs, autosave, find/replace and code inspection.",
            "code",
            onOpenProjects
        )
        SettingsSection("AI Settings", "Cloud provider, model, context and execution preferences.", "chat", onOpenAiSettings)
        SettingsSection("Terminal Settings", "Shell appearance, workspace and allow-listed commands.", "terminal", onOpenTerminal)
        SettingsSection("Build & Run Settings", "Build configuration and device options; build runner not connected.", "build", onOpenBuild)
        SettingsSection("Project Settings", "Default workspace, framework templates and package managers.", "files", onOpenProjects)
        SettingsSection("File & Storage", "Up to 10 attachments, 25 MiB each, 100 MiB per request and 1 GiB private storage.", "storage")
        SettingsSection("Security & Privacy", "Workspace access, approvals, credential encryption and backup exclusions.", "approval")
        SettingsSection("Advanced Settings", "Experimental preferences and developer options.", "more")
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                AstraSectionTitle("About AstraCode")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.astracode_logo),
                        contentDescription = "AstraCode logo",
                        modifier = Modifier.size(72.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Astra", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "Code",
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.horizontalGradient(listOf(Color(0xFF29BFFF), Color(0xFF625BFF), Color(0xFFEF42D8))),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                        Text("Version 0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Built for mobile developers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Apache License 2.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Check the public releases page for new builds, read the license, or open a bug report on GitHub.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { openExternal("https://github.com/MrRedhood/AstraCode/releases") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AstraIcon("preview", size = 18.dp, description = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check for Updates")
                    Spacer(Modifier.weight(1f))
                    Text("↗")
                }
                OutlinedButton(
                    onClick = { openExternal("https://github.com/MrRedhood/AstraCode/blob/main/LICENSE") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AstraIcon("code", size = 18.dp, description = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View License")
                    Spacer(Modifier.weight(1f))
                    Text("↗")
                }
                OutlinedButton(
                    onClick = { openExternal("https://github.com/MrRedhood/AstraCode/issues/new") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AstraIcon("approval", size = 18.dp, description = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Report a Bug")
                    Spacer(Modifier.weight(1f))
                    Text("↗")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, summary: String, icon: String, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AstraIcon(icon, size = 29.dp, description = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AstraChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) })
        }
    }
}

@Composable
private fun AstraNotice(message: String, isError: Boolean) {
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tint.copy(alpha = .10f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = .45f))
    ) {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
    }
}
