package net.fabricmc.loom.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Sends the JAR manifest to get recycled somewhere else.
 * It's not needed here.
 */
public class ManifestRecycler {
    public static void removeManifest(File jar) throws IOException {
        try (FileSystem inputFs = FileSystems.newFileSystem(new URI("jar:" + jar.toURI()), Collections.singletonMap("create", "true"))) {
            Path manifest = inputFs.getPath("META-INF", "MANIFEST.MF");
            if (Files.exists(manifest)) {
                Files.write(manifest, Collections.singleton("Manifest-Version: 1.0"), StandardCharsets.UTF_8);
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI", e);
        }
    }
}
