package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics;
import dev.lambdaurora.spruceui.widget.AbstractSpruceWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class WideButton extends AbstractSpruceWidget {
    private final Component label;
    private final Identifier icon;
    private final Runnable onClick;
    private boolean enabled = true;

    WideButton(Position position, int w, int h, Component label, Runnable onClick) {
        this(position, w, h, label, null, onClick);
    }

    WideButton(Position position, int w, int h, Component label, Identifier icon, Runnable onClick) {
        super(position);
        this.width = w;
        this.height = h;
        this.label = label.copy().withStyle(s -> s.withFont(GuiStyle.FONT));
        this.icon = icon;
        this.onClick = onClick;
    }

    WideButton enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    protected boolean onMouseClick(MouseButtonEvent event, boolean dbl) {
        if (!this.enabled) return false;
        this.onClick.run();
        return true;
    }

    @Override
    protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        boolean hovered = this.enabled && mx >= x && mx < x + w && my >= y && my < y + h;

        int bg = !this.enabled ? GuiStyle.PANEL_BG : (hovered ? GuiStyle.BTN_HOVER : GuiStyle.BTN_BG);
        g.fill(x, y, x + w, y + h, bg);
        int border = GuiStyle.BTN_BORDER;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);

        int inset = 3;
        int regionW = w;
        if (this.icon != null) {
            int iconSize = h - inset * 2;
            int iconX = x + w - inset - iconSize;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, this.icon, iconX, y + inset, iconSize, iconSize);
            regionW = iconX - x;
        }

        Font font = Minecraft.getInstance().font;
        int textW = font.width(this.label);
        float drawX = x + (regionW - textW * GuiStyle.TEXT_SCALE) / 2f;
        float drawY = y + (h - font.lineHeight * GuiStyle.TEXT_SCALE) / 2f;
        g.pose().pushMatrix();
        g.pose().translate(drawX, drawY);
        g.pose().scale(GuiStyle.TEXT_SCALE, GuiStyle.TEXT_SCALE);
        g.text(font, this.label, 0, 0, this.enabled ? 0xffffffff : GuiStyle.TEXT_DIM, true);
        g.pose().popMatrix();
    }
}
