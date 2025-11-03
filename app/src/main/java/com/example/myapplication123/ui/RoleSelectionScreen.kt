package com.example.myapplication123.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectionScreen(
    onRoleSelected: (String) -> Unit
) {
    var showDevMode by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Select Your Role",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { onRoleSelected("teacher") },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = "Teacher (Draw & Guide)",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { onRoleSelected("student") },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = "Student (View Only)",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Divider()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(
            onClick = { showDevMode = !showDevMode },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (showDevMode) "Hide" else "Show",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = " Network Mode (WiFi)",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        if (showDevMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Network Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect via WiFi network. Both devices must be on the same WiFi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRoleSelected("dev_teacher") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Network: Teacher")
                        }
                        OutlinedButton(
                            onClick = { onRoleSelected("dev_student") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Network: Student")
                        }
                    }
                }
            }
        }
    }
}

