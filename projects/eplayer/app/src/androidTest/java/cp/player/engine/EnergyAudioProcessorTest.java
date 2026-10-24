package cp.player.engine;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EnergyAudioProcessorTest {
    @Test public void metersWithoutChangingAnyAudioByte()throws Exception {
        for(int encoding:new int[]{C.ENCODING_PCM_8BIT,C.ENCODING_PCM_16BIT,C.ENCODING_PCM_24BIT,C.ENCODING_PCM_32BIT,C.ENCODING_PCM_FLOAT}) {
            int width=encoding==C.ENCODING_PCM_8BIT?1:encoding==C.ENCODING_PCM_16BIT?2:encoding==C.ENCODING_PCM_24BIT?3:4;
            byte[] bytes=new byte[width*2*64];
            for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i*17+9);
            EnergyAudioProcessor meter=new EnergyAudioProcessor();
            meter.configure(new AudioProcessor.AudioFormat(48000,2,encoding));meter.flush();
            ByteBuffer input=ByteBuffer.allocateDirect(bytes.length).put(bytes);input.flip();
            meter.queueInput(input);ByteBuffer output=meter.getOutput();byte[] actual=new byte[output.remaining()];output.get(actual);
            assertArrayEquals("Animation metering must be bit-for-bit transparent for PCM encoding "+encoding,bytes,actual);
            assertFalse(input.hasRemaining());meter.reset();
        }
    }
    @Test public void emptyPipelineInputDoesNotFailOrReplacePendingAudio()throws Exception {
        EnergyAudioProcessor meter=new EnergyAudioProcessor();
        meter.configure(new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_16BIT));meter.flush();
        meter.queueInput(AudioProcessor.EMPTY_BUFFER);
        assertFalse(meter.getOutput().hasRemaining());
        byte[] bytes={1,2,3,4};
        ByteBuffer input=ByteBuffer.allocateDirect(bytes.length).put(bytes);input.flip();
        meter.queueInput(input);
        meter.queueInput(AudioProcessor.EMPTY_BUFFER);
        ByteBuffer output=meter.getOutput();byte[] actual=new byte[output.remaining()];output.get(actual);
        assertArrayEquals(bytes,actual);
        meter.queueEndOfStream();
        assertTrue(meter.isEnded());
        meter.reset();
    }
    @Test public void hearsTheRightChannelToo()throws Exception {
        EnergyAudioProcessor meter=new EnergyAudioProcessor();
        meter.configure(new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_16BIT));meter.flush();
        ByteBuffer input=ByteBuffer.allocateDirect(32768).order(ByteOrder.LITTLE_ENDIAN);
        while(input.remaining()>=4){input.putShort((short)0);input.putShort((short)12000);}input.flip();
        meter.queueInput(input);
        assertTrue("A song panned to the right must still animate the character",MusicPulse.sample()>.3f);
        meter.reset();
    }
}
