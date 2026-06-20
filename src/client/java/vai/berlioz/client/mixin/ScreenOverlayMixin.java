package vai.berlioz.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vai.berlioz.client.gui.MusicPlayerOverlay;

@Mixin(Screen.class)
public abstract class ScreenOverlayMixin {

    // Fires after any screen's full render
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
    private void onAfterScreenRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MusicPlayerOverlay.INSTANCE.onScreenRender(graphics, mouseX, mouseY, delta);
    }
}