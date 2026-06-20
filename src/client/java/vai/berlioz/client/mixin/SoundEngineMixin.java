package vai.berlioz.client.mixin;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vai.berlioz.client.music.loader.ExternalMusicManager;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "reload", at = @At("RETURN"))
    private void onReloadReturn(CallbackInfo ci) {
        ExternalMusicManager.getInstance().handleSoundEngineReload();
    }
}
