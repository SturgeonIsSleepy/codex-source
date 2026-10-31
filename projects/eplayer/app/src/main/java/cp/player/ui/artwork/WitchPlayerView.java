package cp.player.ui.artwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;

/** Draws one repaired character; Compose owns metadata, transport and accessibility. */
public final class WitchPlayerView extends View {
    private static volatile Assets cachedAssets;
    private final Assets assets;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final CharacterMotion motion = new CharacterMotion();
    private final RectF artBounds = new RectF(0, 0, 865, 1280);
    private boolean playing, playRequested, visible = true, motionEnabled = true;
    private boolean artworkActive = true;
    private boolean hierarchyVisible = true, frameScheduled;
    private long position, lastFrame;
    private boolean night, coverBackdrop;
    private int backdrop = Color.WHITE, assetGeneration;
    private float scale, left, top;
    private final Runnable nextFrame = () -> {
        frameScheduled = false;
        if (canAnimate()) invalidate();
    };
    private boolean canAnimate() {
        return visible && hierarchyVisible && artworkActive && motionEnabled && isAttachedToWindow()
            && (playing || motion.transitioning(playRequested));
    }
    private void cancelFrame() {
        removeCallbacks(nextFrame);
        frameScheduled = false;
    }

    private static final class Assets {
        final Bitmap sky;
        final CharacterRig rig;
        volatile Bitmap nightSky;
        synchronized void prepareNight() { if (nightSky == null) nightSky = NightBackdrop.create(sky); }
        Assets(Context context) {
            sky = load(context, "player-sky.png");
            Bitmap portrait = CharacterArtwork.prepare(load(context, "elaina-layer.png"), load(context, "skin-restoration.png"));
            rig = new CharacterRig(portrait);
        }
    }

    private static synchronized Assets assets(Context context) {
        if (cachedAssets == null) cachedAssets = new Assets(context.getApplicationContext());
        return cachedAssets;
    }
    public static void prepareAssets(Context context) { assets(context); }
    public static void prepareAssets(Context context, boolean night) { if (night) assets(context).prepareNight(); else assets(context); }
    public static synchronized void clearAssetCache() { cachedAssets = null; }
    private static Bitmap load(Context context, String name) {
        try (InputStream in = context.getAssets().open(name)) {
            Bitmap result = BitmapFactory.decodeStream(in);
            if (result == null) throw new IllegalStateException("Invalid illustration: " + name);
            result.setDensity(Bitmap.DENSITY_NONE);
            return result;
        } catch (IOException error) { throw new IllegalStateException("Missing illustration: " + name, error); }
    }

    public WitchPlayerView(Context context) {
        super(context);
        assets = assets(context);
        setBackgroundColor(Color.WHITE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setAppearance(boolean night, int background, int generation) {
        if (this.night == night && backdrop == background && assetGeneration == generation) return;
        this.night = night; backdrop = background; assetGeneration = generation;
        setBackgroundColor(coverBackdrop ? Color.TRANSPARENT : background);
        invalidate();
    }
    /** Keep the original character over Compose's song-cover background. */
    public void setCoverBackdropEnabled(boolean enabled) {
        if (coverBackdrop == enabled) return;
        coverBackdrop = enabled;
        setBackgroundColor(enabled ? Color.TRANSPARENT : backdrop);
        invalidate();
    }
    public void setArtworkActive(boolean active) {
        if (artworkActive == active) return;
        artworkActive = active; lastFrame = 0;
        cancelFrame();
        if (active) invalidate();
    }
    public void setMotionEnabled(boolean enabled) {
        if (motionEnabled == enabled) return;
        motionEnabled = enabled;
        cancelFrame();
        lastFrame = 0;
        invalidate();
    }
    public void setPlayback(boolean isPlaying, boolean requested, long positionMs) {
        boolean changed = playing != isPlaying || playRequested != requested;
        playing = isPlaying;
        playRequested = requested;
        position = positionMs;
        // The existing animation clock already reads the latest position. Progress
        // updates must not repaint a covered, paused or statically rendered portrait.
        if (changed && visible && hierarchyVisible && artworkActive) invalidate();
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        scale = Math.min(width / 865f, height / 1280f);
        left = (width - 865 * scale) / 2;
        top = (height - 1280 * scale) / 2;
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        visible = visibility == VISIBLE;
        if (!visible) cancelFrame();
        lastFrame = 0;
        invalidate();
    }
    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        hierarchyVisible = isVisible;
        lastFrame = 0;
        if (isVisible) invalidate(); else cancelFrame();
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrame = 0;
        invalidate();
    }
    @Override protected void onDetachedFromWindow() {
        cancelFrame();
        lastFrame = 0;
        super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (motionEnabled && visible && hierarchyVisible && artworkActive) motion.step(lastFrame == 0 ? 0 : (now - lastFrame) / 1000f,
            playing, null, (int)Math.min(Integer.MAX_VALUE, position),
            isAttachedToWindow() ? cp.player.engine.MusicPulse.sample() : Float.NaN, playRequested);
        else if (!motionEnabled) motion.settle(playRequested);
        lastFrame = now;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        Bitmap sky = night ? assets.nightSky : assets.sky;
        if (!coverBackdrop && sky != null) canvas.drawBitmap(sky, null, artBounds, paint);
        assets.rig.draw(canvas, paint, motion);
        canvas.restore();
        if (canAnimate() && !frameScheduled) {
            frameScheduled = true;
            postDelayed(nextFrame, 33); // Preserve the existing visible-motion cadence.
        }
    }
}
