package cp.player.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

private data class WordGeometry(val word: TimedWord, val boxes: List<Pair<Rect, Boolean>>, val width: Float)

/** A single text layout is reused; only the active line's clipped ink redraws at the lyric clock. */
@Composable
internal fun KaraokeWords(line: TimedLine, clock: State<Long>, active: Boolean, ink: Color,
    modifier: Modifier = Modifier) {
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val geometry = remember(layout, line.words) {
        val result = layout ?: return@remember emptyList<WordGeometry>()
        var offset = 0
        line.words.map { word ->
            val start = offset
            offset = (offset + word.text.length).coerceAtMost(line.text.length)
            val boxes = mutableListOf<Pair<Rect, Boolean>>()
            var character = start
            while (character < offset) {
                val box = result.getBoundingBox(character)
                val rtl = result.getBidiRunDirection(character) == ResolvedTextDirection.Rtl
                if (box.width > 0 && boxes.lastOrNull()?.first != box) boxes += box to rtl
                character += Character.charCount(Character.codePointAt(line.text, character))
            }
            WordGeometry(word, boxes, boxes.sumOf { it.first.width.toDouble() }.toFloat())
        }
    }
    val precise = line.words.any { it.timed }
    val fontSize = if (line.credit) 16.sp else if (line.background) 22.sp else 33.sp
    Box(modifier) {
        Text(line.text, color = ink.copy(alpha = if (active && precise) .28f else if (active) 1f else .48f),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = fontSize, fontWeight = FontWeight.SemiBold, textMotion = TextMotion.Animated,
                lineHeight = if (line.credit) 24.sp else if (line.background) 31.sp else 46.sp),
            textAlign = if (line.right) TextAlign.End else TextAlign.Start,
            modifier = Modifier.fillMaxWidth().testTag("lyric-text"), onTextLayout = { layout = it })
        if (active && precise) Canvas(Modifier.matchParentSize().testTag("karaoke-highlight")) {
            val result = layout ?: return@Canvas
            val position = clock.value
            val path = Path()
            geometry.forEach { word ->
                var remaining = word.width * LyricParser.progress(word.word, position)
                word.boxes.forEach { (box, rtl) ->
                    val amount = remaining.coerceIn(0f, box.width)
                    if (amount > 0) path.addRect(Rect(if (rtl) box.right - amount else box.left, box.top,
                        if (rtl) box.right else box.left + amount, box.bottom))
                    remaining -= box.width
                }
            }
            clipPath(path) { drawText(result, color = ink) }
        }
    }
}

@Composable
internal fun InterludeDots(clock: State<Long>, start: Long, end: Long?, active: Boolean, ink: Color,
    modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 76.dp, height = 44.dp).semantics { contentDescription = "间奏" }.testTag("interlude-dots")) {
        val time = if (active) (clock.value - start).coerceAtLeast(0).toDouble() else 0.0
        val complete = end?.takeIf { it > start }?.let { ((clock.value - start).toDouble() / (it - start)).coerceIn(0.0, 1.0) } ?: 0.0
        repeat(3) { index ->
            val wave = if (active) sin(time / 1500.0 * PI * 2 - index * .75).toFloat() else 0f
            val filled = ((complete * 3 - index).coerceIn(0.0, 1.0)).toFloat()
            drawCircle(ink.copy(alpha = if (active) .48f + .42f * filled else .45f),
                radius = (5f + if (active) (wave + 1) * .6f else 0f).dp.toPx(),
                center = androidx.compose.ui.geometry.Offset((13 + index * 24).dp.toPx(), center.y + wave * 3.dp.toPx()))
        }
    }
}

@Composable
internal fun LyricVerse(line: TimedLine, clock: State<Long>, active: Boolean, duet: Boolean,
    showTranslation: Boolean, showRomanized: Boolean, ink: Color, modifier: Modifier = Modifier) {
    val emphasis by animateFloatAsState(if (active) 1f else .96f, tween(220), label = "verse-emphasis")
    Box(modifier.fillMaxWidth().graphicsLayer {
        scaleX = emphasis; scaleY = emphasis
        transformOrigin = TransformOrigin(if (line.right) 1f else 0f, .5f)
    }, contentAlignment = if (line.right) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth(if (duet) .86f else 1f)
            .testTag(if (line.right) "lyric-response" else "lyric-lead"),
            horizontalAlignment = if (line.right) Alignment.End else Alignment.Start) {
            if (line.instrumental || line.text.isBlank()) InterludeDots(clock, line.time, line.end, active, ink)
            else KaraokeWords(line, clock, active, ink, Modifier.fillMaxWidth())
            if (showTranslation && line.translation.isNotBlank()) Text(line.translation,
                color = ink.copy(alpha = if (active) .86f else .45f), fontSize = 18.sp, lineHeight = 28.sp,
                textAlign = if (line.right) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp))
            if (showRomanized && line.romanized.isNotBlank()) Text(line.romanized,
                color = ink.copy(alpha = .65f), fontSize = 16.sp, lineHeight = 24.sp,
                textAlign = if (line.right) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        }
    }
}
