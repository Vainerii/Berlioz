package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics;
import dev.lambdaurora.spruceui.widget.AbstractSpruceWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.function.IntConsumer;
import vai.berlioz.client.music.loader.Playlist;

/**
 * Scrollable list of Playlist tracks
 */
final class TrackListWidget extends AbstractSpruceWidget {
    private static final int INSET = 3;
    private static final int ENTRY_H = 24;
    private static final int ENTRY_GAP = 2;
    private static final int BTN = 7;
    private static final int BTN_VGAP = 1;
    private static final int SCROLL_STEP = 12;
    private static final float TEXT_SCALE = GuiStyle.TEXT_SCALE;
    private static final FontDescription FONT_DESC = GuiStyle.FONT;

    private static final Identifier ICON_MOVE_UP =
            Identifier.fromNamespaceAndPath("berlioz", "icon/move_up");
    private static final Identifier ICON_MOVE_DOWN =
            Identifier.fromNamespaceAndPath("berlioz", "icon/move_down");
    private static final Identifier ICON_REMOVE =
            Identifier.fromNamespaceAndPath("berlioz", "icon/remove");
    private static final Identifier ICON_PLAY =
            Identifier.fromNamespaceAndPath("berlioz", "icon/play");

    private final boolean withPlay;
    private Playlist playlist;
    private int currentIndex = -1;
    private int scrollPx = 0;

    IntConsumer onPlay = _ -> { };

    TrackListWidget(Position position, int w, int h, boolean withPlay) {
        super(position);
        this.width = w;
        this.height = h;
        this.withPlay = withPlay;
    }

    void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
        this.scrollPx = 0;
    }

    void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    private int rowStride() {
        return ENTRY_H + ENTRY_GAP;
    }

    private int maxScroll() {
        int n = (this.playlist != null) ? this.playlist.size() : 0;
        int contentH = Math.max(0, n * rowStride() - ENTRY_GAP);  // no trailing gap
        return Math.max(0, contentH - (this.getHeight() - INSET * 2));
    }

    @Override
    protected boolean onMouseScroll(double mx, double my, double sx, double sy) {
        this.scrollPx = Math.max(0, Math.min(maxScroll(),
                this.scrollPx - (int) Math.signum(sy) * SCROLL_STEP));
        return true;
    }

    @Override
    protected boolean onMouseClick(MouseButtonEvent event, boolean dbl) {
        if (this.playlist == null) return false;
        int mx = (int) event.x();
        int my = (int) event.y();
        int rel = my - (this.getY() + INSET) + this.scrollPx;
        if (rel < 0) return false;
        int index = rel / rowStride();
        int within = rel % rowStride();
        if (index >= this.playlist.size() || within >= ENTRY_H) return false;
        int leftX = this.getX() + INSET;
        int removeX = this.getX() + this.getWidth() - INSET - BTN;

        if (mx >= leftX && mx < leftX + BTN) {
            int b = within / (BTN + BTN_VGAP);
            if (within - b * (BTN + BTN_VGAP) >= BTN) return false;
            return clickLeftColumn(b, index);
        }
        if (mx >= removeX && mx < removeX + BTN && within < BTN) {
            this.playlist.removeMusic(index);
            this.scrollPx = Math.min(this.scrollPx, maxScroll());
            return true;
        }
        return false;
    }

    private boolean clickLeftColumn(int button, int index) {
        if (this.withPlay) {
            switch (button) {
                case 0 -> this.playlist.upMusic(index);
                case 1 -> this.onPlay.accept(index);
                case 2 -> this.playlist.downMusic(index);
                default -> { return false; }
            }
        } else {
            switch (button) {
                case 0 -> this.playlist.upMusic(index);
                case 1 -> this.playlist.downMusic(index);
                default -> { return false; }
            }
        }
        this.scrollPx = Math.min(this.scrollPx, maxScroll());
        return true;
    }

    @Override
    protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        g.fill(x, y, x + w, y + h, GuiStyle.LIST_BG);
        drawBorder(g, x, y, w, h, GuiStyle.LIST_BORDER);

        int n = (this.playlist != null) ? this.playlist.size() : 0;
        if (n == 0) return;
        Font font = Minecraft.getInstance().font;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        for (int i = 0; i < n; i++) {
            int entryY = y + INSET - this.scrollPx + i * rowStride();
            if (entryY + ENTRY_H <= y || entryY >= y + h) continue;
            drawEntry(g, font, i, x + INSET, entryY, w - INSET * 2, mx, my);
        }
        g.disableScissor();
    }

    private void drawEntry(SpruceGuiGraphics g, Font font, int index, int ex, int ey, int ew, int mx, int my) {
        Playlist.Music music = this.playlist.get(index);
        if (music == null) return;
        boolean current = this.withPlay && index == this.currentIndex;
        int bg = current ? GuiStyle.ENTRY_CURRENT : GuiStyle.ENTRY_BG;
        g.fill(ex, ey, ex + ew, ey + ENTRY_H, bg);

        drawIconButton(g, ICON_MOVE_UP, ex, ey, mx, my);
        if (this.withPlay) {
            drawIconButton(g, ICON_PLAY, ex, ey + (BTN + BTN_VGAP), mx, my);
            drawIconButton(g, ICON_MOVE_DOWN, ex, ey + (BTN + BTN_VGAP) * 2, mx, my);
        } else {
            drawIconButton(g, ICON_MOVE_DOWN, ex, ey + (BTN + BTN_VGAP), mx, my);
        }

        int textX = ex + BTN + 3;
        Component name = Component.literal(music.name()).withStyle(s -> s.withFont(FONT_DESC));
        g.pose().pushMatrix();
        g.pose().translate(textX, ey + 3);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE);
        g.text(font, name, 0, 0, current ? GuiStyle.TEXT_ACCENT : GuiStyle.TEXT, false);
        g.pose().popMatrix();

        Component dur = Component.literal(GuiStyle.formatDuration(music.duration()))
                .withStyle(s -> s.withFont(FONT_DESC));
        g.pose().pushMatrix();
        g.pose().translate(textX, ey + 13);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE);
        g.text(font, dur, 0, 0, GuiStyle.TEXT_DIM, false);
        g.pose().popMatrix();

        int removeX = ex + ew - BTN;
        g.fill(removeX - 1, ey, ex + ew, ey + BTN + 1, bg);
        drawIconButton(g, ICON_REMOVE, removeX, ey, mx, my);
    }

    private static void drawIconButton(SpruceGuiGraphics g, Identifier icon, int bx, int by, int mx, int my) {
        boolean hovered = mx >= bx && mx < bx + BTN && my >= by && my < by + BTN;
        g.fill(bx, by, bx + BTN, by + BTN, hovered ? GuiStyle.BTN_HOVER : GuiStyle.BTN_BG);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, bx, by, BTN, BTN);
    }

    private static void drawBorder(SpruceGuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
