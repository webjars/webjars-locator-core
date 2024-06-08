package org.webjars;

import static java.util.Objects.requireNonNull;
import static org.webjars.WebJarAssetLocator.WEBJARS_PATH_PREFIX;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webjars.WebJarAssetLocator.WebJarInfo;

/**
 * Utility for extracting WebJars onto the filesystem. The extractor also recognises the node_modules
 * convention used by WebJars. node_modules are a special place within a WebJar that contain the assets
 * required in an environment conforming to the Node API for require.
 */
public class WebJarExtractor {

    /**
     * The node_modules directory prefix as a convenience.
     */
    public static final String PACKAGE_JSON = "package.json";

    /** The bower.json file name. */
    public static final String BOWER_JSON = "bower.json";

    private static final String JAR_PATH_DELIMITER = "/";

    private static final Logger log = LoggerFactory.getLogger(WebJarExtractor.class);

    private final ClassLoader classLoader;

    public WebJarExtractor() {
        this(WebJarExtractor.class.getClassLoader());
    }

    public WebJarExtractor(@Nonnull ClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader);
    }

    /**
     * Extract all WebJars.
     *
     * @param to The directory to extract to
     * @throws java.io.IOException There was a problem extracting the WebJars
     */
    public void extractAllWebJarsTo(@Nullable File to) throws IOException {
        extractWebJarsTo(null, null, to);
    }

    /**
     * Extract the given WebJar to the given location.
     * The WebJar will be extracted, without its version in the path, to the given directory.
     *
     * @param name The name of the WebJar to extract.
     * @param to   The location to extract it to. All WebJars will be merged into this location.
     * @throws java.io.IOException There was a problem extracting the WebJars
     */
    public void extractWebJarTo(@Nullable String name, @Nullable File to) throws IOException {
        extractWebJarsTo(name, null, to);
    }

    /**
     * Extract the node_modules of all WebJars and merge them into the same folder.
     *
     * @param to The location to extract it to. All WebJars will be merged into this location.
     * @throws java.io.IOException There was a problem extracting the WebJars
     */
    public void extractAllNodeModulesTo(@Nullable File to) throws IOException {
        extractWebJarsTo(null, PACKAGE_JSON, to);
    }

    /**
     * Extract the bower_components of all WebJars and merge them into the same folder.
     *
     * @param to The location to extract it to. All WebJars will be merged into this location.
     * @throws java.io.IOException There was a problem extracting the WebJars
     */
    public void extractAllBowerComponentsTo(@Nullable File to) throws IOException {
        extractWebJarsTo(null, BOWER_JSON, to);
    }

    @Nullable
    private String getModuleId(@Nonnull final String moduleNameFile) {
        requireNonNull(moduleNameFile, "Module name file must not be null");

        String json = null;

        try (InputStream inputStream = classLoader.getResourceAsStream(moduleNameFile)) {
            if (inputStream != null) {
                try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
                    json = scanner.useDelimiter("\\A").next();
                }
            }
        }
        catch (IOException e) {
            // ignored
        }

        if (json != null) {
            try {
                return getJsonModuleId(json);
            }
            catch (IOException e) {
                // ignored
            }
        }

        return null;
    }

    private void extractResourcesTo(@Nonnull final String webJarName, @Nonnull final WebJarAssetLocator.WebJarInfo webJarInfo, @Nullable final String moduleFilePath, @Nonnull final ResourceList webJarResources, @Nullable final File to) {
        final String maybeModuleId = moduleFilePath == null ? null : getModuleId(moduleFilePath);
        final String webJarId = maybeModuleId == null ? webJarName : maybeModuleId;
        webJarResources.forEachInputStreamIgnoringIOException((resource, inputStream) -> extractResource(webJarName, webJarInfo, to, webJarId, resource, inputStream));
    }

    private static void extractResource(@Nonnull String webJarName, @Nonnull WebJarInfo webJarInfo, @Nullable File to, @Nonnull String webJarId, @Nonnull Resource resource, @Nonnull InputStream inputStream) {
        final String prefix = String.format("%s%s%s%s%s", WEBJARS_PATH_PREFIX, JAR_PATH_DELIMITER, webJarName, JAR_PATH_DELIMITER, webJarInfo.getVersion() == null ? "" : String.format("%s%s", webJarInfo.getVersion(), JAR_PATH_DELIMITER));
        if (resource.getPath().startsWith(prefix)) {
            final String newPath = resource.getPath().substring(prefix.length());
            final String relativeName = String.format("%s%s%s", webJarId, File.separator, newPath);
            final File newFile = new File(to, relativeName);
            final boolean allowOverwrite = webJarInfo.getVersion() != null && webJarInfo.getVersion().toLowerCase().endsWith("-snapshot");
            if (!newFile.exists() || allowOverwrite) {
                try {
                    newFile.getParentFile().mkdirs();
                    Files.copy(inputStream, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    inputStream.close();
                    Set<PosixFilePermission> resourcePerms = resource.getPosixFilePermissions();
                    if (resourcePerms != null && Files.getFileStore(newFile.toPath()).supportsFileAttributeView(PosixFileAttributeView.class)) {
                        Files.setPosixFilePermissions(newFile.toPath(), resourcePerms);
                    }
                    if (resource.getLastModified() > 0) {
                        boolean lastModifiedSet = newFile.setLastModified(resource.getLastModified());
                        if (!lastModifiedSet) {
                            log.warn("Last modified of file {} could not be changed", newFile);
                        }
                    }
                } catch (IOException e) {
                    log.error("Could not write file", e);
                }
            }
        }
    }

    /**
     * A generalised form for extracting WebJars.
     *
     * @param name           If null then all WebJars are extracted, otherwise the name of a single WebJars.
     * @param moduleNameFile The file to get module name from, can be {@code null}, artifactId will be used in this case.
     * @param to             The location to extract it to. All WebJars will be merged into this location.
     */
    private void extractWebJarsTo(@Nullable final String name, @Nullable final String moduleNameFile, @Nullable final File to) {
        if (name == null) {
            final ClassGraph classGraph = new ClassGraph().overrideClassLoaders(classLoader).ignoreParentClassLoaders().acceptPaths(WEBJARS_PATH_PREFIX);
            try (ScanResult scanResult = classGraph.scan()) {
                final Map<String, WebJarAssetLocator.WebJarInfo> allWebJars = WebJarAssetLocator.findWebJars(scanResult);
                final WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator(allWebJars);

                for (final String webJarName : allWebJars.keySet()) {
                    final String moduleFilePath = webJarAssetLocator.getFullPathExact(webJarName, moduleNameFile);
                    final WebJarAssetLocator.WebJarInfo webJarInfo = webJarAssetLocator.getAllWebJars().get(webJarName);
                    extractResourcesTo(webJarName, webJarInfo, moduleFilePath, WebJarAssetLocator.webJarResources(webJarName, scanResult.getAllResources()), to);
                }
            }
        }
        else {
            final ClassGraph classGraph = new ClassGraph().overrideClassLoaders(classLoader).ignoreParentClassLoaders().acceptPaths(
                String.format("%s/%s/*", WEBJARS_PATH_PREFIX, name));
            try (ScanResult scanResult = classGraph.scan()) {
                final Map<String, WebJarAssetLocator.WebJarInfo> allWebJars = WebJarAssetLocator.findWebJars(scanResult);
                final WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator(allWebJars);
                final ResourceList webJarResources = scanResult.getAllResources();
                final WebJarAssetLocator.WebJarInfo webJarInfo = allWebJars.get(name);
                final String moduleFilePath = webJarAssetLocator.getFullPathExact(name, moduleNameFile);
                extractResourcesTo(name, webJarInfo, moduleFilePath, webJarResources, to);
            }
        }
    }

    @Nullable
    protected static String getJsonModuleId(@Nonnull String packageJson) throws IOException {
        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createParser(packageJson);

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("package.json is not a valid JSON object");
        }

        String moduleId = null;
        while (!parser.isClosed()) {
            parser.nextToken();

            String fieldName = parser.getCurrentName();
            if ("name".equals(fieldName) && parser.getParsingContext().getParent().inRoot()) {
                parser.nextToken();
                moduleId = parser.getText();
                parser.close();
            }
        }

        return moduleId;
    }

}
