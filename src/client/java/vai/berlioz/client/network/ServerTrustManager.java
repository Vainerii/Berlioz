package vai.berlioz.client.network;

import com.mojang.brigadier.CommandDispatcher;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import vai.berlioz.client.config.BerliozConfig;
import vai.berlioz.client.music.loader.ExternalMusicManager;

/**
 * System to make sure the server is trusted
 */
public final class ServerTrustManager {

    private static final Set<String> sessionAllowed = ConcurrentHashMap.newKeySet();
    private static final Set<String> prompted = ConcurrentHashMap.newKeySet();
    private static volatile String lastBlockedNotice = null;

    private ServerTrustManager() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("berlioztrust")
                .then(ClientCommands.literal("allow").executes(_ -> {
                    allowSession();
                    return 1;
                }))
                .then(ClientCommands.literal("always").executes(_ -> {
                    allowAlways();
                    return 1;
                }))
                .then(ClientCommands.literal("block").executes(_ -> {
                    block();
                    return 1;
                })));
    }

    public static void resetSession() {
        sessionAllowed.clear();
        prompted.clear();
        lastBlockedNotice = null;
    }

    public static boolean allowsImposedFetch() {
        String key = currentKey();
        if (key == null) return true;
        BerliozConfig cfg = BerliozConfig.get();
        if (cfg.blockedServers.contains(key)) return false;
        if (cfg.trustedServers.contains(key) || sessionAllowed.contains(key)) return true;
        return switch (cfg.defaultServerTrust) {
            case ALLOW -> true;
            case BLOCK -> false;
            case ASK -> {
                promptOnce(key);
                yield false;
            }
        };
    }

    private static boolean isBlocked() {
        String key = currentKey();
        if (key == null) return false;
        BerliozConfig cfg = BerliozConfig.get();
        if (cfg.blockedServers.contains(key)) return true;
        if (cfg.trustedServers.contains(key) || sessionAllowed.contains(key)) return false;
        return cfg.defaultServerTrust == BerliozConfig.ServerTrust.BLOCK;
    }

    public static void notifyBlockedTrack(String url) {
        if (url.isEmpty() || !isBlocked()) return;
        if (url.equals(lastBlockedNotice)) return;
        lastBlockedNotice = url;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        URI httpUri = httpUri(url);
        Component link = Component.translatable("berlioz.trust.blocked.listen").withStyle(style -> {
            style = style.withColor(ChatFormatting.WHITE).withUnderlined(true)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)));
            return httpUri != null ? style.withClickEvent(new ClickEvent.OpenUrl(httpUri)) : style;
        });
        Component help = Component.translatable("berlioz.trust.blocked.help").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.translatable("berlioz.trust.blocked.helpHover"))));
        Component message = Component.translatable("berlioz.trust.blocked.line", link)
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" "))
                .append(help);
        player.sendSystemMessage(message);
    }

    private static URI httpUri(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return uri;
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    private static String currentKey() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return server != null ? server.ip : null;
    }

    private static void promptOnce(String key) {
        if (!prompted.add(key)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Component message = Component.translatable("berlioz.trust.prompt")
                .append(Component.literal(" "))
                .append(action("berlioz.trust.allow", "/berlioztrust allow", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(action("berlioz.trust.always", "/berlioztrust always", ChatFormatting.AQUA))
                .append(Component.literal(" "))
                .append(action("berlioz.trust.block", "/berlioztrust block", ChatFormatting.RED));
        player.sendSystemMessage(message);
    }

    private static Component action(String labelKey, String command, ChatFormatting color) {
        return Component.translatable(labelKey).withStyle(style -> style
                .withColor(color)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static void allowSession() {
        String key = currentKey();
        if (key == null) return;
        sessionAllowed.add(key);
        notifyPlayer("berlioz.trust.allowed");
        BerliozClientNetwork.askNext();
    }

    private static void allowAlways() {
        String key = currentKey();
        if (key == null) return;
        BerliozConfig cfg = BerliozConfig.get();
        cfg.blockedServers.remove(key);
        if (!cfg.trustedServers.contains(key)) cfg.trustedServers.add(key);
        sessionAllowed.add(key);
        BerliozConfig.HANDLER.save();
        notifyPlayer("berlioz.trust.alwaysAllowed"); // <- coooool
        BerliozClientNetwork.askNext();
    }

    private static void block() {
        String key = currentKey();
        if (key == null) return;
        BerliozConfig cfg = BerliozConfig.get();
        cfg.trustedServers.remove(key);
        sessionAllowed.remove(key);
        if (!cfg.blockedServers.contains(key)) cfg.blockedServers.add(key);
        BerliozConfig.HANDLER.save();
        ExternalMusicManager.getInstance().fadeOutAndStop();
        notifyPlayer("berlioz.trust.blocked");
    }

    private static void notifyPlayer(String key) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.translatable(key));
        }
    }
}
