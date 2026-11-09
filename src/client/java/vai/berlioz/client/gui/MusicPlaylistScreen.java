package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import vai.berlioz.client.ModMenuIntegration;
import vai.berlioz.client.music.loader.ExternalMusicManager;
import vai.berlioz.client.music.loader.Playlist;
import vai.berlioz.client.music.loader.PlaylistImporter;
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
        Playlist playlist = PlaylistManager.getInstance();
        PlaylistPlayer player = PlaylistPlayer.get();
        this.widget = new MusicPlaylistWidget(Position.of(x, 0), this.height, player.getRadius(), player.getFade());

        this.widget.setPlaylist(playlist);
        // Change that to a method
        this.widget.onAddToPlaylist = url -> {
            PlaylistImporter.Kind kind = PlaylistImporter.detect(url);
            if (kind == PlaylistImporter.Kind.NONE) {
                addSingle(url);
            } else {
                openImportDialog(url, kind);
            }
        };
        this.widget.onClearPlaylist = this::openClearDialog;
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
        this.widget.onFadeChanged = player::setFade;
        this.widget.onSeek = seconds -> player.seek((float) seconds);
        this.widget.onOpenAmbient = () -> this.minecraft.setScreen(new AmbientZoneScreen(this.parent));
        this.widget.onOpenConfig =
                () -> this.minecraft.setScreen(ModMenuIntegration.createConfigScreen(this));

        this.addRenderableWidget(this.widget);
    }

    private void addSingle(String url) {
        PlaylistManager.getInstance().addMusic(url);
        ExternalMusicManager.getInstance().preload(url);
        BerliozClientNetwork.requestCache(url, PlaylistPlayer.get().getRadius());
    }

    private void openClearDialog() {
        Playlist playlist = PlaylistManager.getInstance();
        if (playlist.isEmpty()) return;
        this.minecraft.setScreen(new ChoiceScreen(this,
                Component.translatable("berlioz.playlist.clear.title"),
                Component.translatable("berlioz.playlist.clear.message", playlist.size()),
                List.of(
                        new ChoiceScreen.Choice(Component.translatable("berlioz.playlist.clear.confirm"), () -> {
                            PlaylistPlayer.get().resetForNewPlaylist();
                            playlist.clear();
                            this.minecraft.setScreen(this);
                        }),
                        new ChoiceScreen.Choice(Component.translatable("berlioz.dialog.cancel"),
                                () -> this.minecraft.setScreen(this)))));
    }

    private void openImportDialog(String url, PlaylistImporter.Kind kind) {
        boolean withVideoOnly = kind == PlaylistImporter.Kind.VIDEO_IN_PLAYLIST;
        ChoiceScreen dialog = new ChoiceScreen(this,
                Component.translatable("berlioz.import.title"),
                Component.translatable("berlioz.import.loading"),
                importChoices(url, null, withVideoOnly));
        this.minecraft.setScreen(dialog);
        PlaylistImporter.fetch(url, result -> {
            if (this.minecraft.screen != dialog) return;  // cancelled meanwhile
            String name = result.title().isEmpty() ? "Playlist" : result.title();
            dialog.update(Component.translatable("berlioz.import.found", name, result.entries().size()),
                    importChoices(url, result, withVideoOnly));
        }, error -> {
            if (this.minecraft.screen != dialog) return;
            dialog.update(Component.translatable("berlioz.import.failed", error),
                    importChoices(url, null, withVideoOnly));
        });
    }

    private List<ChoiceScreen.Choice> importChoices(String url, PlaylistImporter.Result result,
            boolean withVideoOnly) {
        Playlist playlist = PlaylistManager.getInstance();
        boolean ready = result != null;
        List<ChoiceScreen.Choice> choices = new ArrayList<>();
        choices.add(new ChoiceScreen.Choice(Component.translatable("berlioz.import.merge"), ready, () -> {
            playlist.addResolvedAll(result.entries());
            this.minecraft.setScreen(this);
        }));
        choices.add(new ChoiceScreen.Choice(Component.translatable("berlioz.import.replace"), ready, () -> {
            PlaylistPlayer.get().resetForNewPlaylist();
            playlist.clear();
            playlist.addResolvedAll(result.entries());
            this.minecraft.setScreen(this);
        }));
        if (withVideoOnly) {
            choices.add(new ChoiceScreen.Choice(Component.translatable("berlioz.import.videoOnly"), () -> {
                addSingle(url);
                this.minecraft.setScreen(this);
            }));
        }
        choices.add(new ChoiceScreen.Choice(Component.translatable("berlioz.dialog.cancel"),
                () -> this.minecraft.setScreen(this)));
        return choices;
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
