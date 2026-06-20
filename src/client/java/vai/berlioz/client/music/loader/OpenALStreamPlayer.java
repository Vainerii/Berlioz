package vai.berlioz.client.music.loader;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Class wrote by a lot of chucked up github resources. Il you see weird thing, please tell me, I'm not 100% sure
 * it's really the best doable
 */
public abstract class OpenALStreamPlayer implements MusicSourcePlayer {

    protected static final Logger LOGGER = LoggerFactory.getLogger("Berlioz/Player");

    // PCM chunk size (about 23 ms at 44100 Hz stereo 16-bit)
    static final int CHUNK_SIZE = 4096;
    private static final int NB_BUFFERS = 8;

    protected final Consumer<String> onError;

    private final ConcurrentLinkedQueue<byte[]> pcmQueue = new ConcurrentLinkedQueue<>();
    private volatile int sampleRate = 0;
    private final AtomicBoolean decodeFinished = new AtomicBoolean(false);
    private volatile String decodeError = null;

    private int alSource = 0;
    private final int[] alBuffers = new int[NB_BUFFERS];
    private final ArrayDeque<Integer> freeBuffers = new ArrayDeque<>();
    private boolean alReady = false;
    private long alContextHandle = 0;

    private volatile float duration = 0f;
    private volatile String name = "";
    private volatile float volume = 1.0f;
    private long processedSamples = 0;
    private long samplesInQueue = 0;
    private volatile float seekSeconds = 0f;
    private volatile float playbackStartSecs = 0f;
    private Thread decodeThread;
    private volatile boolean active = false;
    private volatile Runnable onFinished = () -> { };

    protected OpenALStreamPlayer(Consumer<String> onError) {
        this.onError = onError;
    }

    @Override
    public void play() {
        if (decodeThread != null && decodeThread.isAlive()) return;

        active = true;
        decodeFinished.set(false);
        decodeError = null;
        pcmQueue.clear();
        sampleRate = 0;

        float startSec = seekSeconds;
        seekSeconds = 0f;
        playbackStartSecs = startSec;
        processedSamples = 0;
        samplesInQueue = 0;

        decodeThread = new Thread(() -> {
            try {
                decode(startSec);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                String msg = e.getMessage();
                decodeError = (msg != null) ? msg : e.getClass().getSimpleName();
                LOGGER.error("Decode error: {}", decodeError);
            } finally {
                decodeFinished.set(true);
            }
        }, "BerliozDecode");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    @Override
    public void tick() {
        if (!active) return;

        long currentContext = ALC10.alcGetCurrentContext();

        // AL context got destroyed
        if (currentContext == 0) {
            if (alReady) {
                LOGGER.debug("AL context gone - dropping source (decode kept alive).");
                abandonALState();
            }
            return;
        }

        if (alReady && currentContext != alContextHandle) {
            LOGGER.info("AL context changed - reinitializing OpenAL source.");
            abandonALState();
        }

        // Forward decode error to main thread
        if (decodeError != null) {
            String err = decodeError;
            decodeError = null;
            active = false;
            cleanupAL();
            onError.accept(err);
            return;
        }

        if (!alReady && sampleRate > 0) initAL();
        if (!alReady) return;

        int processed = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int buf = AL10.alSourceUnqueueBuffers(alSource);
            int bufBytes = AL10.alGetBufferi(buf, AL10.AL_SIZE);
            long bufSamples = bufBytes / 4L;
            processedSamples += bufSamples;
            samplesInQueue -= bufSamples;
            freeBuffers.offer(buf);
        }

        while (!freeBuffers.isEmpty()) {
            byte[] pcm = pcmQueue.poll();
            if (pcm == null) break;
            int buf = freeBuffers.remove();
            ByteBuffer bb = BufferUtils.createByteBuffer(pcm.length);
            bb.put(pcm).flip();
            AL10.alBufferData(buf, AL10.AL_FORMAT_STEREO16, bb, sampleRate);
            AL10.alSourceQueueBuffers(alSource, buf);
            samplesInQueue += pcm.length / 4L;
        }

        AL10.alSourcef(alSource, AL10.AL_GAIN, volume);

        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        if (state != AL10.AL_PLAYING) {
            int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                AL10.alSourcePlay(alSource);
            } else if (decodeFinished.get() && pcmQueue.isEmpty()) {
                LOGGER.info("Playback finished.");
                active = false;
                cleanupAL();
                onFinished.run();
            }
        }
    }

    @Override
    public void onSoundEngineReload() {
        if (alReady) {
            LOGGER.info("Sound engine reload detected - AL reinitialization pending.");
            abandonALState();
        }
    }

    @Override
    public void stop() {
        active = false;
        Thread t = decodeThread;
        decodeThread = null;
        if (t != null) t.interrupt();
        cleanupAL();
        pcmQueue.clear();
    }

    @Override
    public void pause() {
        if (alReady) AL10.alSourcePause(alSource);
    }

    @Override
    public void unpause() {
        if (alReady) AL10.alSourcePlay(alSource);
    }

    @Override
    public void setTime(float seconds) {
        seekSeconds = seconds;
        stop();
        play();
    }

    @Override
    public float getTime() {
        if (!alReady || sampleRate == 0) return playbackStartSecs;
        return playbackStartSecs + processedSamples / (float) sampleRate;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    @Override
    public void setOnFinished(Runnable onFinished) {
        this.onFinished = (onFinished != null) ? onFinished : () -> { };
    }

    private void initAL() {
        alContextHandle = ALC10.alcGetCurrentContext();
        alSource = AL10.alGenSources();
        AL10.alGenBuffers(alBuffers);
        for (int b : alBuffers) freeBuffers.offer(b);

        AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(alSource, AL10.AL_POSITION, 0f, 0f, 0f);
        AL10.alSourcef(alSource, AL10.AL_ROLLOFF_FACTOR, 0.0f);

        enableHrtf();
        alReady = true;
        LOGGER.info("OpenAL source initialized (id={}, sampleRate={}Hz).", alSource, sampleRate);
    }

    private static void enableHrtf() {
        try {
            final int ALC_HRTF_SOFT = 0x1992;
            long ctx    = ALC10.alcGetCurrentContext();
            long device = ALC10.alcGetContextsDevice(ctx);
            if (!ALC10.alcIsExtensionPresent(device, "ALC_SOFT_HRTF")) return;
            Class<?> cls  = Class.forName("org.lwjgl.openal.SOFTHrtf");
            java.lang.reflect.Method reset = cls.getMethod("alcResetDeviceSOFT", long.class, int[].class);
            reset.invoke(null, device, new int[]{ ALC_HRTF_SOFT, 1, 0 });
            LOGGER.info("OpenAL Soft HRTF enabled.");
        } catch (Exception e) {
            LOGGER.debug("Could not enable HRTF: {}", e.getMessage());
        }
    }

    private void cleanupAL() {
        if (!alReady) return;
        alReady = false;
        long ctx = ALC10.alcGetCurrentContext();
        if (ctx != 0 && ctx == alContextHandle) {
            AL10.alSourceStop(alSource);
            int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
            for (int i = 0; i < queued; i++) AL10.alSourceUnqueueBuffers(alSource);
            AL10.alDeleteSources(alSource);
            AL10.alDeleteBuffers(alBuffers);
            LOGGER.debug("OpenAL source {} released.", alSource);
        }
        freeBuffers.clear();
        alSource = 0;
        alContextHandle = 0;
    }

    private void abandonALState() {
        alReady         = false;
        alSource        = 0;
        alContextHandle = 0;
        freeBuffers.clear();
        samplesInQueue  = 0;
    }

    @Override
    public float getDuration() { return this.duration; }

    @Override
    public String getName() { return this.name; }

    protected final void setSampleRate(int rate) {
        this.sampleRate = rate;
    }

    protected final void setDuration(float seconds) { this.duration = seconds; }

    protected final void setName(String n) {
        this.name = (n == null) ? "" : n;
    }

    protected static String nameFromUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host != null && (host.contains("youtube.com") || host.contains("youtu.be"))) {
                String query = uri.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("v=")) return param.substring(2);
                    }
                }
                // youtu.be/<id>
                String path = uri.getPath();
                String id = path.substring(path.lastIndexOf('/') + 1);
                return id.isEmpty() ? url : id;
            }
            String path = uri.getPath();
            String last = path.substring(path.lastIndexOf('/') + 1);
            int dot = last.lastIndexOf('.');
            String raw = (dot > 0) ? last.substring(0, dot) : last;
            return raw.replace('-', ' ').replace('_', ' ');
        } catch (Exception e) {
            return url;
        }
    }

    protected final void offerPcm(byte[] data, int len) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        byte[] chunk = (len == data.length) ? data.clone() : java.util.Arrays.copyOf(data, len);
        pcmQueue.offer(chunk);
        // 128 ~= 3 seconds
        while (pcmQueue.size() > 128 && !Thread.currentThread().isInterrupted())
            Thread.sleep(10);
    }

    protected abstract void decode(float seekSeconds) throws Exception;
}
