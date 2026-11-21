package com.unmuted.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.unmuted.app.R
import com.unmuted.app.core.onGoogleSignInClicked
import com.unmuted.app.core.onGuestSignInClicked
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.AcousticErrorContainer
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianBackground
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_onboarding_studio_1795250457001),
            contentDescription = "Midnight Studio soundwave rings backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to ObsidianBackground.copy(alpha = 0.25f),
                        0.45f to ObsidianBackground.copy(alpha = 0.65f),
                        0.82f to ObsidianBackground.copy(alpha = 0.96f),
                        1f to ObsidianBackground
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ObsidianSurface.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = ElectricCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AUDIO-FIRST SOCIAL STUDIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioWhite
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Unmuted",
                    style = MaterialTheme.typography.displayLarge,
                    color = StudioWhite
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Speak unfiltered. Hear every side.\nVoice threads, synced lyrics & anonymous rooms.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TitaniumMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeaturePill(
                        icon = Icons.Filled.Mic,
                        title = "Voice First",
                        tint = ElectricCoral,
                        modifier = Modifier.weight(1f)
                    )
                    FeaturePill(
                        icon = Icons.Filled.ClosedCaption,
                        title = "Live Lyrics",
                        tint = SignalMint,
                        modifier = Modifier.weight(1f)
                    )
                    FeaturePill(
                        icon = Icons.Filled.Shield,
                        title = "Anon Rooms",
                        tint = StudioViolet,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(22.dp))

                AnimatedVisibility(visible = errorMessage != null) {
                    val rawError = errorMessage.orEmpty()
                    val sha1Match = remember(rawError) {
                        Regex("""([0-9A-Fa-f]{2}:){19}[0-9A-Fa-f]{2}""").find(rawError)?.value
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AcousticErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SelectionContainer {
                                Text(
                                    text = rawError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AcousticError
                                )
                            }
                            if (sha1Match != null) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(sha1Match))
                                        Toast.makeText(context, "Debug SHA-1 copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = ElectricCoral
                                    ),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy SHA-1 Fingerprint", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isSigningIn = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isSigningIn = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isSigningIn = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isSigningIn = false
                            }
                        )
                    },
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCoral,
                        contentColor = StudioWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = StudioWhite,
                            strokeWidth = 2.4.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Signing in...",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("G", fontWeight = FontWeight.Bold, color = StudioWhite)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        isSigningIn = true
                        onGuestSignInClicked(
                            onAuthSuccess = {
                                isSigningIn = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isSigningIn = false
                                errorMessage = msg
                            },
                            scope = scope
                        )
                    },
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianSurface,
                        contentColor = StudioWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("guest_sign_in_button")
                ) {
                    Text(
                        text = "Continue as Studio Guest",
                        style = MaterialTheme.typography.titleSmall,
                        color = TitaniumMuted
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Your Google identity stays hidden whenever you speak in Anonymous Rooms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TitaniumMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FeaturePill(
    icon: ImageVector,
    title: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface.copy(alpha = 0.85f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = StudioWhite,
                maxLines = 1
            )
        }
    }
}
