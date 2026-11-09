package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.background.SimpleColorBackground;
import dev.lambdaurora.spruceui.border.SimpleBorder;
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import dev.lambdaurora.spruceui.widget.AbstractSpruceWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceContainerWidget;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class ChoiceScreen extends SpruceScreen {
    private static final int PANEL_W = 180;
    private static final int PADDING = GuiStyle.PADDING;
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 3;
    private static final int TEXT_GAP = 6;

    public static final class Choice {
        private final Component label;
        private final boolean enabled;
        private final Runnable action;

        public Choice(Component label, boolean enabled, Runnable action) {
            this.label = label;
            this.enabled = enabled;
            this.action = action;
        }

        public Choice(Component label, Runnable action) {
            this(label, true, action);
        }
    }

    private final Screen parent;
    private Component message;
    private List<Choice> choices;

    public ChoiceScreen(Screen parent, Component title, Component message, List<Choice> choices) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.choices = choices;
    }

    public void update(Component newMessage, List<Choice> newChoices) {
        this.message = newMessage;
        this.choices = newChoices;
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        Font font = Minecraft.getInstance().font;
        int innerW = PANEL_W - PADDING * 2;
        int textW = (int) (innerW / GuiStyle.TEXT_SCALE);
        List<FormattedCharSequence> titleLines = font.split(ChoiceScreen.styled(this.title), textW);
        List<FormattedCharSequence> messageLines = font.split(ChoiceScreen.styled(this.message), textW);
        int lineH = (int) Math.ceil(font.lineHeight * GuiStyle.TEXT_SCALE) + 1;
        int textH = (titleLines.size() + messageLines.size()) * lineH + TEXT_GAP;
        int buttonsH = this.choices.size() * (BTN_H + BTN_GAP) - BTN_GAP;
        int panelH = PADDING + textH + TEXT_GAP + buttonsH + PADDING;

        SpruceContainerWidget panel = new SpruceContainerWidget(
                Position.of((this.width - PANEL_W) / 2, (this.height - panelH) / 2), PANEL_W, panelH);
        panel.setBackground(new SimpleColorBackground(GuiStyle.PANEL_BG));
        panel.setBorder(new SimpleBorder(1, GuiStyle.PANEL_BORDER));

        panel.addChild(new TextBlock(Position.of(panel, PADDING, PADDING), innerW, lineH,
                titleLines, messageLines));

        int y = PADDING + textH + TEXT_GAP;
        for (Choice choice : this.choices) {
            panel.addChild(new WideButton(Position.of(panel, PADDING, y), innerW, BTN_H, choice.label,
                    choice.action).enabled(choice.enabled));
            y += BTN_H + BTN_GAP;
        }
        this.addRenderableWidget(panel);
    }

    private static Component styled(Component text) {
        return text.copy().withStyle(s -> s.withFont(GuiStyle.FONT));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private static final class TextBlock extends AbstractSpruceWidget {
        private final int lineH;
        private final List<FormattedCharSequence> titleLines;
        private final List<FormattedCharSequence> messageLines;

        TextBlock(Position position, int w, int lineH, List<FormattedCharSequence> titleLines,
                List<FormattedCharSequence> messageLines) {
            super(position);
            this.width = w;
            this.lineH = lineH;
            this.titleLines = titleLines;
            this.messageLines = messageLines;
            this.height = (titleLines.size() + messageLines.size()) * lineH + TEXT_GAP;
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            Font font = Minecraft.getInstance().font;
            int y = this.getY();
            for (FormattedCharSequence line : this.titleLines) {
                drawScaled(g, font, line, y, GuiStyle.TEXT_ACCENT);
                y += this.lineH;
            }
            y += TEXT_GAP;
            for (FormattedCharSequence line : this.messageLines) {
                drawScaled(g, font, line, y, GuiStyle.TEXT);
                y += this.lineH;
            }
        }

        private void drawScaled(SpruceGuiGraphics g, Font font, FormattedCharSequence line, int y, int color) {
            g.pose().pushMatrix();
            g.pose().translate(this.getX(), y);
            g.pose().scale(GuiStyle.TEXT_SCALE, GuiStyle.TEXT_SCALE);
            g.text(font, line, 0, 0, color, false);
            g.pose().popMatrix();
        }
    }
}
