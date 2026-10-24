package com.wanderingwitch.player;
import java.io.*;
import java.util.Locale;
public final class MotionCheck {
 static void check(boolean ok,String reason) { if(!ok) throw new AssertionError(reason); }
 static void bounded(CharacterMotion m) {
  check(Float.isFinite(m.torso) && Math.abs(m.torso)<1.4,"torso stays bounded");
  for(int i=0;i<4;i++) check(Float.isFinite(m.angle(i)) && Math.abs(m.angle(i))<1.6,"secondary stays bounded");
 }
 static CharacterMotion run(float dt) {
  CharacterMotion m=new CharacterMotion();
  for(int i=0;i<Math.round(4/dt);i++)m.step(dt,true);
  return m;
 }
 public static void main(String[] args) throws Exception {
  float[] pulse=new float[2400]; for(int i=0;i<pulse.length;i++)pulse[i]=i%20<2?.8f:.02f;
  AudioRhythm rhythm=AudioRhythm.fromEnergy(pulse);
  check(Math.abs(rhythm.bpm-120)<3,"120 BPM");
  CharacterMotion m=new CharacterMotion();
  for(int i=0;i<1200;i++) {m.step(1/60f,true,rhythm,i*1000/60);bounded(m);}
  check(m.eyeOpen==0,"playing closes eyes");
  float angle=m.torso,velocity=m.velocity,part=m.angle(0),partVelocity=m.secondaryVelocity[0];
  m.step(0,false,rhythm,20000);
  check(m.torso==angle && m.velocity==velocity && m.angle(0)==part && m.secondaryVelocity[0]==partVelocity,"pause preserves pose and velocities");
  m.step(1/60f,false,rhythm,20000);
  check(Math.abs(m.torso-angle)<.04 && Math.abs(m.angle(0)-part)<.05,"first release frame is continuous");
  for(int i=0;i<480;i++)m.step(1/60f,false,rhythm,20000);
  check(m.eyeOpen==1 && m.strength==0 && m.torso==0 && !m.transitioning(false),"all layers settle and stop rendering");
  CharacterMotion a=run(1/30f),b=run(1/120f);
  check(Math.abs(a.torso-b.torso)<.012 && Math.abs(a.angle(0)-b.angle(0))<.012,"30 and 120 FPS agree");
  for(int i=0;i<900;i++){m.step((i%3+1)/90f,i%13<7,rhythm,i%2==0?120000:0);bounded(m);}
  check(m.bodyAngle()==0 && m.bodyX()==0 && m.bodyY()==0,"feet grounded");
  System.out.println("PASS: bounded sway; pause preserves pose and velocities; smooth release; full rest; 30/120 FPS agreement; rapid toggles and seeks; fixed feet; 120 BPM analysis");
  if(args.length>0) try(PrintWriter out=new PrintWriter(args[0])) {
   out.println("["); m=new CharacterMotion();
   for(int i=0;i<300;i++) {
    boolean play=i>=20&&i<140; m.step(.05f,play, rhythm,Math.max(0,i-20)*50);
    out.printf(Locale.ROOT,"%s{\"playing\":%s,\"eye\":%.6f,\"torso\":%.6f,\"breath\":%.7f,\"parts\":[%.6f,%.6f,%.6f,%.6f]}",i==0?"":",\n",play,m.eyeOpen,m.torsoAngle(),m.breathScale(),m.angle(0),m.angle(1),m.angle(2),m.angle(3));
   }out.println("\n]");
  }
 }
}
