package vai.berlioz.client.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** YACL-backed client config (volumes, player widget, streaming sources). */
public class BerliozConfig {

    public enum DisplayMode { ALWAYS, CHAT, NEVER }

    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT }

    public enum Style { COMPLETE, MINIMAL }

    public enum ServerTrust { ASK, ALLOW, BLOCK }

    public static final ConfigClassHandler<BerliozConfig> HANDLER =
            ConfigClassHandler.createBuilder(BerliozConfig.class)
                    .id(Identifier.fromNamespaceAndPath("berlioz", "config"))
                    .serializer(handler -> GsonConfigSerializerBuilder.create(handler)
                            .setPath(FabricLoader.getInstance().getConfigDir()
                                    .resolve("berlioz").resolve("config.json"))
                            .build())
                    .build();

    public static BerliozConfig get() {
        return HANDLER.instance();
    }

    // General
    @SerialEntry public float volGlobal = 1.0f;
    @SerialEntry public float volAmbient = 1.0f;
    @SerialEntry public float volDj = 1.0f;
    @SerialEntry public float volServer = 1.0f;
    // @SerialEntry public float volSound = 1.0f;
    @SerialEntry public boolean coverMcSounds = true;
    @SerialEntry public float fade = 2.5f;

    // Player widget
    @SerialEntry public DisplayMode show = DisplayMode.CHAT;
    @SerialEntry public boolean hideWhenIdle = true;
    @SerialEntry public Corner position = Corner.TOP_RIGHT;
    @SerialEntry public Style style = Style.COMPLETE;

    // Streaming sources
    @SerialEntry public boolean allowYoutube = true;
    @SerialEntry public boolean allowSoundcloud = true;
    @SerialEntry public boolean allowRaw = true;

    // Server trust
    @SerialEntry public ServerTrust defaultServerTrust = ServerTrust.ASK;
    @SerialEntry public List<String> trustedServers = new ArrayList<>();
    @SerialEntry public List<String> blockedServers = new ArrayList<>();
}
