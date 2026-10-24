package tv.own.owntv.mobile.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.brand.BrandConfig
import tv.own.owntv.mobile.ui.setup.WizardFrame
import tv.own.owntv.mobile.ui.setup.WizardStep
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText

/**
 * 8K PRO sign-in on the phone.
 *
 * With an activation panel configured it opens on the device's activation code (the admin activates
 * it on the panel and the phone signs in by itself); "Sign in manually" switches to the username and
 * password form, which signs in against the default server. Without a panel only the form exists.
 */
@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    val vm: LoginViewModel = koinViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.onShown() }
    Box(modifier) {
        if (ui.manual) ManualLogin(vm, ui) else ActivationCode(vm, ui)
    }
}

@Composable
private fun ActivationCode(vm: LoginViewModel, ui: LoginUi) {
    val palette = LocalStagePalette.current
    val state = ui.activation
    val code = when (state) {
        is ActivationUi.Waiting -> state.code
        is ActivationUi.Signing -> state.code
        is ActivationUi.Disabled -> state.code
        is ActivationUi.Expired -> state.code
        is ActivationUi.BadAccount -> state.code
        ActivationUi.Registering, ActivationUi.Offline -> null
    }
    val status = when (state) {
        ActivationUi.Registering -> stringResource(R.string.activation_registering)
        is ActivationUi.Waiting -> stringResource(R.string.activation_waiting)
        is ActivationUi.Signing -> stringResource(R.string.login_checking)
        is ActivationUi.Disabled -> stringResource(R.string.activation_disabled)
        is ActivationUi.Expired -> stringResource(R.string.login_error_expired)
        is ActivationUi.BadAccount -> when (state.error) {
            LoginError.Network -> stringResource(R.string.login_error_network)
            LoginError.Expired -> stringResource(R.string.login_error_expired)
            is LoginError.Inactive -> stringResource(R.string.login_error_status, state.error.status)
            else -> stringResource(R.string.activation_bad_account)
        }
        ActivationUi.Offline -> stringResource(R.string.activation_offline)
    }
    val warn = state is ActivationUi.Disabled || state is ActivationUi.Expired ||
        state is ActivationUi.BadAccount || state is ActivationUi.Offline

    WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.activation_title),
        sub = stringResource(R.string.activation_subtitle),
        next = null,
        onNext = {},
        onBack = null,
        extraLeft = listOf(stringResource(R.string.activation_manual) to { vm.showManual(true) }),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .background(palette.ink.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Selectable, so the code can be copied and sent to the provider from the phone itself.
                SelectionContainer {
                    Text(
                        text = code?.chunked(3)?.joinToString(" ") ?: "· · ·",
                        style = stageText(40f, 800),
                        fontFamily = FontFamily.Monospace,
                        color = stageAccent.accent,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                text = status,
                style = stageText(13.5f, 600),
                color = if (warn) palette.danger else palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun ManualLogin(vm: LoginViewModel, ui: LoginUi) {
    val palette = LocalStagePalette.current
    val error = when (val e = ui.error) {
        null -> null
        LoginError.Empty -> stringResource(R.string.login_error_empty)
        LoginError.BadCredentials -> stringResource(R.string.login_error_credentials)
        LoginError.Expired -> stringResource(R.string.login_error_expired)
        is LoginError.Inactive -> stringResource(R.string.login_error_status, e.status)
        LoginError.Network -> stringResource(R.string.login_error_network)
    }
    val sub = if (ui.busy) stringResource(R.string.login_checking) else stringResource(R.string.login_subtitle)
    val backToCode: (() -> Unit)? = if (BrandConfig.activationEnabled) ({ vm.showManual(false) }) else null

    WizardFrame(
        step = WizardStep.PROFILE,
        title = stringResource(R.string.login_title),
        sub = sub,
        next = stringResource(R.string.login_button),
        onNext = vm::login,
        nextEnabled = !ui.busy,
        onBack = backToCode,
        backLabel = stringResource(R.string.activation_use_code),
    ) {
        StageField(
            value = ui.username,
            onValueChange = vm::setUsername,
            label = stringResource(R.string.login_username),
            placeholder = stringResource(R.string.login_username_hint),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            isError = ui.error == LoginError.BadCredentials,
            enabled = !ui.busy,
        )
        StageField(
            value = ui.password,
            onValueChange = vm::setPassword,
            label = stringResource(R.string.login_password),
            placeholder = stringResource(R.string.login_password_hint),
            password = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            isError = ui.error == LoginError.BadCredentials,
            enabled = !ui.busy,
            onImeDone = vm::login,
        )
        if (error != null) {
            Text(
                text = error,
                style = stageText(12.5f, 600),
                color = palette.danger,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
    }
}
