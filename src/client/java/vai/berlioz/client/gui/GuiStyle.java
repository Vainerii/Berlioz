package vai.berlioz.client.gui;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

final class GuiStyle {
    private GuiStyle() {}

    static final FontDescription FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("berlioz", "classic"));
    static final float TEXT_SCALE = 0.9f;

    static final int PANEL_WIDTH = 140;
    static final int PADDING = 6;
    static final int ADD_BUTTON_H = 16;
    static final int URL_FIELD_H = 14;

    static final int PANEL_BG = 0xee101010;
    static final int PANEL_BORDER = 0xff404040;
    static final int LIST_BG = 0xff000000;
    static final int LIST_BORDER = 0xff303030;
    static final int BTN_BG = 0xff2a2a2a;
    static final int BTN_HOVER = 0xff3a3a3a;
    static final int BTN_BORDER = 0xff505050;
    static final int ENTRY_BG = 0xff161616;
    static final int ENTRY_CURRENT = 0xff203848;
    static final int ACCENT = 0xff44ccff;
    static final int PROGRESS = 0xff44dd44;
    static final int TEXT = 0xffdddddd;
    static final int TEXT_DIM = 0xff999999;
    static final int TEXT_ACCENT = 0xff8ed6ff;
    static final int TEXT_DARK = 0xff101010;

    static String formatTime(long s) {
        return String.format("%d:%02d", s / 60, s % 60);
    }

    static String formatDuration(float seconds) {
        if (seconds <= 0f) return "--:--";
        return formatTime((long) seconds);
    }
}
