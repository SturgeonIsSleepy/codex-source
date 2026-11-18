package cp.player.app

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
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

internal val LocalBackPreviewVisibility = compositionLocalOf<((Any, Boolean) -> Unit)?> { null }
internal val LocalDialogDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

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
    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled && !imeVisible) { events ->
        try {
            events.collect { event ->
                preview.edge = event.swipeEdge
                preview.progress.snapTo(event.progress)
            }
            // Commit immediately: gesture progress has already supplied the exit preview.
            latestBack()
            preview.progress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            scope.launch { preview.progress.animateTo(0f, AppMotion.fast()) }
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
    animateDismiss: Boolean = destination == null,
    revealRoot: Boolean = destination == null,
    fixedContent: @Composable BoxScope.() -> Unit = {},
    previewState: BackPreview? = null,
    content: @Composable () -> Unit) {
    val preview = previewState ?: rememberBackPreview()
    val entrance = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val exitEnabled by rememberUpdatedState(animateDismiss)
    fun dismiss() {
        if (closing) return
        if (!exitEnabled) { latestDismiss(); return }
        closing = true
        scope.launch { entrance.animateTo(0f, AppMotion.fast()); latestDismiss() }
    }
    Dialog(::dismiss, properties = properties.copyForPredictiveBack()) {
        PreviewBackHandler(preview, enabled = !closing) {
            if (preview.progress.value > 0f) latestDismiss() else dismiss()
        }
        PreviewWindow(preview, entrance.value, closing, revealRoot)
        LaunchedEffect(Unit) { entrance.animateTo(1f, AppMotion.page()) }
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = entrance.value
            translationY = (1 - entrance.value) * 12.dp.toPx()
        }.then(if (closing) Modifier.pointerInput(Unit) {
            awaitEachGesture {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        } else Modifier)) {
            if (preview.progress.value > 0f) destination?.invoke()
            CompositionLocalProvider(LocalDialogDismiss provides ::dismiss) {
                Box(Modifier.fillMaxSize().backPreview(preview).testTag("predictive-page")) { content() }
            }
        }
        fixedContent()
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

/** A back gesture dismisses a panel in one step rather than first collapsing it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TravelSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true, containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest, modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = gesturesEnabled, containerColor = containerColor, content = content)
}

@Composable
internal fun TravelMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (expanded) TravelSheet(onDismissRequest) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), content = content)
    }
}

@Composable
private fun PreviewWindow(preview: BackPreview, visibility: Float = 1f, exiting: Boolean = false, revealRoot: Boolean = true) {
    val reveal = LocalBackPreviewVisibility.current
    val active = revealRoot && (preview.progress.value > 0f || exiting)
    DisposableEffect(active, reveal) {
        reveal?.invoke(preview, active)
        onDispose { if (active) reveal?.invoke(preview, false) }
    }
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val light = MaterialTheme.colorScheme.background.luminance() >= .3f
    val dim = .20f * visibility * (1f - preview.progress.value)
    SideEffect {
        window?.let {
            it.setWindowAnimations(0)
            it.setDimAmount(dim)
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
}
