package com.wanderingwitch.player;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import java.io.IOException;

public class PlaybackService extends Service {
    static final String CHANNEL = "witch-playback";
    static final int NOTIFICATION = 41;
    final IBinder binder = new LocalBinder();
    MediaPlayer player;
    MediaSession session;
    AudioManager audio;
    AudioFocusRequest focus;
    int index = 1;
    float volume = .66f;
    String error = "";
    boolean loading, resumeOnFocus, foreground, ducked;
    int generation;
    volatile int analysisGeneration;
    volatile AudioRhythm rhythm;
    final AudioRhythm[] rhythmCache=new AudioRhythm[2];
    final long[] rhythmVersions=new long[2];
    final java.util.concurrent.ExecutorService analysisWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable refresh = new Runnable() {
        public void run() {
            publish();
            if (isPlaying()) handler.postDelayed(this, 1000);
        }
    };
    public class LocalBinder extends Binder { PlaybackService service() { return PlaybackService.this; } }
    final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { pause(); }
    };
    @Override public void onCreate() {
        super.onCreate();
        index = getSharedPreferences("player", 0).getInt("track", 1);
        if (index < 0 || index > 1) index = 1;
        volume = getSharedPreferences("player", 0).getFloat("volume", .66f);
        audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(change -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS) { resumeOnFocus = false; pause(); }
                else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { boolean wasPlaying = isPlaying(); pauseInternal(false); resumeOnFocus = wasPlaying; }
                else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { ducked = true; applyVolume(); }
                else if (change == AudioManager.AUDIOFOCUS_GAIN) { ducked = false; applyVolume(); if (resumeOnFocus) { resumeOnFocus = false; play(); } }
            }).build();
        session = new MediaSession(this, "Wandering Witch");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { select(1 - index, true); }
            @Override public void onSkipToPrevious() { select(1 - index, true); }
            @Override public void onSeekTo(long p) { seek(p); }
            @Override public void onStop() { pause(); stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
        });
        session.setActive(true);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
            new NotificationChannel(CHANNEL, "魔女之旅 · 音乐播放", NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        publish();
    }
    @Override public IBinder onBind(Intent i) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String a = intent.getAction();
            if ("toggle".equals(a)) toggle();
            else if ("next".equals(a) || "previous".equals(a)) select(1-index, true);
            else if ("pause".equals(a)) pause();
        }
        return START_NOT_STICKY;
    }
    boolean isPlaying() { return player != null && !loading && player.isPlaying(); }
    int position() { try { return player != null && !loading ? player.getCurrentPosition() : 0; } catch (IllegalStateException e) { return 0; } }
    int duration() { try { return player != null && !loading ? player.getDuration() : 0; } catch (IllegalStateException e) { return 0; } }
    void requestRhythm() {
        final int track=index, token=++analysisGeneration;
        final java.io.File file=Tracks.file(this,track);
        final long version=file.lastModified()*31+file.length();
        if(rhythmCache[track]!=null && rhythmVersions[track]==version) { rhythm=rhythmCache[track]; return; }
        rhythm=null;
        analysisWorker.execute(() -> {
            try {
                AudioRhythm decoded=AudioRhythm.analyze(file,()->token!=analysisGeneration || Thread.currentThread().isInterrupted());
                if(decoded!=null && token==analysisGeneration) handler.post(() -> {
                    if(token!=analysisGeneration) return;
                    rhythmCache[track]=decoded; rhythmVersions[track]=version; rhythm=decoded;
                });
            } catch(Exception ignored) { /* Playback continues with a gentle, continuous fallback sway. */ }
        });
    }
    void select(int target, boolean autoplay) {
        if (target < 0 || target > 1) return;
        releasePlayer(); index = target; error = ""; rhythm=null; analysisGeneration++;
        getSharedPreferences("player", 0).edit().putInt("track", index).apply();
        publish(); if (autoplay) play();
    }
    void toggle() { if (isPlaying() || loading) pause(); else play(); }
    void play() {
        if (loading) return;
        if (!Tracks.ready(this, index)) { error = "请先导入《" + Tracks.TITLES[index] + "》的音频"; publish(); return; }
        error = "";
        // Establish a foreground service before requesting audio focus (Android 15+).
        startService(new Intent(this, PlaybackService.class));
        startForeground(NOTIFICATION, notification()); foreground = true;
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            error = "暂时无法取得音频焦点，请稍后再试"; pause(); return;
        }
        if (player != null) { player.start(); publish(); tick(); return; }
        player = new MediaPlayer(); loading = true;
        final int token = ++generation;
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        try {
            player.setDataSource(Tracks.file(this, index).getAbsolutePath());
            requestRhythm();
            applyVolume();
            player.setOnPreparedListener(p -> { if (token != generation) return; loading = false; p.start(); publish(); tick(); });
            player.setOnCompletionListener(p -> {
                if(token!=generation || p!=player) return;
                int next = 1 - index;
                if (Tracks.ready(this, next)) select(next, true);
                else { p.seekTo(0); pause(); }
            });
            player.setOnErrorListener((p, what, extra) -> {
                if(token!=generation || p!=player) return true;
                error = "音频无法播放，请重新导入 MP3、M4A、FLAC 或 OGG 文件";
                releasePlayer(); pause(); return true;
            });
            player.prepareAsync(); publish();
        } catch (IOException | RuntimeException e) {
            error = "无法打开音频，请重新导入"; releasePlayer(); pause();
        }
    }
    void tick() { handler.removeCallbacks(refresh); handler.post(refresh); }
    void pause() { pauseInternal(true); }
    void pauseInternal(boolean abandonFocus) {
        resumeOnFocus = false;
        if (loading) releasePlayer();
        else if (player != null && player.isPlaying()) player.pause();
        handler.removeCallbacks(refresh); if (abandonFocus) audio.abandonAudioFocusRequest(focus); publish();
        if (foreground) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false; }
    }
    void seek(long position) { if (player != null && !loading) { player.seekTo((int)Math.max(0, Math.min(duration(), position))); publish(); } }
    void setVolume(float value) {
        volume = Math.max(0, Math.min(1, value)); applyVolume();
        getSharedPreferences("player", 0).edit().putFloat("volume", volume).apply();
    }
    void applyVolume() { if (player != null) { float v = volume * (ducked ? .2f : 1f); player.setVolume(v, v); } }
    void releasePlayer() { generation++; loading = false; handler.removeCallbacks(refresh); if (player != null) { player.release(); player = null; } }
    void publish() {
        if (session == null) return;
        int state = loading ? PlaybackState.STATE_BUFFERING : isPlaying() ? PlaybackState.STATE_PLAYING : error.isEmpty() ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_ERROR;
        PlaybackState.Builder builder = new PlaybackState.Builder().setActions(
            PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
            .setState(state, position(), isPlaying() ? 1 : 0);
        if (!error.isEmpty()) builder.setErrorMessage(error);
        session.setPlaybackState(builder.build());
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, Tracks.TITLES[index])
            .putString(MediaMetadata.METADATA_KEY_ARTIST, Tracks.ARTISTS[index])
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "魔女の旅々")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration()).build());
        if (foreground || player != null) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION, notification());
    }
    PendingIntent action(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, PlaybackService.class).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(com.wanderingwitch.player.R.drawable.ic_witch)
            .setContentTitle(Tracks.TITLES[index]).setContentText(Tracks.ARTISTS[index] + " · 魔女の旅々")
            .setContentIntent(open).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying() || loading)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "上一首", action("previous", 1)).build())
            .addAction(new Notification.Action.Builder(isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, isPlaying() ? "暂停" : "播放", action("toggle", 2)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "下一首", action("next", 3)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2)).build();
    }
    @Override public void onDestroy() {
        analysisGeneration++; analysisWorker.shutdownNow();
        handler.removeCallbacksAndMessages(null); releasePlayer();
        audio.abandonAudioFocusRequest(focus); unregisterReceiver(noisy); session.release();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION);
        super.onDestroy();
    }
}
