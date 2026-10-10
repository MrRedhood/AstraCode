package com.mrredhood.astracode

import android.app.Activity
import android.view.View

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
            primary = Color(0xFF285FCB),
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
    trailing: (@Composable () -> Unit)? = null
) {
    val dark = MaterialTheme.colorScheme.background == AstraNavy
    val heroTitleColor = if (dark) Color(0xFFEAF3FF) else Color(0xFF12203B)
    val heroBodyColor = if (dark) AstraMuted else Color(0xFF52627F)
    AstraPanel(
        modifier = Modifier.fillMaxWidth(),
        gradient = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = heroTitleColor)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = heroBodyColor)
                if (trailing != null) {
                    Spacer(Modifier.height(4.dp))
                    trailing()
                }
            }
            Box(
                modifier = Modifier.size(58.dp).background(
                    Brush.linearGradient(listOf(AstraBlue.copy(alpha = .24f), AstraPurple.copy(alpha = .26f), AstraPink.copy(alpha = .12f))),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                AstraIcon(icon, size = 35.dp, description = title)
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
internal fun AstraPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF514BEE),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF252E45),
            disabledContentColor = Color(0xFF8693AC)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
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
            ) { AstraIcon(icon, size = 27.dp, description = title) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    onOpenExecution: () -> Unit
) {
    val context = LocalContext.current
    val workspace = remember(context) { WorkspaceRepository(context.applicationContext).savedTreeUri() }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AstraPageHero(
            title = "Let's build something amazing",
            description = "Your ideas, your workspace, and cloud AI in one mobile coding environment.",
            icon = "code",
            trailing = {
                AstraPrimaryButton("＋  New project", onClick = onCreateProject)
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AstraFeatureCard("AI Chat", "Ask, explain, create", "chat", AstraPurple, Modifier.weight(1f), onOpenChat)
            AstraFeatureCard("Terminal tools", "Safe workspace commands", "terminal", AstraCyan, Modifier.weight(1f), onOpenTerminal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AstraFeatureCard("Project files", "Browse files & edit", "files", AstraGreen, Modifier.weight(1f), onOpenProjects)
            AstraFeatureCard("Build & Run", "Build status & targets", "build", AstraGold, Modifier.weight(1f), onOpenBuild)
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraSectionTitle("Workspace")
                if (workspace == null) {
                    Text("No project folder selected", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose a folder once to browse, edit, and create files within that granted workspace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Workspace access is saved on this device", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "AstraCode can work only inside the folder you selected through Android's document picker.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth()) {
                    AstraIcon("files", size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (workspace == null) "Choose project folder" else "Open project files")
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AstraSectionTitle("AI workflow", trailing = "View execution", onTrailing = onOpenExecution)
                Text(
                    "Chat with a configured cloud model, review each workspace-action approval, and verify the result.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AstraIcon("approval", size = 22.dp, description = "Approval")
                    Text("Approval-gated file creation and moves", style = MaterialTheme.typography.bodySmall)
                }
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
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val frameworks = listOf("Flutter", "Android", "Compose", "Web", "Node.js", "Python", "Java", "Empty")
    val templates = listOf("Basic app", "Bottom navigation", "API starter")
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        AstraPageHero("Create a new project", "Choose a starter direction and configure your project details.", "build")
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("1 · Choose a framework")
                frameworks.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { item ->
                            FilterChip(
                                selected = framework == item,
                                onClick = { framework = item; message = null },
                                label = { Text(item, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
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
                Text("Project location is selected from the workspace screen. AstraCode will not write outside the granted folder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("3 · Starter template")
                templates.forEach { item ->
                    Card(
                        onClick = { template = item },
                        colors = CardDefaults.cardColors(containerColor = if (template == item) AstraPurple.copy(alpha = .18f) else MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (template == item) AstraPurple else MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AstraIcon(if (item == "Basic app") "code" else if (item == "Bottom navigation") "more" else "preview", size = 25.dp)
                            Column(Modifier.weight(1f)) {
                                Text(item, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when (item) {
                                        "Bottom navigation" -> "Starter layout with multiple destinations"
                                        "API starter" -> "Structure for connecting a remote service"
                                        else -> "Small, minimal application scaffold"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (template == item) Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AstraSectionTitle("4 · Additional options")
                SettingToggle("Include example code", "Starter examples when a generator is available", includeExample) { includeExample = it }
                SettingToggle("Initialize Git", "Create a local repository when supported", initializeGit) { initializeGit = it }
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
        AstraPageHero("Terminal", "A scoped workspace console with safe built-in commands.", "terminal")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf("help", "pwd", "ls", "clear", "flutter run").forEach { command ->
                FilterChip(selected = false, onClick = { prompt = command }, label = { Text(command, fontFamily = AstraCodeTypography.CodeFont) })
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
                        fontFamily = FontFamily.Monospace,
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
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        AstraPageHero("Build & Run", "Choose a target and review settings before building.", "build")
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("1 · Select target")
                listOf("Run app", "Debug APK", "Release APK", "Release AAB").forEach { option ->
                    Card(
                        onClick = { target = option; notice = null },
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, if (target == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = if (target == option) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surface)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AstraIcon(if (option.contains("AAB")) "artifact" else if (option.contains("APK")) "build" else "preview", size = 27.dp)
                            Column(Modifier.weight(1f)) {
                                Text(option, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when (option) {
                                        "Run app" -> "Install and launch on a configured device"
                                        "Debug APK" -> "Development build for testing"
                                        "Release APK" -> "Optimized installable package"
                                        else -> "Android App Bundle for store delivery"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (target == option) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AstraSectionTitle("Build output")
                Text("No build has been run from AstraCode in this session.", style = MaterialTheme.typography.titleSmall)
                Text("Logs, test reports, and APK/AAB artifacts will appear here when a verified build integration is available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AiExecutionScreen(onOpenChat: () -> Unit) {
    val steps = listOf("Plan request", "Analyze workspace", "Approve action", "Execute", "Verify result", "Complete")
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        AstraPageHero("AI Execution", "Observe work and evidence. Multi-step autonomous execution is not enabled yet.", "execution")
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AstraSectionTitle("Execution lifecycle")
                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text((index + 1).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
                        Column(Modifier.weight(1f)) {
                            Text(step, style = MaterialTheme.typography.titleSmall)
                            Text("Not started", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What works today", style = MaterialTheme.typography.titleMedium)
                Text("In AI Chat, the configured cloud model can propose one supported workspace action at a time. File creation and moves require explicit approval, persist the audit decision before execution, and report verification evidence.", style = MaterialTheme.typography.bodyMedium)
                Text("This page does not simulate progress or claim that a task has run.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AstraPrimaryButton("Open AI Chat", onClick = onOpenChat, modifier = Modifier.fillMaxWidth())
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
    onOpenAiSettings: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenBuild: () -> Unit
) {
    val accents = listOf("Cyan", "Blue", "Purple", "Pink", "Gold", "Green")
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
        SettingsSection(
            "Editor preferences",
            "File tabs, autosave, find/replace, snapshots and code inspection.",
            "code",
            onOpenProjects
        )
        SettingsSection("AI providers", "Cloud models, encrypted API keys and connection tests.", "chat", onOpenAiSettings)
        SettingsSection("Terminal", "Scoped folder listing and built-in commands only.", "terminal", onOpenTerminal)
        SettingsSection("Build & Run", "Build targets and status; execution remains unavailable.", "build", onOpenBuild)
        SettingsSection("File & storage", "Attachments: up to 10 per message, 25 MiB each, 100 MiB per request; private storage quota 1 GiB.", "storage", onOpenProjects)
        SettingsSection("Security & privacy", "SAF workspace scope, approval audit, credential encryption and backup exclusions.", "approval", onOpenAiSettings)
        AstraPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About AstraCode", style = MaterialTheme.typography.titleMedium)
                Text("A mobile-first, cloud-AI coding environment.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Apache License 2.0 · Designed for Android", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("The supplied font pack defines Space Grotesk, Inter, and JetBrains Mono, but contains no font binaries. This build uses Android system sans-serif/monospace fallbacks until licensed TTF files are added.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, summary: String, icon: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AstraIcon(icon, size = 29.dp, description = title)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
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
