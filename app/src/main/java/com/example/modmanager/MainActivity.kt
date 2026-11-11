package com.darkestdungeon.modmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.darkestdungeon.modmanager.ui.ModManagerApp
import com.darkestdungeon.modmanager.ui.theme.DDAndroidModManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DDAndroidModManagerTheme {
                ModManagerApp()
            }
        }
    }
}
