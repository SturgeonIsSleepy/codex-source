package com.wanderingwitch.player;

import android.media.*;
import android.os.Process;
import java.io.File;
import java.nio.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Local PCM analysis of the selected recording. No microphone or network access. */
final class AudioRhythm {
    static final int WINDOW_MS=25;
    final float[] energy;
    final float bpm;
    final int offsetMs;
    AudioRhythm(float[] energy,float bpm,int offsetMs) { this.energy=energy; this.bpm=bpm; this.offsetMs=offsetMs; }
    float level(int positionMs) {
        float index=Math.max(0,positionMs)/(float)WINDOW_MS;
        int i=(int)index;
        if(i>=energy.length) return 0;
        return energy[i]+(energy[Math.min(i+1,energy.length-1)]-energy[i])*(index-i);
    }
    static AudioRhythm analyze(File file,BooleanSupplier cancelled) throws Exception {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        MediaExtractor extractor=new MediaExtractor(); MediaCodec codec=null;
        ArrayList<Float> windows=new ArrayList<>();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat candidate=extractor.getTrackFormat(i);
                String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(mime!=null && mime.startsWith("audio/")) { format=candidate; extractor.selectTrack(i); break; }
            }
            if(format==null) throw new IllegalArgumentException("No audio track");
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format,null,null,0); codec.start();
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding=AudioFormat.ENCODING_PCM_16BIT;
            boolean inputDone=false,outputDone=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            double sum=0; int samples=0;
            long deadline=android.os.SystemClock.elapsedRealtime()+90000;
            while(!outputDone && !cancelled.getAsBoolean() && android.os.SystemClock.elapsedRealtime()<deadline) {
                if(!inputDone) {
                    int slot=codec.dequeueInputBuffer(10000);
                    if(slot>=0) {
                        ByteBuffer input=codec.getInputBuffer(slot);
                        int size=extractor.readSampleData(input,0);
                        if(size<0) { codec.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true; }
                        else { codec.queueInputBuffer(slot,0,size,extractor.getSampleTime(),0); extractor.advance(); }
                    }
                }
                int slot=codec.dequeueOutputBuffer(info,10000);
                if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat output=codec.getOutputFormat();
                    rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    encoding=output.containsKey(MediaFormat.KEY_PCM_ENCODING)?output.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                    if(encoding!=AudioFormat.ENCODING_PCM_16BIT && encoding!=AudioFormat.ENCODING_PCM_FLOAT) throw new IllegalArgumentException("Unsupported PCM");
                } else if(slot>=0) {
                    ByteBuffer output=codec.getOutputBuffer(slot).order(ByteOrder.nativeOrder());
                    output.position(info.offset); output.limit(info.offset+info.size);
                    int width=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                    int windowSamples=Math.max(1,rate*channels*WINDOW_MS/1000);
                    while(output.remaining()>=width) {
                        float value=width==4?output.getFloat():output.getShort()/32768f;
                        sum+=value*value; samples++;
                        if(samples>=windowSamples) {
                            windows.add((float)Math.sqrt(sum/samples)); sum=0; samples=0;
                            if(windows.size()>144000) throw new IllegalArgumentException("Analysis duration limit");
                        }
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(slot,false);
                }
            }
            if(cancelled.getAsBoolean()) return null;
            if(!outputDone) return null;
            if(samples>0) windows.add((float)Math.sqrt(sum/samples));
            float[] values=new float[windows.size()]; for(int i=0;i<values.length;i++) values[i]=windows.get(i);
            return fromEnergy(values);
        } finally {
            if(codec!=null) { try { codec.stop(); } catch(RuntimeException ignored) {} codec.release(); }
            extractor.release();
        }
    }
    static AudioRhythm fromEnergy(float[] values) {
        if(values.length==0) return new AudioRhythm(new float[]{0},110,0);
        float[] sorted=values.clone(); Arrays.sort(sorted);
        float reference=Math.max(.0001f,sorted[(int)((sorted.length-1)*.95)]);
        float[] flux=new float[values.length];
        for(int i=0;i<values.length;i++) {
            values[i]=Math.min(1,values[i]/reference);
            if(i>0) flux[i]=Math.max(0,values[i]-values[i-1]);
        }
        float best=0,bpm=110; int bestLag=0;
        for(int lag=14;lag<=37;lag++) {
            double correlation=0,normA=0,normB=0;
            for(int i=lag;i<flux.length;i++) { correlation+=flux[i]*flux[i-lag]; normA+=flux[i]*flux[i]; normB+=flux[i-lag]*flux[i-lag]; }
            float score=(float)(correlation/Math.sqrt(Math.max(1e-12,normA*normB)));
            // Prefer a nearby, comfortable pulse when harmonics tie.
            score*=1-.04f*Math.abs(60000f/(lag*WINDOW_MS)-110)/110;
            if(score>best) { best=score; bestLag=lag; }
        }
        int offset=0;
        if(bestLag>0 && best>.12f) {
            bpm=60000f/(bestLag*WINDOW_MS);
            double strongest=-1;
            for(int candidate=0;candidate<bestLag;candidate++) {
                double score=0; for(int i=candidate;i<flux.length;i+=bestLag) score+=flux[i];
                if(score>strongest) { strongest=score; offset=candidate*WINDOW_MS; }
            }
        }
        return new AudioRhythm(values,bpm,offset);
    }
}
