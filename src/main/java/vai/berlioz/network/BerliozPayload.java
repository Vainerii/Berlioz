package vai.berlioz.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class BerliozPayload implements CustomPacketPayload {
    public static final Type<BerliozPayload> TYPE = new Type<>(BerliozChannel.ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, BerliozPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBytes(value.data),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new BerliozPayload(bytes);
            }
    );

    private final byte[] data;

    public BerliozPayload(byte[] data) {
        this.data = data;
    }

    public byte[] data() {
        return data;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static byte[] frame(byte type, byte[] body) {
        byte[] out = new byte[1 + body.length];
        out[0] = type;
        System.arraycopy(body, 0, out, 1, body.length);
        return out;
    }

}
