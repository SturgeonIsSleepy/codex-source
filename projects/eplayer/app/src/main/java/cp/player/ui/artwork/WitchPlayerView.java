package cp.player.ui.artwork;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;

/** Artwork is independent of the playback engine. Controls delegate to CPPlayer's MediaSession. */
public final class WitchPlayerView extends FrameLayout {
    public interface Controls {
        void playPause(); void previous(); void next(); void seek(long position); void queue();
    }
    private Controls controls;
    private final Bitmap reference,sky,portrait;
    private final Typeface brush,hand;
    private final Art art;
    private final Assets assets;
    private final Button previous,play,next,title;
    private final SeekBar seek,volume;
    private final android.media.AudioManager audio;
    private boolean playing,playRequested,buffering,dragging,visible=true,motionEnabled=true;
    private String songTitle="リテラチュア";
    private long position,duration;
    private float scale,left,top;
    private AudioRhythm rhythm;
    private String audioUri;
    private boolean artworkOnly;
    public void setArtworkOnly(boolean value) {
        artworkOnly=value;
        int visibility=value?GONE:VISIBLE;
        previous.setVisibility(visibility);play.setVisibility(visibility);next.setVisibility(visibility);
        title.setVisibility(visibility);seek.setVisibility(visibility);volume.setVisibility(visibility);
        art.invalidate();
    }
    public void setMotionEnabled(boolean value) {
        if(motionEnabled==value)return;
        motionEnabled=value;art.lastFrame=0;art.invalidate();
    }
    public void setAudioUri(String uri) {
        // Live PCM metering already drives motion; do not decode the entire track a second time.
        audioUri=uri;
    }

    private static volatile Assets cachedAssets;
    private static final class Assets {
        final Bitmap reference,sky,portrait;
        final Typeface brush,hand;
        final CharacterRig rig;
        Assets(Context context) {
            reference=load(context,"player-reference.jpg");sky=load(context,"player-sky.png");
            portrait=CharacterArtwork.prepare(load(context,"elaina-layer.png"),load(context,"skin-restoration.png"));
            brush=Typeface.createFromAsset(context.getAssets(),"fonts/YujiBoku-Player.ttf");
            hand=androidx.core.content.res.ResourcesCompat.getFont(context,cp.player.R.font.travel_hand);
            rig=new CharacterRig(portrait);
        }
    }
    public static synchronized void prepareAssets(Context context) {
        if(cachedAssets==null)cachedAssets=new Assets(context.getApplicationContext());
    }
    public static synchronized void clearAssetCache(){cachedAssets=null;}
    private static Bitmap load(Context context,String name){
        try(InputStream in=context.getAssets().open(name)){
            Bitmap result=BitmapFactory.decodeStream(in);result.setDensity(Bitmap.DENSITY_NONE);return result;
        }catch(IOException e){throw new IllegalStateException("Missing illustration: "+name,e);}
    }
    public WitchPlayerView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        prepareAssets(context);assets=cachedAssets;
        reference=assets.reference;sky=assets.sky;portrait=assets.portrait;
        brush=assets.brush;hand=assets.hand;
        audio=(android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        art=new Art(context); addView(art,new LayoutParams(-1,-1));
        previous=button("上一首",()->{if(controls!=null)controls.previous();});
        play=button("播放",()->{if(controls!=null)controls.playPause();});
        next=button("下一首",()->{if(controls!=null)controls.next();});
        title=button("播放队列",()->{if(controls!=null)controls.queue();});
        seek=slider("播放进度");seek.setMax(10000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){dragging=true;}
            public void onStopTrackingTouch(SeekBar b){if(controls!=null)controls.seek(duration*b.getProgress()/10000);dragging=false;}
            public void onProgressChanged(SeekBar b,int value,boolean user){if(user)art.invalidate();}
        });
        volume=slider("系统媒体音量");volume.setMax(audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
        volume.setProgress(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
            public void onProgressChanged(SeekBar b,int value,boolean user){if(user){audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,value,0);art.invalidate();}}
        });
    }
    public void update(String name,boolean isPlaying,boolean isBuffering,long current,long total,Controls callbacks){
        update(name,isPlaying,isBuffering,isPlaying,current,total,callbacks);
    }
    public void update(String name,boolean isPlaying,boolean isBuffering,boolean requested,long current,long total,Controls callbacks){
        songTitle=name; playing=isPlaying;playRequested=requested;buffering=isBuffering;position=current;duration=Math.max(0,total);controls=callbacks;
        if(!dragging)seek.setProgress(duration>0?(int)(10000*position/duration):0);
        seek.setEnabled(duration>0);play.setContentDescription(playRequested?"暂停":"播放");
        title.setContentDescription(name+"，播放队列");
        volume.setProgress(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        art.invalidate();
    }
    private Button button(String description,Runnable action){
        Button b=new Button(getContext());b.setText("");b.setContentDescription(description);b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinimumWidth(0);b.setMinWidth(0);b.setMinHeight(0);b.setPadding(0,0,0,0);
        b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);action.run();});addView(b);return b;
    }
    private SeekBar slider(String description){
        SeekBar b=new SeekBar(getContext());b.setContentDescription(description);b.setPadding(0,0,0,0);
        b.setThumb(new ColorDrawable(Color.TRANSPARENT));b.setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));
        b.setBackgroundColor(Color.TRANSPARENT);b.setSplitTrack(false);addView(b);return b;
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        scale=Math.min((r-l)/865f,(b-t)/1280f);left=((r-l)-865*scale)/2;top=((b-t)-1280*scale)/2;
        art.layout(0,0,r-l,b-t);
        place(previous,145,1015,275,1135);place(play,365,1007,495,1137);place(next,565,1015,695,1135);
        place(title,80,835,350,920);place(seek,83,916,766,966);place(volume,136,1180,702,1236);
    }
    private void place(View v,float x1,float y1,float x2,float y2){
        int min=(int)(48*getResources().getDisplayMetrics().density);
        int w=Math.max(min,Math.round((x2-x1)*scale)),h=Math.max(min,Math.round((y2-y1)*scale));
        int x=Math.round(left+(x1+x2)*scale/2),y=Math.round(top+(y1+y2)*scale/2);v.layout(x-w/2,y-h/2,x+w/2,y+h/2);
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);visible=visibility==VISIBLE;if(art!=null){art.lastFrame=0;art.invalidate();}}
    private final class Art extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final CharacterMotion motion=new CharacterMotion();
        final CharacterRig rig=assets.rig;
        long lastFrame;
        Art(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void crop(Canvas c,int x,int y,int x2,int y2){c.drawBitmap(reference,new Rect(x,y,x2,y2),new RectF(x,y,x2,y2),paint);}
        @Override protected void onDraw(Canvas c){
            long now=SystemClock.uptimeMillis();
            if(motionEnabled)motion.step(lastFrame==0?0:(now-lastFrame)/1000f,playing,rhythm,(int)Math.min(Integer.MAX_VALUE,position),cp.player.engine.MusicPulse.sample(),playRequested);
            else motion.settle(playRequested);
            lastFrame=now;
            c.save();c.translate(left,top);c.scale(scale,scale);
            paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
            c.drawBitmap(sky,null,new RectF(0,0,865,1280),paint);
            if(artworkOnly){
                rig.draw(c,paint,motion);c.restore();
                if(visible&&motionEnabled&&(playing||motion.transitioning(playRequested)))postInvalidateDelayed(33);
                return;
            }
            paint.setTypeface(brush);paint.setTextSize(44);
            if(!songTitle.codePoints().allMatch(cp -> Character.isWhitespace(cp) || paint.hasGlyph(new String(Character.toChars(cp)))))
                paint.setTypeface(hand);
            // Long titles wrap in the surrounding song header; the art remains a single readable line.
            String display=songTitle;
            while(paint.measureText(display)>660 && display.length()>1)display=display.substring(0,display.length()-1);
            if(!display.equals(songTitle))display=display.trim()+"…";
            c.drawText(display,88,900,paint);
            paint.setStrokeWidth(2.8f);paint.setStrokeCap(Paint.Cap.ROUND);c.drawLine(84,939,764,940,paint);
            c.drawCircle(84+680*seek.getProgress()/10000f,939,6.7f,paint);
            crop(c,175,1033,254,1115);crop(c,599,1032,677,1115);
            Path icon=new Path();
            if(playRequested){
                icon.moveTo(402,1032);icon.lineTo(423,1035);icon.lineTo(421,1109);icon.lineTo(402,1115);icon.close();
                icon.moveTo(443,1035);icon.lineTo(464,1031);icon.lineTo(463,1114);icon.lineTo(442,1109);icon.close();
            }else{icon.moveTo(395,1026);icon.lineTo(481,1073);icon.lineTo(395,1120);icon.close();}
            c.drawPath(icon,paint);rig.draw(c,paint,motion);
            crop(c,91,1198,124,1229);crop(c,718,1182,781,1237);
            paint.setColor(Color.BLACK);c.drawLine(137,1209,701,1208,paint);
            float vx=137+564*volume.getProgress()/(float)Math.max(1,volume.getMax());
            paint.setColor(Color.WHITE);c.drawCircle(vx,1208,17,paint);
            paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);c.drawOval(vx-17,1190,vx+16,1225,paint);paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(brush);paint.setTextSize(25);c.drawText(time(dragging?duration*seek.getProgress()/10000:position),84,975,paint);
            paint.setTextAlign(Paint.Align.RIGHT);c.drawText(time(duration),764,975,paint);paint.setTextAlign(Paint.Align.LEFT);
            if(buffering){paint.setTypeface(hand);paint.setTextSize(22);c.drawText("加载中…",85,814,paint);}
            c.restore();if(visible&&motionEnabled&&(playing||motion.transitioning(playRequested)))postInvalidateDelayed(33);
        }
        String time(long ms){long secs=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%d:%02d",secs/60,secs%60);}
    }
}
