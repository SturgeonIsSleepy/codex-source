package cp.player.app

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal val LocalBackPreviewVisibility = compositionLocalOf<((Boolean) -> Unit)?> { null }

/** A cancelled edge gesture never mutates navigation or commits a dialog action. */
@Stable
internal class BackPreview {
    val progress = Animatable(0f)
    var edge by mutableIntStateOf(0)
}

@Composable
internal fun rememberBackPreview() = remember { BackPreview() }

@Composable
internal fun PreviewBackHandler(preview: BackPreview, enabled: Boolean = true, onBack: () -> Unit) {
    val latestBack by rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { event ->
                preview.edge = event.swipeEdge
                preview.progress.snapTo(event.progress)
            }
            if (preview.progress.value > 0f) preview.progress.animateTo(1f, tween(90))
            latestBack()
            preview.progress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            scope.launch { preview.progress.animateTo(0f, tween(180)) }
            throw cancelled
        }
    }
}

internal fun Modifier.backPreview(preview: BackPreview, fullPage: Boolean = true) = graphicsLayer {
    val progress = preview.progress.value.coerceIn(0f, 1f)
    scaleX = 1f - .075f * progress
    scaleY = scaleX
    translationX = if (fullPage) (if (preview.edge == 0) 1 else -1) * size.width * .055f * progress else 0f
    alpha = 1f - .10f * progress
    shape = RoundedCornerShape((if (fullPage) 28f * progress else 28f).dp)
    clip = fullPage && progress > 0f
}

/** Handler lives inside the Dialog window, so the top window owns the gesture. */
@Composable
internal fun TravelDialog(onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    destination: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit) {
    val preview = rememberBackPreview()
    Dialog(onDismissRequest, properties = properties.copyForPredictiveBack()) {
        PreviewBackHandler(preview, onBack = onDismissRequest)
        PreviewWindow(preview)
        Box(Modifier.fillMaxSize()) {
            if (preview.progress.value > 0f) destination?.invoke()
            Box(Modifier.fillMaxSize().backPreview(preview).testTag("predictive-page")) { content() }
        }
    }
}

private fun DialogProperties.copyForPredictiveBack() = DialogProperties(
    dismissOnBackPress = false, dismissOnClickOutside = dismissOnClickOutside,
    securePolicy = securePolicy, usePlatformDefaultWidth = usePlatformDefaultWidth,
    decorFitsSystemWindows = decorFitsSystemWindows
)

@Composable
internal fun TravelAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit, text: (@Composable () -> Unit)? = null) {
    val preview = rememberBackPreview()
    AlertDialog(onDismissRequest, confirmButton,
        modifier = modifier.backPreview(preview, fullPage = false), dismissButton = dismissButton,
        title = { PreviewBackHandler(preview, onBack = onDismissRequest); PreviewWindow(preview); title() }, text = text,
        properties = DialogProperties(dismissOnBackPress = false))
}

/** Context actions use the same predictive sheet behavior as queue and quality. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TravelMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (expanded) ModalBottomSheet(onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), content = content)
    }
}

@Composable
private fun PreviewWindow(preview: BackPreview) {
    val reveal = LocalBackPreviewVisibility.current
    val active = preview.progress.value > 0f
    DisposableEffect(active, reveal) {
        reveal?.invoke(active)
        onDispose { if (active) reveal?.invoke(false) }
    }
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val light = MaterialTheme.colorScheme.background.luminance() >= .3f
    val dim = .20f * (1f - preview.progress.value)
    SideEffect {
        window?.let {
            it.setDimAmount(dim)
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
}
