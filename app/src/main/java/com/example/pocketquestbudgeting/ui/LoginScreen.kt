package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketquestbudgeting.R

private val LoginTeal = Color(0xFF0F6B5C)
private val LinkBlue = Color(0xFF013673)

@Composable
fun LoginScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit = {},
    onNeedHelp: () -> Unit = {},
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 381.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onLogin,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LoginTeal,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .widthIn(max = 228.dp)
                    .fillMaxWidth()
                    .height(39.dp),
            ) {
                Text(text = stringResource(R.string.login))
            }

            Text(
                text = stringResource(R.string.register_ac),
                color = LinkBlue,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = onRegister),
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.need_help_),
                color = LinkBlue,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                modifier = Modifier.clickable(onClick = onNeedHelp),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    MaterialTheme {
        LoginScreen(onLogin = {})
    }
}