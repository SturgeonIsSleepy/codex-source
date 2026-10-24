package cp.player.ui.artwork;

/** Continuous weight shifts; head/hat stay together, secondary layers carry their own inertia. */
final class CharacterMotion {
    float strength,eyeOpen=1,energy=.35f;
    double phase,breathTime;
    float torso,velocity;
    float angularSpeed=(float)(Math.PI*2/5.8);
    final float[] secondary=new float[4],secondaryVelocity=new float[4];
    private static final float[] STIFFNESS={29,23,35,19};
    private static final float[] DAMPING={10.5f,9.5f,11.5f,8.8f};
    void step(float seconds,boolean playing) { step(seconds,playing,null,0); }
    void step(float seconds,boolean playing,AudioRhythm rhythm,int positionMs) {
        step(seconds,playing,rhythm,positionMs,Float.NaN);
    }
    void step(float seconds,boolean playing,AudioRhythm rhythm,int positionMs,float liveEnergy) {
        float dt=Math.max(0,Math.min(seconds,.12f));
        boolean live=Float.isFinite(liveEnergy);
        float targetEnergy=rhythm!=null?rhythm.level(positionMs):live?liveEnergy:.35f;
        float targetSpeed=rhythm==null?(float)(Math.PI*2/5.8):
                (float)(Math.PI*2*Math.max(70,Math.min(180,rhythm.bpm))/480);
        int count=Math.max(1,(int)Math.ceil(dt/.008f)); float h=dt/count;
        for(int n=0;n<count;n++) {
            strength+=((playing?1:0)-strength)*(1-(float)Math.exp(-h/(playing?.85f:.65f)));
            energy+=(targetEnergy-energy)*(1-(float)Math.exp(-h/(targetEnergy>energy?.32f:.75f)));
            angularSpeed+=(targetSpeed-angularSpeed)*(1-(float)Math.exp(-h/1.6f));
            // Continue the movement through release; pausing never freezes a hair strand mid-flight.
            if(playing || strength>.001f) {
                phase+=h*angularSpeed;
                if(playing && rhythm!=null) {
                    double target=(positionMs-rhythm.offsetMs)/1000.0*targetSpeed;
                    double error=Math.atan2(Math.sin(target-phase),Math.cos(target-phase));
                    phase+=Math.max(-.18,Math.min(.18,error/8))*h;
                }
                phase%=Math.PI*2;
            }
            breathTime+=h*strength;
            float musicalAmplitude=rhythm==null&&!live?.78f:.18f+.82f*energy;
            float target=strength*musicalAmplitude*(float)(Math.sin(phase)+.075*Math.sin(2*phase+.5));
            velocity+=(16*(target-torso)-8*velocity)*h;
            torso+=velocity*h;
            for(int i=0;i<secondary.length;i++) {
                float lag=wind(i)*strength - velocity*(i==0?.38f:i==1?.5f:i==2?.18f:.3f);
                secondaryVelocity[i]+=(STIFFNESS[i]*(lag-secondary[i])-DAMPING[i]*secondaryVelocity[i])*h;
                secondary[i]+=secondaryVelocity[i]*h;
            }
        }
        if(playing) eyeOpen=Math.max(0,eyeOpen-dt/.18f);
        else eyeOpen=Math.min(1,eyeOpen+dt/.26f);
        if(!playing && strength<.001f) strength=0;
        if(strength==0 && Math.abs(torso)<.001f && Math.abs(velocity)<.003f) torso=velocity=0;
        if(strength==0) for(int i=0;i<secondary.length;i++) {
            if(Math.abs(secondary[i])<.001f && Math.abs(secondaryVelocity[i])<.003f) secondary[i]=secondaryVelocity[i]=0;
        }
    }
    private float wind(int part) {
        double t=breathTime;
        if(part==0) return (float)(.58*Math.sin(phase-.7)+.13*Math.sin(t*.83+.5));
        if(part==1) return (float)(-.48*Math.sin(phase-1.0)+.12*Math.sin(t*.69+1.9));
        if(part==2) return (float)(.24*Math.sin(phase-1.25)+.06*Math.sin(t*.91));
        return (float)(.33*Math.sin(phase-1.4)+.08*Math.sin(t*.57+1.1));
    }
    float torsoAngle() { return torso; }
    float breathScale() { return 1+strength*.0012f*(float)Math.sin(breathTime*Math.PI*2/5.6); }
    float bodyAngle() { return 0; }
    float bodyX() { return 0; }
    float bodyY() { return 0; }
    boolean transitioning(boolean playing) {
        if(strength>0 || Math.abs(torso)>.001f || Math.abs(velocity)>.001f || Math.abs(eyeOpen-(playing?0:1))>.001f) return true;
        for(int i=0;i<secondary.length;i++) if(secondary[i]!=0 || secondaryVelocity[i]!=0) return true;
        return false;
    }
    float angle(int part) { return secondary[part]; }
}
