package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import vai.berlioz.client.ModMenuIntegration;
import vai.berlioz.client.music.loader.ExternalMusicManager;
import vai.berlioz.client.music.loader.Playlist;
import vai.berlioz.client.music.loader.PlaylistManager;
import vai.berlioz.client.music.loader.PlaylistPlayer;
import vai.berlioz.client.network.BerliozClientNetwork;

/** Hosts the MusicPlaylistWidget */
public class MusicPlaylistScreen extends SpruceScreen {

    private final Screen parent;
    private MusicPlaylistWidget widget;

    public MusicPlaylistScreen(Screen parent) {
        super(Component.translatable("berlioz.playlist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.width - MusicPlaylistWidget.WIDTH;
        this.widget = new MusicPlaylistWidget(Position.of(x, 0), this.height);

        Playlist playlist = PlaylistManager.getInstance();
        PlaylistPlayer player = PlaylistPlayer.get();
        player.setRadius(this.widget.getRadius());

        this.widget.setPlaylist(playlist);
        // Change that to a method
        this.widget.onAddToPlaylist = url -> {
            // Cache locally + ask nearby players to cache it (within the radius).
            playlist.addMusic(url);
            ExternalMusicManager.getInstance().preload(url);
            BerliozClientNetwork.requestCache(url, this.widget.getRadius());
        };
        this.widget.onPlayTrack = player::play;
        this.widget.onPlaybackChanged = state -> {
            switch (state) {
                case PLAYING -> player.resumeOrPlay();
                case PAUSED -> player.pause();
                case STOPPED -> player.stop();
                default -> { }
            }
        };
        this.widget.onPrevious = player::previous;
        this.widget.onNext = player::next;
        this.widget.onRandomChanged = player::setRandom;
        this.widget.onRepeatChanged = mode -> player.setRepeat(toPlayerRepeat(mode));
        this.widget.onRadiusChanged = player::setRadius;
        this.widget.onSeek = seconds -> player.seek((float) seconds);
        this.widget.onOpenAmbient = () -> this.minecraft.setScreen(new AmbientZoneScreen(this.parent));
        this.widget.onOpenConfig =
                () -> this.minecraft.setScreen(ModMenuIntegration.createConfigScreen(this));

        this.addRenderableWidget(this.widget);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.widget == null) return;
        PlaylistPlayer player = PlaylistPlayer.get();
        this.widget.applyPlaybackState(toWidgetState(player));
        this.widget.applyRandom(player.isRandom());
        this.widget.applyRepeatMode(toWidgetRepeat(player.getRepeat()));
        this.widget.setCurrentTrackIndex(player.getCurrentIndex());
    }

    private static MusicPlaylistWidget.PlaybackState toWidgetState(PlaylistPlayer player) {
        if (player.isPlaying()) return MusicPlaylistWidget.PlaybackState.PLAYING;
        if (player.isPaused()) return MusicPlaylistWidget.PlaybackState.PAUSED;
        return MusicPlaylistWidget.PlaybackState.STOPPED;
    }

    private static MusicPlaylistWidget.RepeatMode toWidgetRepeat(PlaylistPlayer.Repeat repeat) {
        return switch (repeat) {
            case ALL -> MusicPlaylistWidget.RepeatMode.ALL;
            case ONE -> MusicPlaylistWidget.RepeatMode.ONE;
            case OFF -> MusicPlaylistWidget.RepeatMode.OFF;
        };
    }

    private static PlaylistPlayer.Repeat toPlayerRepeat(MusicPlaylistWidget.RepeatMode mode) {
        return switch (mode) {
            case ALL -> PlaylistPlayer.Repeat.ALL;
            case ONE -> PlaylistPlayer.Repeat.ONE;
            case OFF -> PlaylistPlayer.Repeat.OFF;
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTracker) {
        // no bg
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    public MusicPlaylistWidget widget() {
        return this.widget;
    }
}
