package vai.berlioz.client.music.loader;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlaylistImporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("Berlioz/PlaylistImport");
    private static final int MAX_ENTRIES = 500;
    // Placeholders yt-dlp lists for entries that can't be played
    private static final Set<String> UNAVAILABLE_TITLES = Set.of("[Private video]", "[Deleted video]");

    public enum Kind {
        /** Single track, not a playlist. */
        NONE,
        /** Playlist page. */
        PLAYLIST,
        /** Video from a playlist (watch?v=X&amp;list=Y). */
        VIDEO_IN_PLAYLIST
    }

    /** One playlist track. */
    public static final class Entry {
        private final String url;
        private final String title;
        private final float duration;

        Entry(String url, String title, float duration) {
            this.url = url;
            this.title = title;
            this.duration = duration;
        }

        public String url() {
            return url;
        }

        public String title() {
            return title;
        }

        public float duration() {
            return duration;
        }
    }

    /** A listed playlist. */
    public static final class Result {
        private final String title;
        private final List<Entry> entries;

        Result(String title, List<Entry> entries) {
            this.title = title;
            this.entries = entries;
        }

        /** Playlist title, empty when unknown. */
        public String title() {
            return title;
        }

        public List<Entry> entries() {
            return entries;
        }
    }

    private PlaylistImporter() {}

    /** Classifies a link from its shape only (YouTube list= links, SoundCloud sets). */
    public static Kind detect(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return Kind.NONE;
        }
        if (uri.getHost() == null) return Kind.NONE;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (host.endsWith("soundcloud.com")) {
            return path.contains("/sets/") ? Kind.PLAYLIST : Kind.NONE;
        }
        boolean youtu = host.equals("youtu.be");
        if (!youtu && !host.endsWith("youtube.com")) return Kind.NONE;
        String list = PlaylistImporter.queryParam(uri, "list");
        if (list == null || list.isEmpty()) return Kind.NONE;
        boolean hasVideo = youtu || PlaylistImporter.queryParam(uri, "v") != null;
        return hasVideo ? Kind.VIDEO_IN_PLAYLIST : Kind.PLAYLIST;
    }

    /**
     * Lists the playlist's tracks on a background thread (up to MAX_ENTRIES). Callbacks run on the
     * render thread.
     */
    public static void fetch(String url, Consumer<Result> onDone, Consumer<String> onError) {
        Thread t = new Thread(() -> {
            try {
                Result result = PlaylistImporter.list(url);
                Minecraft.getInstance().execute(() -> onDone.accept(result));
            } catch (Exception e) {
                LOGGER.warn("Playlist import failed for {}: {}", url, e.toString());
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Minecraft.getInstance().execute(() -> onError.accept(msg));
            }
        }, "BerliozPlaylistImport");
        t.setDaemon(true);
        t.start();
    }

    private static Result list(String url) throws Exception {
        BinaryManager.ensureReady();
        Process ytdlp = new ProcessBuilder(
            BinaryManager.getYtdlp().toString(),
            "--flat-playlist", "--yes-playlist", "--quiet",
            "--encoding", "utf-8",
            "--playlist-end", Integer.toString(MAX_ENTRIES),
            "--print", "%(playlist_title)s\t%(webpage_url,url)s\t%(duration)s\t%(title)s",
            url
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String out;
        try {
            out = new String(ytdlp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            ytdlp.waitFor();
        } finally {
            ytdlp.destroy();
        }

        String playlistTitle = "";
        List<Entry> entries = new ArrayList<>();
        for (String line : out.split("\n")) {
            String[] parts = line.strip().split("\t", 4);
            if (parts.length < 4) continue;
            String entryUrl = parts[1].strip();
            String title = parts[3].strip();
            if (entryUrl.isEmpty() || entryUrl.equals("NA") || UNAVAILABLE_TITLES.contains(title)) continue;
            if (playlistTitle.isEmpty() && !parts[0].equals("NA")) playlistTitle = parts[0].strip();
            float duration = 0f;
            try {
                duration = Float.parseFloat(parts[2].strip());
            } catch (NumberFormatException ignored) {
                // "NA": unknown duration
            }
            if (title.isEmpty() || title.equals("NA")) title = OpenALStreamPlayer.nameFromUrl(entryUrl);
            entries.add(new Entry(entryUrl, title, duration));
        }
        if (entries.isEmpty()) throw new Exception("no playable track found");
        return new Result(playlistTitle, entries);
    }

    private static String queryParam(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (key.equals(name)) return eq >= 0 ? pair.substring(eq + 1) : "";
        }
        return null;
    }
}
