package cp.player.ui.artwork;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import cp.player.MainActivity;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ArtworkPowerTest {
    @Test public void coveredArtworkStopsDrawingAndResumesWithTheSameAnimation() {
        WitchPlayerView.prepareAssets(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext());
        AtomicInteger draws=new AtomicInteger(); WitchPlayerView[] holder=new WitchPlayerView[1];
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                WitchPlayerView view=new WitchPlayerView(activity);holder[0]=view;
                activity.setContentView(view);
                view.getViewTreeObserver().addOnDrawListener(draws::incrementAndGet);
                view.setPlayback(true,true,0);
            });
            SystemClock.sleep(300);assertTrue("Visible animation continues",draws.get()>2);
            scenario.onActivity(activity->holder[0].setArtworkActive(false));
            SystemClock.sleep(100);int covered=draws.get();
            scenario.onActivity(activity->{for(int i=0;i<20;i++)holder[0].setPlayback(true,true,i*500);});
            SystemClock.sleep(250);assertEquals("Progress updates must not repaint covered artwork",covered,draws.get());
            scenario.onActivity(activity->holder[0].setArtworkActive(true));
            SystemClock.sleep(300);assertTrue("Uncovering resumes motion",draws.get()>covered+2);
            scenario.onActivity(activity->holder[0].setVisibility(View.INVISIBLE));
            SystemClock.sleep(100);int hidden=draws.get();
            SystemClock.sleep(250);assertEquals("Invisible artwork has no frame loop",hidden,draws.get());
        }
    }
    @Test public void nightBackdropRemovesOnlyExteriorPaperAndNeverEditsItsSource() {
        Bitmap source=Bitmap.createBitmap(9,9,Bitmap.Config.ARGB_8888);source.eraseColor(Color.WHITE);
        for(int y=2;y<=6;y++)for(int x=2;x<=6;x++)source.setPixel(x,y,Color.rgb(50,140,220));
        source.setPixel(4,4,Color.WHITE);
        int[] before=new int[81];source.getPixels(before,0,9,0,0,9,9);
        Bitmap night=NightBackdrop.create(source);
        assertEquals(0,Color.alpha(night.getPixel(0,0)));
        assertEquals(255,Color.alpha(night.getPixel(4,4)));
        assertTrue(Color.blue(night.getPixel(2,2))>Color.red(night.getPixel(2,2)));
        int[] after=new int[81];source.getPixels(after,0,9,0,0,9,9);
        assertArrayEquals(before,after);
        night.recycle();source.recycle();
    }
}
