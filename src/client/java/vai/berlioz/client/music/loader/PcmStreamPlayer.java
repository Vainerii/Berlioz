package vai.berlioz.client.music.loader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.Closeable;
import java.util.function.Consumer;

/**
 * Base for MP3/OGG sounds
 */
public abstract class PcmStreamPlayer extends OpenALStreamPlayer {

    protected PcmStreamPlayer(Consumer<String> onError) {
        super(onError);
    }

    protected abstract AudioInputStream openRawStream() throws Exception;

    protected abstract AudioInputStream convertToPcm(AudioFormat pcmFormat, AudioInputStream rawStream) throws Exception;

    @Override
    protected final void decode(float seekSeconds) throws Exception {
        AudioInputStream rawStream = openRawStream();
        try {
            AudioFormat pcmFormat = toPcmFormat(rawStream.getFormat());
            long frameLen = rawStream.getFrameLength();
            float frameRate = rawStream.getFormat().getFrameRate();
            if (frameLen > 0 && frameRate > 0) setDuration(frameLen / frameRate);

            AudioInputStream pcmStream = convertToPcm(pcmFormat, rawStream);
            try {
                setSampleRate((int) pcmFormat.getSampleRate());
                LOGGER.info("Decoding started ({}Hz, {} channels).",
                    (int) pcmFormat.getSampleRate(), pcmFormat.getChannels());
                streamPcm(pcmStream, pcmFormat, seekSeconds);
            } finally {
                closeSilently(pcmStream);
            }
        } finally {
            closeSilently(rawStream);
        }
        LOGGER.info("Decoding finished.");
    }

    private void streamPcm(AudioInputStream pcmStream, AudioFormat pcmFormat, float seekSeconds) throws Exception {
        if (seekSeconds > 0) {
            long toSkip = (long) (seekSeconds * pcmFormat.getSampleRate() * pcmFormat.getFrameSize());
            byte[] discardBuffer = new byte[CHUNK_SIZE];
            while (toSkip > 0 && !Thread.currentThread().isInterrupted()) {
                int bytesRead = pcmStream.read(discardBuffer, 0, (int) Math.min(discardBuffer.length, toSkip));
                if (bytesRead <= 0) break;
                toSkip -= bytesRead;
            }
        }

        byte[] buffer = new byte[CHUNK_SIZE];
        int bytesRead;
        while (!Thread.currentThread().isInterrupted() && (bytesRead = pcmStream.read(buffer)) != -1) {
            offerPcm(buffer, bytesRead);
        }
    }

    protected static AudioFormat toPcmFormat(AudioFormat source) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getSampleRate(), 16, 2, 4, source.getSampleRate(), false);
    }

    protected static void closeSilently(Closeable c) {
        if (c != null) try { c.close(); } catch (Exception ignore) {}
    }
}
