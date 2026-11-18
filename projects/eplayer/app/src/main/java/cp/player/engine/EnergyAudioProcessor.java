package cp.player.engine;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.BooleanSupplier;

/** Samples PCM for animation; forwards every audio byte without changing it. */
public final class EnergyAudioProcessor extends BaseAudioProcessor {
    private final BooleanSupplier measure;
    public EnergyAudioProcessor() { this(MusicPulse::shouldMeasure); }
    EnergyAudioProcessor(BooleanSupplier measure) { this.measure = measure; }
    @Override protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat format) {
        switch(format.encoding) {
            case C.ENCODING_PCM_8BIT: case C.ENCODING_PCM_16BIT:
            case C.ENCODING_PCM_24BIT: case C.ENCODING_PCM_32BIT: case C.ENCODING_PCM_FLOAT:
                return format;
            default: return AudioProcessor.AudioFormat.NOT_SET;
        }
    }
    @Override public void queueInput(ByteBuffer input) {
        // Media3 also queues its shared EMPTY_BUFFER while draining the pipeline.
        // Passing that singleton to replaceOutputBuffer(0).put(input) would copy it onto itself.
        if (!input.hasRemaining()) return;
        if (measure.getAsBoolean()) meter(input);
        ByteBuffer output=replaceOutputBuffer(input.remaining());output.put(input);output.flip();
    }
    private void meter(ByteBuffer input) {
        ByteBuffer read=input.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int encoding=inputAudioFormat.encoding;
        int width=encoding==C.ENCODING_PCM_8BIT?1:encoding==C.ENCODING_PCM_16BIT?2:encoding==C.ENCODING_PCM_24BIT?3:4;
        // Keep metering bounded even when a decoder emits a large PCM block.
        // A stride congruent to 1 modulo the channel count visits every channel.
        int channels=Math.max(1,inputAudioFormat.channelCount);
        int samples=read.remaining()/width;
        int stride=Math.max(channels*4+1, (samples/128/channels+1)*channels+1);
        double sum=0;int count=0;
        while(read.remaining()>=width) {
            float value;
            switch(encoding) {
                case C.ENCODING_PCM_8BIT: value=((read.get()&255)-128)/128f;break;
                case C.ENCODING_PCM_16BIT: value=read.getShort()/32768f;break;
                case C.ENCODING_PCM_24BIT:
                    int bits=(read.get()&255)|((read.get()&255)<<8)|(read.get()<<16);
                    value=bits/8388608f;break;
                case C.ENCODING_PCM_32BIT: value=read.getInt()/2147483648f;break;
                default: value=read.getFloat();break;
            }
            sum+=value*value;count++;
            // Metering does not need every sample; audio output still retains the complete buffer.
            read.position(read.position()+Math.min(read.remaining(),width*(stride-1)));
        }
        if(count>0)MusicPulse.publish((float)Math.sqrt(sum/count));
    }
}
