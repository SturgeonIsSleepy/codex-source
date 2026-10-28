package cp.player.ui.glass

import android.os.Build
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Record the content once. The dock reuses its GPU display list, never a screenshot. */
internal fun Modifier.recordGlassScene(scene: GraphicsLayer): Modifier = drawWithContent {
    scene.record { this@drawWithContent.drawContent() }
    drawLayer(scene)
}

/** Native port of the saved SDF/Snell material. Labels stay outside the optical layer. */
@Composable
internal fun LiquidGlass(scene: GraphicsLayer, modifier: Modifier = Modifier, dark: Boolean = false,
                         content: @Composable BoxScope.() -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var dimensions by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = LocalDensity.current.density
    val effect = if (Build.VERSION.SDK_INT >= 33) remember(dimensions, density, dark) {
        if (dimensions.width > 0) createOptics(dimensions.width.toFloat(), dimensions.height.toFloat(), density, dark) else null
    } else null
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot(); dimensions = it.size }
        .clip(RoundedCornerShape(100.dp))) {
        if (effect != null) Box(Modifier.matchParentSize().graphicsLayer { renderEffect = effect }
            .drawWithContent { translate(-origin.x, -origin.y) { drawLayer(scene) } })
        else Box(Modifier.matchParentSize().background(if (dark) Color(0xE6222226) else Color(0xEDF7F7FA)))
        content()
    }
}

@RequiresApi(33)
private fun createOptics(width: Float, height: Float, density: Float, dark: Boolean): androidx.compose.ui.graphics.RenderEffect {
    val shader = RuntimeShader(OPTICS)
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("bevel", minOf(34f * density, height / 2 - .5f))
    shader.setFloatUniform("pixel", density)
    shader.setFloatUniform("dark", if (dark) 1f else 0f)
    val refraction = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "scene")
    return android.graphics.RenderEffect.createChainEffect(refraction,
        android.graphics.RenderEffect.createBlurEffect(3f * density, 3f * density, Shader.TileMode.CLAMP)).asComposeRenderEffect()
}

// Adapted from the user's liquid-glass-model optics.js; see THIRD_PARTY_NOTICES.md.
private const val OPTICS = """
uniform shader scene;
uniform float2 size;
uniform float bevel;
uniform float pixel;
uniform float dark;
float sdf(float2 p) {
    float r = size.y * .5;
    float2 q = abs(p) - size * .5 + r;
    return length(max(q, float2(0))) + min(max(q.x, q.y), 0.) - r;
}
half4 main(float2 xy) {
    float2 p = xy - size * .5;
    float d = max(-sdf(p), 0.);
    float e = .2 * pixel;
    float2 n = float2(sdf(p + float2(e,0)) - sdf(p - float2(e,0)), sdf(p + float2(0,e)) - sdf(p - float2(0,e)));
    n /= max(length(n), .0001);
    float t = min(1., d / max(bevel, 1.));
    float u = 1. - max(t, .015);
    float a = max(1. - pow(u, 4.), .0001);
    float profile = pow(a, .25);
    float incidence = atan(pow(u, 3.) / pow(a, .75));
    float transmitted = asin(sin(incidence) / 1.46);
    float bend = tan(incidence - transmitted) * bevel * profile * 3.2 * smoothstep(0., 1.1 * pixel, d);
    float2 sampleAt = xy - n * bend - p * (1. - 1./1.025);
    half4 color = scene.eval(clamp(sampleAt, float2(0), size));
    float dotLight = dot(n, float2(-.70710678));
    float rim = exp(-pow((d / pixel - .8) / .65, 2.));
    float inner = exp(-pow((d / pixel - 2.1) / 1.25, 2.));
    float fresnel = .035 + .965 * pow(1. - cos(incidence), 5.);
    float reflection = pow(abs(dotLight), 5.) * pow(sin(incidence), 5.);
    float shine = min(.8, (.09 + .46 * max(0.,dotLight) + .18 * max(0.,-dotLight)) * rim + inner * .07 + reflection * fresnel * .28);
    float3 base = mix(float3(color.rgb), float3(dark > .5 ? .10 : 1.), dark > .5 ? .25 : .11);
    return half4(mix(base, float3(1), shine), 1.);
}
"""
