package com.profynus.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.profynus.music.ui.ProfynusApp

/** Single Activity: all destinations retain one Media3 playback session. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ProfynusApp() }
    }
}
