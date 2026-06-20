package vai.berlioz.client.music.loader;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Track display metadata name and duration.
 */
public final class MusicMetadata {

    private static final Logger LOGGER = LoggerFactory.getLogger("Berlioz/Metadata");

    private final String name;
    private final float duration;

    public MusicMetadata(String name, float duration) {
        this.name = name;
        this.duration = duration;
    }

    public String name() {
        return name;
    }

    public float duration() {
        return duration;
    }

    public static void resolve(String url, Consumer<MusicMetadata> onResolved) {
        MusicMetadata hit = peek(url);
        if (hit != null) {
            onResolved.accept(hit);
            return;
        }
        Thread t = new Thread(() -> onResolved.accept(resolveBlocking(url)), "BerliozMetadata");
        t.setDaemon(true);
        t.start();
    }

    private static MusicMetadata peek(String url) {
        return switch (MusicOrigin.of(url)) {
            case YOUTUBE, SOUNDCLOUD -> YtDlpPlayer.peekMetadata(url);
            case DIRECT -> new MusicMetadata(OpenALStreamPlayer.nameFromUrl(url), 0f);
        };
    }

    private static MusicMetadata resolveBlocking(String url) {
        try {
            return switch (MusicOrigin.of(url)) {
                case YOUTUBE, SOUNDCLOUD -> YtDlpPlayer.fetchMetadata(url);
                case DIRECT -> new MusicMetadata(OpenALStreamPlayer.nameFromUrl(url), 0f);
            };
        } catch (Exception e) {
            LOGGER.warn("Metadata resolution failed for {}: {}", url, e.toString());
            return new MusicMetadata(OpenALStreamPlayer.nameFromUrl(url), 0f);
        }
    }
}
