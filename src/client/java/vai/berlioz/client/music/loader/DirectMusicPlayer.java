package vai.berlioz.client.music.loader;

import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider;
import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.FormatConversionProvider;
import java.net.URI;
import java.net.URL;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/** Player for direct audio URLs */
public class DirectMusicPlayer extends PcmStreamPlayer {

    private final String url;

    public DirectMusicPlayer(String url, Consumer<String> onError) {
        super(onError);
        this.url = url;
    }

    @Override
    protected AudioInputStream openRawStream() throws Exception {
        if (!MusicOrigin.isHttp(url)) {
            throw new Exception("Refusing non-http(s) URL: " + url);
        }
        setName(nameFromUrl(url));
        LOGGER.info("Opening direct stream: {}", url);
        URL audioUrl = URI.create(url).toURL();

        // MP3 via mp3spi, OGG via vorbiss
        if (url.toLowerCase().contains(".ogg")) {
            for (AudioFileReader reader : ServiceLoader.load(AudioFileReader.class, DirectMusicPlayer.class.getClassLoader())) {
                try { return reader.getAudioInputStream(audioUrl); }
                catch (UnsupportedAudioFileException ignore) {}
            }
            throw new Exception("Vorbiss not found");
        }
        return new MpegAudioFileReader().getAudioInputStream(audioUrl);
    }

    @Override
    protected AudioInputStream convertToPcm(AudioFormat pcmFormat, AudioInputStream rawStream) throws Exception {
        if (url.toLowerCase().contains(".ogg")) {
            for (FormatConversionProvider conv : ServiceLoader.load(FormatConversionProvider.class, DirectMusicPlayer.class.getClassLoader())) {
                if (conv.isConversionSupported(pcmFormat, rawStream.getFormat()))
                    return conv.getAudioInputStream(pcmFormat, rawStream);
            }
            throw new Exception("OGG to PCM conversion not supported ??");
        }
        return new MpegFormatConversionProvider().getAudioInputStream(pcmFormat, rawStream);
    }
}
