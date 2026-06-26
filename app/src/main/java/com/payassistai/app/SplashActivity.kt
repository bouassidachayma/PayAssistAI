package com.payassistai.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payassistai.app.ui.theme.PayAssistAITheme
import com.payassistai.app.ui.theme.ThemeManager

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDark = ThemeManager.isDarkTheme(this)

        setContent {
            PayAssistAITheme(darkTheme = isDark) {
                SplashScreen(darkTheme = isDark)
            }
        }

        window.decorView.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2000)
    }
}

@Composable
fun SplashScreen(darkTheme: Boolean) {
    // Different backgrounds for light/dark mode
    val background = if (darkTheme) {
        // Dark mode: gradient from primary to darker purple
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF6C63FF),
                Color(0xFF4A3DB8)
            )
        )
    } else {
        // Light mode: Purple80 (#D0BCFF)
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFD0BCFF),
                Color(0xFFD0BCFF)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "💳",
                fontSize = 72.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "PayAssist AI",
                color = Color.White,
                fontSize = 36.sp,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Intelligent Payment Assistant",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}