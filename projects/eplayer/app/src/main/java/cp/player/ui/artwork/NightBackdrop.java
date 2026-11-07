package cp.player.ui.artwork;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Prepare only the sky once; the original portrait and animation layers are never tinted. */
final class NightBackdrop {
    static Bitmap create(Bitmap source) {
        int width = source.getWidth(), height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        boolean[] exterior = new boolean[pixels.length];
        int[] queue = new int[pixels.length];
        int tail = 0;
        for (int x = 0; x < width; x++) {
            tail = offer(x, pixels, exterior, queue, tail);
            tail = offer((height - 1) * width + x, pixels, exterior, queue, tail);
        }
        for (int y = 1; y < height - 1; y++) {
            tail = offer(y * width, pixels, exterior, queue, tail);
            tail = offer(y * width + width - 1, pixels, exterior, queue, tail);
        }
        for (int head = 0; head < tail; head++) {
            int i = queue[head], x = i % width, y = i / width;
            if (x > 0) tail = offer(i - 1, pixels, exterior, queue, tail);
            if (x + 1 < width) tail = offer(i + 1, pixels, exterior, queue, tail);
            if (y > 0) tail = offer(i - width, pixels, exterior, queue, tail);
            if (y + 1 < height) tail = offer(i + width, pixels, exterior, queue, tail);
        }
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            pixels[i] = exterior[i] ? Color.TRANSPARENT : Color.argb(Color.alpha(color),
                Math.round(Color.red(color) * .35f), Math.round(Color.green(color) * .43f), Math.round(Color.blue(color) * .60f));
        }
        Bitmap result = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        result.setDensity(Bitmap.DENSITY_NONE);
        return result;
    }
    private static int offer(int i, int[] pixels, boolean[] exterior, int[] queue, int tail) {
        if (exterior[i]) return tail;
        int color = pixels[i], min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
        int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
        if (Color.alpha(color) == 0 || min >= 236 && max - min <= 28) {
            exterior[i] = true;
            queue[tail++] = i;
        }
        return tail;
    }
    private NightBackdrop() {}
}
