package vai.berlioz.client.music.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class Playlist {

    public static final class Music {
        private final String url;
        private volatile String name;
        private volatile float duration;

        private Music(String url, Runnable onResolved) {
            this.url = url;
            this.name = OpenALStreamPlayer.nameFromUrl(url);
            this.duration = 0f;
            MusicMetadata.resolve(url, meta -> {
                this.name = meta.name();
                this.duration = meta.duration();
                onResolved.run();
            });
        }

        private Music(String url, String name, float duration) {
            this.url = url;
            this.name = name;
            this.duration = duration;
        }

        public String url() {
            return url;
        }

        public String name() {
            return name;
        }

        public float duration() {
            return duration;
        }
    }

    private final List<Music> content = new ArrayList<>();
    private Runnable onChanged = () -> { };

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = (onChanged != null) ? onChanged : () -> { };
    }

    public void addMusic(String url) {
        content.add(new Music(url, this::notifyChanged));
        notifyChanged();
    }

    public void addResolved(String url, String name, float duration) {
        content.add(new Music(url, name, duration));
        notifyChanged();
    }

    public void removeMusic(int index) {
        if (index >= 0 && index < content.size()) {
            content.remove(index);
            notifyChanged();
        }
    }

    public void upMusic(int index) {
        if (index > 0 && index < content.size()) {
            Collections.swap(content, index, index - 1);
            notifyChanged();
        }
    }

    public void downMusic(int index) {
        if (index >= 0 && index < content.size() - 1) {
            Collections.swap(content, index, index + 1);
            notifyChanged();
        }
    }

    public int size() {
        return content.size();
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public Music get(int index) {
        return (index >= 0 && index < content.size()) ? content.get(index) : null;
    }

    private void notifyChanged() {
        this.onChanged.run();
    }
}
