package com.laurasheehan.nextread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.laurasheehan.nextread.ui.AppViewModel
import com.laurasheehan.nextread.ui.NextReadApp
import com.laurasheehan.nextread.ui.NextReadTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NextReadTheme { NextReadApp(vm) } }
    }
}
