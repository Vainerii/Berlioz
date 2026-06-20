package vai.berlioz.client.gui;

import dev.isxander.yacl3.gui.YACLScreen;
import dev.lambdaurora.spruceui.Position;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import vai.berlioz.client.config.BerliozConfig;
import vai.berlioz.client.music.loader.ExternalMusicManager;
import vai.berlioz.client.network.BerliozClientNetwork;

public final class MusicPlayerOverlay {
    public static final MusicPlayerOverlay INSTANCE = new MusicPlayerOverlay();

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("berlioz", "music_player");
    private static final int MARGIN = 4;

    private MusicPlayerWidget widget;
    private BerliozConfig.Style builtStyle;
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;

    private MusicPlayerOverlay() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(_ -> {
            if (INSTANCE.widget != null) INSTANCE.widget.tick();
        });
        HudElementRegistry.addLast(HUD_ID, INSTANCE::onHudRender);

        ScreenEvents.BEFORE_INIT.register((_, screen, _, _) -> {
            ScreenMouseEvents.allowMouseClick(screen).register((_, event) -> {
                if (INSTANCE.widget == null || !INSTANCE.shownOverScreen()) return true;
                return !INSTANCE.widget.mouseClicked(event, false);
            });
            ScreenMouseEvents.allowMouseDrag(screen).register((_, event, dx, dy) -> {
                if (INSTANCE.widget == null || !INSTANCE.shownOverScreen()) return true;
                return !INSTANCE.widget.mouseDragged(event, dx, dy);
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((_, event) -> {
                if (INSTANCE.widget != null && INSTANCE.shownOverScreen()) {
                    INSTANCE.widget.mouseReleased(event);
                }
                return true;
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((_, mouseX, mouseY, scrollX, scrollY) -> {
                if (INSTANCE.widget == null || !INSTANCE.shownOverScreen()) return true;
                return !INSTANCE.widget.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            });
        });
    }

    private void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (BerliozConfig.get().show != BerliozConfig.DisplayMode.ALWAYS) return;
        if (!baseVisible()) return;
        MusicPlayerWidget w = ensureWidget(mc);
        w.extractRenderState(graphics, -1, -1, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    public void onScreenRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!shownOverScreen()) return;
        MusicPlayerWidget w = ensureWidget(Minecraft.getInstance());
        w.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private boolean baseVisible() {
        BerliozConfig cfg = BerliozConfig.get();
        if (cfg.show == BerliozConfig.DisplayMode.NEVER) return false;
        if (!BerliozClientNetwork.isServerSupported()) return false;
        return !cfg.hideWhenIdle || ExternalMusicManager.getInstance().isActive();
    }

    private boolean shownOverScreen() {
        if (!baseVisible()) return false;
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof MusicPlaylistScreen || screen instanceof AmbientZoneScreen
                || screen instanceof YACLScreen) {
            return false;
        }
        BerliozConfig.DisplayMode mode = BerliozConfig.get().show;
        return mode == BerliozConfig.DisplayMode.ALWAYS
                || (mode == BerliozConfig.DisplayMode.CHAT && screen instanceof ChatScreen);
    }

    private MusicPlayerWidget ensureWidget(Minecraft mc) {
        BerliozConfig cfg = BerliozConfig.get();
        int w = MusicPlayerWidget.widthFor(cfg.style);
        int h = MusicPlayerWidget.heightFor(cfg.style);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int x = switch (cfg.position) {
            case TOP_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenW - w - MARGIN;
        };
        int y = switch (cfg.position) {
            case TOP_LEFT, TOP_RIGHT -> MARGIN;
            case BOTTOM_RIGHT -> screenH - h - MARGIN;
        };

        if (this.widget == null || this.builtStyle != cfg.style || x != this.lastX || y != this.lastY) {
            this.widget = new MusicPlayerWidget(Position.of(x, y), cfg.style);
            this.builtStyle = cfg.style;
            this.lastX = x;
            this.lastY = y;
        }
        return this.widget;
    }
}
