package tv.own.owntv.mobile.brand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.screens.settings.updateFailureText
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageSheetHeader
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * 8K PRO: the automatic update as a bottom sheet over any screen. Nothing while there is no newer
 * version; otherwise the download progress, then the system installer takes over.
 */
@Composable
fun BrandUpdateSheet() {
    val updater: BrandUpdater = koinInject()
    val state by updater.state.collectAsStateWithLifecycle()
    if (state is BrandUpdater.State.Idle || state is BrandUpdater.State.Installing) return
    val palette = LocalStagePalette.current
    MobileBottomSheet(onDismissRequest = updater::dismiss) {
        StageSheetHeader(stringResource(R.string.update_title))
        Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
            val release = when (val s = state) {
                is BrandUpdater.State.Downloading -> s.release
                is BrandUpdater.State.Available -> s.release
                is BrandUpdater.State.Failed -> s.release
                else -> null
            }
            if (release != null && state !is BrandUpdater.State.Failed) {
                Text(
                    stringResource(R.string.update_available_version, release.versionName, updater.currentVersion),
                    style = stageText(14.5f, 700), color = palette.text,
                )
                if (release.notes.isNotBlank()) {
                    Text(release.notes, style = stageText(12.5f, 500), color = palette.muted, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(MobileDimens.GapMedium))
            }
            when (val s = state) {
                is BrandUpdater.State.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(MobileDimens.GapSmall))
                        Text(stringResource(R.string.update_downloading, s.percent), style = stageText(13.5f, 500), color = palette.dim)
                    }
                    Spacer(Modifier.height(MobileDimens.GapSmall))
                    Text(stringResource(R.string.update_installer), style = stageText(12f, 500), color = palette.dim)
                    Spacer(Modifier.height(MobileDimens.GapMedium))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        StageButton(text = stringResource(R.string.common_cancel), onClick = updater::dismiss)
                    }
                }
                is BrandUpdater.State.Available -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                    StageButton(text = stringResource(R.string.update_later), onClick = updater::dismiss)
                    Spacer(Modifier.weight(1f))
                    StageButton(text = stringResource(R.string.update_now), onClick = updater::start, style = StageButtonStyle.PRIMARY)
                }
                is BrandUpdater.State.Failed -> {
                    Text(updateFailureText(s.failure), style = stageText(13.5f, 500), color = palette.dim)
                    Spacer(Modifier.height(MobileDimens.GapMedium))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                        StageButton(text = stringResource(R.string.settings_close), onClick = updater::dismiss)
                        Spacer(Modifier.weight(1f))
                        StageButton(text = stringResource(R.string.update_try_again), onClick = updater::start, style = StageButtonStyle.PRIMARY)
                    }
                }
                else -> Unit
            }
            Spacer(Modifier.height(MobileDimens.GapMedium))
        }
    }
}
