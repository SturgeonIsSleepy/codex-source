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
    private long position, lastFrame;
    private float scale, left, top;

    private static final class Assets {
        final Bitmap sky;
        final CharacterRig rig;
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
    public void setMotionEnabled(boolean enabled) {
        if (motionEnabled == enabled) return;
        motionEnabled = enabled;
        lastFrame = 0;
        invalidate();
    }
    public void setPlayback(boolean isPlaying, boolean requested, long positionMs) {
        playing = isPlaying;
        playRequested = requested;
        position = positionMs;
        invalidate();
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
        lastFrame = 0;
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (motionEnabled) motion.step(lastFrame == 0 ? 0 : (now - lastFrame) / 1000f,
            playing, null, (int)Math.min(Integer.MAX_VALUE, position), cp.player.engine.MusicPulse.sample(), playRequested);
        else motion.settle(playRequested);
        lastFrame = now;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        canvas.drawBitmap(assets.sky, null, artBounds, paint);
        assets.rig.draw(canvas, paint, motion);
        canvas.restore();
        if (visible && motionEnabled && (playing || motion.transitioning(playRequested))) postInvalidateDelayed(33);
    }
}
