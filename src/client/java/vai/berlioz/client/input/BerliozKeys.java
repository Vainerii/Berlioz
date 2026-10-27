package vai.berlioz.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import vai.berlioz.client.ModMenuIntegration;
import vai.berlioz.client.gui.AmbientZoneScreen;
import vai.berlioz.client.gui.MusicPlaylistScreen;
import vai.berlioz.client.network.BerliozClientNetwork;

/**
 * Client key bindings.
 */
public final class BerliozKeys {

    private static final KeyMapping OPEN_PLAYLIST = new KeyMapping(
            "berlioz.key.openPlaylist", InputConstants.KEY_M, KeyMapping.Category.MISC);

    private BerliozKeys() {}

    public static void register() {
        KeyMappingHelper.registerKeyMapping(OPEN_PLAYLIST);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_PLAYLIST.consumeClick()) {
                openInterface(client);
            }
        });
    }

    private static void openInterface(Minecraft client) {
        if (client.player == null) return;
        Screen parent = client.gui.screen();
        if (BerliozClientNetwork.hasPlaylistPermission()) {
            client.gui.setScreen(new MusicPlaylistScreen(parent));
        } else if (BerliozClientNetwork.hasAmbientPermission()) {
            client.gui.setScreen(new AmbientZoneScreen(parent));
        } else {
            client.gui.setScreen(ModMenuIntegration.createConfigScreen(parent));
        }
    }
}
