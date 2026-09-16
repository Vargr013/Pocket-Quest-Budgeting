package com.example.pocketquestbudgeting.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pocketquestbudgeting.R
import com.example.pocketquestbudgeting.ui.theme.PlayfairDisplay

private val ScreenBg = Color(0xFFF4F7F6)
private val TextPrimary = Color(0xFF1A2B28)
private val TextSecondary = Color(0xFF6B7C78)
private val LinkBlue = Color(0xFF013673)
private val LoginTeal = Color(0xFF0F6B5C)
private val BudgetingCoral = Color(0xFFE07A5F)
private val FieldGray = Color(0xFFD9D9D9)
private val FieldShape = RoundedCornerShape(20.dp)

@Composable
fun LoginScreen(
    onLogin: (username: String, password: String) -> Unit,
    errorMessage: String? = null,
    onRegister: () -> Unit = {},
    onNeedHelp: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(R.drawable.piggy_login_logo),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )

            // Title + form sit on top of the image, starting near the top
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp) // nudge title onto the logo; tweak if needed
                    .padding(horizontal = 17.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Pocket Quest",
                    fontFamily = PlayfairDisplay,
                    fontSize = 90.sp,
                    lineHeight = 75.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(7.dp)
                            .background(TextPrimary),
                    )
                    Text(
                        text = "Budgeting",
                        fontFamily = PlayfairDisplay,
                        fontSize = 55.sp,
                        color = BudgetingCoral,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(7.dp)
                            .background(TextPrimary),
                    )
                }

                // Form starts right under Budgeting
                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .widthIn(max = 381.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Username", fontSize = 11.sp, color = TextPrimary)
                    LoginPlainField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "Empty",
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Password", fontSize = 11.sp, color = TextPrimary)
                    LoginPlainField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Empty",
                        isPassword = true,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { rememberMe = !rememberMe },
                        ) {
                            Text("Remember Me", fontSize = 11.sp, color = LinkBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .border(1.dp, FieldGray)
                                    .background(if (rememberMe) LoginTeal else Color.Transparent),
                            )
                        }
                        Text(
                            text = "Forgot Password",
                            fontSize = 11.sp,
                            color = LinkBlue,
                            modifier = Modifier.clickable(onClick = onForgotPassword),
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = Color.Red,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { onLogin(username, password) },
                        shape = FieldShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LoginTeal,
                            contentColor = TextPrimary,
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = 228.dp)
                            .fillMaxWidth()
                            .height(39.dp),
                    ) {
                        Text("Login", fontSize = 20.sp)
                    }

                    Text(
                        text = "Register Account",
                        fontSize = 11.sp,
                        color = LinkBlue,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp)
                            .clickable(onClick = onRegister),
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Text(
                        text = "Need help?",
                        fontSize = 11.sp,
                        color = LinkBlue,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(onClick = onNeedHelp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginPlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    height: Dp = 25.dp,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 11.sp, color = TextPrimary),
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
        ),
        cursorBrush = SolidColor(LoginTeal),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(FieldGray, FieldShape)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 11.sp, color = TextSecondary)
                }
                inner()
            }
        },
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    MaterialTheme {
        LoginScreen(onLogin = { _, _ -> })
    }
}