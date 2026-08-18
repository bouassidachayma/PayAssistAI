package com.payassistai.app.ui.screens

import com.payassistai.app.viewmodels.AuthViewModel
import com.payassistai.app.viewmodels.ChatViewModel
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payassistai.app.data.ChatSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val messages by chatViewModel.messages.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val sessions by chatViewModel.sessions.collectAsState()
    val currentSessionId by chatViewModel.currentSessionId.collectAsState()

    var userInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var expandedSessionId by remember { mutableStateOf<Int?>(null) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var newTitle by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    fun openRenameDialog(session: ChatSession) {
        sessionToRename = session
        newTitle = session.title
        showRenameDialog = true
        expandedSessionId = null
    }

    fun togglePin(sessionId: Int) {
        chatViewModel.togglePin(sessionId)
        expandedSessionId = null
    }

    fun deleteSession(sessionId: Int) {
        chatViewModel.deleteSession(sessionId)
        expandedSessionId = null
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AIAssistant",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    TextButton(
                        onClick = {
                            chatViewModel.createNewSession()
                            scope.launch { drawerState.close() }
                        }
                    ) {
                        Text("New chat")
                    }
                }
                HorizontalDivider()

                if (sessions.isEmpty()) {
                    Text(
                        "No previous conversations.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val pinnedSessions = sessions.filter { it.isPinned }
                        .sortedByDescending { it.updatedAt }
                    val unpinnedSessions = sessions.filter { !it.isPinned }
                        .sortedByDescending { it.updatedAt }

                    val now = System.currentTimeMillis()
                    val grouped = unpinnedSessions.groupBy { session ->
                        val diffDays = TimeUnit.MILLISECONDS.toDays(now - session.updatedAt).toInt()
                        when {
                            diffDays == 0 -> "Today"
                            diffDays <= 7 -> "Last 7 Days"
                            diffDays <= 30 -> "Last 30 Days"
                            else -> "Older"
                        }
                    }

                    val groupOrder = listOf("Today", "Last 7 Days", "Last 30 Days", "Older")

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (pinnedSessions.isNotEmpty()) {
                            item {
                                Text(
                                    "Pinned",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(pinnedSessions) { session ->
                                ChatHistoryItem(
                                    session = session,
                                    isCurrent = session.id == currentSessionId,
                                    isPinned = true,
                                    onItemClick = {
                                        chatViewModel.switchToSession(session.id)
                                        scope.launch { drawerState.close() }
                                    },
                                    onTogglePin = { togglePin(session.id) },
                                    onRename = { openRenameDialog(session) },
                                    onDelete = { deleteSession(session.id) }
                                )
                            }
                        }

                        groupOrder.forEach { header ->
                            val sessionsInGroup = grouped[header] ?: emptyList()
                            if (sessionsInGroup.isNotEmpty()) {
                                item {
                                    Text(
                                        header,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(sessionsInGroup) { session ->
                                    ChatHistoryItem(
                                        session = session,
                                        isCurrent = session.id == currentSessionId,
                                        isPinned = false,
                                        onItemClick = {
                                            chatViewModel.switchToSession(session.id)
                                            scope.launch { drawerState.close() }
                                        },
                                        onTogglePin = { togglePin(session.id) },
                                        onRename = { openRenameDialog(session) },
                                        onDelete = { deleteSession(session.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        gesturesEnabled = true
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Open menu",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                chatViewModel.exportChatAsPDF(context)
                            },
                            enabled = messages.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Export chat as PDF",
                                tint = if (messages.isNotEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(
                            onClick = {
                                chatViewModel.createNewSession()
                                scope.launch { drawerState.close() }
                            }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (messages.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "💬",
                            fontSize = 64.sp
                        )
                        Text(
                            "Start chatting with AIAssistant",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Ask me about payment issues, errors, or transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextField(
                                value = userInput,
                                onValueChange = { userInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ask something...") },
                                enabled = !isLoading
                            )
                            Button(
                                onClick = {
                                    if (userInput.isNotBlank()) {
                                        chatViewModel.sendMessage(userInput)
                                        userInput = ""
                                    }
                                },
                                enabled = userInput.isNotBlank() && !isLoading
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        val isUser = message.sender == "user"
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isUser) {
                                Text(message.text, modifier = Modifier.padding(12.dp))
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = message.text,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(message.text))
                                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask something...") },
                        enabled = !isLoading
                    )
                    Button(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                chatViewModel.sendMessage(userInput)
                                userInput = ""
                            }
                        },
                        enabled = userInput.isNotBlank() && !isLoading
                    ) { Text("Send") }
                }
            }
        }
    }

    if (showRenameDialog && sessionToRename != null) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                sessionToRename = null
                newTitle = ""
            },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("New title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            val session = sessionToRename
                            if (session != null) {
                                chatViewModel.updateSessionTitle(session.id, newTitle)
                            }
                            showRenameDialog = false
                            sessionToRename = null
                            newTitle = ""
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    sessionToRename = null
                    newTitle = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatHistoryItem(
    session: ChatSession,
    isCurrent: Boolean,
    isPinned: Boolean,
    onItemClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.title.ifEmpty { "New Chat" },
                    style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (isPinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            onRename()
                            expanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isPinned) "Unpin" else "Pin") },
                        onClick = {
                            onTogglePin()
                            expanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDelete()
                            expanded = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}