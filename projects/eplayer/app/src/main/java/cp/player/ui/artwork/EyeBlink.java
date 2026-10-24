package cp.player.ui.artwork;

import android.graphics.*;

/** Upper lids reveal a fixed eye texture. No portrait swapping or iris scaling. */
final class EyeBlink {
    private final Eye[] eyes;
    EyeBlink(Bitmap portrait) {
        eyes=new Eye[]{
            new Eye(portrait,new Rect(472,350,516,380),
                CharacterRig.polygon(473,360,480,355,491,352,499,354,501,357,509,359,513,364,510,373,501,378,483,378,474,370),
                new float[]{475,363,484,353,502,353,511,362},new float[]{506,378,484,379},
                new float[]{475,365,486,369,501,367,511,362},488,383,505,380),
            new Eye(portrait,new Rect(547,341,590,377),
                CharacterRig.polygon(548,352,554,347,562,343,574,343,584,347,589,352,589,365,582,371,565,371,552,365),
                new float[]{550,354,558,343,578,343,588,352},new float[]{581,370,557,372},
                new float[]{550,357,560,363,578,361,588,354},554,380,574,376)
        };
    }
    void draw(Canvas canvas,float openness) {
        float open=Math.max(0,Math.min(1,openness));
        if(open>=1) return;
        open=open*open*(3-2*open);
        for(Eye eye:eyes) eye.draw(canvas,open);
    }
    private static final class Eye {
        final Rect bounds;
        final Bitmap texture;
        final Path window,aperture=new Path(),lid=new Path();
        final float[] upper,lower,closed,current=new float[8];
        final Paint skin=new Paint(Paint.ANTI_ALIAS_FLAG),ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint pixels=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Eye(Bitmap portrait,Rect bounds,Path window,float[] upper,float[] lower,float[] closed,int x1,int y1,int x2,int y2) {
            this.bounds=bounds; this.window=window; this.upper=upper; this.lower=lower; this.closed=closed;
            texture=Bitmap.createBitmap(portrait,bounds.left,bounds.top,bounds.width(),bounds.height());
            skin.setShader(new LinearGradient(bounds.left,0,bounds.right,0,skinColor(portrait,x1,y1),skinColor(portrait,x2,y2),Shader.TileMode.CLAMP));
            ink.setColor(Color.rgb(42,34,32)); ink.setStyle(Paint.Style.STROKE); ink.setStrokeCap(Paint.Cap.ROUND);
        }
        void draw(Canvas canvas,float open) {
            for(int i=0;i<8;i++) current[i]=mix(closed[i],upper[i],open);
            lid.reset(); lid.moveTo(current[0],current[1]);
            lid.cubicTo(current[2],current[3],current[4],current[5],current[6],current[7]);
            aperture.set(lid);
            aperture.cubicTo(mix(closed[4],lower[0],open),mix(closed[5],lower[1],open),
                mix(closed[2],lower[2],open),mix(closed[3],lower[3],open),current[0],current[1]);
            aperture.close();
            canvas.save(); canvas.clipPath(window); canvas.drawPath(window,skin);
            if(open>0) {
                canvas.save(); canvas.clipPath(aperture);
                canvas.drawBitmap(texture,null,bounds,pixels); canvas.restore();
            }
            ink.setStrokeWidth(2.1f+.5f*open); canvas.drawPath(lid,ink); canvas.restore();
        }
        private static float mix(float a,float b,float t) { return a+(b-a)*t; }
        private static int skinColor(Bitmap image,int cx,int cy) {
            int r=0,g=0,b=0;
            for(int y=cy-1;y<=cy+1;y++) for(int x=cx-1;x<=cx+1;x++) {
                int color=image.getPixel(x,y); r+=Color.red(color);g+=Color.green(color);b+=Color.blue(color);
            }
            return Color.rgb(r/9,g/9,b/9);
        }
    }
}
