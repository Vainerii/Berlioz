package vai.berlioz.client.music.loader;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import vai.berlioz.client.config.BerliozConfig;

/**
 * Owns the music channel (one track at a time) and exposes its playback controls.
 */
public class ExternalMusicManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Berlioz/Manager");

    private static final ExternalMusicManager INSTANCE = new ExternalMusicManager();

    private final MusicChannel music = new MusicChannel("music");
    // private final MusicChannel sfx = new MusicChannel("sfx");

    public Runnable onTrackFinished = () -> { };

    public Runnable onTrackError = () -> { };

    private ExternalMusicManager() {
        music.onTrackFinished = () -> onTrackFinished.run();
        music.onTrackError = () -> onTrackError.run();
    }

    public static ExternalMusicManager getInstance() {
        return INSTANCE;
    }

    public MusicChannel music() {
        return music;
    }

    // public MusicChannel sfx() {
    //     return sfx;
    // }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            INSTANCE.music.tick();
            // INSTANCE.sfx.tick();
            if (INSTANCE.music.isActive() && BerliozConfig.get().coverMcSounds) client.getMusicManager().stopPlaying();
        });
    }

    public void preload(String url) {
        if (!isOriginAllowed(url)) return;
        switch (MusicOrigin.of(url)) {
            case YOUTUBE, SOUNDCLOUD -> YtDlpPlayer.preload(url,
                    () -> LOGGER.info("Cached stream for: {}", url),
                    err -> LOGGER.warn("Failed to cache {}: {}", url, err));
            case DIRECT -> MusicMetadata.resolve(url, meta ->
                    LOGGER.info("Resolved metadata for: {} ({})", url, meta.name()));
        }
    }

    public void handleSoundEngineReload() {
        LOGGER.info("MC sound engine reloaded - reinitializing OpenAL.");
        music.onSoundEngineReload();
        // sfx.onSoundEngineReload();
    }

    public void loadAt(String url, float offsetSeconds, MusicChannel.Bus bus, Consumer<String> onError) {
        music.loadAt(url, offsetSeconds, bus, onError);
    }

    public void fadeOutAndStop() {
        music.fadeOutAndStop();
    }

    public void pause() {
        music.pause();
    }

    public void unpause() {
        music.unpause();
    }

    public void stop() {
        music.stop();
    }

    public void setTime(float seconds) {
        music.setTime(seconds);
    }

    public float getTime() {
        return music.getTime();
    }

    public float getDuration() {
        return music.getDuration();
    }

    public String getTrackName() {
        return music.getTrackName();
    }

    public void setSliderGain(float gain) {
        music.setSliderGain(gain);
    }

    public float getSliderGain() {
        return music.getSliderGain();
    }

    public boolean isActive() {
        return music.isActive();
    }

    public MusicChannel.Bus currentBus() {
        return music.getBus();
    }

    static boolean isOriginAllowed(String url) {
        if (!MusicOrigin.isHttp(url)) return false;
        BerliozConfig cfg = BerliozConfig.get();
        return switch (MusicOrigin.of(url)) {
            case YOUTUBE -> cfg.allowYoutube;
            case SOUNDCLOUD -> cfg.allowSoundcloud;
            case DIRECT -> cfg.allowRaw;
        };
    }

    static MusicSourcePlayer createPlayer(String url, Consumer<String> onError) {
        MusicOrigin origin = MusicOrigin.of(url);
        LOGGER.debug("{} player selected.", origin);
        return switch (origin) {
            case YOUTUBE, SOUNDCLOUD -> new YtDlpPlayer(url, onError);
            case DIRECT -> new DirectMusicPlayer(url, onError);
        };
    }
}
