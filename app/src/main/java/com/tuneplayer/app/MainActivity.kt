package com.tuneplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tuneplayer.app.ui.TunePlayerScreen
import com.tuneplayer.app.ui.theme.TunePlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TunePlayerTheme {
                TunePlayerScreen()
            }
        }
    }
}
