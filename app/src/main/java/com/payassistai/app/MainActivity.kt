package com.payassistai.app

import com.payassistai.app.viewmodels.AuthViewModel
import com.payassistai.app.viewmodels.ChatViewModel
import com.payassistai.app.viewmodels.TransactionsViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.payassistai.app.ui.screens.*
import com.payassistai.app.ui.theme.PayAssistAITheme
import com.payassistai.app.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDark = ThemeManager.isDarkTheme(this)
        setContent {
            var darkTheme by remember { mutableStateOf(isDark) }
            PayAssistAITheme(darkTheme = darkTheme) {
                AppContent(
                    darkTheme = darkTheme,
                    onThemeToggle = {
                        darkTheme = !darkTheme
                        ThemeManager.setDarkTheme(applicationContext, darkTheme)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    darkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val chatViewModel: ChatViewModel = hiltViewModel()
    val transactionsViewModel: TransactionsViewModel = hiltViewModel()

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val currentMerchant by authViewModel.currentMerchant.collectAsState()
    var showAdminPanel by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    if (!isLoggedIn) {
        LoginScreen(
            authViewModel = authViewModel,
            darkTheme = darkTheme
        )
    } else {
        var selectedTab by remember { mutableStateOf(0) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PayAssistAI")
                            Spacer(Modifier.width(8.dp))
                            if (currentMerchant != null) {
                                Text(
                                    "(${currentMerchant!!.name})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onThemeToggle) {
                            Icon(
                                if (darkTheme) Icons.Default.WbSunny else Icons.Default.NightsStay,
                                contentDescription = "Toggle Theme"
                            )
                        }
                        IconButton(onClick = { showChangePasswordDialog = true }) {
                            Icon(Icons.Default.Lock, contentDescription = "Change Password")
                        }
                        if (authViewModel.isAdmin()) {
                            IconButton(onClick = { showAdminPanel = !showAdminPanel }) {
                                Icon(Icons.Default.People, contentDescription = "Admin")
                            }
                        }
                        IconButton(onClick = { authViewModel.logout() }) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (!showAdminPanel) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                            label = { Text("Chat") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.List, contentDescription = "Transactions") },
                            label = { Text("Transactions") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                            label = { Text("Dashboard") }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(Modifier.fillMaxSize().padding(paddingValues)) {
                if (showAdminPanel) {
                    AdminPanelScreen(
                        authViewModel = authViewModel,
                        onBack = { showAdminPanel = false }
                    )
                } else {
                    when (selectedTab) {
                        0 -> ChatScreen(
                            chatViewModel = chatViewModel,
                            authViewModel = authViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> TransactionsScreen(
                            transactionsViewModel = transactionsViewModel,
                            authViewModel = authViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                        2 -> DashboardScreen(
                            transactionsViewModel = transactionsViewModel,
                            authViewModel = authViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (showChangePasswordDialog) {
            ChangePasswordDialog(
                authViewModel = authViewModel,
                onDismiss = { showChangePasswordDialog = false }
            )
        }
    }
}