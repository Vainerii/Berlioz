package vai.berlioz.client.music.loader;

public interface MusicSourcePlayer {

    void play();

    void pause();

    void unpause();

    void setTime(float seconds);

    float getTime();

    default float getDuration() { return 0f; }

    default String getName() { return ""; }

    default void setOnFinished(Runnable onFinished) { }

    void stop();

    void setVolume(float volume);

    void tick();

    void onSoundEngineReload();
}
