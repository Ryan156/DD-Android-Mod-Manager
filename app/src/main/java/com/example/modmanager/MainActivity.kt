package com.darkestdungeon.modmanager

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.darkestdungeon.modmanager.ui.ModManagerApp
import com.darkestdungeon.modmanager.ui.theme.DDAndroidModManagerTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.darkestdungeon.modmanager.ui.AppLanguage.localizedContext(newBase))
    }

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
