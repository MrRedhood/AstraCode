package com.mrredhood.astracode

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * AstraCode's original UI icon language. These are drawn locally with Compose Canvas:
 * no icon font, remote asset, or extra dependency is required.
 *
 * Keep the app launcher icon independent from this in-app icon system.
 */
@Composable
fun AstraIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    description: String = name.replace('-', ' ')
) {
    val dark = isSystemInDarkTheme()
    val cyan = if (dark) Color(0xFF63D8F2) else Color(0xFF087FA8)
    val blue = if (dark) Color(0xFF64A8FF) else Color(0xFF245DDB)
    val gold = if (dark) Color(0xFFFFBE62) else Color(0xFFCA6A13)
    val violet = if (dark) Color(0xFFB79AFF) else Color(0xFF7040CE)
    val ink = if (dark) Color(0xFFE6F0FF) else Color(0xFF172544)
    val stroke = Stroke(width = 2.1.dp.toPxSafe(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    Canvas(modifier = modifier.size(size).semantics { contentDescription = description }) {
        val w = this.size.width
        val h = this.size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun line(a: Offset, b: Offset, color: Color, width: Float = stroke.width) =
            drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round)
        fun circle(x: Float, y: Float, r: Float, color: Color, filled: Boolean = false) =
            drawCircle(color, radius = w * r, center = p(x, y), style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else stroke)
        fun path(color: Color, vararg points: Pair<Float, Float>, closed: Boolean = false, filled: Boolean = false) {
            val shape = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(w * point.first, h * point.second)
                    else lineTo(w * point.first, h * point.second)
                }
                if (closed) close()
            }
            drawPath(shape, color, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else stroke)
        }
        when (name.lowercase()) {
            "chat" -> {
                drawCircle(cyan, w * .29f, p(.47f, .45f), style = stroke)
                path(gold, .66f to .20f, .82f to .17f, .77f to .33f)
                path(cyan, .31f to .66f, .23f to .79f, .43f to .72f)
                circle(.47f, .45f, .055f, gold, true)
                circle(.60f, .34f, .025f, violet, true)
            }
            "code" -> {
                path(gold, .40f to .27f, .20f to .50f, .40f to .73f, filled = false)
                path(cyan, .60f to .27f, .80f to .50f, .60f to .73f)
                path(violet, .56f to .20f, .45f to .50f, .37f to .80f)
                circle(.80f, .20f, .045f, gold, true)
            }
            "files" -> {
                drawRoundRect(blue, topLeft = p(.16f, .28f), size = Size(w * .62f, h * .53f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .09f), style = stroke)
                path(gold, .20f to .28f, .20f to .19f, .47f to .19f, .56f to .28f)
                path(cyan, .27f to .40f, .79f to .40f)
                path(violet, .27f to .53f, .68f to .53f)
                path(gold, .27f to .65f, .58f to .65f)
            }
            "git" -> {
                line(p(.27f,.25f), p(.70f,.50f), gold)
                line(p(.27f,.75f), p(.70f,.50f), cyan)
                line(p(.27f,.25f), p(.27f,.75f), violet)
                circle(.27f,.25f,.095f,cyan,true)
                circle(.27f,.75f,.095f,gold,true)
                circle(.70f,.50f,.105f,violet,true)
            }
            "build" -> {
                path(cyan, .50f to .12f, .82f to .30f, .50f to .48f, .18f to .30f, closed = true)
                path(gold, .18f to .30f, .18f to .66f, .50f to .86f, .50f to .48f, closed = true)
                path(violet, .82f to .30f, .82f to .66f, .50f to .86f, .50f to .48f, closed = true)
                circle(.50f,.30f,.035f,ink,true)
            }
            "more" -> {
                path(cyan, .25f to .16f, .40f to .31f, .25f to .46f, .10f to .31f, closed = true, filled = true)
                path(gold, .66f to .16f, .81f to .31f, .66f to .46f, .51f to .31f, closed = true, filled = true)
                path(violet, .25f to .54f, .40f to .69f, .25f to .84f, .10f to .69f, closed = true, filled = true)
                path(blue, .66f to .54f, .81f to .69f, .66f to .84f, .51f to .69f, closed = true, filled = true)
            }
            "terminal" -> {
                drawRoundRect(blue, p(.12f,.18f), Size(w*.76f,h*.64f), androidx.compose.ui.geometry.CornerRadius(w*.10f), style=stroke)
                path(gold, .27f to .38f, .39f to .50f, .27f to .62f)
                line(p(.46f,.63f),p(.68f,.63f),cyan)
                circle(.72f,.30f,.035f,gold,true)
            }
            "preview" -> {
                drawRoundRect(violet, p(.13f,.18f), Size(w*.74f,h*.64f), androidx.compose.ui.geometry.CornerRadius(w*.10f), style=stroke)
                path(gold, .43f to .34f, .64f to .50f, .43f to .66f, closed=true, filled=true)
                path(cyan, .22f to .72f, .78f to .72f)
            }
            "activity" -> {
                path(cyan, .12f to .58f, .30f to .58f, .42f to .28f, .55f to .72f, .68f to .43f, .78f to .43f)
                circle(.42f,.28f,.045f,gold,true)
                circle(.55f,.72f,.045f,violet,true)
            }
            "settings" -> {
                circle(.50f,.50f,.28f,cyan)
                circle(.50f,.50f,.11f,gold)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4.0
                    val x1 = .50f + cos(a).toFloat() * .29f
                    val y1 = .50f + sin(a).toFloat() * .29f
                    val x2 = .50f + cos(a).toFloat() * .39f
                    val y2 = .50f + sin(a).toFloat() * .39f
                    line(p(x1,y1),p(x2,y2),violet)
                }
            }
            "ai", "assistant" -> {
                path(gold, .50f to .12f, .59f to .40f, .86f to .50f, .59f to .60f, .50f to .88f, .41f to .60f, .14f to .50f, .41f to .40f, closed=true, filled=true)
                circle(.76f,.22f,.045f,cyan,true)
                circle(.24f,.75f,.035f,violet,true)
            }
            "agents" -> {
                drawRoundRect(cyan,p(.20f,.30f),Size(w*.60f,h*.45f),androidx.compose.ui.geometry.CornerRadius(w*.13f),style=stroke)
                line(p(.50f,.15f),p(.50f,.30f),gold)
                circle(.50f,.12f,.045f,gold,true)
                circle(.38f,.48f,.035f,violet,true); circle(.62f,.48f,.035f,violet,true)
                path(blue,.37f to .61f,.50f to .67f,.63f to .61f)
                line(p(.32f,.75f),p(.25f,.86f),gold); line(p(.68f,.75f),p(.75f,.86f),gold)
            }
            "search" -> { circle(.43f,.42f,.25f,cyan); line(p(.61f,.61f),p(.83f,.83f),gold) }
            "run" -> { path(gold,.36f to .20f,.73f to .50f,.36f to .80f,closed=true,filled=true); path(cyan,.22f to .12f,.22f to .88f) }
            "new-file" -> {
                path(cyan,.28f to .13f,.60f to .13f,.75f to .28f,.75f to .68f,.28f to .68f,closed=true)
                line(p(.60f,.13f),p(.60f,.29f),gold); line(p(.60f,.29f),p(.75f,.29f),gold)
                line(p(.52f,.48f),p(.52f,.84f),violet); line(p(.34f,.66f),p(.70f,.66f),violet)
            }
            "new-folder" -> {
                path(gold,.12f to .29f,.40f to .29f,.48f to .39f,.86f to .39f,.82f to .76f,.14f to .76f,closed=true)
                line(p(.50f,.47f),p(.50f,.68f),cyan); line(p(.40f,.58f),p(.60f,.58f),cyan)
            }
            "rename" -> {
                path(gold,.20f to .72f,.28f to .48f,.65f to .20f,.79f to .34f,.42f to .71f,closed=true)
                line(p(.17f,.81f),p(.41f,.78f),cyan); line(p(.65f,.20f),p(.79f,.34f),violet)
            }
            "delete" -> {
                path(gold,.30f to .30f,.70f to .30f,.66f to .80f,.34f to .80f,closed=true)
                line(p(.24f,.23f),p(.76f,.23f),cyan); line(p(.40f,.17f),p(.60f,.17f),violet)
                line(p(.44f,.40f),p(.44f,.68f),ink); line(p(.56f,.40f),p(.56f,.68f),ink)
            }
            "pull" -> { line(p(.50f,.16f),p(.50f,.69f),cyan); path(gold,.28f to .50f,.50f to .72f,.72f to .50f); line(p(.22f,.82f),p(.78f,.82f),violet) }
            "push" -> { line(p(.50f,.84f),p(.50f,.31f),gold); path(cyan,.28f to .50f,.50f to .28f,.72f to .50f); line(p(.22f,.18f),p(.78f,.18f),violet) }
            "commit" -> { line(p(.12f,.50f),p(.36f,.50f),cyan); line(p(.64f,.50f),p(.88f,.50f),gold); circle(.50f,.50f,.15f,violet) }
            "branch" -> { line(p(.30f,.20f),p(.30f,.80f),cyan); path(gold,.30f to .38f,.56f to .38f,.70f to .24f); path(violet,.30f to .62f,.56f to .62f,.70f to .76f); circle(.30f,.20f,.07f,gold,true); circle(.30f,.80f,.07f,violet,true); circle(.70f,.24f,.07f,cyan,true) }
            "cloud-build" -> {
                circle(.36f,.53f,.19f,cyan); circle(.57f,.43f,.24f,violet); circle(.72f,.57f,.15f,gold)
                line(p(.20f,.67f),p(.79f,.67f),cyan)
                path(gold,.43f to .49f,.54f to .60f,.66f to .43f)
            }
            "test" -> {
                path(cyan,.38f to .15f,.62f to .15f,.58f to .37f,.75f to .72f,.66f to .83f,.34f to .83f,.25f to .72f,.42f to .37f,closed=true)
                path(gold,.36f to .59f,.46f to .69f,.66f to .48f)
            }
            "release" -> {
                path(cyan,.20f to .78f,.28f to .50f,.62f to .20f,.78f to .17f,.75f to .50f,.48f to .72f,closed=true)
                circle(.60f,.35f,.07f,gold)
                path(violet,.20f to .78f,.12f to .86f,.35f to .81f)
            }
            "diff" -> { path(cyan,.43f to .25f,.24f to .50f,.43f to .75f); path(gold,.57f to .25f,.76f to .50f,.57f to .75f); line(p(.49f,.20f),p(.49f,.80f),violet) }
            "help" -> {
                circle(.50f,.50f,.36f,cyan)
                path(gold,.36f to .38f,.40f to .28f,.57f to .30f,.65f to .40f,.62f to .49f,.50f to .58f,.50f to .65f)
                circle(.50f,.76f,.035f,violet,true)
            }
            "diagnostics" -> {
                circle(.50f,.50f,.34f,cyan)
                path(gold,.20f to .52f,.36f to .52f,.44f to .34f,.54f to .66f,.63f to .45f,.79f to .45f)
                circle(.73f,.25f,.04f,violet,true)
            }
            else -> {
                circle(.50f,.50f,.30f,cyan)
                circle(.50f,.50f,.12f,gold)
                path(violet,.50f to .08f,.57f to .20f,.50f to .32f,.43f to .20f,closed=true)
            }
        }
    }
}

private fun Dp.toPxSafe(): Float = value * 1.0f
