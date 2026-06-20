package vai.berlioz.client.music.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlaylistManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Berlioz/Playlist");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ENTRY_LIST = new TypeToken<List<Entry>>() {}.getType();

    private static final Playlist PLAYLIST = new Playlist();
    private static boolean loaded = false;

    private PlaylistManager() {}

    private static final class Entry {
        String url;
        String name;
        float duration;
    }

    public static synchronized Playlist getInstance() {
        if (!loaded) {
            loaded = true;
            load();
            PLAYLIST.setOnChanged(() -> Minecraft.getInstance().execute(PlaylistManager::save));
        }
        return PLAYLIST;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("berlioz").resolve("playlist.json");
    }

    private static void load() {
        Path path = file();
        if (!Files.exists(path)) return;
        try {
            List<Entry> entries = GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8), ENTRY_LIST);
            if (entries == null) return;
            for (Entry e : entries) {
                if (e != null && e.url != null) PLAYLIST.addResolved(e.url, e.name != null ? e.name : e.url, e.duration);
            }
            LOGGER.info("Loaded {} playlist track(s).", PLAYLIST.size());
        } catch (IOException | JsonSyntaxException ex) {
            LOGGER.warn("Failed to load playlist: {}", ex.toString());
        }
    }

    private static synchronized void save() {
        List<Entry> entries = new ArrayList<>(PLAYLIST.size());
        for (int i = 0; i < PLAYLIST.size(); i++) {
            Playlist.Music m = PLAYLIST.get(i);
            if (m == null) continue;
            Entry e = new Entry();
            e.url = m.url();
            e.name = m.name();
            e.duration = m.duration();
            entries.add(e);
        }
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(entries, ENTRY_LIST), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.warn("Failed to save playlist: {}", ex.toString());
        }
    }
}
