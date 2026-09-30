package com.laura.royaltasks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.laura.royaltasks.ui.TaskViewModel
import com.laura.royaltasks.ui.TodoScreen
import com.laura.royaltasks.ui.theme.RoyalTasksTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoyalTasksTheme {
                TodoScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDay()
    }
}
