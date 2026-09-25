package com.chatooz.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import com.chatooz.app.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatooz.app.ui.theme.*
import com.chatooz.app.viewmodel.ChatoozViewModel

@Composable
fun AuthScreen(
    viewModel: ChatoozViewModel,
    modifier: Modifier = Modifier
) {
    val authError by viewModel.authError.collectAsState()
    val authLoading by viewModel.authLoading.collectAsState()
    val isDark by viewModel.isDark.collectAsState()

    var email by remember { mutableStateOf("") }

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Decorative gradient circles
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        listOf(IndigoPrimary.copy(alpha = 0.25f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(160.dp)
                )
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .background(
                    Brush.radialGradient(
                        listOf(VioletAccent.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(130.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Logo area
            Image(
                painter = painterResource(id = R.drawable.ic_chatooz_logo),
                contentDescription = "Chatooz",
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Chatooz",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textPrim
            )
            Text(
                text = "Connect with friends, your way",
                fontSize = 15.sp,
                color = textSec,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 40.dp)
            )

            // Gmail Input Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) DarkCard else LightSurface,
                shadowElevation = if (isDark) 0.dp else 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Continue with Email",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrim
                    )
                    Text(
                        text = "We'll send a 6-digit verification code to your email",
                        fontSize = 13.sp,
                        color = textSec,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            viewModel.clearAuthError()
                        },
                        label = { Text("Email address") },
                        placeholder = { Text("you@gmail.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = IndigoPrimary)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.sendEmailOtp(email)
                        }),
                        singleLine = true,
                        isError = authError != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoPrimary,
                            unfocusedBorderColor = if (isDark) DarkDivider else LightDivider,
                            focusedTextColor = textPrim,
                            unfocusedTextColor = textPrim
                        )
                    )

                    AnimatedVisibility(visible = authError != null) {
                        Text(
                            text = authError ?: "",
                            color = RoseAccent,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.sendEmailOtp(email) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        enabled = !authLoading && email.isNotBlank()
                    ) {
                        if (authLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Verification Code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Secure login via verification code sent directly from chatooz.help@gmail.com",
                fontSize = 12.sp,
                color = textSec,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun OtpVerificationScreen(
    viewModel: ChatoozViewModel,
    modifier: Modifier = Modifier
) {
    val authError by viewModel.authError.collectAsState()
    val authSuccessMessage by viewModel.authSuccessMessage.collectAsState()
    val authLoading by viewModel.authLoading.collectAsState()
    val pendingEmail by viewModel.pendingEmail.collectAsState()
    val otpTimerSeconds by viewModel.otpTimerSeconds.collectAsState()
    val isDark by viewModel.isDark.collectAsState()

    var otpCode by remember { mutableStateOf("") }

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkCard else LightSurface
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Icon Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(listOf(IndigoPrimary, VioletAccent)),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MarkEmailRead,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Enter Verification Code",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textPrim,
                textAlign = TextAlign.Center
            )
            Text(
                text = "We sent a 6-digit code to\n$pendingEmail",
                fontSize = 14.sp,
                color = textSec,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = surface,
                shadowElevation = if (isDark) 0.dp else 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Enter 6-Digit Code",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSec
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OtpSixDigitInput(
                        value = otpCode,
                        onValueChange = {
                            otpCode = it
                            viewModel.clearAuthError()
                            if (it.length == 6) {
                                viewModel.verifyEmailOtp(it)
                            }
                        },
                        isError = authError != null,
                        isDark = isDark,
                        textPrim = textPrim,
                        onDone = {
                            if (otpCode.length == 6) {
                                viewModel.verifyEmailOtp(otpCode)
                            }
                        }
                    )

                    AnimatedVisibility(visible = authError != null) {
                        Text(
                            text = authError ?: "",
                            color = RoseAccent,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    AnimatedVisibility(visible = authSuccessMessage != null && authError == null) {
                        Text(
                            text = authSuccessMessage ?: "",
                            color = EmeraldAccent,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.verifyEmailOtp(otpCode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        enabled = !authLoading && otpCode.length == 6
                    ) {
                        if (authLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verify & Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Resend OTP Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.goToAuth() }
                        ) {
                            Text("Change Email", fontSize = 13.sp, color = textSec)
                        }

                        if (otpTimerSeconds > 0) {
                            Text(
                                text = "Resend in ${otpTimerSeconds}s",
                                fontSize = 13.sp,
                                color = textSec
                            )
                        } else {
                            TextButton(
                                onClick = { viewModel.resendEmailOtp() },
                                enabled = !authLoading
                            ) {
                                Text("Resend Code", fontSize = 13.sp, color = IndigoPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun OtpSixDigitInput(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    isDark: Boolean,
    textPrim: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }

    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                onValueChange(newValue)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 6) {
                    val digit = value.getOrNull(i)?.toString() ?: ""
                    val isCurrentCell = isFocused && (i == value.length || (i == 5 && value.length == 6))
                    val isFilled = digit.isNotEmpty()

                    val borderColor = when {
                        isError -> RoseAccent
                        isCurrentCell -> IndigoPrimary
                        isFilled -> IndigoPrimary.copy(alpha = 0.75f)
                        else -> if (isDark) DarkDivider else LightDivider
                    }
                    val borderWidth = if (isCurrentCell || isError) 2.dp else 1.2.dp
                    val cellBg = when {
                        isFilled -> IndigoPrimary.copy(alpha = 0.12f)
                        isCurrentCell -> IndigoPrimary.copy(alpha = 0.07f)
                        else -> if (isDark) Color(0xFF1E2430) else Color(0xFFF9FAFB)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cellBg)
                            .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (digit.isNotEmpty()) {
                            Text(
                                text = digit,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrim,
                                textAlign = TextAlign.Center
                            )
                        } else if (isCurrentCell) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(22.dp)
                                    .background(IndigoPrimary, shape = RoundedCornerShape(1.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isDark) Color(0xFF4B5563) else Color(0xFFD1D5DB),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SetupScreen(
    viewModel: ChatoozViewModel,
    modifier: Modifier = Modifier
) {
    val authError by viewModel.authError.collectAsState()
    val authLoading by viewModel.authLoading.collectAsState()
    val pendingEmail by viewModel.pendingEmail.collectAsState()
    val isDark by viewModel.isDark.collectAsState()

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }

    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkCard else LightSurface
    val textPrim = if (isDark) TextPrimDark else TextPrimLight
    val textSec = if (isDark) TextSecDark else TextSecLight

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(listOf(EmeraldAccent, CyanAccent)),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Welcome! Set up your profile", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textPrim, textAlign = TextAlign.Center)
            Text(
                text = "Signing up as $pendingEmail",
                fontSize = 13.sp,
                color = IndigoPrimary,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = surface,
                shadowElevation = if (isDark) 0.dp else 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Display Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; viewModel.clearAuthError() },
                        label = { Text("Your full name *") },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = IndigoPrimary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoPrimary,
                            unfocusedBorderColor = if (isDark) DarkDivider else LightDivider,
                            focusedTextColor = textPrim,
                            unfocusedTextColor = textPrim
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Username
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            val clean = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }
                            username = clean
                            viewModel.clearAuthError()
                            usernameAvailable = if (clean.length >= 3) viewModel.checkUsernameAvailability(clean) else null
                        },
                        label = { Text("Choose your @username *") },
                        leadingIcon = { Text("  @", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary) },
                        trailingIcon = {
                            when (usernameAvailable) {
                                true -> Icon(Icons.Default.CheckCircle, "Available", tint = EmeraldAccent)
                                false -> Icon(Icons.Default.Cancel, "Taken", tint = RoseAccent)
                                null -> {}
                            }
                        },
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = when (usernameAvailable) {
                                    true -> "✓ @$username is available!"
                                    false -> "✗ @$username is already taken"
                                    null -> "3-20 characters: a-z, 0-9, underscores"
                                },
                                color = when (usernameAvailable) {
                                    true -> EmeraldAccent
                                    false -> RoseAccent
                                    null -> textSec
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = when (usernameAvailable) {
                                true -> EmeraldAccent
                                false -> RoseAccent
                                null -> IndigoPrimary
                            },
                            unfocusedBorderColor = if (isDark) DarkDivider else LightDivider,
                            focusedTextColor = textPrim,
                            unfocusedTextColor = textPrim
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Optional Mobile Number
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Mobile Number (Optional)") },
                        placeholder = { Text("+91 9876543210") },
                        leadingIcon = { Icon(Icons.Default.Phone, null, tint = IndigoPrimary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        supportingText = {
                            Text("Optional — helps friends find you", color = textSec, fontSize = 11.sp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoPrimary,
                            unfocusedBorderColor = if (isDark) DarkDivider else LightDivider,
                            focusedTextColor = textPrim,
                            unfocusedTextColor = textPrim
                        )
                    )

                    AnimatedVisibility(visible = authError != null) {
                        Text(
                            text = authError ?: "",
                            color = RoseAccent,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.createAccount(pendingEmail, name, username, phone) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        enabled = !authLoading && usernameAvailable == true && name.isNotBlank()
                    ) {
                        if (authLoading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        } else {
                            Text("Create Account 🚀", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
