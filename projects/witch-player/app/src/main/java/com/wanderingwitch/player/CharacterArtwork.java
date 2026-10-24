package com.wanderingwitch.player;

import android.graphics.*;

/** Apply the artist's registered skin restoration once; all animation states share the result. */
final class CharacterArtwork {
    private static final Rect TARGET=new Rect(395,240,650,650);
    static Bitmap prepare(Bitmap base,Bitmap restoration) {
        Bitmap result=base.copy(Bitmap.Config.ARGB_8888,true);
        int w=TARGET.width(),h=TARGET.height();
        Bitmap patch=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(patch);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(restoration,null,new Rect(0,0,w,h),paint);
        matchSkinPalette(patch,base);
        int[] colors={Color.TRANSPARENT,Color.WHITE,Color.WHITE,Color.TRANSPARENT};
        Shader x=new LinearGradient(0,0,w,0,colors,new float[]{0,2f/w,1-2f/w,1},Shader.TileMode.CLAMP);
        Shader y=new LinearGradient(0,0,0,h,colors,new float[]{0,2f/h,1-2f/h,1},Shader.TileMode.CLAMP);
        paint.setShader(new ComposeShader(x,y,PorterDuff.Mode.MULTIPLY));
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(0,0,w,h,paint);
        paint.setShader(null); paint.setXfermode(null);
        new Canvas(result).drawBitmap(patch,null,TARGET,paint);
        base.recycle(); restoration.recycle(); patch.recycle();
        return result;
    }
    // Generated retouches can shift pale skin slightly. Match them to the existing body once.
    private static void matchSkinPalette(Bitmap patch,Bitmap body) {
        int[][] points={{540,385},{532,442},{450,520}};
        float[] shift=new float[3];
        for(int[] point:points) {
            int before=body.getPixel(point[0],point[1]);
            int after=patch.getPixel(point[0]-TARGET.left,point[1]-TARGET.top);
            shift[0]+=(Color.red(before)-Color.red(after))/3f;
            shift[1]+=(Color.green(before)-Color.green(after))/3f;
            shift[2]+=(Color.blue(before)-Color.blue(after))/3f;
        }
        int w=patch.getWidth(),h=patch.getHeight(); int[] colors=new int[w*h];
        patch.getPixels(colors,0,w,0,0,w,h);
        for(int i=0;i<colors.length;i++) {
            int c=colors[i],r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            float skin=unit((r-190)/40f)*unit((g-150)/35f)*unit((r-g-6)/6f)*unit((g-b-1)/4f);
            colors[i]=Color.argb(Color.alpha(c),channel(r+skin*shift[0]),channel(g+skin*shift[1]),channel(b+skin*shift[2]));
        }
        patch.setPixels(colors,0,w,0,0,w,h);
    }
    private static float unit(float x) { return Math.max(0,Math.min(1,x)); }
    private static int channel(float x) { return Math.max(0,Math.min(255,Math.round(x))); }
    private CharacterArtwork() {}
}
