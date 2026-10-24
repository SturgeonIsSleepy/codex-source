package cp.player.ui.artwork;

import android.graphics.*;

/** Cached anatomical layers: grounded legs, articulated torso, complete head, delayed hair and hem. */
final class CharacterRig {
    final Bitmap source,torso,legs;
    final EyeBlink eyes;
    final Path allParts=new Path();
    final Path[] masks=new Path[4];
    final Bitmap[] pieces=new Bitmap[4];
    final RectF[] bounds=new RectF[4];
    final float[][] pivots={{370,451},{677,420},{584,94},{299,848}};
    CharacterRig(Bitmap source) {
        this.source=source; eyes=new EyeBlink(source);
        masks[0]=polygon(366,448,386,456,359,492,334,532,312,578,290,632,282,652,276,618,292,564,326,514,350,482);
        masks[1]=polygon(672,403,691,422,715,459,748,493,775,541,787,583,774,622,751,648,727,647,743,620,753,588,741,552,716,506,693,473,675,439);
        masks[2]=polygon(583,76,602,78,624,85,648,87,670,96,675,105,664,115,644,121,623,116,606,109,585,105);
        masks[3]=polygon(299,842,313,854,296,875,276,894,300,905,324,910,307,929,281,923,260,908,242,910,219,901,231,887,260,870,283,851);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        for(int i=0;i<masks.length;i++) {
            allParts.addPath(masks[i]); RectF r=new RectF(); masks[i].computeBounds(r,true);
            r.set((float)Math.floor(r.left)-2,(float)Math.floor(r.top)-2,(float)Math.ceil(r.right)+2,(float)Math.ceil(r.bottom)+2); bounds[i]=r;
            pieces[i]=Bitmap.createBitmap((int)r.width(),(int)r.height(),Bitmap.Config.ARGB_8888);
            pieces[i].setDensity(Bitmap.DENSITY_NONE);
            Canvas c=new Canvas(pieces[i]); c.translate(-r.left,-r.top); c.clipPath(masks[i]); c.drawBitmap(source,0,0,p);
        }
        torso=Bitmap.createBitmap(source.getWidth(),945,Bitmap.Config.ARGB_8888);
        torso.setDensity(Bitmap.DENSITY_NONE);
        Canvas c=new Canvas(torso); c.clipOutPath(allParts); c.drawBitmap(source,0,0,p);
        legs=Bitmap.createBitmap(source,0,930,source.getWidth(),source.getHeight()-930);
        legs.setDensity(Bitmap.DENSITY_NONE);
    }
    static Path polygon(float... points) {
        Path p=new Path(); p.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2) p.lineTo(points[i],points[i+1]); p.close(); return p;
    }
    void draw(Canvas canvas,Paint paint,CharacterMotion motion) {
        canvas.save(); canvas.translate(91,183); canvas.scale(663f/source.getWidth(),994f/source.getHeight());
        canvas.drawBitmap(legs,0,930,paint);
        canvas.save(); canvas.rotate(motion.torsoAngle(),530,940); canvas.scale(1,motion.breathScale(),530,940);
        canvas.drawBitmap(torso,0,0,paint);
        for(int i=0;i<pieces.length;i++) {
            canvas.save(); canvas.rotate(motion.angle(i),pivots[i][0],pivots[i][1]);
            canvas.drawBitmap(pieces[i],bounds[i].left,bounds[i].top,paint); canvas.restore();
        }
        eyes.draw(canvas,motion.eyeOpen);
        canvas.restore(); canvas.restore();
    }
}
