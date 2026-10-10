package com.mrredhood.astracode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

private enum class PrimaryDestination(
    val label: String,
    val icon: String,
    val title: String,
    val description: String
) {
    Home("Home", "home", "Your coding space", "Build, browse, and get help from cloud AI."),
    Projects("Projects", "files", "Project workspace", "Select a folder and manage files safely."),
    AI("AI", "chat", "AI coding workspace", "Chat with a configured cloud model and review approved actions."),
    Terminal("Terminal", "terminal", "Safe terminal", "Run built-in commands scoped to your selected workspace."),
    More("More", "more", "Tools and settings", "Find configuration, build options, execution status and help.")
}

private data class MoreEntry(
    val title: String,
    val category: String,
    val summary: String
)

private val moreEntries = listOf(
    MoreEntry("Create Project", "Workspace & editing", "Choose a starter framework and configure a project."),
    MoreEntry("Workspace", "Workspace & editing", "Project access, files, folders and editor preferences."),
    MoreEntry("Editor", "Workspace & editing", "Editing, tabs, autosave, search and preview options."),
    MoreEntry("AI & Models", "AI & automation", "Cloud providers, model selection and connection status."),
    MoreEntry("AI Execution", "AI & automation", "Task lifecycle and honest execution capability status."),
    MoreEntry("Automation & Operations", "AI & automation", "Task activity, scheduled work and operation history."),
    MoreEntry("Git & GitHub", "Git & delivery", "Repository connections, history and remote integrations."),
    MoreEntry("Build & Run", "Git & delivery", "Build targets and configuration; execution is not yet wired."),
    MoreEntry("Build & CI", "Git & delivery", "Hosted workflow status, logs and verification."),
    MoreEntry("Artifacts & Reports", "Git & delivery", "Build outputs, test reports and diagnostics."),
    MoreEntry("Quality & Diagnostics", "Quality & safety", "Application health, troubleshooting and reports."),
    MoreEntry("Security & Notifications", "Quality & safety", "Permissions, approvals, privacy and notification controls."),
    MoreEntry("Settings", "Settings & support", "App appearance, accent colors, and coding workflow settings."),
    MoreEntry("Help & Guide", "Settings & support", "Learn how AstraCode works and troubleshoot common problems."),
    MoreEntry("About AstraCode", "Settings & support", "Application identity, version and project information.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AstraCodeApp() }
    }
}

@Composable
private fun AstraCodeApp() {
    val context = LocalContext.current.applicationContext
    val uiPreferences = remember(context) { AstraUiPreferences(context) }
    var selectedName by rememberSaveable { mutableStateOf(PrimaryDestination.Home.name) }
    var selectedMoreEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var moreSearchQuery by rememberSaveable { mutableStateOf("") }
    var themeMode by rememberSaveable { mutableStateOf(uiPreferences.themeMode()) }
    var accentName by rememberSaveable { mutableStateOf(uiPreferences.accent()) }
    var codeFontSize by rememberSaveable { mutableIntStateOf(uiPreferences.codeFontSize()) }
    val selected = PrimaryDestination.values().firstOrNull { it.name == selectedName } ?: PrimaryDestination.Home

    BackHandler(enabled = selected == PrimaryDestination.More && selectedMoreEntry != null) {
        selectedMoreEntry = null
    }

    fun selectDestination(destination: PrimaryDestination) {
        selectedName = destination.name
        if (destination != PrimaryDestination.More) selectedMoreEntry = null
    }

    AstraCodeTheme(mode = themeMode, accent = accentName) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compact = AstraCodeLayoutPolicy.usesBottomNavigation(maxWidth.value)
                if (compact) {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                PrimaryDestination.values().forEach { destination ->
                                    NavigationBarItem(
                                        selected = selected == destination,
                                        onClick = { selectDestination(destination) },
                                        icon = {
                                            AstraIcon(
                                                destination.icon,
                                                size = 26.dp,
                                                description = null
                                            )
                                        },
                                        label = { Text(destination.label) }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        DestinationScreen(
                            destination = selected,
                            selectedMoreEntry = selectedMoreEntry,
                            searchQuery = moreSearchQuery,
                            onSearchQueryChange = { moreSearchQuery = it },
                            onOpenMoreEntry = { selectedMoreEntry = it },
                            onBackToMore = { selectedMoreEntry = null },
                            onSelectDestination = ::selectDestination,
                            compact = true,
                            themeMode = themeMode,
                            onThemeModeChange = { themeMode = it; uiPreferences.saveThemeMode(it) },
                            accentName = accentName,
                            onAccentChange = { accentName = it; uiPreferences.saveAccent(it) },
                            codeFontSize = codeFontSize,
                            onCodeFontSizeChange = { codeFontSize = it; uiPreferences.saveCodeFontSize(it) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail {
                            PrimaryDestination.values().forEach { destination ->
                                NavigationRailItem(
                                    selected = selected == destination,
                                    onClick = { selectDestination(destination) },
                                    icon = {
                                        AstraIcon(
                                            destination.icon,
                                            size = 26.dp,
                                            description = null
                                        )
                                    },
                                    label = { Text(destination.label) }
                                )
                            }
                        }
                        Scaffold(modifier = Modifier.weight(1f)) { innerPadding ->
                            DestinationScreen(
                                destination = selected,
                                selectedMoreEntry = selectedMoreEntry,
                                searchQuery = moreSearchQuery,
                                onSearchQueryChange = { moreSearchQuery = it },
                                onOpenMoreEntry = { selectedMoreEntry = it },
                                onBackToMore = { selectedMoreEntry = null },
                                onSelectDestination = ::selectDestination,
                                compact = false,
                                themeMode = themeMode,
                                onThemeModeChange = { themeMode = it; uiPreferences.saveThemeMode(it) },
                                accentName = accentName,
                                onAccentChange = { accentName = it; uiPreferences.saveAccent(it) },
                            codeFontSize = codeFontSize,
                            onCodeFontSizeChange = { codeFontSize = it; uiPreferences.saveCodeFontSize(it) },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationScreen(
    destination: PrimaryDestination,
    selectedMoreEntry: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenMoreEntry: (String) -> Unit,
    onBackToMore: () -> Unit,
    onSelectDestination: (PrimaryDestination) -> Unit,
    compact: Boolean,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    accentName: String,
    onAccentChange: (String) -> Unit,
    codeFontSize: Int,
    onCodeFontSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (destination == PrimaryDestination.AI) Modifier
                else Modifier.verticalScroll(rememberScrollState())
            )
            .padding(horizontal = if (compact) 20.dp else 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.astracode_logo),
                contentDescription = "AstraCode logo",
                modifier = Modifier.size(28.dp)
            )
            Text(
                "ASTRACODE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            if (destination == PrimaryDestination.More && selectedMoreEntry != null) selectedMoreEntry
            else destination.title,
            style = MaterialTheme.typography.headlineMedium
        )
        if (
            destination == PrimaryDestination.More &&
            selectedMoreEntry in listOf("Create Project", "Build & Run", "AI Execution", "Settings")
        ) {
            OutlinedButton(onClick = onBackToMore) { Text("‹ Back to More") }
        }
        if (destination == PrimaryDestination.More) {
            when (selectedMoreEntry) {
                null -> MoreHubScreen(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onOpenEntry = { entry ->
                        if (entry == "Workspace" || entry == "Editor") {
                            onSelectDestination(PrimaryDestination.Projects)
                        } else {
                            onOpenMoreEntry(entry)
                        }
                    }
                )
                "Help & Guide" -> HelpGuideScreen(onBack = onBackToMore)
                "AI & Models" -> AiProviderSettingsScreen(onBack = onBackToMore)
                "Create Project" -> CreateProjectScreen(onOpenProjects = { onSelectDestination(PrimaryDestination.Projects) })
                "Build & Run" -> BuildRunScreen()
                "AI Execution" -> AiExecutionScreen(onOpenChat = { onSelectDestination(PrimaryDestination.AI) })
                "Settings" -> AstraSettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    accent = accentName,
                    onAccentChange = onAccentChange,
                    codeFontSize = codeFontSize,
                    onCodeFontSizeChange = onCodeFontSizeChange,
                    onOpenAiSettings = { onOpenMoreEntry("AI & Models") },
                    onOpenProjects = { onSelectDestination(PrimaryDestination.Projects) },
                    onOpenTerminal = { onSelectDestination(PrimaryDestination.Terminal) },
                    onOpenBuild = { onOpenMoreEntry("Build & Run") }
                )
                else -> MoreEntryDetailScreen(
                    entry = moreEntries.firstOrNull { it.title == selectedMoreEntry },
                    onBack = onBackToMore
                )
            }
        } else if (destination == PrimaryDestination.Home) {
            HomeDashboardScreen(
                onOpenProjects = { onSelectDestination(PrimaryDestination.Projects) },
                onOpenChat = { onSelectDestination(PrimaryDestination.AI) },
                onOpenTerminal = { onSelectDestination(PrimaryDestination.Terminal) },
                onOpenBuild = { onSelectDestination(PrimaryDestination.More); onOpenMoreEntry("Build & Run") },
                onCreateProject = { onSelectDestination(PrimaryDestination.More); onOpenMoreEntry("Create Project") },
                onOpenExecution = { onSelectDestination(PrimaryDestination.More); onOpenMoreEntry("AI Execution") }
            )
        } else if (destination == PrimaryDestination.Projects) {
            WorkspaceScreen(codeFontSize = codeFontSize)
        } else if (destination == PrimaryDestination.AI) {
            AiChatScreen(
                onOpenAiSettings = {
                    onSelectDestination(PrimaryDestination.More)
                    onOpenMoreEntry("AI & Models")
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else if (destination == PrimaryDestination.Terminal) {
            TerminalScreen()
        } else {
            DestinationSummary(destination)
        }
        Text(
            "Code smarter. Ship from your phone.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DestinationSummary(destination: PrimaryDestination) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AstraIcon(destination.icon, size = 48.dp, description = "${destination.label} icon")
            Text(destination.description, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Foundation build · Features are being implemented in phases.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MoreHubScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenEntry: (String) -> Unit
) {
    Text(
        "Find tools, configuration and help from one place.",
        style = MaterialTheme.typography.bodyLarge
    )
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search tools and settings") },
        singleLine = true
    )

    val query = searchQuery.trim()
    val filtered = moreEntries.filter { entry ->
        query.isEmpty() ||
            entry.title.contains(query, ignoreCase = true) ||
            entry.category.contains(query, ignoreCase = true) ||
            entry.summary.contains(query, ignoreCase = true)
    }
    if (filtered.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("No matching tools or settings", fontWeight = FontWeight.SemiBold)
                Text("Try a different search term.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        filtered.groupBy { it.category }.forEach { (category, entries) ->
            Text(category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            entries.forEach { entry ->
                Card(
                    onClick = { onOpenEntry(entry.title) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                entry.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreEntryDetailScreen(entry: MoreEntry?, onBack: () -> Unit) {
    OutlinedButton(onClick = onBack) { Text("Back to More") }
    Text(entry?.title ?: "Unavailable section", style = MaterialTheme.typography.titleLarge)
    Text(
        entry?.summary ?: "This section is not available in the current foundation build.",
        style = MaterialTheme.typography.bodyLarge
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Being implemented in phases", fontWeight = FontWeight.SemiBold)
            Text(
                "This entry is a navigation placeholder, not a completed feature. Its controls will appear when the corresponding implementation and verification are ready.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun HelpGuideScreen(onBack: () -> Unit) {
    OutlinedButton(onClick = onBack) { Text("Back to More") }
    Text("AstraCode Help & Guide", style = MaterialTheme.typography.titleLarge)
    GuideSection(
        title = "Getting started",
        body = "AstraCode is designed for coding and project workflows from an Android device. Home provides shortcuts into Projects, cloud AI Chat, the safe Terminal, Build & Run, project setup and AI Execution status. Projects opens the selected Android Storage Access Framework workspace; the editor retains its existing tabs, autosave, recovery, snapshots, find/replace and safe live-preview behavior. Not every visual screen means its underlying runner is available: project scaffolding, unrestricted shell execution, local/hosted build launching from the UI, and autonomous multi-step AI execution are not enabled yet."
    )
    GuideSection(
        title = "Accessibility and adaptive navigation",
        body = "Primary navigation items keep visible text labels for assistive technology; their custom icon drawings are decorative to avoid reading the same name twice. Narrow windows use a bottom navigation bar, while windows 600 dp and wider use a navigation rail. Primary selection, More search text and the selected More section are saveable and are restored when Android recreates the activity with saved instance state."
    )
    GuideSection(
        title = "Dashboard, terminal and execution status",
        body = "Use the Home dashboard to open Projects, AI Chat, Terminal, Build & Run, Create Project or AI Execution. Settings supports Dark, Light and System themes plus six accent colors; both appearance choices persist locally across app restarts, and system bar contrast follows the selected theme. The terminal runs only the built-in help, pwd, ls and clear commands, scoped to the selected SAF workspace; arbitrary shell, Flutter, Git, Python and build commands are rejected and do not launch a process. Build & Run is a configuration/status UI only; clicking Start build explicitly reports that no build started. Create Project validates a project name but does not create files until a real generator is implemented. AI Execution displays the planned lifecycle without simulating a running task. Today, supported one-action AI workspace tools run from Chat and require explicit approval and direct verification evidence."
    )
    GuideSection(
        title = "Workspace and files",
        body = "Choose a project folder through Android's system picker. Browse, filter, refresh, create, rename, move or delete items within that selected tree. Open up to eight text/code files as tabs and switch tabs without losing each draft or selection. Create up to ten local snapshots per file, compare a snapshot against the current draft with a bounded diff view, or restore a snapshot into the draft. Restoring does not write to the workspace; the editable draft auto-saves after a short pause, or use Save now to request an immediate save. Dirty drafts up to 2 MiB receive app-private recovery copies. Supported text/code files up to 2 MiB can be edited and saved, though performance depends on the device. **Fold / inspect code** supports up to 1,500,000 lines and 2 MiB, using virtualized rows and a bounded fold-region index. Above those folding limits, AstraCode shows metrics without the line listing or fold regions. Files above 2 MiB, unsupported types and binary files stay read-only. HTML/HTM, CSS and JavaScript files also offer **Show live preview**. The preview stays below the source editor, refreshes after typing pauses and does not save workspace changes. CSS/JavaScript use a sample page; HTML renders standalone. Network requests, remote resources, file access, form submissions and navigation are blocked; linked sibling files are not loaded."
    )
    GuideSection(
        title = "Editor find and replace",
        body = "In a supported text/code file, open Find / replace. Search is case-insensitive. Find next selects each match and wraps to the start; Replace match replaces the selection or next match; Replace all applies to non-overlapping matches in the current draft. Find/replace changes auto-save to storage after a short pause; use Save now to request an immediate save."
    )
    GuideSection(
        title = "AI providers and security",
        body = "Open More → AI & Models to select a cloud provider, use Discover models or enter a model ID manually, and optionally set a custom HTTPS base URL. Saved API keys are encrypted by Android Keystore, masked in the UI and excluded from backup. Save & test sends a short prompt to the selected model and may incur provider charges; it does not send workspace files. The AI destination uses the saved provider/model. Chat history is stored in a bounded local database and survives leaving Chat or restarting AstraCode. Use New chat and History to create, reopen or delete conversations. The chat database is excluded from Android backup. Chat supports up to 10 explicitly selected files per message, up to 25 MiB each and 100 MiB total per provider request. The app-private on-device attachment storage quota is 1 GiB. Images, audio, video, text/code and documents can be selected; the configured provider/model must support the file's modality. File bytes are copied to private app storage and sent when you send the message. Attachment files are excluded from Android backup, while metadata is saved in local history. Workspace files are never attached automatically. Chat may request workspace_list (list names), workspace_read (read a small text/code file), or workspace_create_file (create each new text/code file with its own 15 MiB UTF-8 cap) and workspace_move (move an existing file or folder after explicit approval). Review every action's approval card before running it. AstraCode saves the approval decision, path and reason before execution (including the proposed content hash for file creation); if that audit record cannot be saved, the operation is not run. Creation is restricted to the selected SAF workspace and an existing folder, refuses paths that already exist, verifies saved bytes by reading them back and records SHA-256 evidence. Tool output is untrusted workspace data. Overwrite, delete, shell and build actions remain unavailable. AI moves require explicit approval, refuse existing destination-name conflicts and are verified after execution. Never attach secrets. Never paste API keys into chat or bug reports."
    )
    GuideSection(
        title = "Build verification",
        body = "A successful message is not proof that a build or test passed. Check the actual workflow result, logs and reports before treating a release as ready."
    )
    GuideSection(
        title = "Troubleshooting",
        body = "For a failed build, inspect the first meaningful error in the logs. For missing workspace files, reselect the folder and grant access again. Include sanitized reproduction steps when reporting a bug."
    )
}

@Composable
private fun GuideSection(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
