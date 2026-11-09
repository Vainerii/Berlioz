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

    public static final float FADE_CLIENT_DEFAULT = -1f;

    private static final float TICKS_PER_SECOND = 20f;

    private final String name;

    private MusicSourcePlayer currentPlayer = null;
    private String currentUrl = "";

    private Bus bus = Bus.DJ;
    private float sliderGain = 1f;
    private boolean muted = false;
    private float fadeGain = 1f;
    private float fadeTarget = 1f;
    private float fadeStep = 0f;
    private boolean stopAfterFade = false;

    private MusicSourcePlayer outgoingPlayer = null;
    private Bus outgoingBus = Bus.DJ;
    private float outgoingGain = 0f;
    private float outgoingStep = 0f;
    private int holdTicks = 0;
    private float fadeInAfterHold = 0f;
    private volatile int generation = 0;

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
        advanceOutgoing();
        if (holdTicks > 0 && --holdTicks == 0) endHold();
        if (trackFinishedPending) {
            trackFinishedPending = false;
            handleTrackFinished();
        }
        if (trackErroredPending) {
            trackErroredPending = false;
            handleTrackError();
        }
    }

    public void loadAt(String url, float offsetSeconds, Bus bus, float fadeSeconds, Consumer<String> onError) {
        if (!ExternalMusicManager.isOriginAllowed(url)) {
            onError.accept("This source is disabled in the Berlioz config");
            return;
        }
        float total = MusicChannel.resolveFade(fadeSeconds);
        boolean audible = currentPlayer != null && !trackFinishedPending && holdTicks == 0;
        float fadeOut = 0f;
        float fadeIn = 0f;
        if (audible && total > 0f) {
            if (url.equals(currentUrl) || bus == Bus.AMBIENT) {
                fadeOut = total / 2f;
                fadeIn = total / 2f;
            } else {
                fadeOut = total;
            }
        }

        stopOutgoing();
        if (currentPlayer != null) {
            if (fadeOut > 0f) {
                outgoingPlayer = currentPlayer;
                outgoingBus = this.bus;
                outgoingGain = fadeGain;
                outgoingStep = outgoingGain / MusicChannel.ticks(fadeOut);
            } else {
                currentPlayer.stop();
            }
        }
        resetFadeFull();

        this.bus = bus;
        this.currentUrl = url;
        LOGGER.info("[{}] Loading ordered URL at {}s (fade out {}s, in {}s): {}",
                name, offsetSeconds, fadeOut, fadeIn, url);
        int gen = ++generation;
        trackFinishedPending = false;
        trackErroredPending = false;
        Consumer<String> reportAndAdvance = err -> {
            onError.accept(err);
            if (gen == this.generation) this.trackErroredPending = true;
        };
        currentPlayer = ExternalMusicManager.createPlayer(url, reportAndAdvance);
        currentPlayer.setOnFinished(() -> {
            if (gen == this.generation) this.trackFinishedPending = true;
        });
        if (outgoingPlayer != null) {
            holdTicks = MusicChannel.ticks(fadeOut);
            fadeInAfterHold = fadeIn;
            fadeGain = 0f;
            fadeTarget = 0f;
        }
        applyGain();
        currentPlayer.setTime(offsetSeconds);
    }

    public void fadeOutAndStop() {
        fadeOutAndStop(FADE_CLIENT_DEFAULT);
    }

    public void fadeOutAndStop(float fadeSeconds) {
        if (currentPlayer == null) return;
        if (holdTicks > 0) {
            // Still silent behind an outgoing fade: nothing to fade, the outgoing one finishes on its own
            currentPlayer.stop();
            currentPlayer = null;
            resetFadeFull();
            return;
        }
        fadeTo(0f, MusicChannel.resolveFade(fadeSeconds), true);
    }

    public void pause() {
        stopOutgoing();
        if (holdTicks > 0) {
            holdTicks = 0;
            fadeGain = 1f;
            fadeTarget = 1f;
            applyGain();
        }
        if (currentPlayer != null) currentPlayer.pause();
    }

    public void unpause() {
        if (currentPlayer != null) currentPlayer.unpause();
    }

    public void stop() {
        stopOutgoing();
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

    public void setMuted(boolean muted) {
        this.muted = muted;
        applyGain();
    }

    public boolean isMuted() {
        return muted;
    }

    void onSoundEngineReload() {
        MusicSourcePlayer p = currentPlayer;
        if (p != null) p.onSoundEngineReload();
        MusicSourcePlayer out = outgoingPlayer;
        if (out != null) out.onSoundEngineReload();
    }

    // 80 seems reasonable but idk
    private static final double FADE_FLOOR_DB = -80.0;

    private void applyGain() {
        if (currentPlayer != null) currentPlayer.setVolume(volumeFor(bus, fadeGain));
        if (outgoingPlayer != null) outgoingPlayer.setVolume(volumeFor(outgoingBus, outgoingGain));
    }

    private float volumeFor(Bus target, float gain) {
        float mute = muted ? 0f : 1f;
        return MusicChannel.busVolume(target) * sliderGain * mute * MusicChannel.curvedFade(gain)
                * BerliozConfig.get().volGlobal;
    }

    private static float curvedFade(float gain) {
        if (gain <= 0f) return 0f;
        if (gain >= 1f) return 1f;
        return (float) Math.pow(10.0, FADE_FLOOR_DB * (1f - gain) / 20.0);
    }

    private static float resolveFade(float fadeSeconds) {
        return fadeSeconds >= 0f ? fadeSeconds : Math.max(0f, BerliozConfig.get().fade);
    }

    private static int ticks(float seconds) {
        return Math.max(1, Math.round(seconds * TICKS_PER_SECOND));
    }

    private static float busVolume(Bus target) {
        BerliozConfig cfg = BerliozConfig.get();
        return switch (target) {
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
        holdTicks = 0;
        fadeInAfterHold = 0f;
    }

    private void endHold() {
        if (currentPlayer == null) return;
        if (fadeInAfterHold > 0f) {
            fadeTo(1f, fadeInAfterHold, false);
        } else {
            fadeGain = 1f;
            fadeTarget = 1f;
            applyGain();
        }
        fadeInAfterHold = 0f;
    }

    private void advanceOutgoing() {
        MusicSourcePlayer out = outgoingPlayer;
        if (out == null) return;
        out.tick();
        outgoingGain -= outgoingStep;
        if (outgoingGain <= 0f) {
            stopOutgoing();
        } else {
            out.setVolume(volumeFor(outgoingBus, outgoingGain));
        }
    }

    private void stopOutgoing() {
        if (outgoingPlayer != null) {
            outgoingPlayer.stop();
            outgoingPlayer = null;
        }
        outgoingGain = 0f;
        outgoingStep = 0f;
    }

    private void fadeTo(float target, float seconds, boolean stopAtEnd) {
        this.fadeTarget = target;
        this.stopAfterFade = stopAtEnd;
        this.fadeStep = (target - fadeGain) / Math.max(1f, seconds * TICKS_PER_SECOND);
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
