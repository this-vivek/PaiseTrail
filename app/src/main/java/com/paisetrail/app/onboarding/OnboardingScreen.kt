package com.paisetrail.app.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.paisetrail.app.ui.components.AuroraHealth
import com.paisetrail.app.ui.components.AuroraHeroCard
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.PillShape

private enum class OnboardingStep { WELCOME, NOTIFICATIONS, SMS }

/** First-launch flow (spec §5.1) — today the app is otherwise inert until two permissions are
 * granted from deep inside Settings, which is the #1 UX gap. Three steps: value prop, then
 * notification access and SMS access each with a plain-language explanation of exactly what's
 * read, a live example, and a deep link to the system toggle. SMS is skippable; both detect a
 * grant on resume and show a check instead of asking again. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var smsGranted by remember { mutableStateOf(hasSmsPermissions(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = isNotificationListenerEnabled(context)
                smsGranted = hasSmsPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { smsGranted = hasSmsPermissions(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        AuroraHeroCard(
            health = AuroraHealth.CALM,
            modifier = Modifier.fillMaxSize(),
            height = null,
            shape = androidx.compose.ui.graphics.RectangleShape,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AnimatedContent(targetState = step, label = "onboardingStep") { current ->
                    when (current) {
                        OnboardingStep.WELCOME -> WelcomeStep(onNext = { step = OnboardingStep.NOTIFICATIONS })
                        OnboardingStep.NOTIFICATIONS -> PermissionStep(
                            icon = Icons.Outlined.Notifications,
                            title = "Read UPI payment notifications",
                            explanation = "PaiseTrail reads notifications from GPay, PhonePe, CRED and Paytm to " +
                                "detect payments the moment they happen. Everything is parsed on-device — nothing " +
                                "ever leaves your phone.",
                            exampleLabel = "GPay",
                            exampleText = "₹450.00 paid to Cafe Coffee Day",
                            granted = notificationGranted,
                            onGrant = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            onNext = { step = OnboardingStep.SMS },
                            skippable = false,
                        )
                        OnboardingStep.SMS -> PermissionStep(
                            icon = Icons.Outlined.Sms,
                            title = "Read bank debit SMS",
                            explanation = "Some banks only confirm a payment by SMS, not a notification. PaiseTrail " +
                                "reads debit alerts from your bank to catch those too — parsed on-device, same as " +
                                "notifications.",
                            exampleLabel = "HDFC Bank",
                            exampleText = "Sent Rs.450.00 from HDFC Bank A/C *6996",
                            granted = smsGranted,
                            onGrant = {
                                smsPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
                                )
                            },
                            onNext = {
                                viewModel.completeOnboarding()
                                onDone()
                            },
                            skippable = true,
                            nextLabel = "Done",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(PaisaSpacing.loose)) {
        Text(
            text = "PaiseTrail",
            style = PaisaTheme.typography.display,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Every UPI payment, automatically categorized and mapped — without lifting a finger.",
            style = PaisaTheme.typography.body,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PaisaSpacing.tight),
        )
        PrimaryButton(text = "Get started", onClick = onNext, modifier = Modifier.padding(top = PaisaSpacing.loose))
    }
}

@Composable
private fun PermissionStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    explanation: String,
    exampleLabel: String,
    exampleText: String,
    granted: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit,
    skippable: Boolean,
    nextLabel: String = "Next",
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(PaisaSpacing.loose)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(
            text = title,
            style = PaisaTheme.typography.title,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PaisaSpacing.tight),
        )
        Text(
            text = explanation,
            style = PaisaTheme.typography.body,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PaisaSpacing.tight),
        )
        Box(
            modifier = Modifier
                .padding(top = PaisaSpacing.normal)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(PaisaSpacing.tight),
        ) {
            Column {
                Text(text = exampleLabel, style = PaisaTheme.typography.label, color = Color.White.copy(alpha = 0.7f))
                Text(text = exampleText, style = PaisaTheme.typography.bodyBold, color = Color.White)
            }
        }

        if (granted) {
            Row(
                modifier = Modifier.padding(top = PaisaSpacing.loose),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PaisaTheme.colors.positive)
                Text(
                    text = "Granted",
                    style = PaisaTheme.typography.bodyBold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            PrimaryButton(text = nextLabel, onClick = onNext, modifier = Modifier.padding(top = PaisaSpacing.normal))
        } else {
            PrimaryButton(text = "Grant access", onClick = onGrant, modifier = Modifier.padding(top = PaisaSpacing.loose))
            if (skippable) {
                Text(
                    text = "Skip for now",
                    style = PaisaTheme.typography.bodyBold,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.clickable(onClick = onNext).padding(top = PaisaSpacing.tight),
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = PaisaTheme.typography.bodyBold, color = PaisaTheme.colors.bg)
    }
}

private fun hasSmsPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED

private fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
