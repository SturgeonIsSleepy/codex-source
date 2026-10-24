package cp.player.engine;

import android.os.SystemClock;

/** Read-only amplitude shared by the audio engines and artwork. No microphone permission. */
public final class MusicPulse {
    private static volatile float rms;
    private static volatile long updated;
    private MusicPulse() {}
    public static void publish(float value) {
        rms=Float.isFinite(value)?Math.max(0,value):0;
        updated=SystemClock.elapsedRealtime();
    }
    public static float sample() {
        if(updated==0 || SystemClock.elapsedRealtime()-updated>750)return Float.NaN;
        return Math.min(1,(float)Math.sqrt(rms)*2.3f);
    }
}
