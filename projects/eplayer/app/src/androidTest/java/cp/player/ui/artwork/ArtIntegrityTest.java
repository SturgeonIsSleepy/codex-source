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
}
