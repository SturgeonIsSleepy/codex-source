package app.eplayer.qa;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.UiAutomation;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.test.InstrumentationTestCase;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Cross-app platform-only checks: never loads ePlayer/Kotlin/Compose implementation classes. */
public class R8MusicCheck extends InstrumentationTestCase {
    private static final String TARGET = "app.eplayer.music", TITLE = "灰色のサーガ";
    private UiAutomation ui;
    private File evidence;
    private Uri fixture;
    private MediaController controller;
    private interface Condition { boolean ready() throws Exception; }

    @Override protected void setUp() throws Exception {
        super.setUp();
        ui = getInstrumentation().getUiAutomation();
        ui.adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);
        evidence = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "r8-check");
        assertTrue(evidence.isDirectory() || evidence.mkdirs());
    }
    @Override protected void tearDown() throws Exception {
        try {
            if (controller != null) controller.getTransportControls().pause();
            if (fixture != null) getInstrumentation().getTargetContext().getContentResolver().delete(fixture, null, null);
            AccessibilityNodeInfo collapse = find("收起播放器", true);
            if (collapse != null) click(collapse);
        } finally { ui.dropShellPermissionIdentity(); super.tearDown(); }
    }
    public void testNativeAudioLifecycle() throws Exception { exercise("Rust 音频", "native"); }
    public void testSystemAudioLifecycle() throws Exception { exercise("系统音频", "system"); }

    private void exercise(String engine, String name) throws Exception {
        try {
            shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n app.eplayer.music/cp.player.MainActivity");
            waitFor("home", () -> find("设置", true) != null);
            if (find("播放与声音", false) == null && find(engine, false) == null) click(find("设置", true));
            waitFor("settings root or engine", () -> find("播放与声音", false) != null || find(engine, false) != null);
            if (find("播放与声音", false) != null) click(find("播放与声音", false));
            waitFor("settings engine", () -> find(engine, false) != null);
            click(find(engine, false));
            waitFor("selected engine", () -> selected(engine));
            click(find("完成", false));
            waitFor("home after settings", () -> find("设置", true) != null);
            if (find("曲库", true) != null) click(find("曲库", true));
            // The redesigned library enters Playlists; the fixed import slots live under Songs.
            if (find(TITLE, false) == null && find("歌曲", false) != null) click(find("歌曲", false));
            waitFor("library import entry", () -> find(TITLE, false) != null);
            if (count("导入") > 1) {
                String filename = "ePlayer-QA-" + UUID.randomUUID() + ".wav";
                createFixture(filename);
                click(find("导入", false));
                waitFor("document picker", () -> rootPackage().contains("documentsui"));
                // Recent documents normally includes the new WAV. Open Downloads if needed.
                SystemClock.sleep(500);
                if (find(filename, false) == null) {
                    AccessibilityNodeInfo roots = find("Show roots", true);
                    if (roots == null) roots = find("Show navigation drawer", true);
                    if (roots != null) {
                        click(roots);
                        waitFor("Downloads root", () -> find("Downloads", false) != null);
                        click(find("Downloads", false));
                    }
                }
                waitFor("synthetic WAV in document picker", () -> find(filename, false) != null);
                click(find(filename, false));
                waitFor("import completed", () -> TARGET.equals(rootPackage()) && count("导入") == 1);
                capture(name + "-imported");
            }
            click(find(TITLE, false));
            MediaSessionManager sessions = getInstrumentation().getTargetContext().getSystemService(MediaSessionManager.class);
            waitFor("target media session", () -> {
                for (MediaController candidate : sessions.getActiveSessions(null))
                    if (TARGET.equals(candidate.getPackageName())) { controller = candidate; return true; }
                return false;
            });
            AudioManager audio = getInstrumentation().getTargetContext().getSystemService(AudioManager.class);
            waitFor("real output and 30-second duration", () -> playing() && duration() >= 29000 && duration() <= 31000 && audio.isMusicActive());
            waitFor("artwork loaded", () -> !hasProgress(ui.getRootInActiveWindow()));
            ui.waitForIdle(100, 3000);
            SystemClock.sleep(450);
            capture(name + "-playing");
            // Android screenrecord is silent; the WAV is generated only for this test.
            shell("timeout 8s screenrecord --time-limit 4 --bit-rate 2000000 /data/local/tmp/eplayer-" + name + "-motion.mp4");
            controller.getTransportControls().pause();
            waitFor("pause", () -> state() == PlaybackState.STATE_PAUSED);
            long paused = position(); SystemClock.sleep(1000);
            assertTrue("Paused position changed: " + paused + " -> " + position(), Math.abs(position() - paused) < 500);
            capture(name + "-paused");
            controller.getTransportControls().seekTo(12000);
            waitFor("paused seek to twelve seconds", () -> state() == PlaybackState.STATE_PAUSED && Math.abs(position() - 12000) < 600);
            controller.getTransportControls().play();
            waitFor("resume", () -> playing() && audio.isMusicActive());
            long before = position();
            assertTrue(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));
            SystemClock.sleep(2200);
            assertTrue("Background session stopped", playing() && audio.isMusicActive());
            assertTrue("Background position did not advance", position() > before + 1200);
            assertTrue(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS));
            waitFor("actual system notification title", () -> "com.android.systemui".equals(rootPackage()) && find(TITLE, false) != null);
            capture(name + "-notification");
            click(find(TITLE, false));
            waitFor("notification returned expanded player", () -> TARGET.equals(rootPackage()) && find("收起播放器", true) != null);
            controller.getTransportControls().pause();
            waitFor("pause after notification return", () -> state() == PlaybackState.STATE_PAUSED);
            SystemClock.sleep(450); // Let the continuous eyelid transition settle before visual evidence.
            capture(name + "-returned-paused");
            try (FileWriter log = new FileWriter(new File(evidence, name + ".txt"))) {
                log.write("engine selected in UI=" + engine + "\nduration=" + duration() + "\npaused seek=12000\nreturned position=" + position() + "\n");
            }
        } catch (Throwable failure) {
            capture(name + "-failed"); dump(name + "-failed");
            if (failure instanceof Exception) throw (Exception)failure;
            if (failure instanceof Error) throw (Error)failure;
            throw new AssertionError(failure);
        }
    }
    private void createFixture(String filename) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        fixture = getInstrumentation().getTargetContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        assertNotNull(fixture);
        try (OutputStream out = new BufferedOutputStream(getInstrumentation().getTargetContext().getContentResolver().openOutputStream(fixture))) {
            ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            int bytes = 44100 * 30 * 2;
            header.put("RIFF".getBytes("US-ASCII")).putInt(36 + bytes).put("WAVEfmt ".getBytes("US-ASCII"))
                .putInt(16).putShort((short)1).putShort((short)1).putInt(44100).putInt(88200).putShort((short)2).putShort((short)16)
                .put("data".getBytes("US-ASCII")).putInt(bytes);
            out.write(header.array());
            ByteBuffer period = ByteBuffer.allocate(2205 * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 2205; i++) period.putShort((short)(Math.sin(i * 2 * Math.PI * 440 / 44100) * 3000));
            for (int i = 0; i < 600; i++) out.write(period.array());
        }
        values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        getInstrumentation().getTargetContext().getContentResolver().update(fixture, values, null, null);
    }
    private void waitFor(String description, Condition condition) throws Exception {
        long end = SystemClock.elapsedRealtime() + 20000;
        while (SystemClock.elapsedRealtime() < end) { if (condition.ready()) return; SystemClock.sleep(100); }
        fail("Timed out: " + description + "; root=" + rootPackage() + "; playback=" + state() + "; position=" + position() + "; duration=" + duration());
    }
    private int state() { PlaybackState s = controller == null ? null : controller.getPlaybackState(); return s == null ? -1 : s.getState(); }
    private boolean playing() { return state() == PlaybackState.STATE_PLAYING; }
    private long duration() { MediaMetadata m = controller == null ? null : controller.getMetadata(); return m == null ? -1 : m.getLong(MediaMetadata.METADATA_KEY_DURATION); }
    private long position() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        if (s == null) return -1;
        return s.getPosition() + (s.getState() == PlaybackState.STATE_PLAYING ? (long)((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed()) : 0);
    }
    private String rootPackage() { AccessibilityNodeInfo r = ui.getRootInActiveWindow(); return r == null ? "" : String.valueOf(r.getPackageName()); }
    private AccessibilityNodeInfo find(String text, boolean description) {
        AccessibilityNodeInfo r = ui.getRootInActiveWindow(); if (r == null) return null;
        return findNode(r, text, description);
    }
    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo n, String value, boolean description) {
        CharSequence text = description ? n.getContentDescription() : n.getText();
        if (n.isVisibleToUser() && text != null && value.equals(text.toString())) return n;
        for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo child = n.getChild(i); if (child != null) { AccessibilityNodeInfo found = findNode(child, value, description); if (found != null) return found; } }
        return null;
    }
    private int count(String text) { AccessibilityNodeInfo r = ui.getRootInActiveWindow(); return r == null ? 0 : countNode(r, text); }
    private int countNode(AccessibilityNodeInfo n, String text) {
        int count = n.isVisibleToUser() && text.contentEquals(n.getText() == null ? "" : n.getText()) ? 1 : 0;
        for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo child = n.getChild(i); if (child != null) count += countNode(child, text); }
        return count;
    }
    private boolean selected(String label) {
        AccessibilityNodeInfo text = find(label, false);
        if (text == null) return false;
        Rect bounds = new Rect(); text.getBoundsInScreen(bounds);
        AccessibilityNodeInfo[] nearest = new AccessibilityNodeInfo[1];
        int[] distance = new int[]{Integer.MAX_VALUE};
        nearestCheckable(ui.getRootInActiveWindow(), bounds.centerY(), nearest, distance);
        float limit = 24 * getInstrumentation().getTargetContext().getResources().getDisplayMetrics().density;
        return nearest[0] != null && distance[0] < limit && nearest[0].isChecked();
    }
    private void nearestCheckable(AccessibilityNodeInfo n, int y, AccessibilityNodeInfo[] nearest, int[] distance) {
        if (n == null) return;
        if (n.isVisibleToUser() && n.isCheckable()) {
            Rect bounds = new Rect(); n.getBoundsInScreen(bounds);
            int delta = Math.abs(bounds.centerY() - y);
            if (delta < distance[0]) { nearest[0] = n; distance[0] = delta; }
        }
        for (int i = 0; i < n.getChildCount(); i++) nearestCheckable(n.getChild(i), y, nearest, distance);
    }
    private boolean hasProgress(AccessibilityNodeInfo n) {
        if (n == null) return false;
        if (n.isVisibleToUser() && "android.widget.ProgressBar".equals(String.valueOf(n.getClassName()))) return true;
        for (int i = 0; i < n.getChildCount(); i++) if (hasProgress(n.getChild(i))) return true;
        return false;
    }
    private void click(AccessibilityNodeInfo n) {
        assertNotNull("Missing UI action", n);
        for (AccessibilityNodeInfo node = n; node != null; node = node.getParent())
            if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
        fail("UI node has no working click action");
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = ui.executeShellCommand(command); InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] buffer = new byte[4096]; while (stream.read(buffer) != -1) { }
        }
    }
    private void capture(String name) throws Exception {
        Bitmap bitmap = ui.takeScreenshot(); if (bitmap == null) return;
        try (OutputStream out = new FileOutputStream(new File(evidence, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
    }
    private void dump(String name) throws Exception {
        try (FileWriter writer = new FileWriter(new File(evidence, name + ".txt"))) { dumpNode(ui.getRootInActiveWindow(), writer, 0); }
    }
    private void dumpNode(AccessibilityNodeInfo n, Writer out, int depth) throws Exception {
        if (n == null) return;
        Rect bounds = new Rect(); n.getBoundsInScreen(bounds);
        out.write(depth + " " + n.getPackageName() + " text=" + n.getText() + " description=" + n.getContentDescription() + " clickable=" + n.isClickable() + " " + bounds + "\n");
        for (int i = 0; i < n.getChildCount(); i++) dumpNode(n.getChild(i), out, depth + 1);
    }
}
