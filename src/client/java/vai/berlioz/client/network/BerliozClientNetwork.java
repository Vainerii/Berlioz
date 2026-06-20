package vai.berlioz.client.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vai.berlioz.client.music.loader.AmbientZoneData;
import vai.berlioz.client.music.loader.ExternalMusicManager;
import vai.berlioz.client.music.loader.MusicChannel;
import vai.berlioz.client.music.loader.PlaylistPlayer;
import vai.berlioz.network.BerliozPayload;
import vai.berlioz.network.MessageType;

public final class BerliozClientNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("berlioz/net");
    private static final Map<Byte, Handler> HANDLERS = new HashMap<>();
    private static final byte[] EMPTY = new byte[0];

    private static volatile boolean playlistPermission = false;
    private static volatile boolean ambientPermission = false;

    private static volatile List<AmbientZoneData> ambientZones = List.of();
    private static Runnable onZoneListUpdated = () -> { };

    public interface Handler {
        void handle(Minecraft client, byte[] body);
    }

    private BerliozClientNetwork() {}

    public static void init(String modVersion) {
        PayloadTypeRegistry.serverboundPlay().register(BerliozPayload.TYPE, BerliozPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BerliozPayload.TYPE, BerliozPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(BerliozPayload.TYPE, (payload, context) -> {
            byte[] data = payload.data();
            if (data.length == 0) {
                LOGGER.warn("Received empty Berlioz packet");
                return;
            }
            byte type = data[0];
            byte[] body = new byte[data.length - 1];
            System.arraycopy(data, 1, body, 0, body.length);

            Handler handler = HANDLERS.get(type);
            if (handler == null) {
                LOGGER.warn("No Berlioz handler for message type 0x{}", String.format("%02X", type));
                return;
            }
            context.client().execute(() -> handler.handle(context.client(), body));
        });

        ClientPlayConnectionEvents.JOIN.register((_, _, _) -> {
            playlistPermission = false;
            ambientPermission = false;
            ServerTrustManager.resetSession();
            if (ClientPlayNetworking.canSend(BerliozPayload.TYPE)) {
                LOGGER.info("Berlioz_Server detected, performing handshake");
                send(MessageType.HELLO, modVersion.getBytes(StandardCharsets.UTF_8));
            } else {
                LOGGER.info("Server does not support the berlioz:main channel");
            }
        });

        registerReceiver(MessageType.WELCOME, (_, body) ->
                LOGGER.info("Server accepted handshake (server version: {})",
                        new String(body, StandardCharsets.UTF_8)));
        registerReceiver(MessageType.PERMISSION, (_, body) -> {
            playlistPermission = body.length > 0 && body[0] != 0;
            ambientPermission = body.length > 1 && body[1] != 0;
            LOGGER.info("Server permissions: playlist={}, ambient={}",
                    playlistPermission, ambientPermission);
        });
        registerReceiver(MessageType.CACHE_ORDER, (_, body) -> {
            if (!ServerTrustManager.allowsImposedFetch()) return;
            String url = new String(body, StandardCharsets.UTF_8);
            LOGGER.info("Received cache order for: {}", url);
            ExternalMusicManager.getInstance().preload(url);
        });
        registerReceiver(MessageType.PLAY_ORDER, (_, body) -> {
            if (body.length < Float.BYTES + 2) return;
            ByteBuffer buf = ByteBuffer.wrap(body);
            float offset = buf.getFloat();
            boolean paused = buf.get() != 0;
            // Server kind byte: 0=DJ, 1=ambient, 2=server
            MusicChannel.Bus bus = switch (buf.get()) {
                case 1 -> MusicChannel.Bus.AMBIENT;
                case 2 -> MusicChannel.Bus.SERVER;
                default -> MusicChannel.Bus.DJ;
            };
            byte[] urlBytes = new byte[buf.remaining()];
            buf.get(urlBytes);
            String url = new String(urlBytes, StandardCharsets.UTF_8);
            if (!ServerTrustManager.allowsImposedFetch()) {
                ServerTrustManager.notifyBlockedTrack(url);
                return;
            }
            LOGGER.info("Broadcast play order: {} @ {}s (paused={}, bus={})", url, offset, paused, bus);
            ExternalMusicManager mgr = ExternalMusicManager.getInstance();
            mgr.loadAt(url, offset, bus, err -> LOGGER.warn("Failed broadcast track {}: {}", url, err));
            if (paused) mgr.pause();
        });
        registerReceiver(MessageType.STOP_ORDER, (_, _) ->
                ExternalMusicManager.getInstance().fadeOutAndStop());
        registerReceiver(MessageType.PAUSE_ORDER, (_, _) ->
                ExternalMusicManager.getInstance().pause());
        registerReceiver(MessageType.RESUME_ORDER, (_, _) ->
                ExternalMusicManager.getInstance().unpause());
        registerReceiver(MessageType.ZONE_LIST, (_, body) -> {
            ambientZones = decodeZoneList(body);
            onZoneListUpdated.run();
        });

        PlaylistPlayer player = PlaylistPlayer.get();
        player.onPlayStarted = url -> broadcastStart(url, player.getRadius());
        player.onPaused = BerliozClientNetwork::broadcastPause;
        player.onResumed = BerliozClientNetwork::broadcastResume;
        player.onStopped = BerliozClientNetwork::broadcastStop;
        player.onSeek = BerliozClientNetwork::broadcastSeek;
        player.onListenerTrackEnded = BerliozClientNetwork::askNext;
        player.onUpcoming = urls -> {
            int radius = player.getRadius();
            for (String u : urls) {
                ExternalMusicManager.getInstance().preload(u);
                requestCache(u, radius);
            }
        };
    }

    public static void registerReceiver(byte type, Handler handler) {
        HANDLERS.put(type, handler);
    }

    public static boolean isServerSupported() {
        return ClientPlayNetworking.canSend(BerliozPayload.TYPE);
    }

    public static boolean hasPlaylistPermission() {
        return playlistPermission;
    }

    public static boolean hasAmbientPermission() {
        return ambientPermission;
    }

    public static void requestCache(String url, int radius) {
        send(MessageType.CACHE_REQUEST, radiusUrlBody(url, radius));
    }

    public static void broadcastStart(String url, int radius) {
        send(MessageType.BROADCAST_START, radiusUrlBody(url, radius));
    }

    public static void broadcastPause() {
        send(MessageType.BROADCAST_PAUSE, EMPTY);
    }

    public static void broadcastResume() {
        send(MessageType.BROADCAST_RESUME, EMPTY);
    }

    public static void broadcastStop() {
        send(MessageType.BROADCAST_STOP, EMPTY);
    }

    public static void broadcastSeek(float offsetSeconds) {
        send(MessageType.BROADCAST_SEEK, ByteBuffer.allocate(Float.BYTES).putFloat(offsetSeconds)
                .array());
    }

    // Ambient zones

    public static void requestZoneList(int radius) {
        send(MessageType.ZONE_LIST_REQUEST, ByteBuffer.allocate(Integer.BYTES).putInt(radius).array());
    }

    public static void saveZone(AmbientZoneData zone) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            boolean hasId = zone.id() != null;
            out.writeBoolean(hasId);
            if (hasId) {
                out.writeLong(zone.id().getMostSignificantBits());
                out.writeLong(zone.id().getLeastSignificantBits());
            }
            out.writeUTF(zone.name());
            out.writeInt(zone.x1());
            out.writeInt(zone.y1());
            out.writeInt(zone.z1());
            out.writeInt(zone.x2());
            out.writeInt(zone.y2());
            out.writeInt(zone.z2());
            out.writeInt(zone.urls().size());
            for (String url : zone.urls()) {
                out.writeUTF(url);
            }
        } catch (IOException e) {
            return;
        }
        send(MessageType.ZONE_SAVE, baos.toByteArray());
    }

    public static void deleteZone(UUID id) {
        send(MessageType.ZONE_DELETE, uuidBody(id));
    }

    public static void askNext() {
        send(MessageType.ASK_NEXT, EMPTY);
    }

    public static List<AmbientZoneData> getAmbientZones() {
        return ambientZones;
    }

    public static void setZoneListListener(Runnable listener) {
        onZoneListUpdated = (listener != null) ? listener : () -> { };
    }

    private static List<AmbientZoneData> decodeZoneList(byte[] body) {
        List<AmbientZoneData> out = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                UUID id = new UUID(in.readLong(), in.readLong());
                String name = in.readUTF();
                int x1 = in.readInt();
                int y1 = in.readInt();
                int z1 = in.readInt();
                int x2 = in.readInt();
                int y2 = in.readInt();
                int z2 = in.readInt();
                int n = in.readInt();
                List<String> urls = new ArrayList<>();
                for (int j = 0; j < n; j++) {
                    urls.add(in.readUTF());
                }
                out.add(new AmbientZoneData(id, name, x1, y1, z1, x2, y2, z2, urls));
            }
        } catch (IOException e) {
            LOGGER.warn("Invalid packet");
        }
        return out;
    }

    private static byte[] uuidBody(UUID id) {
        return ByteBuffer.allocate(2 * Long.BYTES)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private static byte[] radiusUrlBody(String url, int radius) {
        byte[] urlBytes = url.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[Integer.BYTES + urlBytes.length];
        body[0] = (byte) (radius >>> 24);
        body[1] = (byte) (radius >>> 16);
        body[2] = (byte) (radius >>> 8);
        body[3] = (byte) radius;
        System.arraycopy(urlBytes, 0, body, Integer.BYTES, urlBytes.length);
        return body;
    }

    public static void send(byte type, byte[] body) {
        if (!isServerSupported()) {
            LOGGER.debug("Cannot send Berlioz message");
            return;
        }
        ClientPlayNetworking.send(new BerliozPayload(BerliozPayload.frame(type, body)));
    }
}
