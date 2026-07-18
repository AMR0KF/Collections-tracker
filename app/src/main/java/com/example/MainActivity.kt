package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.sync.SyncManager
import com.example.ui.CollectionsUi
import com.example.ui.CollectionsViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Database, Repository, and SyncManager
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = AppRepository(database.appDao())
    val syncManager = SyncManager(applicationContext, repository)
    
    // Initialize ViewModel using Factory
    val viewModel: CollectionsViewModel by viewModels {
      CollectionsViewModel.Factory(repository, syncManager)
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          CollectionsUi(viewModel = viewModel)
        }
      }
    }
  }
}
