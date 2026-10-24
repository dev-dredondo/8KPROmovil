package tv.own.owntv.mobile.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.stage.stageBackground

/**
 * 8K PRO setup: nothing to type. The user has just signed in (activation code or username and
 * password), so the first run creates a profile named after the account and imports the account's
 * playlist on its own; from Settings it re-links and re-syncs that playlist for the active profile.
 *
 * Same signature as OwnTV's wizard, so every caller (the shell's first run, the setup route) gets this.
 * [entry] is accepted and ignored: there is only one kind of content to add.
 */
@Composable
fun SetupFlow(
    onDone: (profileId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") entry: SetupEntry = SetupEntry.ADD_CONTENT,
) {
    val vm: SetupViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val firstRun = onCancel == null
    val playlistName = stringResource(R.string.app_name)
    // Starts by itself once; after a Cancel it waits for "Try again".
    var started by rememberSaveable { mutableStateOf(false) }
    val start: () -> Unit = {
        started = true
        vm.reset()
        vm.startBrandSetup(playlistName, createProfileNamed = firstRun)
    }
    LaunchedEffect(Unit) { if (!started) start() }
    BackHandler(enabled = !firstRun) { onCancel?.invoke() }

    Box(modifier.fillMaxSize().stageBackground()) {
        if (!started) {
            WizardFrame(
                step = WizardStep.PLAYLIST,
                title = stringResource(R.string.setup_preparing_catalog),
                sub = null,
                next = stringResource(R.string.setup_try_again_caps),
                onNext = start,
                onBack = onCancel,
            )
        } else {
            ImportProgress(
                state = state,
                progress = progress,
                name = playlistName,
                onContinue = { vm.finish(onDone) },
                onAddGuide = null,
                onRunInBackground = { vm.continueInBackground(onDone) },
                onRetry = start,
                onCancel = {
                    vm.cancelImport()
                    started = false
                    onCancel?.invoke()
                },
            )
        }
    }
}
