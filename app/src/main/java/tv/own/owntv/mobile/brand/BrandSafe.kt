package tv.own.owntv.mobile.brand

import android.content.res.Resources
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.core.util.FriendlySyncFailure

/**
 * 8K PRO: an unclassified failure is shown as its raw exception text, which for network errors carries
 * the request URL ("unexpected end of stream on http://host/xmltv.php?…"). One that names the account's
 * server is worded as the generic sync error instead, so the server's address never reaches the screen.
 */
fun FriendlySyncFailure.brandSafe(): FriendlySyncFailure =
    if (this is FriendlySyncFailure.Unknown && BrandConfig.mentionsServer(rawMessage)) FriendlySyncFailure.Generic else this

/** [FriendlySyncFailure.displayText], never naming the server. */
fun FriendlySyncFailure.brandSafeText(res: Resources): String =
    brandSafeText(brandSafe().displayText(res), res)

/** [SourceImporter.SetupFailure.displayText], never naming the server. */
fun SourceImporter.SetupFailure.brandSafeText(res: Resources): String {
    val safe = if (this is SourceImporter.SetupFailure.Sync) SourceImporter.SetupFailure.Sync(failure.brandSafe()) else this
    return brandSafeText(safe.displayText(res), res)
}

/** Any wording that still carries the address falls back to the generic line. */
fun brandSafeText(text: String, res: Resources): String =
    if (BrandConfig.mentionsServer(text) || text.contains("http://", ignoreCase = true) || text.contains("https://", ignoreCase = true)) {
        FriendlySyncFailure.Generic.displayText(res)
    } else {
        text
    }
