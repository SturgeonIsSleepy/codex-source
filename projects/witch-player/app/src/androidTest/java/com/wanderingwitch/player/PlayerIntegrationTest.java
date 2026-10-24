package com.wanderingwitch.player;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import java.io.*;

/** Test-only instrumentation; never packaged in the deliverable APK. */
public final class PlayerIntegrationTest extends Instrumentation {
    MainActivity activity;
    boolean preview,expressionsOnly;
    int previewFrames=300;
    StringBuilder passed = new StringBuilder();
    @Override public void onCreate(Bundle args) { super.onCreate(args); expressionsOnly=args!=null && "true".equals(args.getString("expressions")); if(args!=null) previewFrames=Integer.parseInt(args.getString("frames","300")); preview=args != null && "true".equals(args.getString("preview")); start(); }
    @Override public void onStart() {
        Bundle results = new Bundle();
        try {
            getTargetContext().getSharedPreferences("MainActivity",0).edit().putBoolean("introduced",true).commit();
            activity = (MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> activity.playback != null,"service bind");
            if(expressionsOnly) {
                main(this::checkPortraitStability);
                File folder=new File(getTargetContext().getExternalFilesDir(null),"visual-preview"); folder.mkdirs();
                String[] names={"open","half","closed","sway"};
                for(int state=0;state<names.length;state++) {
                    final int value=state;
                    android.graphics.Bitmap[] shot={null},face={null};
                    main(() -> {
                        activity.playback.pause(); activity.visible=false;
                        float openness=value==0?1:value==1?.5f:0;
                        activity.layout.art.motion.eyeOpen=openness;
                        if(value==3) { activity.layout.art.motion.torso=.8f; java.util.Arrays.fill(activity.layout.art.motion.secondary,.5f); }
                        android.graphics.Paint font=new android.graphics.Paint(); font.setTypeface(activity.handwriting);
                        for(int glyph:"灰色のサーガリテラチュア0123456789:".codePoints().toArray()) check(font.hasGlyph(new String(Character.toChars(glyph))),"brush glyph "+glyph);
                        activity.layout.art.lastFrame=0;
                        shot[0]=android.graphics.Bitmap.createBitmap(activity.layout.getWidth(),activity.layout.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                        activity.layout.draw(new android.graphics.Canvas(shot[0]));
                        face[0]=renderPortrait(openness);
                    });
                    try(OutputStream out=new FileOutputStream(new File(folder,names[state]+".png"))) { shot[0].compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); }
                    shot[0].recycle();
                    android.graphics.Bitmap enlarged=android.graphics.Bitmap.createScaledBitmap(face[0],612,708,true);
                    try(OutputStream out=new FileOutputStream(new File(folder,"face-"+names[state]+".png"))) { enlarged.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); }
                    enlarged.recycle(); face[0].recycle();
                }
                File blink=new File(folder,"blink"); blink.mkdirs();
                for(int frame=0;frame<150;frame++) {
                    float open=frame<30?0:frame<=45?(frame-30)/15f:frame<96?1:frame<=107?1-(frame-96)/11f:0;
                    android.graphics.Bitmap image=renderPortrait(open);
                    try(OutputStream out=new FileOutputStream(new File(blink,String.format(java.util.Locale.ROOT,"frame-%03d.png",frame)))) { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); } image.recycle();
                }
                android.graphics.Bitmap upper=android.graphics.Bitmap.createBitmap(765,1230,android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas detail=new android.graphics.Canvas(upper); detail.scale(3,3); detail.translate(-395,-240); detail.drawBitmap(activity.layout.character,0,0,null);
                try(OutputStream out=new FileOutputStream(new File(folder,"upper-body-source.png"))) { upper.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); } upper.recycle();
                results.putString("stream","PASS: eyes-only pixel invariance and four native visual states\n"+passed); finish(Activity.RESULT_OK,results); return;
            }
            if(preview) { captureRigPreview(); results.putString("stream","Rig preview captured"); finish(Activity.RESULT_OK,results); return; }
            File fixture = new File(getTargetContext().getCacheDir(),"test-tone.wav"); writeWave(fixture);
            for (int i=0;i<2;i++) {
                final int index=i;
                main(() -> { activity.importIndex=index; activity.onActivityResult(10,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(fixture))); });
                await(() -> Tracks.ready(getTargetContext(),index) && !activity.importing,"import " + Tracks.TITLES[i]);
                main(() -> { if(activity.playlist!=null) activity.playlist.dismiss(); });
            }
            main(() -> {
                check(activity.layout.character.hasAlpha(), "transparent character layer");
                CharacterMotion motion = new CharacterMotion();
                motion.step(0,false);
                for(int n=0;n<12;n++) motion.step(.1f,true);
                check(Math.abs(motion.torsoAngle())>.1 && motion.eyeOpen==0, "playing closes eyes and articulates torso");
                float before=motion.torsoAngle(); motion.step(0,false);
                check(motion.torsoAngle()==before && motion.eyeOpen==0, "pause transition begins without a pose jump");
                for(int n=0;n<60;n++) motion.step(.1f,false);
                check(motion.strength==0 && motion.eyeOpen==1 && motion.torsoAngle()==0, "pause opens eyes and settles sway");
                float[] pulse=new float[2400]; for(int i=0;i<pulse.length;i++) pulse[i]=i%20<2?.8f:.02f;
                AudioRhythm signal=AudioRhythm.fromEnergy(pulse);
                check(Math.abs(signal.bpm-120)<3 && signal.level(0)>signal.level(200), "audio pulse analysis detects 120 BPM and changing energy");
                checkPortraitStability();
                check(motion.bodyAngle()==0 && motion.bodyX()==0 && motion.bodyY()==0,"feet remain grounded");
                check(activity.handwriting != null, "bundled handwriting font loaded");
                activity.playback.select(1,false); activity.layout.play.performClick();
            });
            await(() -> activity.playback.isPlaying(),"play via illustrated button");
            await(() -> activity.playback.rhythm!=null,"actual WAV decoded for music-driven motion");
            await(() -> activity.layout.art.motion.eyeOpen==0,"rendered player closes eyes during playback");
            main(() -> {
                check(activity.playback.duration() > 20000,"decoded duration");
                slide(activity.layout.volume,.25f);
                check(Math.abs(activity.playback.volume-.25f)<.05,"volume");
                slide(activity.layout.progress,.28f);
            });
            await(() -> activity.playback.position()>=7900,"seek");
            main(() -> activity.layout.next.performClick());
            await(() -> activity.playback.index==0 && activity.playback.isPlaying(),"next track");
            main(() -> activity.layout.previous.performClick());
            await(() -> activity.playback.index==1 && activity.playback.isPlaying(),"previous wraps fixed playlist");
            main(() -> activity.layout.play.performClick());
            main(() -> check(!activity.playback.isPlaying(),"pause button"));
            await(() -> activity.layout.art.motion.eyeOpen==1,"rendered player opens eyes after pause");
            main(() -> activity.layout.play.performClick());
            await(() -> activity.playback.isPlaying(),"resume");
            main(() -> activity.moveTaskToBack(true));
            Thread.sleep(1200);
            main(() -> check(activity.playback.isPlaying() && activity.playback.foreground,"background playback"));
            main(() -> activity.playback.noisy.onReceive(getTargetContext(),new Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)));
            await(() -> !activity.playback.isPlaying(),"headphone disconnect pauses");
            main(() -> { activity.playback.select(1,false); activity.playback.setVolume(.66f); activity.layout.repaint(); });
            results.putString("stream","PASS\n"+passed);
            results.putString("result","PASS");
            finish(Activity.RESULT_OK,results);
        } catch(Throwable error) {
            StringWriter trace=new StringWriter(); error.printStackTrace(new PrintWriter(trace));
            results.putString("stream","FAIL\n"+passed+trace); finish(Activity.RESULT_CANCELED,results);
        }
    }
    android.graphics.Bitmap renderPortrait(float openness) {
        android.graphics.Bitmap result=android.graphics.Bitmap.createBitmap(204,236,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(result); canvas.translate(-434,-240);
        canvas.drawBitmap(activity.layout.character,0,0,null);
        activity.layout.art.rig.eyes.draw(canvas,openness);
        return result;
    }
    void checkPortraitStability() {
        int reference=activity.layout.character.getPixel(480,1100);
        for(int[] point:new int[][]{{540,385},{450,520},{535,443}}) {
            int color=activity.layout.character.getPixel(point[0],point[1]);
            check(Math.abs(android.graphics.Color.red(color)-android.graphics.Color.red(reference))<12
                &&Math.abs(android.graphics.Color.green(color)-android.graphics.Color.green(reference))<12
                &&Math.abs(android.graphics.Color.blue(color)-android.graphics.Color.blue(reference))<12,"restored face, arm and neck match body skin palette");
        }
        int previous=-1;
        for(float openness:new float[]{1,.75f,.5f,.25f,0}) {
            android.graphics.Bitmap eye=renderPortrait(openness); int count=0;
            for(int y=0;y<eye.getHeight();y++) for(int x=0;x<eye.getWidth();x++) {
                int c=eye.getPixel(x,y),r=android.graphics.Color.red(c),g=android.graphics.Color.green(c),b=android.graphics.Color.blue(c);
                if(g>60 && g<210 && b>g+18 && r>g+8) count++;
            }
            android.util.Log.i("WitchVisual","iris at "+openness+" = "+count+"; previous "+previous);
            try {
                File debug=new File(getTargetContext().getExternalFilesDir(null),"iris-check"); debug.mkdirs();
                try(OutputStream out=new FileOutputStream(new File(debug,"iris-"+openness+".png"))) { eye.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); }
            } catch(IOException e) { throw new RuntimeException(e); }
            check(previous<0 || count<=previous+3,"iris visibility decreases as upper lids close: "+count+" after "+previous);
            if(openness==0) check(count==0,"closed lids leave no pupil fragments");
            previous=count; eye.recycle();
        }
        android.graphics.Bitmap open=renderPortrait(1),half=renderPortrait(.5f),closed=renderPortrait(0);
        int changing=0;
        for(int y=0;y<236;y++) for(int x=0;x<204;x++) {
            int px=x+434,py=y+240;
            boolean eye=(px>=472&&px<=516&&py>=350&&py<=380)||(px>=548&&px<=590&&py>=342&&py<=377);
            int original=open.getPixel(x,y);
            if(!eye && (original!=half.getPixel(x,y)||original!=closed.getPixel(x,y))) throw new AssertionError("Portrait changed outside eyes at "+px+","+py);
            else if(original!=closed.getPixel(x,y)) changing++;
        }
        for(int[] hair:new int[][]{{505,355},{514,356},{541,352},{592,356},{502,347}}) {
            int x=hair[0]-434,y=hair[1]-240;
            check(open.getPixel(x,y)==half.getPixel(x,y)&&open.getPixel(x,y)==closed.getPixel(x,y),"fringe near eyelid stays fixed");
        }
        check(changing>100,"open and closed eyes differ; all other portrait pixels are identical");
        open.recycle(); half.recycle(); closed.recycle();
    }
    void captureRigPreview() throws Exception {
        File folder=new File(getTargetContext().getExternalFilesDir(null),"motion-preview"); folder.mkdirs();
        main(() -> { activity.playback.pause(); activity.visible=false; });
        for(int frame=0;frame<previewFrames;frame++) {
            final int current=frame;
            if(frame==20) { main(() -> activity.playback.play()); await(() -> activity.playback.isPlaying(),"preview playback"); main(() -> activity.playback.player.setLooping(true)); }
            if(frame==140) main(() -> { activity.playback.player.setLooping(false); activity.playback.pause(); });
            android.graphics.Bitmap[] shot={null};
            main(() -> {
                CharacterMotion motion=activity.layout.art.motion;
                if(current==0) {
                    motion.eyeOpen=1; motion.strength=0; motion.phase=0; motion.torso=motion.velocity=0; motion.breathTime=0; motion.angularSpeed=(float)(Math.PI*2/5.8); java.util.Arrays.fill(motion.secondary,0); java.util.Arrays.fill(motion.secondaryVelocity,0);
                }
                motion.step(.05f,current>=20 && current<140,activity.playback.rhythm,Math.max(0,current-20)*50);
                activity.layout.art.lastFrame=0;
                shot[0]=android.graphics.Bitmap.createBitmap(activity.layout.getWidth(),activity.layout.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                activity.layout.draw(new android.graphics.Canvas(shot[0]));
            });
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(new File(folder,String.format(java.util.Locale.ROOT,"frame-%02d.jpg",frame))))) {
                shot[0].compress(android.graphics.Bitmap.CompressFormat.JPEG,92,out);
            }
            shot[0].recycle();
        }
    }
    static void slide(android.widget.SeekBar bar,float fraction) {
        long time=android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,bar.getWidth()*fraction,bar.getHeight()/2f,0);
        android.view.MotionEvent up=android.view.MotionEvent.obtain(time,time+50,android.view.MotionEvent.ACTION_UP,bar.getWidth()*fraction,bar.getHeight()/2f,0);
        bar.onTouchEvent(down); bar.onTouchEvent(up); down.recycle(); up.recycle();
    }
    interface Condition { boolean get(); }
    void main(Runnable r) {
        Throwable[] error={null};
        runOnMainSync(() -> { try { r.run(); } catch(Throwable t) { error[0]=t; } });
        if(error[0]!=null) throw new AssertionError("UI check failed",error[0]);
    }
    void await(Condition condition,String name) throws Exception {
        long deadline=System.currentTimeMillis()+20000; boolean[] ok={false};
        do { main(() -> ok[0]=condition.get()); if(ok[0]) { passed.append(name).append("\n"); Bundle status=new Bundle(); status.putString("stream", "PASS: "+name+"\n"); sendStatus(1,status); return; } Thread.sleep(100); } while(System.currentTimeMillis()<deadline);
        throw new AssertionError("Timed out: "+name);
    }
    void check(boolean value,String name) { if(!value) throw new AssertionError(name); passed.append(name).append("\n"); }
    static void writeWave(File f) throws IOException {
        int rate=22050, frames=rate*60, bytes=frames*2;
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f), 65536))) {
            out.writeBytes("RIFF"); le32(out,bytes+36); out.writeBytes("WAVEfmt "); le32(out,16); le16(out,1); le16(out,1); le32(out,rate); le32(out,rate*2); le16(out,2); le16(out,16); out.writeBytes("data"); le32(out,bytes);
            for(int i=0;i<frames;i++) le16(out,(int)(Math.sin(i*2*Math.PI*440/rate)*(200+2400*Math.exp(-((i%(rate/2))/(double)(rate/2))*12))));
        }
    }
    static void le16(DataOutputStream out,int n)throws IOException { out.writeByte(n);out.writeByte(n>>8); }
    static void le32(DataOutputStream out,int n)throws IOException { le16(out,n);le16(out,n>>16); }
}
