package vai.berlioz.client.worldedit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/**
 * Get WE selection positions
 * WIP
 */
public final class WorldEditTracker {

    private static final WorldEditTracker INSTANCE = new WorldEditTracker();

    private static final Pattern FIRST_POSITION =
            Pattern.compile("First position set to \\((-?\\d+), (-?\\d+), (-?\\d+)\\)");
    private static final Pattern SECOND_POSITION =
            Pattern.compile("Second position set to \\((-?\\d+), (-?\\d+), (-?\\d+)\\)");

    private boolean hasFirst = false;
    private int firstX;
    private int firstY;
    private int firstZ;

    private boolean hasSecond = false;
    private int secondX;
    private int secondY;
    private int secondZ;

    private WorldEditTracker() {}

    public static WorldEditTracker getInstance() {
        return INSTANCE;
    }

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                INSTANCE.accept(message.getString()));
    }

    private void accept(String text) {
        Matcher first = WorldEditTracker.FIRST_POSITION.matcher(text);
        if (first.find()) {
            this.firstX = Integer.parseInt(first.group(1));
            this.firstY = Integer.parseInt(first.group(2));
            this.firstZ = Integer.parseInt(first.group(3));
            this.hasFirst = true;
            return;
        }
        Matcher second = WorldEditTracker.SECOND_POSITION.matcher(text);
        if (second.find()) {
            this.secondX = Integer.parseInt(second.group(1));
            this.secondY = Integer.parseInt(second.group(2));
            this.secondZ = Integer.parseInt(second.group(3));
            this.hasSecond = true;
        }
    }

    public boolean hasFirstPosition() { return this.hasFirst; }

    public int getFirstX() { return this.firstX; }

    public int getFirstY() { return this.firstY; }

    public int getFirstZ() { return this.firstZ; }

    public boolean hasSecondPosition() { return this.hasSecond; }

    public int getSecondX() { return this.secondX; }

    public int getSecondY() { return this.secondY; }

    public int getSecondZ() { return this.secondZ; }
}
