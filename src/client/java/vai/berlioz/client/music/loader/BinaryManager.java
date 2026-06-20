package vai.berlioz.client.music.loader;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages the yt-dlp + ffmpeg binaries in /berlioz/bin: downloads on demand, updates yt-dlp daily.
 * Some parts of this class has been verified and improved by IA (then double-checked). I don't want to risk anything
 * on the base of me not having enough knowledge on security. This class is critical.
 * Also, I don't have a macOS, so I was not able to test it for this OS, so the macOS parts were generated.
 */
public class BinaryManager {

    private static final Path BIN_DIR = FabricLoader.getInstance()
        .getConfigDir().resolve("berlioz").resolve("bin");

    private static final long UPDATE_INTERVAL_MS = 86400000L; // 1 day
    private static final Path UPDATE_STAMP = BIN_DIR.resolve("yt-dlp-last-update.txt");

    private static final String YTDLP_BASE = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";

    private static final String FFMPEG_WIN_URL =
            "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-8.1.1-essentials_build.zip";
    private static final String FFMPEG_WIN_SHA256 =
            "6f58ce889f59c311410f7d2b18895b33c03456463486f3b1ebc93d97a0f54541";
    // maybe too short, 2 years of retention, but it is only for linux and we expect to update faster than every 2 years ofc
    private static final String FFMPEG_LIN_URL =
            "https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-06-20-13-30/ffmpeg-N-125136-gb57ff00bcf-linux64-gpl.tar.xz";
    private static final String FFMPEG_LIN_SHA256 =
            "e73c0658d2b778e92d5367d3b47368c86f1589ae93764ea74cdca9e213fbba59";
    private static final String FFMPEG_MAC_URL =
            "https://evermeet.cx/ffmpeg/ffmpeg-8.1.2.zip";
    private static final String FFMPEG_MAC_SHA256 =
            "e91df72a1ee7c26606f90dd2dd4dcccc6a75140ff9ea6fdd50faae828b82ba69";


    private static volatile Path ytdlpPath;
    private static volatile Path ffmpegPath;

    private static final String OS = BinaryManager.getOs();

    /** Ensures yt-dlp and ffmpeg are present, downloading any missing one. */
    public static synchronized void ensureReady() throws Exception {
        Files.createDirectories(BIN_DIR);
        if (!BinaryManager.isValid(ytdlpPath)) ytdlpPath = BinaryManager.fetchYtdlp();
        if (!BinaryManager.isValid(ffmpegPath)) ffmpegPath = BinaryManager.fetchFfmpeg();
        BinaryManager.scheduleYtdlpUpdate(ytdlpPath);
    }

    public static Path getYtdlp()  { return ytdlpPath; }
    public static Path getFfmpeg() { return ffmpegPath; }

    public static synchronized void forceReload() throws Exception {
        Files.deleteIfExists(BIN_DIR.resolve(OS.equals("windows") ? "yt-dlp.exe" : "yt-dlp"));
        Files.deleteIfExists(BIN_DIR.resolve(OS.equals("windows") ? "ffmpeg.exe" : "ffmpeg"));
        Files.deleteIfExists(UPDATE_STAMP);
        ytdlpPath = null;
        ffmpegPath = null;
        ensureReady();
    }

    private static Path fetchYtdlp() throws Exception {
        Path dest = BIN_DIR.resolve(OS.equals("windows") ? "yt-dlp.exe" : "yt-dlp");
        if (BinaryManager.isValid(dest)) return dest;
        BinaryManager.downloadVerifiedYtdlp(dest);
        BinaryManager.makeExecutable(dest);
        if (OS.equals("macos"))
            BinaryManager.removeQuarantine(dest);
        return dest;
    }

    /** The yt-dlp asset name for this OS, as it appears in the release's SHA2-256SUMS list. */
    private static String ytdlpAssetName() {
        return switch (OS) {
            case "windows" -> "yt-dlp.exe";
            case "macos" -> "yt-dlp_macos";
            default -> "yt-dlp";
        };
    }

    /**
     * Downloads the latest yt-dlp to a temp file, verifies it against the SHA-256 published in the
     * release's SHA2-256SUMS, then moves it into dest. On any mismatch, a SecurityException is thrown.
     */
    private static void downloadVerifiedYtdlp(Path dest) throws Exception {
        String filename = ytdlpAssetName();
        Path tmp = BIN_DIR.resolve(dest.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        try {
            BinaryManager.download(YTDLP_BASE + filename, tmp);
            String expected = BinaryManager.fetchExpectedHash(YTDLP_BASE + "SHA2-256SUMS", filename);
            BinaryManager.verifySha256(tmp, expected);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static Path fetchFfmpeg() throws Exception {
        Path dest = BIN_DIR.resolve(OS.equals("windows") ? "ffmpeg.exe" : "ffmpeg");
        if (BinaryManager.isValid(dest)) return dest;

        Path tmp = BIN_DIR.resolve("ffmpeg-dl.tmp");
        Files.deleteIfExists(tmp); // if a failed download left
        switch (OS) {
            // Each archive is verified against its hash before extraction
            case "windows" -> {
                BinaryManager.download(FFMPEG_WIN_URL, tmp);
                BinaryManager.verifyArchive(tmp, FFMPEG_WIN_SHA256);
                BinaryManager.extractZipEntry(tmp, "bin/ffmpeg.exe", dest);
            }
            case "macos" -> {
                BinaryManager.download(FFMPEG_MAC_URL, tmp);
                BinaryManager.verifyArchive(tmp, FFMPEG_MAC_SHA256);
                BinaryManager.extractZipEntry(tmp, "ffmpeg", dest);
            }
            default -> {
                BinaryManager.download(FFMPEG_LIN_URL, tmp);
                BinaryManager.verifyArchive(tmp, FFMPEG_LIN_SHA256);
                BinaryManager.extractTarXz(tmp, dest);
            }
        }

        Files.deleteIfExists(tmp);
        BinaryManager.makeExecutable(dest);
        if (OS.equals("macos")) BinaryManager.removeQuarantine(dest);
        return dest;
    }

    private static void extractZipEntry(Path zip, String suffix, Path dest) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = zis.getNextEntry()) != null; ) {
                String name = e.getName();
                if (!e.isDirectory() && (name.equals(suffix) || name.endsWith("/" + suffix))) {
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new Exception("Entry not found : " + suffix);
    }

    // Uses system tar (GNU tar on Linux), since Java has no built-in XZ support.
    private static void extractTarXz(Path tarXz, Path dest) throws Exception {
        Process proc = new ProcessBuilder(
            "tar", "-xJOf", tarXz.toString(), "--wildcards", "--no-anchored", "*/bin/ffmpeg"
        ).redirectErrorStream(true).start();
        try (InputStream in = proc.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        int exit = proc.waitFor();
        if (exit != 0 || !BinaryManager.isValid(dest))
            throw new Exception("tar extraction of ffmpeg failed (exit " + exit + ")");
    }

    private static void download(String url, Path dest) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }

    private static String downloadString(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static void verifySha256(Path file, String expectedHex) throws Exception {
        String actual = BinaryManager.sha256Hex(file);
        if (!actual.equalsIgnoreCase(expectedHex)) {
            Files.deleteIfExists(file);
            throw new SecurityException("Checksum mismatch for " + file.getFileName()
                + " (expected " + expectedHex + ", got " + actual + ")");
        }
    }

    private static String sha256Hex(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) md.update(buf, 0, r);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /** Looks up filename's SHA-256 in a SHA2-256SUMS-format list. */
    private static String fetchExpectedHash(String sumsUrl, String filename) throws Exception {
        String sums = BinaryManager.downloadString(sumsUrl);
        for (String line : sums.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2 && parts[1].equals(filename)) {
                return parts[0];
            }
        }
        throw new SecurityException("No checksum for " + filename + " in " + sumsUrl);
    }

    /**
     * Verifies the downloaded ffmpeg archive against its pinned SHA-256. When the pinned hash is
     * blank (no hash filled in for this OS yet), logs a security warning and continues unverified
     * rather than blocking the installation.
     */
    private static void verifyArchive(Path archive, String expectedHash) throws Exception {
        if (expectedHash.isBlank()) {
            System.getLogger("Berlioz/Binary").log(System.Logger.Level.WARNING,
                "ffmpeg installed WITHOUT checksum verification on this OS (no pinned hash set).");
            return;
        }
        BinaryManager.verifySha256(archive, expectedHash);
    }

    /** Daily verified re-download of yt-dlp */
    private static void scheduleYtdlpUpdate(Path ytdlp) {
        try {
            long lastUpdate = Files.exists(UPDATE_STAMP) ? Long.parseLong(Files.readString(UPDATE_STAMP).trim()) : 0L;
            if (System.currentTimeMillis() - lastUpdate < UPDATE_INTERVAL_MS) return;
        } catch (Exception ignored) {}

        Thread t = new Thread(() -> {
            try {
                BinaryManager.downloadVerifiedYtdlp(ytdlp);
                BinaryManager.makeExecutable(ytdlp);
                if (OS.equals("macos")) BinaryManager.removeQuarantine(ytdlp);
                Files.writeString(UPDATE_STAMP, String.valueOf(System.currentTimeMillis()));
            } catch (Exception ignored) { }
        }, "yt-dlp-update");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Marks file as executable. No-op on Windows; on POSIX adds the execute bit
     * (owner/group/others), falling back to File.setExecutable.
     */
    private static void makeExecutable(Path file) throws IOException {
        if (OS.equals("windows")) return;
        try {
            Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(file));
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException e) {
            if (!file.toFile().setExecutable(true, false)) {
                throw new IOException("Failed to make executable: " + file);
            }
        }
    }

    /** Best-effort removal of the macOS quarantine attribute (missing attr is fine). */
    private static void removeQuarantine(Path file) {
        try {
            new ProcessBuilder("xattr", "-d", "com.apple.quarantine", file.toString())
                .redirectErrorStream(true).start().waitFor();
        } catch (Exception ignored) {}
    }

    private static boolean isValid(Path p) {
        try { return p != null && Files.exists(p) && Files.size(p) > 0; }
        catch (IOException e) { return false; }
    }

    private static String getOs() {
        String n = System.getProperty("os.name").toLowerCase();
        if (n.contains("win")) return "windows";
        if (n.contains("mac")) return "macos";
        return "linux";
    }
}
