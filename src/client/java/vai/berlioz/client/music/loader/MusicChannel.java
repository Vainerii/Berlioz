package vai.berlioz.client.music.loader;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vai.berlioz.client.config.BerliozConfig;

/**
 * One audio channel for now
 */
public final class MusicChannel {

    public enum Bus { SERVER, DJ, AMBIENT }
    //public enum Bus { SERVER, DJ, AMBIENT, SFX }

    private static final Logger LOGGER = LoggerFactory.getLogger("Berlioz/Channel");

    private final String name;

    private MusicSourcePlayer currentPlayer = null;
    private String currentUrl = "";

    private Bus bus = Bus.DJ;
    private float sliderGain = 1f;
    private float fadeGain = 1f;
    private float fadeTarget = 1f;
    private float fadeStep = 0f;
    private boolean stopAfterFade = false;

    private volatile boolean trackFinishedPending = false;
    private volatile boolean trackErroredPending = false;

    public Runnable onTrackFinished = () -> { };

    public Runnable onTrackError = () -> { };

    MusicChannel(String name) {
        this.name = name;
    }

    // Per-tick update

    void tick() {
        MusicSourcePlayer p = currentPlayer;
        if (p != null) {
            p.tick();
            advanceFade();
        }
        if (trackFinishedPending) {
            trackFinishedPending = false;
            handleTrackFinished();
        }
        if (trackErroredPending) {
            trackErroredPending = false;
            handleTrackError();
        }
    }

    public void loadAt(String url, float offsetSeconds, Bus bus, Consumer<String> onError) {
        if (!ExternalMusicManager.isOriginAllowed(url)) {
            onError.accept("This source is disabled in the Berlioz config");
            return;
        }
        if (currentPlayer != null) currentPlayer.stop();
        this.bus = bus;
        this.currentUrl = url;
        LOGGER.info("[{}] Loading ordered URL at {}s: {}", name, offsetSeconds, url);
        Consumer<String> reportAndAdvance = err -> {
            onError.accept(err);
            this.trackErroredPending = true;
        };
        currentPlayer = ExternalMusicManager.createPlayer(url, reportAndAdvance);
        fadeGain = 0f;
        wireFinish(currentPlayer);
        applyGain();
        currentPlayer.setTime(offsetSeconds);
        fadeTo(1f, BerliozConfig.get().fade, false);
    }

    public void fadeOutAndStop() {
        if (currentPlayer != null) fadeTo(0f, BerliozConfig.get().fade, true);
    }

    public void pause() {
        if (currentPlayer != null) currentPlayer.pause();
    }

    public void unpause() {
        if (currentPlayer != null) currentPlayer.unpause();
    }

    public void stop() {
        if (currentPlayer != null) {
            LOGGER.info("[{}] Stopping playback.", name);
            currentPlayer.stop();
            currentPlayer = null;
        }
        resetFadeFull();
    }

    public void setTime(float seconds) {
        if (currentPlayer != null) currentPlayer.setTime(seconds);
    }

    public float getTime() {
        return (currentPlayer != null) ? currentPlayer.getTime() : 0f;
    }

    public float getDuration() {
        return (currentPlayer != null) ? currentPlayer.getDuration() : 0f;
    }

    public String getTrackName() {
        return (currentPlayer != null) ? currentPlayer.getName() : "";
    }

    public String getTrackUrl() {
        return (currentPlayer != null) ? currentUrl : "";
    }

    public boolean isActive() {
        return currentPlayer != null;
    }

    public Bus getBus() {
        return bus;
    }

    public void setSliderGain(float gain) {
        this.sliderGain = Math.max(0f, gain);
        applyGain();
    }

    public float getSliderGain() {
        return sliderGain;
    }

    void onSoundEngineReload() {
        MusicSourcePlayer p = currentPlayer;
        if (p != null) p.onSoundEngineReload();
    }

    // 80 seems reasonable but idk
    private static final double FADE_FLOOR_DB = -80.0;

    private void applyGain() {
        if (currentPlayer != null) {
            currentPlayer.setVolume(busVolume() * sliderGain * curvedFade() * BerliozConfig.get().volGlobal);
        }
    }

    private float curvedFade() {
        if (fadeGain <= 0f) return 0f;
        if (fadeGain >= 1f) return 1f;
        return (float) Math.pow(10.0, FADE_FLOOR_DB * (1f - fadeGain) / 20.0);
    }

    private float busVolume() {
        BerliozConfig cfg = BerliozConfig.get();
        return switch (bus) {
            case SERVER -> cfg.volServer;
            case DJ -> cfg.volDj;
            case AMBIENT -> cfg.volAmbient;
            // case SFX -> cfg.volSound;
        };
    }

    private void resetFadeFull() {
        fadeGain = 1f;
        fadeTarget = 1f;
        fadeStep = 0f;
        stopAfterFade = false;
    }

    private void fadeTo(float target, float seconds, boolean stopAtEnd) {
        this.fadeTarget = target;
        this.stopAfterFade = stopAtEnd;
        this.fadeStep = (target - fadeGain) / Math.max(1f, seconds * 20f);  // ~20 client ticks/s
        if (this.fadeStep == 0f) {
            fadeGain = target;
            applyGain();
        }
    }

    private void advanceFade() {
        if (fadeGain != fadeTarget) {
            fadeGain += fadeStep;
            if ((fadeStep >= 0f && fadeGain > fadeTarget) || (fadeStep < 0f && fadeGain < fadeTarget)) {
                fadeGain = fadeTarget;
            }
            applyGain();
        }
        if (fadeGain == fadeTarget && stopAfterFade) {
            stopAfterFade = false;
            stop();
        }
    }

    private void wireFinish(MusicSourcePlayer player) {
        player.setOnFinished(() -> this.trackFinishedPending = true);
    }

    private void handleTrackFinished() {
        currentPlayer = null;
        onTrackFinished.run();
    }

    private void handleTrackError() {
        currentPlayer = null;
        resetFadeFull();
        onTrackError.run();
    }
}
