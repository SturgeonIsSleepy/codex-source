package cp.player.ui.artwork;

import android.graphics.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ArtIntegrityTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private Bitmap asset(String name)throws IOException {
        try(InputStream input=context.getAssets().open(name)){return BitmapFactory.decodeStream(input);}
    }
    @Test public void expressionPreservesHairSkinAndHands()throws Exception {
        Bitmap portrait=CharacterArtwork.prepare(asset("elaina-layer.png"),asset("skin-restoration.png"));
        int reference=portrait.getPixel(480,1100);
        for(int[] point:new int[][]{{540,385},{450,520},{535,443}}){
            int color=portrait.getPixel(point[0],point[1]);
            assertTrue(Math.abs(Color.red(color)-Color.red(reference))<12);
            assertTrue(Math.abs(Color.green(color)-Color.green(reference))<12);
            assertTrue(Math.abs(Color.blue(color)-Color.blue(reference))<12);
        }
        EyeBlink blink=new EyeBlink(portrait);
        int previous=-1;
        File output=new File(context.getExternalFilesDir(null),"art-check");assertTrue(output.mkdirs()||output.isDirectory());
        int[] original=new int[portrait.getWidth()*portrait.getHeight()];portrait.getPixels(original,0,portrait.getWidth(),0,0,portrait.getWidth(),portrait.getHeight());
        for(float open:new float[]{1,.75f,.5f,.25f,0}){
            Bitmap frame=portrait.copy(Bitmap.Config.ARGB_8888,true);blink.draw(new Canvas(frame),open);
            int[] actual=new int[original.length];frame.getPixels(actual,0,frame.getWidth(),0,0,frame.getWidth(),frame.getHeight());
            int iris=0,changed=0;
            for(int y=0;y<frame.getHeight();y++)for(int x=0;x<frame.getWidth();x++){
                boolean eye=(x>=472&&x<=516&&y>=350&&y<=380)||(x>=547&&x<=590&&y>=341&&y<=377);
                int i=y*frame.getWidth()+x;
                if(!eye && original[i]!=actual[i])fail("Expression altered hair, face or hand outside the eyelids at "+x+","+y);
                if(original[i]!=actual[i])changed++;
                if(eye){int c=actual[i],r=Color.red(c),g=Color.green(c),b=Color.blue(c);if(g>60&&g<210&&b>g+18&&r>g+8)iris++;}
            }
            if(previous>=0)assertTrue("Iris must disappear progressively",iris<=previous+3);
            if(open==0){assertEquals(0,iris);assertTrue(changed>100);}
            previous=iris;
            Bitmap detail=Bitmap.createBitmap(frame,395,240,255,410);
            try(OutputStream stream=new FileOutputStream(new File(output,"expression-"+open+".png"))){detail.compress(Bitmap.CompressFormat.PNG,100,stream);}
            detail.recycle();frame.recycle();
        }
        portrait.recycle();
    }
    @Test public void drawingIsStableAcrossDisplayDensities()throws Exception {
        Bitmap source=CharacterArtwork.prepare(asset("elaina-layer.png"),asset("skin-restoration.png"));
        CharacterRig rig=new CharacterRig(source);CharacterMotion motion=new CharacterMotion();
        Bitmap a=Bitmap.createBitmap(865,1280,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(865,1280,Bitmap.Config.ARGB_8888);
        a.setDensity(160);b.setDensity(480);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        rig.draw(new Canvas(a),paint,motion);rig.draw(new Canvas(b),paint,motion);
        int[] first=new int[865*1280],second=new int[first.length];
        a.getPixels(first,0,865,0,0,865,1280);b.getPixels(second,0,865,0,0,865,1280);
        assertArrayEquals("Head, hat and body must have identical geometry on high-density phones",first,second);
        a.recycle();b.recycle();source.recycle();
    }
    @Test public void animationSettlesWithoutPoseJumps(){
        CharacterMotion motion=new CharacterMotion();
        for(int i=0;i<360;i++)motion.step(1/60f,true);
        assertEquals(0,motion.eyeOpen,0);
        float angle=motion.torsoAngle();motion.step(0,false);assertEquals(angle,motion.torsoAngle(),0);
        for(int i=0;i<720;i++)motion.step(1/60f,false);
        assertEquals(1,motion.eyeOpen,0);assertEquals(0,motion.torsoAngle(),.001f);
        assertFalse(motion.transitioning(false));
    }
    @Test public void bufferingKeepsEyesClosedWhileMotionSettles(){
        CharacterMotion motion=new CharacterMotion();
        for(int i=0;i<360;i++)motion.step(1/60f,true);
        for(int i=0;i<720;i++)motion.step(1/60f,false,null,0,Float.NaN,true);
        assertEquals(0,motion.eyeOpen,0);assertEquals(0,motion.torsoAngle(),.001f);
        assertFalse(motion.transitioning(true));
        for(int i=0;i<60;i++)motion.step(1/60f,false,null,0,Float.NaN,false);
        assertEquals(1,motion.eyeOpen,0);
    }
    @Test public void staticModePreservesTheRepairedPortraitAndChangesOnlyEyelids()throws Exception {
        final Bitmap[] frames=new Bitmap[3];
        final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                WitchPlayerView view=new WitchPlayerView(context);view.setArtworkOnly(true);
                int width=android.view.View.MeasureSpec.makeMeasureSpec(865,android.view.View.MeasureSpec.EXACTLY);
                int height=android.view.View.MeasureSpec.makeMeasureSpec(1280,android.view.View.MeasureSpec.EXACTLY);
                view.measure(width,height);view.layout(0,0,865,1280);
                for(int i=0;i<3;i++) {
                    if(i>0)view.setMotionEnabled(false);
                    view.update("fixture",false,i==1,i==1,0,0,null);
                    frames[i]=Bitmap.createBitmap(865,1280,Bitmap.Config.ARGB_8888);
                    view.draw(new Canvas(frames[i]));
                }
            }catch(Throwable error){failure.set(error);}
        });
        if(failure.get()!=null)throw new AssertionError(failure.get());
        int[][] pixels=new int[3][865*1280];
        for(int i=0;i<3;i++)frames[i].getPixels(pixels[i],0,865,0,0,865,1280);
        assertArrayEquals("Disabling motion and returning to pause must keep the same repaired portrait",pixels[0],pixels[2]);
        Bitmap original=asset("elaina-layer.png");
        float sx=663f/original.getWidth(),sy=994f/original.getHeight();original.recycle();
        Rect left=new Rect((int)(91+472*sx)-3,(int)(183+350*sy)-3,(int)Math.ceil(91+516*sx)+3,(int)Math.ceil(183+380*sy)+3);
        Rect right=new Rect((int)(91+547*sx)-3,(int)(183+341*sy)-3,(int)Math.ceil(91+590*sx)+3,(int)Math.ceil(183+377*sy)+3);
        int changed=0;
        for(int y=0;y<1280;y++)for(int x=0;x<865;x++) {
            int i=y*865+x;
            if(pixels[0][i]!=pixels[1][i]){
                changed++;
                assertTrue("Buffering/static expressions must not change face, hair, hat or hands",left.contains(x,y)||right.contains(x,y));
            }
        }
        assertTrue("Requested playback still closes eyes with motion disabled",changed>20);
        for(Bitmap frame:frames)frame.recycle();
    }

}
