package vai.berlioz.network;

import net.minecraft.resources.Identifier;

public final class BerliozChannel {
    public static final String NAMESPACE = "berlioz";
    public static final String NAME = "main";
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NAMESPACE, NAME);
    public static final String KEY = NAMESPACE + ":" + NAME;

    private BerliozChannel() {}
}
