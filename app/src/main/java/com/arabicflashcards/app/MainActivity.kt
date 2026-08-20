package com.arabicflashcards.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arabicflashcards.app.ui.FlashcardScreen
import com.arabicflashcards.app.ui.theme.ArabicFlashcardsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArabicFlashcardsApp()
        }
    }
}

@Composable
fun ArabicFlashcardsApp() {
    ArabicFlashcardsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            FlashcardScreen()
        }
    }
}
