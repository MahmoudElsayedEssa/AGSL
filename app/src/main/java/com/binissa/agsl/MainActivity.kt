package com.binissa.agsl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.binissa.agsl.globe.GlobeEffectScreen
import com.binissa.agsl.ui.theme.AGSLTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AGSLTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        GlobeEffectScreen()
                    }
                }
            }
        }
    }
}


/*

import com.binissa.agsl.R

 val imageBitmap = remember {
        context.resources.getDrawable(R.drawable.donut, null)
            .toBitmap()
            .asImageBitmap()    }
 */