package app.darek.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.darek.R
import app.darek.listen.Hearing
import app.darek.listen.LISTENING_CHANNEL_ID
import app.darek.listen.Listening
import app.darek.listen.NotificationBlock
import app.darek.listen.Persona
import app.darek.listen.hasMicrophone
import app.darek.listen.listeningNotificationBlock
import app.darek.listen.listeningRes
import app.darek.theme.Spacing
import app.darek.ui.appViewModelExtras
import app.darek.ui.common.startActivitySafely
import app.darek.work.NotificationPermissionOutcome
import app.darek.work.hasAutostartSettings
import app.darek.work.isIgnoringBatteryOptimisations
import app.darek.work.openAppNotificationSettings
import app.darek.work.openAutostartSettings
import app.darek.work.openBatteryOptimisationSettings
import app.darek.work.openChannelNotificationSettings
import app.darek.work.rememberNotificationPermissionAsk

/** Darek's one screen: *Słuchaj* and the last Reply. Listening outlives it, in [app.darek.listen.ListeningService]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel =
        viewModel(factory = HomeViewModel.Factory, extras = appViewModelExtras()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(context.hasMicrophone()) }
    var notificationBlock by remember { mutableStateOf(context.listeningNotificationBlock()) }
    var needsSetup by remember { mutableStateOf(context.needsBackgroundSetup()) }
    var notificationsForGood by rememberSaveable { mutableStateOf(false) }
    var deniedNow by rememberSaveable { mutableStateOf(false) }
    var disclosing by rememberSaveable { mutableStateOf(false) }
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }

    val askNotifications =
        rememberNotificationPermissionAsk { outcome ->
            notificationBlock = context.listeningNotificationBlock()
            notificationsForGood = outcome == NotificationPermissionOutcome.PermanentlyDenied
            if (notificationBlock == null) viewModel.listen()
        }
    val startWithNotifications = {
        notificationBlock = context.listeningNotificationBlock()
        when (notificationBlock) {
            null -> viewModel.listen()
            NotificationBlock.APP -> askNotifications()
            NotificationBlock.CHANNEL -> Unit
        }
    }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            deniedNow = !ok
            viewModel.onMicAnswer(ok, rationaleBefore, activity?.rationaleDue() ?: false)
            if (ok) startWithNotifications()
        }
    val request = {
        rationaleBefore = activity?.rationaleDue() ?: false
        ask.launch(Manifest.permission.RECORD_AUDIO)
    }
    val startAfterDisclosure = {
        when {
            granted -> startWithNotifications()
            state.micDeniedForGood -> context.openAppSettings()
            else -> request()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        granted = context.hasMicrophone()
        if (granted) deniedNow = false
        viewModel.onMicGrantRead(granted)
        notificationBlock = context.listeningNotificationBlock()
        if (notificationBlock != null) viewModel.onNotificationsBlocked()
        needsSetup = context.needsBackgroundSetup()
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets).padding(Spacing.base)) {
            if (state.showTipAsk) {
                TipAskCard(
                    onTip = {
                        viewModel.answerTipAsk()
                        onOpenSupport()
                    },
                    onDecline = viewModel::answerTipAsk,
                )
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.base),
                ) {
                    ListenControl(
                        phase = state.listening,
                        persona = state.persona,
                        hearingName = state.hearingName,
                        hearing = state.hearing,
                        failed = state.listenFailed,
                        onListen = {
                            if (state.disclosed) startAfterDisclosure() else disclosing = true
                        },
                        onStop = viewModel::stopListening,
                    )
                    state.lastReply?.let { reply ->
                        Text(
                            text = "„$reply”",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                    val block = notificationBlock
                    if (granted && state.disclosed && block != null) {
                        NotificationsOff(
                            persona = state.persona,
                            onFix = {
                                when {
                                    block == NotificationBlock.CHANNEL ->
                                        context.openChannelNotificationSettings(LISTENING_CHANNEL_ID)
                                    notificationsForGood -> context.openAppNotificationSettings()
                                    else -> askNotifications()
                                }
                            },
                        )
                    }
                    if (!granted && (deniedNow || state.micDeniedForGood)) {
                        MicDenied(
                            persona = state.persona,
                            forGood = state.micDeniedForGood,
                            onAllow = request,
                            onOpenSettings = context::openAppSettings,
                        )
                    }
                }
            }
        }
    }

    if (disclosing) {
        ModalBottomSheet(
            onDismissRequest = { disclosing = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState(),
                        ).padding(horizontal = Spacing.base)
                        .padding(bottom = Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.snug),
            ) {
                Text(stringResource(R.string.mic_disclosure_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.mic_disclosure_body), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = {
                        disclosing = false
                        viewModel.markDisclosed()
                        startAfterDisclosure()
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.mic_disclosure_continue)) }
            }
        }
    }

    if (state.listening == Listening.Phase.ON && !state.setupShown && needsSetup) {
        BackgroundSetupSheet(onDone = viewModel::markSetupShown)
    }
}

/** Shown once, after the first service start; the same buttons stay in Settings (phase-2.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundSetupSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    val xiaomi = remember { context.hasAutostartSettings() }
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(
                        rememberScrollState(),
                    ).padding(horizontal = Spacing.base)
                    .padding(bottom = Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.snug),
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(if (xiaomi) R.string.setup_body_xiaomi else R.string.setup_body_battery),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (xiaomi) {
                OutlinedButton(onClick = { context.openAutostartSettings() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_autostart))
                }
            }
            OutlinedButton(
                onClick = { context.openBatteryOptimisationSettings() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.setup_battery))
            }
            Button(onClick = onDone, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.setup_done))
            }
        }
    }
}

@Composable
private fun NotificationsOff(
    persona: Persona,
    onFix: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(persona.notificationsOffRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onFix) { Text(stringResource(R.string.notifications_off_fix)) }
    }
}

@Composable
private fun ListenControl(
    phase: Listening.Phase,
    persona: Persona,
    hearingName: Boolean,
    hearing: Hearing,
    failed: Boolean,
    onListen: () -> Unit,
    onStop: () -> Unit,
) {
    when (phase) {
        Listening.Phase.OFF ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.tight),
            ) {
                Button(onClick = onListen) { Text(stringResource(R.string.home_listen)) }
                if (failed) {
                    Text(
                        text = stringResource(persona.listenFailedRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        Listening.Phase.PREPARING ->
            Button(onClick = {}, enabled = false) { Text(stringResource(R.string.home_preparing)) }
        Listening.Phase.ON ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.tight),
            ) {
                Text(
                    stringResource(if (hearingName) persona.hearingRes() else persona.listeningRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hearingName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                when (hearing) {
                    Hearing.HEARS -> Unit
                    Hearing.PAUSED_FOR_CALL ->
                        Text(
                            stringResource(R.string.listening_paused_call),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    Hearing.CANNOT_HEAR ->
                        Text(
                            stringResource(R.string.listening_cannot_hear),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                }
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.home_stop)) }
            }
    }
}

@Composable
private fun MicDenied(
    persona: Persona,
    forGood: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(persona.micDeniedRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (forGood) {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.mic_open_settings)) }
        } else {
            TextButton(onClick = onAllow) { Text(stringResource(R.string.mic_allow)) }
        }
    }
}

/** On Xiaomi always, since autostart cannot be read back; elsewhere only while the exemption is missing. */
private fun Context.needsBackgroundSetup(): Boolean = hasAutostartSettings() || !isIgnoringBatteryOptimisations()

private fun Activity.rationaleDue(): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)

private fun Context.openAppSettings() {
    startActivitySafely(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
}

// Two resources per line, not one with the name as an argument: Polish declines the name and its
// verbs agree with it (ADR-0016).
private fun Persona.hearingRes(): Int =
    when (this) {
        Persona.DAREK -> R.string.home_hearing
        Persona.DARIA -> R.string.home_hearing_daria
    }

private fun Persona.listenFailedRes(): Int =
    when (this) {
        Persona.DAREK -> R.string.home_listen_failed
        Persona.DARIA -> R.string.home_listen_failed_daria
    }

private fun Persona.notificationsOffRes(): Int =
    when (this) {
        Persona.DAREK -> R.string.notifications_off
        Persona.DARIA -> R.string.notifications_off_daria
    }

private fun Persona.micDeniedRes(): Int =
    when (this) {
        Persona.DAREK -> R.string.mic_denied
        Persona.DARIA -> R.string.mic_denied_daria
    }
