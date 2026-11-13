package com.droid.nanitroutines.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var status by remember {
                mutableStateOf(WearRoutineDispatcher.getLastStatus(applicationContext))
            }

            WearWatchScreen(
                statusText = status,
                onTriggerNaptime = {
                    WearRoutineDispatcher.triggerRoutineAsync(applicationContext, "NAPTIME") {
                        status = it
                    }
                },
                onTriggerWakeup = {
                    WearRoutineDispatcher.triggerRoutineAsync(applicationContext, "WAKEUP") {
                        status = it
                    }
                }
            )
        }
    }
}

@Composable
private fun WearWatchScreen(
    statusText: String,
    onTriggerNaptime: () -> Unit,
    onTriggerWakeup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Kip's Room",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = statusText,
            color = Color(0xFF38BDF8),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onTriggerNaptime,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Text(
                text = "🌙  Naptime",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onTriggerWakeup,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Text(
                text = "☀️  Wakeup",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
