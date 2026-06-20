package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.SpruceTextAlignment;
import dev.lambdaurora.spruceui.background.SimpleColorBackground;
import dev.lambdaurora.spruceui.border.SimpleBorder;
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics;
import dev.lambdaurora.spruceui.widget.AbstractSpruceWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceContainerWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import vai.berlioz.client.config.BerliozConfig;
import vai.berlioz.client.music.loader.ExternalMusicManager;

import java.util.function.DoubleConsumer;

public class MusicPlayerWidget extends SpruceContainerWidget {
    private static final int COMPLETE_WIDTH = 170;
    private static final int COMPLETE_HEIGHT = 32;
    private static final int MINIMAL_WIDTH = 120;
    private static final int MINIMAL_HEIGHT = 14;
    private static final float TEXT_SCALE = 0.7f;
    private static final int PADDING = 4;
    private static final int VOLUME_WIDTH = 5;
    private static final int MINIMAL_SLIDER_W = 40;

    private static final int REFRESH_TICK_DELAY = 5;

    private final ScaledLabel nameLabel;
    private final VolumeSlider volumeSlider;
    private final ProgressBar progressBar;
    private final ScaledLabel elapsedLabel;
    private final ScaledLabel remainingLabel;
    private String lastTrackName = null;
    private int tickCounter = REFRESH_TICK_DELAY;

    public static int widthFor(BerliozConfig.Style style) {
        return style == BerliozConfig.Style.MINIMAL ? MINIMAL_WIDTH : COMPLETE_WIDTH;
    }

    public static int heightFor(BerliozConfig.Style style) {
        return style == BerliozConfig.Style.MINIMAL ? MINIMAL_HEIGHT : COMPLETE_HEIGHT;
    }

    public MusicPlayerWidget(Position position, BerliozConfig.Style style) {
        super(position, widthFor(style), heightFor(style));

        this.setBackground(new SimpleColorBackground(0xcc1e1e1e));
        this.setBorder(new SimpleBorder(1, 0xff808080));

        int width = this.getWidth();
        int height = this.getHeight();
        int textHeight = (int) Math.ceil(Minecraft.getInstance().font.lineHeight * TEXT_SCALE);
        Component defaultName = Component.translatable("berlioz.player.defaultName");

        if (style == BerliozConfig.Style.MINIMAL) {
            int contentWidth = width - MINIMAL_SLIDER_W - PADDING * 3;
            this.nameLabel = new ScaledLabel(
                    Position.of(this, PADDING, (height - textHeight) / 2),
                    contentWidth, textHeight, TEXT_SCALE, SpruceTextAlignment.LEFT, defaultName);
            this.addChild(this.nameLabel);

            int sliderH = 4;
            this.volumeSlider = new VolumeSlider(
                    Position.of(this, width - PADDING - MINIMAL_SLIDER_W, (height - sliderH) / 2),
                    MINIMAL_SLIDER_W, sliderH, true);

            this.progressBar = null;
            this.elapsedLabel = null;
            this.remainingLabel = null;
        } else {
            int contentWidth = width - VOLUME_WIDTH - PADDING * 3;
            this.nameLabel = new ScaledLabel(
                    Position.of(this, PADDING, PADDING),
                    contentWidth, textHeight, TEXT_SCALE, SpruceTextAlignment.LEFT, defaultName);
            this.addChild(this.nameLabel);

            int progressY = PADDING + textHeight + 4;
            int progressHeight = Math.max(2, height / 12);
            this.progressBar = new ProgressBar(
                    Position.of(this, PADDING, progressY), contentWidth, progressHeight);
            this.addChild(this.progressBar);

            int labelY = progressY + progressHeight + 4;
            int halfW = contentWidth / 2;
            this.elapsedLabel = new ScaledLabel(
                    Position.of(this, PADDING, labelY), halfW, textHeight, TEXT_SCALE,
                    SpruceTextAlignment.LEFT, Component.literal(formatTime(0)));
            this.remainingLabel = new ScaledLabel(
                    Position.of(this, PADDING + halfW, labelY), halfW, textHeight, TEXT_SCALE,
                    SpruceTextAlignment.RIGHT, Component.literal("--:--"));
            this.addChild(this.elapsedLabel);
            this.addChild(this.remainingLabel);

            this.volumeSlider = new VolumeSlider(
                    Position.of(this, width - PADDING - VOLUME_WIDTH, PADDING),
                    VOLUME_WIDTH, height - PADDING * 2, false);
        }

        this.volumeSlider.setValue(gainToSlider(ExternalMusicManager.getInstance().getSliderGain()));
        this.volumeSlider.listener =
                v -> ExternalMusicManager.getInstance().setSliderGain(sliderToGain(v));
        this.addChild(this.volumeSlider);
        this.tick();
    }

    public void tick() {
        if (++tickCounter < REFRESH_TICK_DELAY) return;
        tickCounter = 0;

        ExternalMusicManager emm = ExternalMusicManager.getInstance();
        float elapsed = emm.getTime();
        float total = emm.getDuration();
        if (this.elapsedLabel != null) {
            this.elapsedLabel.setText(Component.literal(formatTime((long) elapsed)));
        }
        if (total > 0) {
            if (this.remainingLabel != null) {
                this.remainingLabel.setText(Component.literal("-" + formatTime((long) (total - elapsed))));
            }
            if (this.progressBar != null) this.progressBar.setValue(elapsed / total);
        } else {
            if (this.remainingLabel != null) this.remainingLabel.setText(Component.literal("--:--"));
            if (this.progressBar != null) this.progressBar.setValue(0.0);
        }
        String name = emm.getTrackName();
        if (!name.equals(this.lastTrackName)) {
            this.lastTrackName = name;
            this.nameLabel.setText(name.isEmpty()
                    ? Component.translatable("berlioz.player.defaultName")
                    : Component.literal(name));
        }
    }

    private static String formatTime(long s) {
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static float sliderToGain(double slider) {
        if (slider <= 0.0) return 0f;
        if (slider >= 1.0) return 1f;
        // Music is not linear
        return (float) Math.pow(10.0, slider - 1.0);
    }

    private static double gainToSlider(float gain) {
        if (gain <= 0f) return 0.0;
        if (gain >= 1f) return 1.0;
        return 1.0 + Math.log10(gain);
    }

    private static final class ScaledLabel extends AbstractSpruceWidget {
        private final float scale;
        private final SpruceTextAlignment alignment;
        private Component text;

        ScaledLabel(Position position, int w, int h, float scale,
                    SpruceTextAlignment alignment, Component text) {
            super(position);
            this.width = w;
            this.height = h;
            this.scale = scale;
            this.alignment = alignment;
            this.setText(text);
        }

        void setText(Component text) {
            this.text = text.copy().withStyle(style -> style.withFont(GuiStyle.FONT));
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            Font font = Minecraft.getInstance().font;
            int textW = font.width(this.text);
            int xOffset = switch (this.alignment) {
                case CENTER -> (int) ((this.getWidth() - textW * this.scale) / 2.0f);
                case RIGHT -> (int) (this.getWidth() - textW * this.scale);
                default -> 0;
            };
            float originX = this.getX() + xOffset;
            float originY = this.getY();
            g.enableScissor(this.getX(), this.getY(),
                    this.getX() + this.getWidth(), this.getY() + this.getHeight());
            g.pose().pushMatrix();
            g.pose().translate(originX, originY);
            g.pose().scale(this.scale, this.scale);
            g.text(font, this.text, 0, 0, 0xffffffff, true);
            g.pose().popMatrix();
            g.disableScissor();
        }
    }

    private static final class ProgressBar extends AbstractSpruceWidget {
        private double value = 0.0;

        ProgressBar(Position position, int w, int h) {
            super(position);
            this.width = w;
            this.height = h;
        }

        void setValue(double v) { this.value = Mth.clamp(v, 0.0, 1.0); }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
            g.fill(x, y, x + w, y + h, GuiStyle.PANEL_BORDER);
            int filled = (int) (w * this.value);
            g.fill(x, y, x + filled, y + h, GuiStyle.PROGRESS);
            if (filled < w) {
                g.fill(x + filled - 1, y - 2, x + filled + 2, y + h + 2, 0xffffffff);
            }
        }
    }

    private static final class VolumeSlider extends AbstractSpruceWidget {
        private double value = 1.0;
        private final boolean horizontal;
        DoubleConsumer listener;

        VolumeSlider(Position position, int w, int h, boolean horizontal) {
            super(position);
            this.width = w;
            this.height = h;
            this.horizontal = horizontal;
        }

        void setValue(double v) {
            this.value = Mth.clamp(v, 0.0, 1.0);
            if (this.listener != null) this.listener.accept(this.value);
        }

        @Override
        protected boolean onMouseClick(MouseButtonEvent event, boolean dbl) {
            this.updateFromMouse(event);
            return true;
        }

        @Override
        protected boolean onMouseDrag(MouseButtonEvent event, double dx, double dy) {
            this.updateFromMouse(event);
            return true;
        }

        @Override
        protected boolean onMouseScroll(double mx, double my, double sx, double sy) {
            this.setValue(this.value + sy * 0.05);
            return true;
        }

        private void updateFromMouse(MouseButtonEvent event) {
            if (this.horizontal) {
                this.setValue((event.x() - this.getX()) / this.getWidth());
            } else {
                this.setValue(1.0 - (event.y() - this.getY()) / this.getHeight());
            }
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
            g.fill(x, y, x + w, y + h, GuiStyle.LIST_BORDER);               // rail
            if (this.horizontal) {
                int filled = (int) (w * this.value);
                g.fill(x, y, x + filled, y + h, GuiStyle.ACCENT);           // fill left -> right
                int handleX = x + filled;                                    // vertical handle
                g.fill(handleX - 1, y - 2, handleX + 1, y + h + 2, 0xffffffff);
            } else {
                int filled = (int) (h * this.value);
                g.fill(x, y + h - filled, x + w, y + h, GuiStyle.ACCENT);   // fill bottom -> top
                int handleY = y + h - filled;                                // horizontal handle
                g.fill(x - 2, handleY - 1, x + w + 2, handleY + 1, 0xffffffff);
            }
        }
    }
}
