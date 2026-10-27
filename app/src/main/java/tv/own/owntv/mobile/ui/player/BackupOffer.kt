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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.brand.BackupCatalog
import tv.own.owntv.mobile.ui.screens.live.LiveTuner
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.player.PlaybackEngine

/** Continuous trouble (no picture, reconnecting) before the backup is offered. */
private const val STALL_AFTER_MS = 20_000L
/** An engine that has given up and says so: offer sooner. */
private const val ERROR_AFTER_MS = 5_000L
/** A channel that cut out this many times within [STUTTER_WINDOW_MS] is offered the backup too. */
private const val STUTTER_CUTS = 4
private const val STUTTER_WINDOW_MS = 120_000L
/** Seconds before the best backup match plays by itself when the user has not picked one. */
private const val AUTO_PLAY_SECS = 10

/**
 * 8K PRO: when a live channel stops and does not come back — still loading or reconnecting after
 * [STALL_AFTER_MS], or failed — offer the channels of the backup provider whose name looks like it.
 * The backup's channels exist nowhere else in the app; picking one plays it in place ([LiveTuner.playBackup]).
 * "Keep waiting" hides the offer for this channel until it is tuned again.
 */
@Composable
fun BackupOffer(tuner: LiveTuner, engine: PlaybackEngine, channel: ChannelEntity?, modifier: Modifier = Modifier) {
    val catalog: BackupCatalog = koinInject()
    if (channel == null || !catalog.configured) return
    // The backup's list is fetched while the channel still plays, so an offer never waits for it.
    LaunchedEffect(Unit) { catalog.warmUp() }
    var dismissedFor by remember { mutableStateOf<Long?>(null) }
    var offerFor by remember { mutableStateOf<Long?>(null) }
    // Backup streams that failed while this player was open: tried last the next time round the cycle.
    var failedUrls by remember { mutableStateOf(emptySet<String>()) }
    // True when the offer is up because the channel keeps cutting out, not because it stopped.
    var stutter by remember { mutableStateOf(false) }
    val onBackup = tuner.playingBackup
    LaunchedEffect(channel.id, engine) {
        offerFor = null
        stutter = false
        // When each cut (buffering after the picture had started) began, for spotting a channel that keeps
        // cutting out without ever stopping. Collected, not polled: most cuts are shorter than a second.
        val cuts = ArrayDeque<Long>()
        launch {
            var played = false
            var wasBuffering = false
            kotlinx.coroutines.flow.combine(engine.isPlaying, engine.buffering) { p, b -> p to b }.collect { (playing, buffering) ->
                if (playing && !buffering) played = true
                if (buffering && !wasBuffering && played) cuts.addLast(SystemClock.elapsedRealtime())
                wasBuffering = buffering
            }
        }
        var troubleSince: Long? = null
        while (true) {
            delay(1_000)
            val failed = engine.error.value != null
            val trouble = failed || engine.buffering.value
            val now = SystemClock.elapsedRealtime()
            troubleSince = if (trouble) troubleSince ?: now else null
            val since = troubleSince
            val tooLong = since != null && now - since >= (if (failed) ERROR_AFTER_MS else STALL_AFTER_MS)
            while (cuts.isNotEmpty() && now - cuts.first() > STUTTER_WINDOW_MS) cuts.removeFirst()
            val stuttering = cuts.size >= STUTTER_CUTS
            when {
                // The backup channel failed too: back to the real one. If that fails again, the offer (and
                // its countdown) starts over — the cycle the user asked for, until one of them plays.
                onBackup && tooLong -> {
                    failedUrls = failedUrls + channel.streamUrl
                    tuner.returnFromBackup()
                    return@LaunchedEffect
                }
                onBackup -> Unit
                tooLong && dismissedFor != channel.id -> { offerFor = channel.id; stutter = false }
                stuttering && dismissedFor != channel.id && offerFor != channel.id -> { offerFor = channel.id; stutter = true }
                since == null && !stutter -> offerFor = null // the channel came back by itself
            }
        }
    }
    if (onBackup || offerFor != channel.id) return

    val suffix = stringResource(R.string.backup_suffix)
    // null = still looking; Unavailable = the backup server did not answer.
    val result by produceState<Offer?>(initialValue = null, channel.id) {
        value = catalog.alternatives(channel.name)?.let { Offer.Found(it) } ?: Offer.Unavailable
    }
    // The user's own search, for when the names are too different to be matched.
    var query by remember(channel.id) { mutableStateOf("") }
    // With no choice made, the best match plays by itself after AUTO_PLAY_SECS (paused while searching).
    val ranked = (result as? Offer.Found)?.alternatives.orEmpty().sortedBy { it.url in failedUrls }
    val autoPick = ranked.firstOrNull()
    var remaining by remember(channel.id) { mutableStateOf(AUTO_PLAY_SECS) }
    LaunchedEffect(channel.id, autoPick?.url, stutter) {
        // A channel that still plays, only badly, is the user's call: no countdown for it.
        if (stutter) return@LaunchedEffect
        val pick = autoPick ?: return@LaunchedEffect
        remaining = AUTO_PLAY_SECS
        while (remaining > 0) {
            delay(1_000)
            if (query.isBlank()) remaining--
        }
        tuner.playBackup("${pick.name} · $suffix", pick.url, pick.logoUrl)
    }
    val searched by produceState<List<BackupCatalog.Alternative>?>(initialValue = null, query) {
        value = if (query.isBlank()) null else {
            delay(250)
            catalog.search(query) ?: emptyList()
        }
    }
    PlayerToast(modifier.widthIn(max = 440.dp).padding(16.dp)) {
        Text(
            stringResource(if (stutter) R.string.backup_offer_title_stutter else R.string.backup_offer_title),
            style = stageText(17f, 800), color = Color.White, textAlign = TextAlign.Center,
        )
        val found = ranked.takeIf { result is Offer.Found }
        val sub = when {
            result == null -> stringResource(R.string.backup_offer_searching)
            result == Offer.Unavailable -> stringResource(R.string.backup_offer_unreachable)
            found.isNullOrEmpty() -> stringResource(R.string.backup_offer_none)
            else -> stringResource(R.string.backup_offer_text)
        }
        Text(sub, style = stageText(13f, 500), color = Color(0xFFCDD5D2), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (autoPick != null && query.isBlank() && !stutter) {
            Text(
                stringResource(R.string.backup_offer_auto, autoPick.name, remaining),
                style = stageText(13f, 700), color = stageAccent.accent, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (result is Offer.Found) {
            tv.own.owntv.mobile.ui.stage.StageField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.backup_offer_search),
                placeholder = channel.name,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
        }
        val shown = if (query.isNotBlank()) searched.orEmpty() else found.orEmpty()
        if (result == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
        } else if (query.isNotBlank() && searched != null && shown.isEmpty()) {
            Text(stringResource(R.string.backup_offer_none), style = stageText(13f, 500), color = Color(0xFFCDD5D2))
        } else if (shown.isNotEmpty()) {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                shown.forEach { alt ->
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

/** First wait before a channel the engines gave up on is opened again; each retry waits longer, up to [RETRY_MAX_MS]. */
private const val RETRY_FIRST_MS = 5_000L
private const val RETRY_MAX_MS = 30_000L
/** With a backup provider: after the offer's own wait (5 s) and countdown (10 s) have had their turn. */
private const val RETRY_FIRST_WITH_BACKUP_MS = 30_000L

/**
 * 8K PRO: a live channel is never given up on. When the engines have
 * given up (ExoPlayer, then mpv, and an error on screen), the channel is opened again from the start
 * after [RETRY_FIRST_MS], then a little later each time, until it plays.
 */
@Composable
fun KeepTrying(tuner: LiveTuner, engine: PlaybackEngine, channel: ChannelEntity?) {
    val catalog: BackupCatalog = koinInject()
    if (channel == null || tuner.playingBackup) return
    // With a backup the offer (and its automatic switch) comes first; this only catches what it leaves:
    // no similar channel, the backup unreachable, or "Keep waiting".
    val firstWait = if (catalog.configured) RETRY_FIRST_WITH_BACKUP_MS else RETRY_FIRST_MS
    LaunchedEffect(channel.id, engine) {
        var failedSince: Long? = null
        var attempts = 0
        while (true) {
            delay(1_000)
            val now = SystemClock.elapsedRealtime()
            if (engine.error.value != null) {
                val since = failedSince ?: now.also { failedSince = it }
                if (now - since >= (firstWait + RETRY_FIRST_MS * attempts).coerceAtMost(maxOf(RETRY_MAX_MS, firstWait))) {
                    attempts++
                    failedSince = null
                    tuner.retryLive()
                }
            } else {
                failedSince = null
                if (engine.isPlaying.value && !engine.buffering.value) attempts = 0
            }
        }
    }
}

private sealed interface Offer {
    data class Found(val alternatives: List<BackupCatalog.Alternative>) : Offer
    data object Unavailable : Offer
}
