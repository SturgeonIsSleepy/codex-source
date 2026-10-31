package cp.player.engine;

import android.os.SystemClock;

/** Read-only amplitude shared by the audio engines and artwork. No microphone permission. */
public final class MusicPulse {
    private static volatile float rms;
    private static volatile long updated;
    private static final PulseDemand demand = new PulseDemand();
    private MusicPulse() {}
    public static void publish(float value) {
        rms=Float.isFinite(value)?Math.max(0,value):0;
        updated=SystemClock.elapsedRealtime();
    }
    public static float sample() {
        long now = SystemClock.elapsedRealtime();
        demand.request(now);
        if(updated==0 || now-updated>750)return Float.NaN;
        return Math.min(1,(float)Math.sqrt(rms)*2.3f);
    }
    public static boolean shouldMeasure() { return demand.tryMeasure(SystemClock.elapsedRealtime()); }
}
