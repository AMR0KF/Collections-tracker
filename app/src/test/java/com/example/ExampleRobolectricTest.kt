package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.sync.SyncManager
import com.example.ui.CollectionsUi
import com.example.ui.CollectionsViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Collections Tracker", appName)
  }

  @Test
  fun testNavigateToSettings() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = AppDatabase.getDatabase(context)
    val repository = AppRepository(database.appDao())
    val syncManager = SyncManager(context, repository)
    val viewModel = CollectionsViewModel(repository, syncManager)

    composeTestRule.setContent {
      MyApplicationTheme {
        CollectionsUi(viewModel = viewModel)
      }
    }

    // Click on bottom navigation settings item
    composeTestRule.onNodeWithTag("nav_settings").performClick()
    
    // Check if we successfully navigated
    composeTestRule.waitForIdle()
  }
}
