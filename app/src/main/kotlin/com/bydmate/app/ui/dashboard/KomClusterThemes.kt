package com.bydmate.app.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kom-BYDMate: Cluster էջի ոճերը (օգտատիրոջ հաստատած կոնցեպտներով, 2026-09-30)․
 *  - CLASSIC — նախկինը (մարտկոցի շերտ, մուգ ճանապարհ);
 *  - LAGOON («Լիճ») — կենտրոնական վահանակում լուսավոր ալիք, որի մակարդակը լիցքն է;
 *  - TIDE («Մակընթացություն») — ալիքը ամբողջ լայնքով՝ ապակյա սարքերի տակով;
 *  - ARCH («Կամար») — լիցքը՝ 160° կամար ճանապարհի վրայով, վերին եզրը՝ արագաչափի վերին եզրի մակարդակին։
 * Ալիքը միակ անընդհատ անիմացիան է (~30 կադր/վ, միայն LAGOON / TIDE ոճերում)։
 */
enum class ClusterTheme { CLASSIC, LAGOON, TIDE, ARCH }
enum class ArchLabels { ENDS, CYCLE }
enum class InfoRows { TWO, ONE }

data class ClusterLook(
    val theme: ClusterTheme = ClusterTheme.CLASSIC,
    val d3: Boolean = true,
    val archLabels: ArchLabels = ArchLabels.ENDS,
    val infoRows: InfoRows = InfoRows.TWO,
)

/** Ալիքի ժամացույց վայրկյաններով (~30 կադր/վ)․ [active]-ը false է՝ կանգնած է (0), frame-եր չի ծախսում։ */
@Composable
internal fun rememberWaveClock(active: Boolean): Float {
    val t by produceState(0f, active) {
        if (!active) { value = 0f; return@produceState }
        var start = 0L
        var lastEmit = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                if (now - lastEmit >= 33_000_000L) { value = (now - start) / 1e9f; lastEmit = now }
            }
        }
    }
    return t
}

/** Ալիքի գույները՝ ըստ լիցքի (TIDE)․ կապտականաչ → սաթագույն → կարմիր։ */
internal fun tideColors(soc: Int): Pair<Color, Color> = when {
    soc >= 50 -> Color(0xFF1FD1C1) to Color(0xFF1B7FD6)
    soc >= 20 -> Color(0xFFF5B32A) to Color(0xFFC2641A)
    else -> Color(0xFFFF5A4A) to Color(0xFFA01A1A)
}

/** Եռաշերտ ալիք [x0]..[x1] միջակայքում, [level] բարձրության վրա, վերևում՝ լուսավոր գագաթի գիծ։ */
internal fun DrawScope.drawWave(x0: Float, x1: Float, level: Float, t: Float, c1: Color, c2: Color, amp: Float) {
    val w = x1 - x0
    val step = 8f
    for (layer in 0 until 3) {
        val ph = t * (0.9f + layer * 0.35f) + layer * 1.7f
        val a = amp * (1f - layer * 0.25f)
        val k = (2f * PI.toFloat() / w) * (1.3f + layer * 0.4f)
        val path = Path().apply {
            moveTo(x0, size.height)
            var x = x0
            while (x <= x1) {
                lineTo(x, level + sin(x * k + ph) * a + sin(x * k * 2.3f - ph * 1.3f) * a * 0.3f + layer * 10f)
                x += step
            }
            lineTo(x1, size.height); close()
        }
        drawPath(path, Brush.verticalGradient(
            listOf(c1.copy(alpha = 0.55f - layer * 0.12f), c2.copy(alpha = 0.25f - layer * 0.05f)),
            startY = level - amp, endY = size.height,
        ))
    }
    val k = (2f * PI.toFloat() / w) * 1.3f
    val crest = Path()
    var x = x0
    var first = true
    while (x <= x1) {
        val y = level + sin(x * k + t * 0.9f) * amp + sin(x * k * 2.3f - t * 1.17f) * amp * 0.3f
        if (first) { crest.moveTo(x, y); first = false } else crest.lineTo(x, y)
        x += step
    }
    drawPath(crest, c1.copy(alpha = 0.35f), style = Stroke(9f, cap = StrokeCap.Round))
    drawPath(crest, Color(0xFFBFF6FF).copy(alpha = 0.85f), style = Stroke(3f, cap = StrokeCap.Round))
}

/** Լիցքի կամարը․ [sweepDeg] բացվածքով, համաչափ վերևի առանցքի շուրջը, լցված մասը՝ ձախից։ */
internal fun DrawScope.drawSocArch(c: Offset, r: Float, lw: Float, soc: Float, sweepDeg: Float, d3: Boolean) {
    val start = 270f - sweepDeg / 2f
    val tl = Offset(c.x - r, c.y - r)
    val sz = Size(r * 2, r * 2)
    if (d3) drawArc(Color.Black.copy(alpha = 0.45f), start, sweepDeg, false, tl + Offset(0f, lw * 0.35f), sz,
        style = Stroke(lw + 8f, cap = StrokeCap.Round))
    drawArc(Color(0xCC28405F), start, sweepDeg, false, tl, sz, style = Stroke(lw, cap = StrokeCap.Round))
    val f = (soc / 100f).coerceIn(0f, 1f)
    val fill = (sweepDeg * f).coerceAtLeast(0.5f)
    val stops = arrayOf(
        0f to Color(0xFFE5322D),
        start / 360f to Color(0xFFE5322D),
        (start + sweepDeg * 0.1f) / 360f to Color(0xFFF5C518),
        (start + sweepDeg * 0.4f) / 360f to Color(0xFF9AD94C),
        (start + sweepDeg * 0.8f) / 360f to Color(0xFF1FC9A8),
        1f to Color(0xFF1FC9A8),
    )
    val brush = Brush.sweepGradient(*stops, center = c)
    // «Փայլ»՝ լայն, կիսաթափանցիկ շերտ լցված մասի տակ
    drawArc(brush, start, fill, false, tl, sz, alpha = 0.25f, style = Stroke(lw * 1.7f, cap = StrokeCap.Round))
    drawArc(brush, start, fill, false, tl, sz, style = Stroke(lw, cap = StrokeCap.Round))
    if (d3 && fill > 3f) {
        val hr = r + lw * 0.28f
        drawArc(Color.White.copy(alpha = 0.3f), start + 1.5f, fill - 2.5f, false, Offset(c.x - hr, c.y - hr), Size(hr * 2, hr * 2),
            style = Stroke(lw * 0.22f, cap = StrokeCap.Round))
    }
    val ea = Math.toRadians((start + fill).toDouble())
    val sp = Offset(c.x + cos(ea).toFloat() * r, c.y + sin(ea).toFloat() * r)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), Color.Transparent), center = sp, radius = lw * 1.1f),
        radius = lw * 1.1f, center = sp)
}

private val numStyle = TextStyle(shadow = Shadow(Color(0x99000000), Offset(2f, 3f), 4f))

@Composable
internal fun InfoItem(icon: ImageVector, text: String, fs: Int = 18, color: Color = TextPrimary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size((fs + 2).dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = fs.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun InfoSep() {
    Spacer(Modifier.width(8.dp))
    Box(Modifier.width(1.5.dp).height(20.dp).background(TextSecondary.copy(alpha = 0.5f)))
    Spacer(Modifier.width(8.dp))
}

/**
 * Ջերմաստիճանների (և բարձրության) բլոկը վերևի աջ անկյունում․ [withAlt]՝ բարձրությունն էլ է այստեղ
 * (երկու տողով՝ վերևում բարձրությունը, ներքևում «մեքենա | դրսում», մեկ տողով՝ երեքը միասին)։
 */
@Composable
internal fun CornerInfo(rows: InfoRows, withAlt: Boolean, inside: String, outside: String, alt: String, modifier: Modifier) {
    val fs = if (rows == InfoRows.ONE && withAlt) 15 else 17
    if (rows == InfoRows.ONE) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (withAlt) { InfoItem(Icons.Outlined.Terrain, alt, fs); InfoSep() }
            InfoItem(Icons.Outlined.DirectionsCar, inside, fs); InfoSep()
            InfoItem(Icons.Outlined.WbSunny, outside, fs)
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (withAlt) {
                InfoItem(Icons.Outlined.Terrain, alt, fs)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoItem(Icons.Outlined.DirectionsCar, inside, fs); InfoSep(); InfoItem(Icons.Outlined.WbSunny, outside, fs)
                }
            } else {
                InfoItem(Icons.Outlined.DirectionsCar, inside, fs)
                InfoItem(Icons.Outlined.WbSunny, outside, fs)
            }
        }
    }
}

/** Ալիքային ոճերի վերին տողը՝ փոխանցում | բարձրություն | ջերմաստիճաններ։ */
@Composable
internal fun WaveHeader(gear: String, gearColor: Color, inside: String, outside: String, alt: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(gear, color = gearColor, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        InfoSep()
        InfoItem(Icons.Outlined.Terrain, alt, 22)
        InfoSep()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoItem(Icons.Outlined.DirectionsCar, inside, 16)
            InfoItem(Icons.Outlined.WbSunny, outside, 16)
        }
    }
}

@Composable
internal fun BigNumber(text: String, modifier: Modifier, fs: Int = 30) {
    Text(text, color = Color(0xFFF4F7FB), fontSize = fs.sp, fontWeight = FontWeight.Bold, style = numStyle,
        maxLines = 1, softWrap = false, modifier = modifier)
}

/** 5 վայրկյանը մեկ փոխվող ինդեքս (0/1)՝ «~293 km» ↔ «93%»։ */
@Composable
internal fun rememberCycleIndex(active: Boolean): Int {
    val i by produceState(0, active) {
        if (!active) return@produceState
        while (true) { delay(5_000L); value = 1 - value }
    }
    return i
}

/**
 * Կամարի կենտրոնական մաս։ [gaugeSide]՝ սարքի քառակուսու կողմը (սարքի արտաքին եզրը ≈ 0.495·side),
 * որ կամարի վերին եզրը հավասարվի արագաչափի վերին եզրին, իսկ շառավիղը լինի նրա 340/265-ը։
 */
@Composable
internal fun ArchCenter(
    look: ClusterLook, gaugeSide: Dp, captionH: Dp, soc: Int, rangeText: String,
    gear: String, gearColor: Color, inside: String, outside: String, alt: String,
    road: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val gaugeBoxH = maxHeight - captionH
        val topY = (gaugeBoxH - gaugeSide) / 2 + gaugeSide * 0.005f
        val outer = min(gaugeSide * 0.495f * (340f / 265f), w / 2 - 6.dp)
        val lw = gaugeSide * (if (look.d3) 0.072f else 0.045f)
        val r = outer - lw / 2
        val cy = topY + outer
        val sweep = 160f
        Canvas(Modifier.fillMaxSize()) {
            drawSocArch(Offset(size.width / 2, cy.toPx()), r.toPx(), lw.toPx(), soc.toFloat(), sweep, look.d3)
        }
        road(Modifier.fillMaxSize().padding(top = cy - r * 0.55f))
        Text(gear, color = gearColor, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp))
        val underTop = cy - r + lw / 2
        CornerInfo(look.infoRows, withAlt = look.archLabels == ArchLabels.CYCLE, inside, outside, alt,
            Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 2.dp))
        val a0 = Math.toRadians((270.0 - sweep / 2))
        val endDx = (cos(a0).toFloat() * r.value).dp          // < 0
        val endDy = (sin(a0).toFloat() * r.value).dp
        val sideInset = w / 2 + endDx + lw / 2 + 8.dp
        if (look.archLabels == ArchLabels.ENDS) {
            Row(Modifier.align(Alignment.TopCenter).offset(y = underTop + 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terrain, null, tint = Color(0xFF8FB3D9), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(6.dp))
                Text(alt, color = Color(0xFF7FF0C8), fontSize = 26.sp, fontWeight = FontWeight.Bold, style = numStyle)
            }
            BigNumber(rangeText, Modifier.align(Alignment.TopStart).offset(x = sideInset, y = cy + endDy - 44.dp), 24)
            BigNumber("$soc%", Modifier.align(Alignment.TopEnd).offset(x = -sideInset, y = cy + endDy - 44.dp), 24)
        } else {
            val idx = rememberCycleIndex(true)
            Crossfade(targetState = idx, animationSpec = tween(450), label = "arch-cycle",
                modifier = Modifier.align(Alignment.TopCenter).offset(y = underTop + 20.dp)) { i ->
                BigNumber(if (i == 0) rangeText else "$soc%", Modifier, 32)
            }
        }
    }
}
