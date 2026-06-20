package vai.berlioz.client.music.loader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public final class PlaylistPlayer {

    public enum Repeat { OFF, ALL, ONE }

    private static final int MAX_HISTORY = 128; // for random
    private static final int PRELOAD_AHEAD = 3; // upcoming pre-cached tracks

    private static final PlaylistPlayer INSTANCE = new PlaylistPlayer();

    private final Random rng = new Random();

    // Shuffle: bag of indices not yet played this cycle (+ the size it was built for)
    private final List<Integer> shuffleBag = new ArrayList<>();
    private int shuffleBagSize = 0;
    private final Deque<Integer> history = new ArrayDeque<>();

    private int currentIndex = -1;
    private boolean random = false;
    private Repeat repeat = Repeat.OFF;
    private boolean playing = false;
    private boolean paused = false;
    private int radius = 8;
    // Consecutive stream failures to avoid spamming
    private int consecutiveErrors = 0;

    public Consumer<String> onPlayStarted = _ -> { };
    public Runnable onPaused = () -> { };
    public Runnable onResumed = () -> { };
    public Runnable onStopped = () -> { };
    public Consumer<Float> onSeek = _ -> { };
    public Runnable onListenerTrackEnded = () -> { };
    public Consumer<List<String>> onUpcoming = _ -> { };

    private PlaylistPlayer() {
        ExternalMusicManager mgr = ExternalMusicManager.getInstance();
        mgr.onTrackFinished = this::autoAdvance;
        mgr.onTrackError = this::onTrackErrored;
    }

    public static PlaylistPlayer get() {
        return INSTANCE;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public boolean isRandom() {
        return random;
    }

    public Repeat getRepeat() {
        return repeat;
    }

    public boolean isPlaying() {
        return playing && !paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setRandom(boolean enabled) {
        this.random = enabled;
        resetShuffle();
    }

    public void setRepeat(Repeat mode) {
        this.repeat = mode;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int blocks) {
        this.radius = Math.max(1, blocks);
    }

    public void play(int index) {
        Playlist.Music music = PlaylistManager.getInstance().get(index);
        if (music == null) return;
        if (currentIndex >= 0 && currentIndex != index) pushHistory(currentIndex);
        shuffleBag.remove((Integer) index);
        this.currentIndex = index;
        startUrl(music.url());
    }

    public void resumeOrPlay() {
        this.consecutiveErrors = 0;
        if (playing && !paused) return;
        if (paused) {
            this.paused = false;
            this.playing = true;
            this.onResumed.run();
            return;
        }
        play(Math.max(currentIndex, 0));
    }

    public void pause() {
        if (playing && !paused) {
            this.paused = true;
            this.onPaused.run();
        }
    }

    public void stop() {
        boolean wasActive = playing || paused;
        this.playing = false;
        this.paused = false;
        this.consecutiveErrors = 0;
        resetShuffle();
        if (wasActive) {
            this.onStopped.run();
        }
    }

    public void autoAdvance() {
        this.consecutiveErrors = 0;
        if (isImposed() || !playing) {
            this.onListenerTrackEnded.run();
            return;
        }
        int index = (repeat == Repeat.ONE && currentIndex >= 0) ? currentIndex : nextIndex();
        if (index >= 0) {
            play(index);
        } else {
            this.playing = false;
            this.paused = false;
            this.onStopped.run();
        }
    }

    public void onTrackErrored() {
        if (isImposed() || !playing) {
            this.onListenerTrackEnded.run();
            return;
        }
        int n = PlaylistManager.getInstance().size();
        if (++consecutiveErrors >= Math.max(1, n)) {
            this.consecutiveErrors = 0;
            this.playing = false;
            this.paused = false;
            this.onStopped.run();
            return;
        }
        int index = nextIndex();
        if (index >= 0) {
            play(index);
        } else {
            this.playing = false;
            this.paused = false;
            this.onStopped.run();
        }
    }

    public void seek(float seconds) {
        if (!playing) return;
        this.paused = false;
        this.onSeek.accept(seconds);
    }

    public void next() {
        this.consecutiveErrors = 0;
        int index = nextIndex();
        if (index >= 0) play(index);
    }

    public void previous() {
        this.consecutiveErrors = 0;
        if (random) {
            if (!history.isEmpty()) replay(history.pop());
            return;
        }
        int n = PlaylistManager.getInstance().size();
        if (n == 0) return;
        int prev = currentIndex - 1;
        if (prev < 0) {
            if (repeat != Repeat.ALL) return;
            prev = n - 1;
        }
        replay(prev);
    }

    private boolean isImposed() {
        return ExternalMusicManager.getInstance().currentBus() != MusicChannel.Bus.DJ;
    }

    private void startUrl(String url) {
        this.playing = true;
        this.paused = false;
        this.onPlayStarted.accept(url);
        this.onUpcoming.accept(peekUpcoming(PRELOAD_AHEAD));
    }

    private List<String> peekUpcoming(int n) {
        List<String> out = new ArrayList<>();
        int size = PlaylistManager.getInstance().size();
        if (size == 0 || n <= 0 || repeat == Repeat.ONE) return out;
        if (random) {
            for (int i = 0; i < shuffleBag.size() && out.size() < n; i++) {
                addUrlAt(out, shuffleBag.get(i));
            }
            return out;
        }
        int idx = currentIndex;
        for (int k = 0; k < n && k < size; k++) {
            idx++;
            if (idx >= size) {
                if (repeat != Repeat.ALL) break;
                idx = 0;
            }
            addUrlAt(out, idx);
        }
        return out;
    }

    private void addUrlAt(List<String> out, int index) {
        Playlist.Music m = PlaylistManager.getInstance().get(index);
        if (m != null) out.add(m.url());
    }

    private void replay(int index) {
        Playlist.Music music = PlaylistManager.getInstance().get(index);
        if (music == null) return;
        this.currentIndex = index;
        startUrl(music.url());
    }

    private int nextIndex() {
        int n = PlaylistManager.getInstance().size();
        if (n == 0) return -1;
        if (random) return nextShuffleIndex(n);
        int next = currentIndex + 1;
        if (next >= n) return repeat == Repeat.ALL ? 0 : -1;
        return next;
    }

    private int nextShuffleIndex(int n) {
        if (n == 1) return repeat == Repeat.ALL ? 0 : -1;
        if (shuffleBagSize != n) {
            rebuildShuffleBag(n);
        } else if (shuffleBag.isEmpty()) {
            if (repeat != Repeat.ALL) return -1;
            rebuildShuffleBag(n);
        }
        return shuffleBag.isEmpty() ? -1 : shuffleBag.removeFirst();
    }

    private void rebuildShuffleBag(int n) {
        shuffleBag.clear();
        for (int i = 0; i < n; i++) {
            if (i != currentIndex) {
                shuffleBag.add(i);
            }
        }
        Collections.shuffle(shuffleBag, rng);
        shuffleBagSize = n;
    }

    private void resetShuffle() {
        shuffleBag.clear();
        shuffleBagSize = 0;
        history.clear();
    }

    private void pushHistory(int index) {
        history.push(index);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }
}
