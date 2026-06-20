package vai.berlioz.client.music.loader;

import java.util.List;
import java.util.UUID;

public final class AmbientZoneData {
    private final UUID id;
    private final String name;
    private final int x1;
    private final int y1;
    private final int z1;
    private final int x2;
    private final int y2;
    private final int z2;
    private final List<String> urls;

    public AmbientZoneData(UUID id, String name, int x1, int y1, int z1, int x2, int y2, int z2, List<String> urls) {
        this.id = id;
        this.name = name;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        this.urls = List.copyOf(urls);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int x1() {
        return x1;
    }

    public int y1() {
        return y1;
    }

    public int z1() {
        return z1;
    }

    public int x2() {
        return x2;
    }

    public int y2() {
        return y2;
    }

    public int z2() {
        return z2;
    }

    public List<String> urls() {
        return urls;
    }

    public double centerX() {
        return (Math.min(x1, x2) + Math.max(x1, x2)) / 2.0 + 0.5;
    }

    public double centerY() {
        return (Math.min(y1, y2) + Math.max(y1, y2)) / 2.0 + 0.5;
    }

    public double centerZ() {
        return (Math.min(z1, z2) + Math.max(z1, z2)) / 2.0 + 0.5;
    }
}
