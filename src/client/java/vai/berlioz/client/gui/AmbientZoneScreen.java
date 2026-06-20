package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import vai.berlioz.client.ModMenuIntegration;
import vai.berlioz.client.music.loader.AmbientZoneData;
import vai.berlioz.client.network.BerliozClientNetwork;

public class AmbientZoneScreen extends SpruceScreen {

    private static final int NEARBY_RADIUS = 300;
    private static final int REFRESH_TICKS = 100;  // re-request zones every ~5 s

    private final Screen parent;
    private MusicAmbientWidget widget;
    private int tickCounter = 0;

    public AmbientZoneScreen(Screen parent) {
        super(Component.translatable("berlioz.ambient.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new MusicAmbientWidget(Position.of(0, 0), this.width, this.height);
        this.widget.onOpenPlaylist =
                () -> this.minecraft.setScreen(new MusicPlaylistScreen(this.parent));
        this.widget.onOpenConfig =
                () -> this.minecraft.setScreen(ModMenuIntegration.createConfigScreen(this));
        this.widget.onSave = BerliozClientNetwork::saveZone;
        this.widget.onDelete = BerliozClientNetwork::deleteZone;
        this.widget.onTp = AmbientZoneScreen::teleportToZone;
        this.addRenderableWidget(this.widget);
        requestZones();
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.tickCounter >= REFRESH_TICKS) {
            this.tickCounter = 0;
            requestZones();
        }
    }

    private static void requestZones() {
        if (BerliozClientNetwork.hasAmbientPermission()) {
            BerliozClientNetwork.requestZoneList(NEARBY_RADIUS);
        }
    }

    private static void teleportToZone(AmbientZoneData zone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        mc.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f",
                zone.centerX(), zone.centerY(), zone.centerZ()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // No background
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTracker) {}

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
