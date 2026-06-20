package vai.berlioz.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import vai.berlioz.client.config.BerliozConfig;
import vai.berlioz.client.music.loader.BinaryManager;
import vai.berlioz.client.music.loader.YtDlpPlayer;

import java.util.function.Consumer;
import java.util.function.Supplier;


public class ModMenuIntegration implements ModMenuApi {

    private static final Component title = Component.translatable("berlioz.config.title");

    private static final Component c_general_name = Component.translatable("berlioz.config.category.general.name");
    private static final Component c_general_tooltip = Component.translatable("berlioz.config.category.general.tooltip");
    private static final Component c_general_vol_global = Component.translatable("berlioz.config.category.general.volume.global");
    private static final Component c_general_vol_ambient = Component.translatable("berlioz.config.category.general.volume.ambient");
    private static final Component c_general_vol_dj = Component.translatable("berlioz.config.category.general.volume.dj");
    private static final Component c_general_vol_server = Component.translatable("berlioz.config.category.general.volume.server");
    // private static final Component c_general_vol_sound = Component.translatable("berlioz.config.category.general.volume.sound");
    private static final Component c_general_cover = Component.translatable("berlioz.config.category.general.cover");
    private static final Component c_general_fade = Component.translatable("berlioz.config.category.general.fade");

    private static final Component c_player_name = Component.translatable("berlioz.config.category.player.name");
    private static final Component c_player_tooltip = Component.translatable("berlioz.config.category.player.tooltip");
    private static final Component c_player_show = Component.translatable("berlioz.config.category.player.show");
    private static final Component c_player_show_always = Component.translatable("berlioz.config.category.player.show.always");
    private static final Component c_player_show_chat = Component.translatable("berlioz.config.category.player.show.chat");
    private static final Component c_player_show_hide = Component.translatable("berlioz.config.category.player.show.hide");
    private static final Component c_player_hide = Component.translatable("berlioz.config.category.player.hide");
    private static final Component c_player_position = Component.translatable("berlioz.config.category.player.position");
    private static final Component c_player_position_top_left = Component.translatable("berlioz.config.category.player.position.top_left");
    private static final Component c_player_position_top_right = Component.translatable("berlioz.config.category.player.position.top_right");
    private static final Component c_player_position_bottom_right = Component.translatable("berlioz.config.category.player.position.bottom_right");
    private static final Component c_player_style = Component.translatable("berlioz.config.category.player.style");
    private static final Component c_player_style_complete = Component.translatable("berlioz.config.category.player.style.complete");
    private static final Component c_player_style_minimal = Component.translatable("berlioz.config.category.player.style.minimal");

    private static final Component c_stream_name = Component.translatable("berlioz.config.category.stream.name");
    private static final Component c_stream_tooltip = Component.translatable("berlioz.config.category.stream.tooltip");
    private static final Component c_stream_youtube = Component.translatable("berlioz.config.category.stream.youtube");
    private static final Component c_stream_soundcloud = Component.translatable("berlioz.config.category.stream.soundcloud");
    private static final Component c_stream_raw = Component.translatable("berlioz.config.category.stream.raw");
    private static final Component c_stream_reload = Component.translatable("berlioz.config.category.stream.reload");
    private static final Component c_stream_empty = Component.translatable("berlioz.config.category.stream.empty");

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::createConfigScreen;
    }

    public static Screen createConfigScreen(Screen parent) {
        BerliozConfig cfg = BerliozConfig.get();
        BerliozConfig def = BerliozConfig.HANDLER.defaults();

        ConfigCategory general = ConfigCategory.createBuilder()
                .name(c_general_name)
                .tooltip(c_general_tooltip)
                .option(volume(c_general_vol_global, def.volGlobal, () -> cfg.volGlobal, v -> cfg.volGlobal = v))
                .option(volume(c_general_vol_ambient, def.volAmbient, () -> cfg.volAmbient, v -> cfg.volAmbient = v))
                .option(volume(c_general_vol_dj, def.volDj, () -> cfg.volDj, v -> cfg.volDj = v))
                .option(volume(c_general_vol_server, def.volServer, () -> cfg.volServer, v -> cfg.volServer = v))
                // .option(volume(c_general_vol_sound, def.volSound, () -> cfg.volSound, v -> cfg.volSound = v))
                .option(toggle(c_general_cover, def.coverMcSounds, () -> cfg.coverMcSounds, v -> cfg.coverMcSounds = v))
                .option(Option.<Float>createBuilder()
                        .name(c_general_fade)
                        .binding(def.fade, () -> cfg.fade, v -> cfg.fade = v)
                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                .range(0.0f, 10.0f).step(0.1f)
                                .formatValue(v -> Component.literal(String.format("%.1f s", v))))
                        .build())
                .build();

        ConfigCategory player = ConfigCategory.createBuilder()
                .name(c_player_name)
                .tooltip(c_player_tooltip)
                .option(Option.<BerliozConfig.DisplayMode>createBuilder()
                        .name(c_player_show)
                        .binding(def.show, () -> cfg.show, v -> cfg.show = v)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(BerliozConfig.DisplayMode.class)
                                .formatValue(ModMenuIntegration::displayLabel))
                        .build())
                .option(toggle(c_player_hide, def.hideWhenIdle, () -> cfg.hideWhenIdle, v -> cfg.hideWhenIdle = v))
                .option(Option.<BerliozConfig.Corner>createBuilder()
                        .name(c_player_position)
                        .binding(def.position, () -> cfg.position, v -> cfg.position = v)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(BerliozConfig.Corner.class)
                                .formatValue(ModMenuIntegration::cornerLabel))
                        .build())
                .option(Option.<BerliozConfig.Style>createBuilder()
                        .name(c_player_style)
                        .binding(def.style, () -> cfg.style, v -> cfg.style = v)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(BerliozConfig.Style.class)
                                .formatValue(ModMenuIntegration::styleLabel))
                        .build())
                .build();

        ConfigCategory stream = ConfigCategory.createBuilder()
                .name(c_stream_name)
                .tooltip(c_stream_tooltip)
                .option(toggle(c_stream_youtube, def.allowYoutube, () -> cfg.allowYoutube, v -> cfg.allowYoutube = v))
                .option(toggle(c_stream_soundcloud, def.allowSoundcloud, () -> cfg.allowSoundcloud, v -> cfg.allowSoundcloud = v))
                .option(toggle(c_stream_raw, def.allowRaw, () -> cfg.allowRaw, v -> cfg.allowRaw = v))
                .option(ButtonOption.createBuilder()
                        .name(c_stream_reload)
                        .action((_, _) -> reloadBinaries())
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(c_stream_empty)
                        .action((_, _) -> clearSessionCache())
                        .build())
                .build();

        YetAnotherConfigLib yacl = YetAnotherConfigLib.createBuilder()
                .title(title)
                .save(BerliozConfig.HANDLER::save)
                .category(general)
                .category(player)
                .category(stream)
                .build();

        return yacl.generateScreen(parent);
    }

    private static Option<Float> volume(Component name, float def, Supplier<Float> getter, Consumer<Float> setter) {
        return Option.<Float>createBuilder()
                .name(name)
                .binding(def, getter, setter)
                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                        .range(0.0f, 1.0f).step(0.01f)
                        .formatValue(v -> Component.literal(Math.round(v * 100) + "%")))
                .build();
    }

    private static Option<Boolean> toggle(Component name, boolean def, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(name)
                .binding(def, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Component displayLabel(BerliozConfig.DisplayMode mode) {
        return switch (mode) {
            case ALWAYS -> c_player_show_always;
            case CHAT -> c_player_show_chat;
            case NEVER -> c_player_show_hide;
        };
    }

    private static Component cornerLabel(BerliozConfig.Corner corner) {
        return switch (corner) {
            case TOP_LEFT -> c_player_position_top_left;
            case TOP_RIGHT -> c_player_position_top_right;
            case BOTTOM_RIGHT -> c_player_position_bottom_right;
        };
    }

    private static Component styleLabel(BerliozConfig.Style style) {
        return switch (style) {
            case COMPLETE -> c_player_style_complete;
            case MINIMAL -> c_player_style_minimal;
        };
    }

    private static void reloadBinaries() {
        Thread t = new Thread(() -> {
            try {
                BinaryManager.forceReload();
            } catch (Exception ignored) { }
        }, "berlioz-bin-reload");
        t.setDaemon(true);
        t.start();
    }

    private static void clearSessionCache() {
        YtDlpPlayer.clearCache();
    }
}
