package com.uwbcompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Email+password login/registration. Talks to the backend /auth endpoints. */
@Composable
fun LoginScreen(
    error: String?,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (username: String, email: String, password: String) -> Unit,
) {
    var registering by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("UWB Peer Compass")
        if (registering) {
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        Button(
            onClick = {
                if (registering) onRegister(username, email, password) else onLogin(email, password)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text(if (registering) "Create account" else "Log in") }
        TextButton(onClick = { registering = !registering }) {
            Text(if (registering) "Have an account? Log in" else "New here? Create an account")
        }
    }
}
