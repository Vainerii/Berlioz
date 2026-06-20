package vai.berlioz.client;

import net.fabricmc.api.ClientModInitializer;
import vai.berlioz.Berlioz;
import vai.berlioz.client.config.BerliozConfig;
import vai.berlioz.client.gui.MusicPlayerOverlay;
import vai.berlioz.client.input.BerliozKeys;
import vai.berlioz.client.music.loader.ExternalMusicManager;
import vai.berlioz.client.network.BerliozClientNetwork;
import vai.berlioz.client.network.ServerTrustManager;
import vai.berlioz.client.worldedit.WorldEditTracker;

public class BerliozClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		BerliozConfig.HANDLER.load();
		BerliozClientNetwork.init(Berlioz.MOD_VERSION);
		ServerTrustManager.register();
		ExternalMusicManager.register();
		BerliozKeys.register();
		MusicPlayerOverlay.register();
		WorldEditTracker.register();
	}
}
