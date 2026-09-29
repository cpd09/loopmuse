package com.example.loopmuse.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun AppAuthGate(activity: ComponentActivity, content: @Composable () -> Unit) {
    val authenticator = remember(activity) { GoogleAppAuthenticator(activity) }
    var signedIn by remember { mutableStateOf(authenticator.hasGoogleSession()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(authenticator) {
        val listener = FirebaseAuth.AuthStateListener {
            signedIn = authenticator.hasGoogleSession()
        }
        authenticator.firebaseAuth.addAuthStateListener(listener)
        onDispose { authenticator.firebaseAuth.removeAuthStateListener(listener) }
    }

    if (signedIn) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("LoopMuse", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text("음악과 함께하는 일상을 시작하려면 Google 계정으로 로그인해 주세요.")
            Spacer(Modifier.height(24.dp))
            if (busy) CircularProgressIndicator()
            else Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = authenticator.isConfigured,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        val result = authenticator.signIn()
                        busy = false
                        if (result.isSuccess) signedIn = true
                        else error = result.exceptionOrNull()?.localizedMessage ?: "로그인하지 못했습니다. 다시 시도해 주세요."
                    }
                }
            ) { Text("Google 계정으로 로그인") }
            if (!authenticator.isConfigured) {
                Spacer(Modifier.height(16.dp))
                Text("Google 로그인 설정이 아직 완료되지 않았습니다.", color = MaterialTheme.colorScheme.error)
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
