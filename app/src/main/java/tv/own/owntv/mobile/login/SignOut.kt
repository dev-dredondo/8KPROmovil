package tv.own.owntv.mobile.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.screens.settings.StageConfirmSheet

/** 8K PRO: "Sign out?" — clears the saved account; the shell then shows the login screen by itself. */
@Composable
fun SignOutSheet(onDismiss: () -> Unit) {
    val sessionStore: SessionStore = koinInject()
    val scope = rememberCoroutineScope()
    StageConfirmSheet(
        title = stringResource(R.string.login_logout),
        text = stringResource(R.string.login_logout_message),
        confirm = stringResource(R.string.login_logout),
        onConfirm = { scope.launch { sessionStore.clear() } },
        onDismiss = onDismiss,
        danger = true,
    )
}
