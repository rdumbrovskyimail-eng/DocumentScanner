package com.docs.scanner.presentation.screens.terms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TermsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TermsViewModel = hiltViewModel()
) {
    val upcoming by viewModel.upcomingTerms.collectAsState()
    val completed by viewModel.completedTerms.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Deadlines") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Upcoming") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Completed") })
            }

            when (selectedTab) {
                0 -> TermsList(upcoming, viewModel::completeTerm, viewModel::deleteTerm)
                1 -> TermsList(completed, null, viewModel::deleteTerm)
            }
        }
    }

    if (showDialog) {
        CreateTermDialog(
            onDismiss = { showDialog = false },
            onCreate = { title, date, minutes ->
                viewModel.createTerm(title, date, minutes)
                showDialog = false
            }
        )
    }
}

// Остальные composable (TermsList, TermCard, CreateTermDialog) — без изменений, работают