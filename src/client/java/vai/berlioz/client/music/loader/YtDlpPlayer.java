package vai.berlioz.client.music.loader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Streaming player for any site yt-dlp supports (YouTube, SoundCloud, more maybe later): yt-dlp resolves the
 * stream URL, then ffmpeg converts it to PCM. Handles CDN throttling via
 * ffmpeg reconnect. DRM-protected tracks cannot be resolved (yt-dlp fails).
 */
public class YtDlpPlayer extends OpenALStreamPlayer {

    private static final int SAMPLE_RATE = 44100;

    // Resolved stream URLs are signed and can expire, cache 4 h max.
    private static final long URL_TTL_MS = 4 * 3600_000L;
    private static final ConcurrentHashMap<String, CachedEntry> CACHE = new ConcurrentHashMap<>();

    private static final class CachedEntry {
        private final String streamUrl;
        private final String title;
        private final float duration;
        private final long expiryMs;

        CachedEntry(String streamUrl, String title, float duration, long expiryMs) {
            this.streamUrl = streamUrl;
            this.title = title;
            this.duration = duration;
            this.expiryMs = expiryMs;
        }

        String streamUrl() {
            return streamUrl;
        }

        String title() {
            return title;
        }

        float duration() {
            return duration;
        }

        long expiryMs() {
            return expiryMs;
        }
    }

    private static final class ResolvedInfo {
        private final String streamUrl;
        private final String title;
        private final float duration;

        ResolvedInfo(String streamUrl, String title, float duration) {
            this.streamUrl = streamUrl;
            this.title = title;
            this.duration = duration;
        }

        String streamUrl() {
            return streamUrl;
        }

        String title() {
            return title;
        }

        float duration() {
            return duration;
        }
    }

    private final String url;

    private volatile Process ytdlpProcess;
    private volatile Process ffmpegProcess;

    public YtDlpPlayer(String url, Consumer<String> onError) {
        super(onError);
        this.url = url;
    }

    @Override
    protected void decode(float seekSeconds) throws Exception {
        BinaryManager.ensureReady();
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        ResolvedInfo info = resolveInfo();  // title + duration + stream URL (-> cache)
        setName(info.title());
        if (info.duration() > 0) setDuration(info.duration());
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        setSampleRate(SAMPLE_RATE);

        StreamResult first = streamOnce(info.streamUrl(), seekSeconds, info.duration());
        if (first.failed) {
            CACHE.remove(url);
            float resumeAt = seekSeconds + first.seconds;
            LOGGER.warn("Stream failed after {}s ({}), retrying from {}s with a fresh URL: {}",
                    first.seconds, first.reason, resumeAt, url);
            ResolvedInfo fresh = resolveInfo();
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            StreamResult second = streamOnce(fresh.streamUrl(), resumeAt, fresh.duration());
            if (second.failed) {
                CACHE.remove(url);
                throw new Exception("stream failed twice (" + second.reason + ")");
            }
        }
        LOGGER.info("Streaming finished.");
    }

    private static final class StreamResult {
        private final boolean failed;
        private final float seconds;
        private final String reason;

        StreamResult(boolean failed, float seconds, String reason) {
            this.failed = failed;
            this.seconds = seconds;
            this.reason = reason;
        }
    }

    private static final float TRUNCATION_TOLERANCE_S = 5f;
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * 2 * 2;
    private static final int STDERR_MAX_CHARS = 500;

    private StreamResult streamOnce(String streamUrl, float seekSeconds, float duration) throws Exception {
        List<String> cmdFfmpeg = new ArrayList<>();
        cmdFfmpeg.add(BinaryManager.getFfmpeg().toString());
        cmdFfmpeg.add("-hide_banner");
        cmdFfmpeg.add("-loglevel");
        cmdFfmpeg.add("error");
        // Reconnect on problem (so the next track is not read in the middle of prev one).
        cmdFfmpeg.add("-reconnect");
        cmdFfmpeg.add("1");
        cmdFfmpeg.add("-reconnect_streamed");
        cmdFfmpeg.add("1");
        cmdFfmpeg.add("-reconnect_delay_max");
        cmdFfmpeg.add("5");
        if (seekSeconds > 0) {
            cmdFfmpeg.add("-ss");
            cmdFfmpeg.add(String.valueOf((int) seekSeconds));
        }
        cmdFfmpeg.add("-i");
        cmdFfmpeg.add(streamUrl);
        cmdFfmpeg.add("-f");
        cmdFfmpeg.add("s16le");
        cmdFfmpeg.add("-ar");
        cmdFfmpeg.add(String.valueOf(SAMPLE_RATE));
        cmdFfmpeg.add("-ac");
        cmdFfmpeg.add("2");
        cmdFfmpeg.add("pipe:1");

        Process ffmpeg = new ProcessBuilder(cmdFfmpeg).start();
        ffmpegProcess = ffmpeg;
        StringBuilder stderr = new StringBuilder();
        Thread errReader = YtDlpPlayer.drainStderr(ffmpeg, stderr);
        long bytes = 0;
        try {
            LOGGER.info("Streaming started ({}Hz stereo).", SAMPLE_RATE);
            InputStream pcmIn = ffmpeg.getInputStream();
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while (!Thread.currentThread().isInterrupted() && (bytesRead = pcmIn.read(buffer)) != -1) {
                offerPcm(buffer, bytesRead);
                bytes += bytesRead;
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            ffmpeg.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            errReader.join(1000);
        } finally {
            destroySilently(ffmpeg);
            ffmpegProcess = null;
        }

        float seconds = (float) bytes / BYTES_PER_SECOND;
        int exit = ffmpeg.isAlive() ? -1 : ffmpeg.exitValue();
        String err;
        synchronized (stderr) {
            err = stderr.toString().strip();
        }
        if (exit != 0) {
            return new StreamResult(true, seconds, "ffmpeg exit " + exit + (err.isEmpty() ? "" : ": " + err));
        }
        if (duration > 0 && seekSeconds < duration - TRUNCATION_TOLERANCE_S
                && seekSeconds + seconds < duration - TRUNCATION_TOLERANCE_S) {
            return new StreamResult(true, seconds, "stream cut at " + (seekSeconds + seconds) + "s of " + duration + "s");
        }
        return new StreamResult(false, seconds, "");
    }

    private static Thread drainStderr(Process process, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    synchronized (sink) {
                        sink.append(line).append(' ');
                        if (sink.length() > STDERR_MAX_CHARS) sink.delete(0, sink.length() - STDERR_MAX_CHARS);
                    }
                }
            } catch (IOException ignored) {}
        }, "BerliozFfmpegStderr");
        t.setDaemon(true);
        t.start();
        return t;
    }

    @Override
    public void stop() {
        Process yt = ytdlpProcess;
        Process ff = ffmpegProcess;
        ytdlpProcess = null;
        ffmpegProcess = null;
        super.stop();
        destroySilently(yt);
        destroySilently(ff);
    }

    public static void preload(String url, Runnable onDone, java.util.function.Consumer<String> onError) {
        CachedEntry cached = CACHE.get(url);
        if (cached != null && System.currentTimeMillis() < cached.expiryMs()) {
            onDone.run();
            return;
        }
        Thread t = new Thread(() -> {
            try {
                BinaryManager.ensureReady();
                fetchAndCache(url, null);
                onDone.run();
            } catch (Exception e) {
                onError.accept(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }, "BerliozPreload");
        t.setDaemon(true);
        t.start();
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Title + duration, reusing the cache.
     */
    public static MusicMetadata fetchMetadata(String url) throws Exception {
        CachedEntry cached = CACHE.get(url);
        if (cached == null) {
            BinaryManager.ensureReady();
            cached = fetchAndCache(url, null);
        }
        return new MusicMetadata(cached.title(), cached.duration());
    }

    public static MusicMetadata peekMetadata(String url) {
        CachedEntry e = CACHE.get(url);
        return e == null ? null : new MusicMetadata(e.title(), e.duration());
    }

    private ResolvedInfo resolveInfo() throws Exception {
        CachedEntry cached = CACHE.get(url);
        if (cached != null && System.currentTimeMillis() < cached.expiryMs()) {
            LOGGER.info("Using cached stream info for: {}", url);
            return new ResolvedInfo(cached.streamUrl(), cached.title(), cached.duration());
        }
        LOGGER.info("Resolving stream for: {}", url);
        CachedEntry entry = fetchAndCache(url, p -> ytdlpProcess = p);
        return new ResolvedInfo(entry.streamUrl(), entry.title(), entry.duration());
    }

    private static CachedEntry fetchAndCache(String url, java.util.function.Consumer<Process> processTracker) throws Exception {
        Process ytdlp = new ProcessBuilder(
            BinaryManager.getYtdlp().toString(),
            "--no-playlist", "--quiet",
            "--print", "title",
            "--print", "duration",
            "--print", "urls",
            "-f", "bestaudio",
            url
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (processTracker != null) processTracker.accept(ytdlp);
        try {
            String[] lines = new String(ytdlp.getInputStream().readAllBytes()).trim().split("\n");
            ytdlp.waitFor();

            String title    = lines.length > 0 && !lines[0].isBlank() ? lines[0].trim() : nameFromUrl(url);
            float  duration = 0f;
            if (lines.length > 1) {
                try { duration = Float.parseFloat(lines[1].trim()); } catch (NumberFormatException ignore) {}
            }
            String streamUrl = lines.length > 2 ? lines[2].trim() : "";
            if (streamUrl.isEmpty()) throw new Exception("yt-dlp returned no stream URL");
            CachedEntry entry = new CachedEntry(streamUrl, title, duration, System.currentTimeMillis() + URL_TTL_MS);
            CACHE.put(url, entry);
            return entry;
        } finally {
            destroySilently(ytdlp);
            if (processTracker != null) processTracker.accept(null);
        }
    }

    private static void destroySilently(Process p) {
        if (p != null) p.destroyForcibly();
    }
}
