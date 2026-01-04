package com.docs.scanner.presentation.screens.terms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.domain.model.Term

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    onNavigateBack: () -> Unit,
    viewModel: TermsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    val upcoming = (uiState as? TermsUiState.Success)?.upcomingTerms ?: emptyList()
    val completed = (uiState as? TermsUiState.Success)?.completedTerms ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Deadlines") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // Minimal create path: add UI later (dialog/date picker).
                viewModel.createTerm(
                    title = "New term",
                    description = null,
                    dueDate = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
                    reminderMinutesBefore = 60
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (uiState) {
                is TermsUiState.Error -> Text((uiState as TermsUiState.Error).message, color = MaterialTheme.colorScheme.error)
                TermsUiState.Loading -> Text("Loading...")
                is TermsUiState.Success -> {
                    TabRow(selectedTabIndex = tab) {
                        Tab(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            text = { Text("Upcoming (${upcoming.size})") }
                        )
                        Tab(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            text = { Text("Completed (${completed.size})") }
                        )
                    }

                    if (tab == 0) {
                        TermsList(
                            terms = upcoming,
                            onComplete = { viewModel.completeTerm(it.id.value) },
                            onDelete = viewModel::deleteTerm
                        )
                    } else {
                        TermsList(
                            terms = completed,
                            onComplete = null,
                            onDelete = viewModel::deleteTerm
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsList(
    terms: List<Term>,
    onComplete: ((Term) -> Unit)?,
    onDelete: (Term) -> Unit
) {
    if (terms.isEmpty()) {
        Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(terms, key = { it.id.value }) { term ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(term.title, style = MaterialTheme.typography.titleMedium)
                        term.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (onComplete != null) {
                        IconButton(onClick = { onComplete(term) }) {
                            Icon(Icons.Default.Check, contentDescription = "Complete")
                        }
                    } else {
                        Spacer(modifier = Modifier.padding(0.dp))
                    }
                    IconButton(onClick = { onDelete(term) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

