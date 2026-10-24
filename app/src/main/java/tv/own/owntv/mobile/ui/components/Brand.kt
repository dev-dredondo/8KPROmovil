package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.mobile.R

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/**
 * The icon's flat mark (no shadow, no next card); at 32 dp and below the simpler small drawing. With Settings ›
 * Accent-colored logo on (the default) the play triangle takes the accent, as on the TV: Pixel draws its dots
 * with an accent triangle, a card gets a tinted triangle over its own. [followAccent] false draws the icon as
 * the launcher shows it (the icon picker).
 */
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, followAccent: Boolean = true) {
    // 8K PRO: one logo for every icon colour (res/drawable-nodpi/brand_mark.png).
    Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = modifier.size(size))
}

/** "Pixel": core's dot-matrix TV set, the play triangle's dots in [triangle]. */
@Composable
private fun PixelMark(triangle: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 100f
        val m = tv.own.owntv.core.brand.PixelMark
        m.dots.forEach { d ->
            val color = when (d.kind) {
                tv.own.owntv.core.brand.PixelMark.Kind.BODY -> Color(m.BODY)
                tv.own.owntv.core.brand.PixelMark.Kind.TRIANGLE -> triangle
                tv.own.owntv.core.brand.PixelMark.Kind.SIDE -> Color(m.SIDE)
            }
            drawCircle(color, m.DOT_RADIUS * u, androidx.compose.ui.geometry.Offset(d.x * u, d.y * u))
        }
        drawCircle(Color(m.POWER), m.POWER_RADIUS * u, androidx.compose.ui.geometry.Offset(m.POWER_X * u, m.POWER_Y * u))
    }
}

/**
 * The wordmark (the TV's `Wordmark`): lowercase "own" in cream on a dark page (the
 * text colour on a light one), "tv" in the accent, drawn from core's two vectors at [width]; its height
 * follows the 988 × 182 drawing.
 */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier) {
    // 8K PRO: "8K" in the text colour and "PRO" in the accent, sized to the box OwnTV's wordmark had.
    val palette = tv.own.owntv.mobile.ui.stage.LocalStagePalette.current
    val own = if (palette.isLight) palette.text else WordmarkCream
    val accent = tv.own.owntv.mobile.ui.stage.stageAccent.accent
    val height = width * 182f / 988f
    val fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (height * 1.05f).toSp() }
    val style = androidx.compose.ui.text.TextStyle(
        fontSize = fontSize,
        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
        lineHeight = fontSize,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    )
    Box(modifier.size(width, height), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
        androidx.compose.foundation.text.BasicText(
            text = androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = own)); append("8K "); pop()
                pushStyle(androidx.compose.ui.text.SpanStyle(color = accent)); append("PRO"); pop()
            },
            style = style,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private val WordmarkCream = Color(0xFFFFF6EE)
