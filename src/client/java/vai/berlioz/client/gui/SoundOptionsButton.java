package vai.berlioz.client.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import vai.berlioz.client.ModMenuIntegration;

public final class SoundOptionsButton {
    private static final int MARGIN = 6;
    private static final int BUTTON_H = 20;
    private static final int TEXT_PADDING = 16;

    private SoundOptionsButton() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, _) -> {
            if (!(screen instanceof SoundOptionsScreen)) return;
            Component label = Component.translatable("berlioz.options.sounds");
            Font font = Screens.getFont(screen);
            int w = font.width(label) + TEXT_PADDING;
            Button button = Button.builder(label,
                            _ -> client.setScreen(ModMenuIntegration.createConfigScreen(screen)))
                    .bounds(width - w - MARGIN, MARGIN, w, BUTTON_H)
                    .build();
            Screens.getWidgets(screen).add(button);
        });
    }
}
