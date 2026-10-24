package com.wanderingwitch.player;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.io.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    PlaybackService playback;
    PlayerLayout layout;
    final Handler handler = new Handler(Looper.getMainLooper());
    int importIndex = -1;
    boolean bound, visible, importing;
    Dialog playlist;
    Typeface handwriting;
    final Runnable ticker = new Runnable() {
        public void run() {
            if (!visible) return;
            layout.repaint();
            handler.postDelayed(this, 250);
        }
    };
    final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            playback = ((PlaybackService.LocalBinder)binder).service(); layout.repaint();
        }
        public void onServiceDisconnected(ComponentName name) { playback = null; }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        handwriting = Typeface.createFromAsset(getAssets(), "fonts/YujiBoku-Player.ttf");
        getWindow().setStatusBarColor(Color.WHITE); getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getDecorView(); // Create the decor before querying its insets controller.
            getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
            getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else getWindow().getDecorView().setSystemUiVisibility(5894);
        if (saved != null) importIndex = saved.getInt("importIndex", -1);
        layout = new PlayerLayout(); setContentView(layout);
        bound = bindService(new Intent(this, PlaybackService.class), connection, BIND_AUTO_CREATE);
        if (!getPreferences(0).getBoolean("introduced", false)) {
            AlertDialog welcome = new AlertDialog.Builder(this).setTitle("魔女の旅々")
                .setMessage("固定歌单：灰色のサーガ / リテラチュア。\n\n点按曲名打开歌单，为两首歌分别导入本地音频。之后可离线播放，支持后台与锁屏控制。\n\n画面中的播放键、上一首、下一首、进度条和音量条均可操作。")
                .setPositiveButton("开始", (d,w) -> getPreferences(0).edit().putBoolean("introduced", true).apply()).create();
            welcome.setOnShowListener(d -> styleText(welcome.getWindow().getDecorView()));
            welcome.show();
        }
    }
    @Override protected void onStart() { super.onStart(); visible = true; layout.art.lastFrame = 0; handler.post(ticker); }
    @Override protected void onStop() { super.onStop(); visible = false; handler.removeCallbacks(ticker); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null); if (bound) unbindService(connection);
        if (playlist != null) playlist.dismiss(); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putInt("importIndex", importIndex); super.onSaveInstanceState(out); }
    void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    void toggle() {
        if (playback == null) return;
        if (!Tracks.ready(this, playback.index)) { showPlaylist(); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 42);
        playback.toggle(); layout.repaint();
        if (!playback.error.isEmpty()) toast(playback.error);
    }
    void skip() {
        if (playback == null) return;
        boolean wasPlaying = playback.isPlaying();
        playback.select(1 - playback.index, wasPlaying);
        if (!Tracks.ready(this, playback.index)) { playback.pause(); toast("该曲目尚未导入，点按曲名添加音频"); }
        layout.repaint();
    }
    void showPlaylist() {
        if (isFinishing()) return;
        if (playlist != null && playlist.isShowing()) return;
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24),dp(22),dp(24),dp(16));
        TextView title = new TextView(this); title.setText("魔女の旅々"); title.setTag("artText"); title.setTextColor(Color.rgb(44,57,62)); title.setTextSize(24); content.addView(title);
        TextView subtitle = new TextView(this); subtitle.setText("固定歌单 · 本地离线播放"); subtitle.setTextSize(13); subtitle.setPadding(0,dp(8),0,dp(18)); content.addView(subtitle);
        for (int i=0; i<2; i++) {
            final int track = i;
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0,dp(8),0,dp(12));
            TextView name = new TextView(this); name.setText((playback != null && playback.index == i ? "♪  " : "") + Tracks.TITLES[i]); name.setTag("artText"); name.setTextSize(21); name.setTextColor(Color.BLACK); row.addView(name);
            TextView artist = new TextView(this); artist.setText(Tracks.ARTISTS[i] + (Tracks.ready(this,i) ? "  ·  已导入" : "  ·  尚未导入")); artist.setPadding(0,dp(6),0,dp(8)); row.addView(artist);
            LinearLayout actions = new LinearLayout(this);
            Button play = new Button(this); play.setText("播放"); play.setEnabled(Tracks.ready(this,i) && !importing);
            play.setOnClickListener(v -> { if (playback != null) { playback.select(track,false); playlist.dismiss(); toggle(); } });
            actions.addView(play, new LinearLayout.LayoutParams(0,dp(48),1));
            Button choose = new Button(this); choose.setText(importing ? "正在导入…" : Tracks.ready(this,i) ? "更换音频" : "导入音频"); choose.setEnabled(!importing);
            choose.setOnClickListener(v -> { playlist.dismiss(); pick(track); }); actions.addView(choose,new LinearLayout.LayoutParams(0,dp(48),1)); row.addView(actions); content.addView(row);
        }
        TextView hint = new TextView(this); hint.setText("请选择对应歌曲的 MP3、M4A、FLAC、WAV 或 OGG 文件。导入后保存在应用内；此处不会增加其他曲目。"); hint.setTextSize(12); hint.setPadding(0,dp(10),0,dp(8)); content.addView(hint);
        playlist = new Dialog(this); playlist.setContentView(content);
        Window window = playlist.getWindow(); if (window != null) { window.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); window.setLayout(-1,-2); window.setGravity(Gravity.BOTTOM); }
        playlist.show(); if (window != null) { window.setLayout(-1,-2); styleText(window.getDecorView()); }
    }
    void styleText(View view) {
        if (view instanceof TextView) { TextView text=(TextView)view; text.setTypeface("artText".equals(text.getTag()) ? handwriting : Typeface.create("sans-serif",Typeface.NORMAL)); text.getPaint().setFakeBoldText(false); }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) styleText(group.getChildAt(i));
        }
    }
    int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }
    void pick(int index) {
        if (importing) return;
        importIndex = index;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(pick, 10); } catch (ActivityNotFoundException e) { toast("设备没有文件选择器"); }
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null || importIndex < 0 || importIndex > 1) return;
        final int target = importIndex; final Uri uri = data.getData(); importIndex = -1; importing = true;
        toast("正在导入《" + Tracks.TITLES[target] + "》…");
        java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            File temp = new File(getFilesDir(), "import-" + target + ".partial");
            String failure = null;
            try {
                try (InputStream input = getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(temp)) {
                    if (input == null) throw new IOException();
                    byte[] bytes = new byte[65536]; long total = 0; int count;
                    while ((count=input.read(bytes)) != -1) { total += count; if (total > 512L*1024*1024) throw new IOException("文件超过 512 MB"); output.write(bytes,0,count); }
                }
                MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                try {
                    metadata.setDataSource(temp.getAbsolutePath());
                    String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    String hasAudio = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
                    if (duration == null || Long.parseLong(duration) <= 0 || !"yes".equals(hasAudio)) throw new IOException("请选择有效的音频文件");
                } finally { metadata.release(); }
                // Only the final atomic rename replaces the existing, validated recording.
            } catch (Exception e) { failure = e.getMessage() != null ? e.getMessage() : "无法读取音频"; }
            final String error = failure;
            runOnUiThread(() -> {
                importing = false;
                if (error != null) { temp.delete(); if (!isDestroyed()) toast("导入失败：" + error); return; }
                if (playback != null && playback.index == target) { playback.pause(); playback.releasePlayer(); }
                if (!temp.renameTo(Tracks.file(this,target))) { temp.delete(); if (!isDestroyed()) toast("保存失败，请检查可用空间"); return; }
                if (!isDestroyed()) { toast("《" + Tracks.TITLES[target] + "》已导入"); layout.repaint(); showPlaylist(); }
            });
            worker.shutdown();
        });
    }

    final class PlayerLayout extends android.widget.FrameLayout {
        final Bitmap reference, sky, character;
        final ArtView art;
        final Button previous, play, next, title, mute;
        final SeekBar progress, volume;
        float scale, left, top;
        boolean dragging;
        float oldVolume = .66f;
        PlayerLayout() {
            super(MainActivity.this); setBackgroundColor(Color.WHITE);
            Bitmap ref = null;
            try (InputStream in = getAssets().open("player-reference.jpg")) { ref = BitmapFactory.decodeStream(in); } catch (IOException e) { throw new IllegalStateException(e); }
            reference = ref;
            sky = assetBitmap("player-sky.png"); character = CharacterArtwork.prepare(assetBitmap("elaina-layer.png"),assetBitmap("skin-restoration.png"));
            art = new ArtView(); addView(art, new LayoutParams(-1,-1));
            previous = control("上一首", () -> skip());
            play = control("播放", () -> toggle());
            next = control("下一首", () -> skip());
            title = control("打开固定歌单并导入音频", () -> showPlaylist());
            mute = control("静音", () -> { if (playback != null) { if (playback.volume > 0) { oldVolume=playback.volume; playback.setVolume(0); } else playback.setVolume(oldVolume > 0 ? oldVolume : .66f); repaint(); } });
            progress = slider("播放进度"); progress.setMax(10000);
            progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onStartTrackingTouch(SeekBar s) { dragging = true; }
                public void onStopTrackingTouch(SeekBar s) { if (playback != null) playback.seek((long)playback.duration()*s.getProgress()/10000); dragging = false; repaint(); }
                public void onProgressChanged(SeekBar s,int p,boolean user) { if (user) art.invalidate(); }
            });
            volume = slider("播放音量"); volume.setMax(100); volume.setProgress(66);
            volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
                public void onProgressChanged(SeekBar s,int p,boolean user) { if(user && playback != null) { playback.setVolume(p/100f); art.invalidate(); } }
            });
            art.setOnLongClickListener(v -> { showPlaylist(); return true; });
        }
        Bitmap assetBitmap(String name) {
            try (InputStream in = getAssets().open(name)) { return BitmapFactory.decodeStream(in); }
            catch (IOException e) { throw new IllegalStateException("Missing animation layer: " + name, e); }
        }
        Button control(String description, Runnable onClick) {
            Button b = new Button(MainActivity.this); b.setBackgroundColor(Color.TRANSPARENT); b.setText(""); b.setContentDescription(description); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setPadding(0,0,0,0); b.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick.run(); }); addView(b); return b;
        }
        SeekBar slider(String description) {
            SeekBar s = new SeekBar(MainActivity.this); s.setContentDescription(description); s.setPadding(0,0,0,0);
            s.setThumb(new ColorDrawable(Color.TRANSPARENT)); s.setProgressDrawable(new ColorDrawable(Color.TRANSPARENT)); s.setBackgroundColor(Color.TRANSPARENT); s.setSplitTrack(false); addView(s); return s;
        }
        void repaint() {
            if (playback != null) {
                if (!dragging) progress.setProgress(playback.duration() > 0 ? (int)(10000L*playback.position()/playback.duration()) : 0);
                volume.setProgress(Math.round(playback.volume*100));
                play.setContentDescription(playback.loading ? "取消加载" : playback.isPlaying() ? "暂停" : "播放");
                title.setContentDescription(Tracks.TITLES[playback.index] + "，打开固定歌单并导入音频");
                progress.setEnabled(playback.duration() > 0);
            }
            art.invalidate();
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
            scale = Math.min((r-l)/865f,(b-t)/1280f); left=((r-l)-865*scale)/2; top=((b-t)-1280*scale)/2;
            art.layout(0,0,r-l,b-t);
            place(previous,145,1015,275,1135); place(play,365,1007,495,1137); place(next,565,1015,695,1135);
            place(title,80,835,350,920); place(progress,83,916,766,966);
            place(mute,65,1168,133,1245); place(volume,136,1180,702,1236);
        }
        void place(View v,float x1,float y1,float x2,float y2) {
            int min=dp(48); int w=Math.max(min,Math.round((x2-x1)*scale)), h=Math.max(min,Math.round((y2-y1)*scale));
            int cx=Math.round(left+(x1+x2)/2*scale), cy=Math.round(top+(y1+y2)/2*scale);
            v.layout(cx-w/2,cy-h/2,cx+w/2,cy+h/2);
        }
        final class ArtView extends View {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            final RectF page = new RectF(0,0,865,1280);
            ArtView() { super(MainActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
            void crop(Canvas c, int x,int y,int x2,int y2) { paint.setAlpha(255); c.drawBitmap(reference,new Rect(x,y,x2,y2),new RectF(x,y,x2,y2),paint); }
            final CharacterMotion motion = new CharacterMotion();
            final CharacterRig rig = new CharacterRig(character);
            long lastFrame;
            void drawCharacter(Canvas canvas) { rig.draw(canvas,paint,motion); }
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                long now = SystemClock.uptimeMillis();
                boolean playing = playback != null && playback.isPlaying();
                motion.step(lastFrame == 0 ? 0 : (now-lastFrame)/1000f, playing,playback==null?null:playback.rhythm,playback==null?0:playback.position()); lastFrame = now;
                canvas.save(); canvas.translate(left,top); canvas.scale(scale,scale);
                canvas.drawBitmap(sky,null,page,paint);
                int track = playback != null ? playback.index : 1;
                paint.setTypeface(handwriting); paint.setFakeBoldText(false); paint.setTextSkewX(0);
                paint.setTextSize(44); paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.FILL);
                // Both fixed titles use one real brush face at exactly the same size.
                canvas.drawText(Tracks.TITLES[track],88,900,paint);
                paint.setColor(Color.BLACK); paint.setStrokeWidth(2.8f); paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(84,939,764,940,paint);
                float ratio = progress.getProgress()/10000f;
                float x = 84+680*ratio;
                canvas.drawCircle(x,939,6.7f,paint);
                crop(canvas,175,1033,254,1115); crop(canvas,599,1032,677,1115);
                if (playback != null && playback.isPlaying()) {
                    
                    paint.setColor(Color.BLACK);
                    Path first = new Path(); first.moveTo(402,1032); first.lineTo(423,1035); first.lineTo(421,1109); first.lineTo(402,1115); first.close(); canvas.drawPath(first,paint);
                    Path second = new Path(); second.moveTo(443,1035); second.lineTo(464,1031); second.lineTo(463,1114); second.lineTo(442,1109); second.close(); canvas.drawPath(second,paint);
                } else {
                    paint.setColor(Color.BLACK);
                    Path triangle = new Path(); triangle.moveTo(395,1026); triangle.lineTo(481,1073); triangle.lineTo(395,1120); triangle.close(); canvas.drawPath(triangle,paint);
                }
                // Character is a true separate transparent layer, in front of the progress line.
                drawCharacter(canvas);
                // Volume icons are also real pieces of the supplied hand drawn UI.
                crop(canvas,91,1198,124,1229); crop(canvas,718,1182,781,1237);
                paint.setColor(Color.BLACK); paint.setStrokeWidth(2.7f); canvas.drawLine(137,1209,701,1208,paint);
                float vx=137+564*volume.getProgress()/100f;
                paint.setColor(Color.WHITE); canvas.drawCircle(vx,1208,17,paint);
                paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.8f);
                canvas.drawOval(vx-17,1190,vx+16,1225,paint); paint.setStyle(Paint.Style.FILL);
                canvas.drawLine(vx-17,1205,vx-12,1212,paint); canvas.drawLine(vx+13,1204,vx+18,1207,paint);
                if (playback != null && playback.duration() > 0) {
                    paint.setColor(Color.rgb(40,40,40)); paint.setTextSize(25); paint.setTypeface(handwriting); paint.setFakeBoldText(false);
                    canvas.drawText(time(dragging ? (int)((long)playback.duration()*progress.getProgress()/10000) : playback.position()),84,975,paint);
                    paint.setTextAlign(Paint.Align.RIGHT); canvas.drawText(time(playback.duration()),764,975,paint); paint.setTextAlign(Paint.Align.LEFT);
                }
                if (playback != null && playback.loading) { paint.setColor(Color.rgb(80,100,110)); paint.setTextSize(19); canvas.drawText("読み込み中…",85,814,paint); }
                if (playback != null && !playback.error.isEmpty()) { paint.setColor(Color.rgb(130,70,60)); paint.setTextSize(18); canvas.drawText("音频不可用，请点按曲名重新导入",85,814,paint); }
                canvas.restore();
                if (visible && (playing || motion.transitioning(playing))) postInvalidateOnAnimation();
            }
            String time(int ms) { int sec=ms/1000; return String.format(java.util.Locale.ROOT,"%d:%02d",sec/60,sec%60); }
        }
    }
}
