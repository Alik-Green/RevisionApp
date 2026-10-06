package com.revisionapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.revisionapp.platform.PlatformContext
import com.revisionapp.platform.createPlatformServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val platform = createPlatformServices(PlatformContext(applicationContext))
        setContent {
            App(platform)
        }
    }
}
