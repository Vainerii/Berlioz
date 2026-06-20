package vai.berlioz.client.music.loader;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Classifies a URL by provider */
public enum MusicOrigin {
    YOUTUBE,
    SOUNDCLOUD,
    DIRECT;

    public static MusicOrigin of(String url) {
        String host = hostOf(url);
        if (host != null) {
            if (host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be")) {
                return YOUTUBE;
            }
            if (host.equals("soundcloud.com") || host.endsWith(".soundcloud.com")) {
                return SOUNDCLOUD;
            }
        }
        return DIRECT;
    }

    public static boolean isHttp(String url) {
        try {
            String scheme = new URI(url).getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
