package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.background.SimpleColorBackground;
import dev.lambdaurora.spruceui.border.SimpleBorder;
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics;
import dev.lambdaurora.spruceui.widget.AbstractSpruceWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceContainerWidget;
import dev.lambdaurora.spruceui.widget.text.SpruceTextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import vai.berlioz.client.music.loader.ExternalMusicManager;
import vai.berlioz.client.music.loader.Playlist;
import vai.berlioz.client.network.BerliozClientNetwork;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/** Music player widget. */
public class MusicPlaylistWidget extends SpruceContainerWidget {
    public static final int WIDTH = GuiStyle.PANEL_WIDTH;

    private static final int PADDING = GuiStyle.PADDING;
    private static final int BTN_SIZE = 18;
    private static final int BTN_GAP = 3;
    private static final int ICON_INSET = 4;
    private static final int ROW_GAP = 4;
    private static final int RADIUS_ROW_H = 16;
    private static final int RADIUS_FIELD_W = 26;
    private static final int SPIN_BTN_W = 10;
    private static final int ADD_BTN_H = GuiStyle.ADD_BUTTON_H;
    private static final int URL_FIELD_H = GuiStyle.URL_FIELD_H;
    private static final float TEXT_SCALE = GuiStyle.TEXT_SCALE;
    private static final int SEEK_RAIL_H = 5;

    private static final FontDescription FONT_DESC = GuiStyle.FONT;

    private interface Icon {
        void draw(SpruceGuiGraphics g, int x, int y, int size, int color);
    }

    private static final Icon ICON_PREVIOUS = sprite("icon/previous");
    private static final Icon ICON_PLAY = sprite("icon/play");
    private static final Icon ICON_PAUSE = sprite("icon/pause");
    private static final Icon ICON_STOP = sprite("icon/stop");
    private static final Icon ICON_NEXT = sprite("icon/next");
    private static final Icon ICON_REPEAT_ALL = sprite("icon/repeat");
    private static final Icon ICON_REPEAT_ONE = sprite("icon/repeat_one");
    private static final Icon ICON_RANDOM = sprite("icon/random");

    private static final Identifier ICON_PLAYLIST_ADD =
            Identifier.fromNamespaceAndPath("berlioz", "icon/playlist_add");
    private static final Identifier ICON_SETTINGS =
            Identifier.fromNamespaceAndPath("berlioz", "icon/settings");

    private static Icon sprite(String path) {
        Identifier id = Identifier.fromNamespaceAndPath("berlioz", path);
        return (g, x, y, s, c) -> g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, s, s);
    }

    public enum PlaybackState { PLAYING, PAUSED, STOPPED }

    public enum RepeatMode { OFF, ALL, ONE }

    private final ToggleButton previousBtn;
    private final ToggleButton playBtn;
    private final ToggleButton pauseBtn;
    private final ToggleButton stopBtn;
    private final ToggleButton nextBtn;
    private final ToggleButton randomBtn;
    private final ToggleButton repeatAllBtn;
    private final ToggleButton repeatOneBtn;
    private final SpruceTextFieldWidget radiusField;
    private final SpruceTextFieldWidget urlField;
    private final TrackListWidget playlistView;
    private final PlayerBar playerBar;

    private PlaybackState playbackState = PlaybackState.STOPPED;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private boolean randomEnabled = false;
    private int radiusValue = 3;
    private boolean suppressFieldListener = false;

    public Consumer<PlaybackState> onPlaybackChanged = _ -> { };
    public Consumer<RepeatMode> onRepeatChanged = _ -> { };
    public Consumer<Boolean> onRandomChanged = _ -> { };
    public IntConsumer onRadiusChanged = _ -> { };
    public Runnable onPrevious = () -> { };
    public Runnable onNext = () -> { };
    public Consumer<String> onAddToPlaylist = _ -> { };
    public IntConsumer onPlayTrack = _ -> { };
    public DoubleConsumer onSeek = _ -> { };

    public Runnable onOpenAmbient = () -> { };
    public Runnable onOpenConfig = () -> { };

    public MusicPlaylistWidget(Position position, int height) {
        super(position, WIDTH, height);

        this.setBackground(new SimpleColorBackground(GuiStyle.PANEL_BG));
        this.setBorder(new SimpleBorder(1, GuiStyle.PANEL_BORDER));

        int headerH = 16;
        int gearW = 16;
        int gearGap = 2;
        this.addChild(new WideButton(
                Position.of(this, PADDING, PADDING), gearW, headerH,
                Component.empty(), ICON_SETTINGS, () -> this.onOpenConfig.run()));
        this.addChild(new WideButton(
                Position.of(this, PADDING + gearW + gearGap, PADDING),
                WIDTH - PADDING * 2 - gearW - gearGap, headerH,
                Component.translatable("berlioz.playlist.ambient"), () -> this.onOpenAmbient.run())
                .enabled(BerliozClientNetwork.hasAmbientPermission()));

        int row1StartX = (WIDTH - (BTN_SIZE * 5 + BTN_GAP * 4)) / 2;
        int row1Y = PADDING + headerH + ROW_GAP;
        this.previousBtn = new ToggleButton(
                Position.of(this, row1StartX, row1Y), BTN_SIZE, ICON_PREVIOUS);
        this.playBtn = new ToggleButton(
                Position.of(this, row1StartX + (BTN_SIZE + BTN_GAP), row1Y), BTN_SIZE, ICON_PLAY);
        this.pauseBtn = new ToggleButton(
                Position.of(this, row1StartX + (BTN_SIZE + BTN_GAP) * 2, row1Y), BTN_SIZE, ICON_PAUSE);
        this.stopBtn = new ToggleButton(
                Position.of(this, row1StartX + (BTN_SIZE + BTN_GAP) * 3, row1Y), BTN_SIZE, ICON_STOP);
        this.nextBtn = new ToggleButton(
                Position.of(this, row1StartX + (BTN_SIZE + BTN_GAP) * 4, row1Y), BTN_SIZE, ICON_NEXT);
        this.previousBtn.onClick = () -> this.onPrevious.run();
        this.playBtn.onClick = () -> setPlaybackState(PlaybackState.PLAYING);
        this.pauseBtn.onClick = () -> setPlaybackState(PlaybackState.PAUSED);
        this.stopBtn.onClick = () -> setPlaybackState(PlaybackState.STOPPED);
        this.nextBtn.onClick = () -> this.onNext.run();
        this.stopBtn.checked = true;
        this.addChild(this.previousBtn);
        this.addChild(this.playBtn);
        this.addChild(this.pauseBtn);
        this.addChild(this.stopBtn);
        this.addChild(this.nextBtn);

        int row2StartX = (WIDTH - (BTN_SIZE * 3 + BTN_GAP * 2)) / 2;
        int row2Y = row1Y + BTN_SIZE + ROW_GAP;
        this.randomBtn = new ToggleButton(
                Position.of(this, row2StartX, row2Y), BTN_SIZE, ICON_RANDOM);
        this.repeatAllBtn = new ToggleButton(
                Position.of(this, row2StartX + BTN_SIZE + BTN_GAP, row2Y), BTN_SIZE, ICON_REPEAT_ALL);
        this.repeatOneBtn = new ToggleButton(
                Position.of(this, row2StartX + (BTN_SIZE + BTN_GAP) * 2, row2Y), BTN_SIZE, ICON_REPEAT_ONE);
        this.randomBtn.onClick = () -> setRandom(!this.randomEnabled);
        this.repeatAllBtn.onClick = () -> setRepeatMode(
                this.repeatMode == RepeatMode.ALL ? RepeatMode.OFF : RepeatMode.ALL);
        this.repeatOneBtn.onClick = () -> setRepeatMode(
                this.repeatMode == RepeatMode.ONE ? RepeatMode.OFF : RepeatMode.ONE);
        this.addChild(this.randomBtn);
        this.addChild(this.repeatAllBtn);
        this.addChild(this.repeatOneBtn);

        int row3Y = row2Y + BTN_SIZE + ROW_GAP * 2;
        Font font = Minecraft.getInstance().font;
        Component radiusText = Component.translatable("berlioz.playlist.radius").append(" :");
        int labelW = (int) Math.ceil(font.width(radiusText) * TEXT_SCALE) + 3;

        int radiusRowW = labelW + RADIUS_FIELD_W + SPIN_BTN_W;
        int radiusRowX = (WIDTH - radiusRowW) / 2;

        int labelY = row3Y + Math.round((RADIUS_ROW_H - font.lineHeight * TEXT_SCALE) / 2f);
        ScaledLabel radiusLabel = new ScaledLabel(
                Position.of(this, radiusRowX, labelY), labelW, radiusText);
        this.addChild(radiusLabel);

        int fieldX = radiusRowX + labelW;
        int fieldW = RADIUS_FIELD_W;
        this.radiusField = new SpruceTextFieldWidget(
                Position.of(this, fieldX, row3Y),
                fieldW, RADIUS_ROW_H, Component.translatable("berlioz.playlist.radius"));
        this.radiusField.setText(Integer.toString(this.radiusValue));
        this.radiusField.setTextPredicate(s -> s.isEmpty() || s.matches("[1-9][0-9]*"));
        this.radiusField.setChangedListener(s -> {
            if (this.suppressFieldListener || s.isEmpty()) return;
            try {
                int v = Integer.parseInt(s);
                if (v >= 1 && v != this.radiusValue) {
                    this.radiusValue = v;
                    this.onRadiusChanged.accept(v);
                }
            } catch (NumberFormatException ignored) { }
        });
        this.radiusField.setRenderTextProvider((str, ignored) ->
                Component.literal(str)
                        .withStyle(s -> s.withFont(FONT_DESC))
                        .getVisualOrderText());
        this.addChild(this.radiusField);

        int spinX = fieldX + fieldW;
        int halfH = RADIUS_ROW_H / 2;
        SpinButton incBtn = new SpinButton(
                Position.of(this, spinX, row3Y), SPIN_BTN_W, halfH, '+');
        SpinButton decBtn = new SpinButton(
                Position.of(this, spinX, row3Y + halfH), SPIN_BTN_W, RADIUS_ROW_H - halfH, '-');
        incBtn.onClick = () -> setRadius(this.radiusValue + 1);
        decBtn.onClick = () -> setRadius(this.radiusValue - 1);
        this.addChild(incBtn);
        this.addChild(decBtn);

        int textH = (int) Math.ceil(font.lineHeight * TEXT_SCALE);
        int playerY = row3Y + RADIUS_ROW_H + ROW_GAP;
        int playerH = textH + 2 + SEEK_RAIL_H + 2 + textH;
        this.playerBar = new PlayerBar(
                Position.of(this, PADDING, playerY), WIDTH - PADDING * 2, playerH);
        this.addChild(this.playerBar);

        int addBtnY = height - PADDING - ADD_BTN_H;
        int urlFieldY = addBtnY - 2 - URL_FIELD_H;
        int listY = playerY + playerH + ROW_GAP;
        int listH = Math.max(0, urlFieldY - ROW_GAP - listY);
        this.playlistView = new TrackListWidget(
                Position.of(this, PADDING, listY),
                WIDTH - PADDING * 2, listH, true);
        this.playlistView.onPlay = index -> this.onPlayTrack.accept(index);
        this.addChild(this.playlistView);

        this.urlField = new SpruceTextFieldWidget(
                Position.of(this, PADDING, urlFieldY),
                WIDTH - PADDING * 2, URL_FIELD_H, Component.literal("URL"));
        this.urlField.setRenderTextProvider((str, ignored) ->
                Component.literal(str).withStyle(s -> s.withFont(FONT_DESC)).getVisualOrderText());
        this.addChild(this.urlField);

        this.addChild(new WideButton(
                Position.of(this, PADDING, addBtnY),
                WIDTH - PADDING * 2, ADD_BTN_H,
                Component.translatable("berlioz.playlist.add"), ICON_PLAYLIST_ADD, this::submitUrl));
    }

    private void submitUrl() {
        String url = this.urlField.getText().trim();
        if (url.isEmpty()) {
            return;
        }
        this.onAddToPlaylist.accept(url);
        this.urlField.setText("");
    }

    public void setPlaylist(Playlist playlist) {
        this.playlistView.setPlaylist(playlist);
    }

    public void setCurrentTrackIndex(int index) {
        this.playlistView.setCurrentIndex(index);
    }

    public PlaybackState getPlaybackState() { return this.playbackState; }

    public RepeatMode getRepeatMode() { return this.repeatMode; }

    public boolean isRandomEnabled() { return this.randomEnabled; }

    public int getRadius() { return this.radiusValue; }

    private void setPlaybackState(PlaybackState state) {
        applyPlaybackState(state);
        this.onPlaybackChanged.accept(state);
    }

    public void applyPlaybackState(PlaybackState state) {
        this.playbackState = state;
        this.playBtn.checked = state == PlaybackState.PLAYING;
        this.pauseBtn.checked = state == PlaybackState.PAUSED;
        this.stopBtn.checked = state == PlaybackState.STOPPED;
    }

    private void setRepeatMode(RepeatMode mode) {
        applyRepeatMode(mode);
        this.onRepeatChanged.accept(mode);
    }

    public void applyRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        this.repeatAllBtn.checked = mode == RepeatMode.ALL;
        this.repeatOneBtn.checked = mode == RepeatMode.ONE;
    }

    private void setRandom(boolean enabled) {
        applyRandom(enabled);
        this.onRandomChanged.accept(enabled);
    }

    public void applyRandom(boolean enabled) {
        this.randomEnabled = enabled;
        this.randomBtn.checked = enabled;
    }

    private void setRadius(int newValue) {
        int clamped = Math.max(1, newValue);
        if (clamped == this.radiusValue) return;
        this.radiusValue = clamped;
        this.suppressFieldListener = true;
        this.radiusField.setText(Integer.toString(clamped));
        this.suppressFieldListener = false;
        this.onRadiusChanged.accept(clamped);
    }

    private static final class ToggleButton extends AbstractSpruceWidget {
        private final Icon icon;
        boolean checked = false;
        Runnable onClick = () -> { };

        ToggleButton(Position position, int size, Icon icon) {
            super(position);
            this.width = size;
            this.height = size;
            this.icon = icon;
        }

        @Override
        protected boolean onMouseClick(MouseButtonEvent event, boolean dbl) {
            this.onClick.run();
            return true;
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            int bg = this.checked ? GuiStyle.ACCENT : GuiStyle.BTN_BG;
            int border = this.checked ? 0xffffffff : GuiStyle.BTN_BORDER;
            g.fill(x, y, x + w, y + h, bg);
            // 1-pixel border
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y, x + 1, y + h, border);
            g.fill(x + w - 1, y, x + w, y + h, border);

            // Tint offered to icons that opt in; the current sprite icons ignore it.
            int color = this.checked ? GuiStyle.TEXT_DARK : GuiStyle.TEXT;
            this.icon.draw(g, x + ICON_INSET, y + ICON_INSET, w - ICON_INSET * 2, color);
        }
    }

    private static final class SpinButton extends AbstractSpruceWidget {
        private final String label;
        Runnable onClick = () -> { };

        SpinButton(Position position, int w, int h, char label) {
            super(position);
            this.width = w;
            this.height = h;
            this.label = String.valueOf(label);
        }

        @Override
        protected boolean onMouseClick(MouseButtonEvent event, boolean dbl) {
            this.onClick.run();
            return true;
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            g.fill(x, y, x + w, y + h, GuiStyle.BTN_BG);
            g.fill(x, y, x + w, y + 1, GuiStyle.BTN_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, GuiStyle.BTN_BORDER);
            g.fill(x, y, x + 1, y + h, GuiStyle.BTN_BORDER);
            g.fill(x + w - 1, y, x + w, y + h, GuiStyle.BTN_BORDER);

            Font font = Minecraft.getInstance().font;
            float scale = 0.6f;
            int textW = font.width(this.label);
            float drawX = x + (w - textW * scale) / 2f;
            float drawY = y + (h - font.lineHeight * scale) / 2f;
            g.pose().pushMatrix();
            g.pose().translate(drawX, drawY);
            g.pose().scale(scale, scale);
            g.text(font, this.label, 0, 0, 0xffeeeeee, false);
            g.pose().popMatrix();
        }
    }

    private static final class ScaledLabel extends AbstractSpruceWidget {
        private final Component text;

        ScaledLabel(Position position, int w, Component text) {
            super(position);
            this.width = w;
            this.height = (int) Math.ceil(Minecraft.getInstance().font.lineHeight * TEXT_SCALE);
            this.text = text.copy().withStyle(s -> s.withFont(FONT_DESC));
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            Font font = Minecraft.getInstance().font;
            g.pose().pushMatrix();
            g.pose().translate(this.getX(), this.getY());
            g.pose().scale(TEXT_SCALE, TEXT_SCALE);
            g.text(font, this.text, 0, 0, 0xffeeeeee, false);
            g.pose().popMatrix();
        }
    }

    private final class PlayerBar extends AbstractSpruceWidget {
        private float totalSeconds = 0f;

        PlayerBar(Position position, int w, int h) {
            super(position);
            this.width = w;
            this.height = h;
        }

        @Override
        protected boolean onMouseClick(MouseButtonEvent event, boolean dbl) {
            if (this.totalSeconds <= 0f) return false;
            int railTop = this.getY() + (int) Math.ceil(Minecraft.getInstance().font.lineHeight * TEXT_SCALE) + 2;
            if (event.y() < railTop) return false;
            double frac = Math.max(0.0, Math.min(1.0, (event.x() - this.getX()) / (double) this.getWidth()));
            MusicPlaylistWidget.this.onSeek.accept(frac * this.totalSeconds);
            return true;
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            ExternalMusicManager emm = ExternalMusicManager.getInstance();
            float elapsed = emm.getTime();
            float total = emm.getDuration();
            this.totalSeconds = total;
            String name = emm.getTrackName();

            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            Font font = Minecraft.getInstance().font;
            int textH = (int) Math.ceil(font.lineHeight * TEXT_SCALE);

            Component nameText = (name.isEmpty()
                    ? Component.translatable("berlioz.player.defaultName")
                    : Component.literal(name)).withStyle(s -> s.withFont(FONT_DESC));
            g.enableScissor(x, y, x + w, y + textH + 1);
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(TEXT_SCALE, TEXT_SCALE);
            g.text(font, nameText, 0, 0, GuiStyle.TEXT, false);
            g.pose().popMatrix();
            g.disableScissor();

            int railY = y + textH + 2;
            g.fill(x, railY, x + w, railY + SEEK_RAIL_H, GuiStyle.PANEL_BORDER);
            double frac = total > 0 ? Math.max(0.0, Math.min(1.0, elapsed / total)) : 0.0;
            int filled = (int) (w * frac);
            g.fill(x, railY, x + filled, railY + SEEK_RAIL_H, GuiStyle.PROGRESS);
            if (total > 0 && filled < w) {
                g.fill(x + filled - 1, railY - 2, x + filled + 2, railY + SEEK_RAIL_H + 2, 0xffffffff);
            }

            int timesY = railY + SEEK_RAIL_H + 2;
            MusicPlaylistWidget.drawScaledText(g, font, GuiStyle.formatTime((long) elapsed),
                    x, timesY, false);
            String remaining = total > 0 ? "-" + GuiStyle.formatTime((long) (total - elapsed))
                    : "--:--";
            MusicPlaylistWidget.drawScaledText(g, font, remaining, x + w, timesY, true);
        }
    }

    private static void drawScaledText(SpruceGuiGraphics g, Font font, String text,
                                       int x, int y, boolean rightAlign) {
        Component c = Component.literal(text).withStyle(s -> s.withFont(FONT_DESC));
        int drawX = rightAlign ? x - (int) Math.ceil(font.width(c) * TEXT_SCALE) : x;
        g.pose().pushMatrix();
        g.pose().translate(drawX, y);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE);
        g.text(font, c, 0, 0, GuiStyle.TEXT_DIM, false);
        g.pose().popMatrix();
    }

}
