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

        List<String> cmdFfmpeg = new ArrayList<>();
        cmdFfmpeg.add(BinaryManager.getFfmpeg().toString());
        cmdFfmpeg.add("-hide_banner");
        cmdFfmpeg.add("-loglevel");
        cmdFfmpeg.add("quiet");
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
        cmdFfmpeg.add(info.streamUrl());
        cmdFfmpeg.add("-f");
        cmdFfmpeg.add("s16le");
        cmdFfmpeg.add("-ar");
        cmdFfmpeg.add(String.valueOf(SAMPLE_RATE));
        cmdFfmpeg.add("-ac");
        cmdFfmpeg.add("2");
        cmdFfmpeg.add("pipe:1");

        Process ffmpeg = new ProcessBuilder(cmdFfmpeg)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        ffmpegProcess = ffmpeg;
        try {
            LOGGER.info("Streaming started ({}Hz stereo).", SAMPLE_RATE);
            InputStream pcmIn = ffmpeg.getInputStream();
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while (!Thread.currentThread().isInterrupted() && (bytesRead = pcmIn.read(buffer)) != -1) {
                offerPcm(buffer, bytesRead);
            }
        } finally {
            destroySilently(ffmpeg);
            ffmpegProcess = null;
        }
        LOGGER.info("Streaming finished.");
    }

    @Override
    public void stop() {
        Process yt = ytdlpProcess;
        Process ff = ffmpegProcess;
        ytdlpProcess = null;
        ffmpegProcess = null;
        destroySilently(yt);
        destroySilently(ff);
        super.stop();
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
