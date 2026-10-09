package com.mrredhood.astracode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Destination(val label: String, val icon: String, val title: String, val description: String)

private val destinations = listOf(
    Destination("Chat", "chat", "AI coding workspace", "Plan work, inspect actions and verify changes with cloud AI."),
    Destination("Code", "code", "Your workspace", "Choose a project to browse and edit files. Workspace access is coming next."),
    Destination("Git", "git", "Version control", "Review changes, history and remote operations in one focused place."),
    Destination("Build", "build", "Build and verify", "Run builds, inspect test results and manage artifacts."),
    Destination("More", "more", "Tools and settings", "Project activity, diagnostics, integrations, security, settings and Help & Guide.")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AstraCodeApp() }
    }
}

@Composable
private fun AstraCodeApp() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val destination = destinations[selected]
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(bottomBar = {
                NavigationBar {
                    destinations.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { AstraIcon(item.icon, size = 26.dp, description = "${item.label} icon") },
                            label = { Text(item.label) }
                        )
                    }
                }
            }) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text("ASTRACODE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(destination.title, style = MaterialTheme.typography.headlineMedium)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AstraIcon(destination.icon, size = 48.dp, description = "${destination.label} icon")
                            Text(destination.description, style = MaterialTheme.typography.bodyLarge)
                            Text("Foundation build · Features are being implemented in phases.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (selected == 0) {
                        Button(onClick = { selected = 1 }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open workspace")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Code smarter. Ship from your phone.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
