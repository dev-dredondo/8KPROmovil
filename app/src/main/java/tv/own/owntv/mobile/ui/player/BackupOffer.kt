package tv.own.owntv.mobile.ui.player

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.brand.BackupCatalog
import tv.own.owntv.mobile.ui.screens.live.LiveTuner
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.player.PlaybackEngine

/** Continuous trouble (no picture, reconnecting) before the backup is offered. */
private const val STALL_AFTER_MS = 20_000L
/** An engine that has given up and says so: offer sooner. */
private const val ERROR_AFTER_MS = 5_000L

/**
 * 8K PRO: when a live channel stops and does not come back — still loading or reconnecting after
 * [STALL_AFTER_MS], or failed — offer the channels of the backup provider whose name looks like it.
 * The backup's channels exist nowhere else in the app; picking one plays it in place ([LiveTuner.playBackup]).
 * "Keep waiting" hides the offer for this channel until it is tuned again.
 */
@Composable
fun BackupOffer(tuner: LiveTuner, engine: PlaybackEngine, channel: ChannelEntity?, modifier: Modifier = Modifier) {
    val catalog: BackupCatalog = koinInject()
    if (channel == null || !catalog.configured || tuner.playingBackup) return
    var dismissedFor by remember { mutableStateOf<Long?>(null) }
    var offerFor by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(channel.id, engine) {
        offerFor = null
        var troubleSince: Long? = null
        while (true) {
            delay(1_000)
            val failed = engine.error.value != null
            val trouble = failed || engine.buffering.value
            val now = SystemClock.elapsedRealtime()
            troubleSince = if (trouble) troubleSince ?: now else null
            val since = troubleSince
            if (since != null && dismissedFor != channel.id && now - since >= (if (failed) ERROR_AFTER_MS else STALL_AFTER_MS)) {
                offerFor = channel.id
            } else if (since == null) {
                offerFor = null // the channel came back by itself
            }
        }
    }
    if (offerFor != channel.id) return

    val suffix = stringResource(R.string.backup_suffix)
    // null = still looking; Unavailable = the backup server did not answer.
    val result by produceState<Offer?>(initialValue = null, channel.id) {
        value = catalog.alternatives(channel.name)?.let { Offer.Found(it) } ?: Offer.Unavailable
    }
    PlayerToast(modifier.widthIn(max = 440.dp).padding(16.dp)) {
        Text(
            stringResource(R.string.backup_offer_title),
            style = stageText(17f, 800), color = Color.White, textAlign = TextAlign.Center,
        )
        val found = (result as? Offer.Found)?.alternatives
        val sub = when {
            result == null -> stringResource(R.string.backup_offer_searching)
            result == Offer.Unavailable -> stringResource(R.string.backup_offer_unreachable)
            found.isNullOrEmpty() -> stringResource(R.string.backup_offer_none)
            else -> stringResource(R.string.backup_offer_text)
        }
        Text(sub, style = stageText(13f, 500), color = Color(0xFFCDD5D2), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        if (result == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
        } else if (!found.isNullOrEmpty()) {
            Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState())) {
                found.forEach { alt ->
                    StageButton(
                        onClick = { tuner.playBackup("${alt.name} · $suffix", alt.url, alt.logoUrl) },
                        text = alt.name,
                        style = StageButtonStyle.PRIMARY,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        StageButton(
            onClick = { dismissedFor = channel.id; offerFor = null },
            text = stringResource(R.string.backup_offer_wait),
            style = StageButtonStyle.SECONDARY,
            small = true,
        )
    }
}

private sealed interface Offer {
    data class Found(val alternatives: List<BackupCatalog.Alternative>) : Offer
    data object Unavailable : Offer
}
