package org.webjars;

import static java.util.Objects.requireNonNull;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Locate WebJar assets. The class is thread safe.
 */
public class WebJarAssetLocator {

    private static final Logger Logger = LoggerFactory.getLogger(WebJarAssetLocator.class);

    /**
     * The webjar package name.
     */
    public static final String WEBJARS_PACKAGE = "META-INF.resources.webjars";

    /**
     * The path to where webjar resources live.
     */
    public static final String WEBJARS_PATH_PREFIX = "META-INF/resources/webjars";

    private static final Pattern WEBJAR_EXTRACTOR_PATTERN = Pattern.compile(WEBJARS_PATH_PREFIX + "/([^/]*)/([^/]*)/(.*)$");

    public static class WebJarInfo {

        final String version;
        final String groupId;
        final String artifactId;
        final URI uri;
        final Collection<String> contents;

        WebJarInfo(@Nullable final String version, Optional<MavenProperties> mavenProperties, final URI uri, @Nonnull final Collection<String> contents) {
            this.version = version;
            this.groupId = mavenProperties.map(MavenProperties::getGroupId).orElse(null);
            this.artifactId = mavenProperties.map(MavenProperties::getArtifactId).orElse(null);
            this.uri = uri;
            this.contents = contents;
        }

        @Nullable
        public String getVersion() {
            return version;
        }

        @Nullable
        public String getGroupId() {
            return groupId;
        }

        @Nullable
        public String getArtifactId() {
            return artifactId;
        }

        public URI getUri() {
            return uri;
        }

        /**
         * @return an immutable list of resources.
         */
        @Nonnull
        public Collection<String> getContents() {
            return contents;
        }
    }

    protected final Map<String, WebJarInfo> allWebJars;

    public Map<String, WebJarInfo> getAllWebJars() {
        return allWebJars;
    }

    @Nonnull
    protected static ResourceList webJarResources(@Nonnull final String webJarName, @Nonnull final ResourceList resources) {
        if (isEmpty(webJarName)) {
            throw new IllegalArgumentException("WebJar name must not be null or empty");
        }
        requireNonNull(resources, "Resources must not be null");
        return resources.filter(resource -> resource.getPath().startsWith(String.format("%s/%s/", WEBJARS_PATH_PREFIX, webJarName)));
    }

    @Nullable
    protected static String webJarVersion(@Nullable final String webJarName, @Nonnull final ResourceList resources) {
        if (isEmpty(webJarName)) {
            return null;
        }
        if (resources.isEmpty()) {
            return null;
        }
        final String aPath = resources.get(0).getPath();
        final String prefix = String.format("%s/%s/", WEBJARS_PATH_PREFIX, webJarName);
        if (aPath.startsWith(prefix)) {
            try {
                final String withoutName = aPath.substring(prefix.length());
                final String maybeVersion = withoutName.substring(0, withoutName.indexOf('/'));
                ResourceList withMaybeVersion = resources.filter(resource -> resource.getPath().startsWith(
                    String.format("%s%s/", prefix, maybeVersion)));

                if (withMaybeVersion.size() == resources.size()) {
                    return maybeVersion;
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static Optional<MavenProperties> findMavenInfo(@Nullable final URI classpathElementURI) {
        final ClassGraph classGraph = new ClassGraph().overrideClasspath(classpathElementURI).ignoreParentClassLoaders().acceptPaths("META-INF/maven");
        try (ScanResult scanResult = classGraph.scan()) {
            final ResourceList maybePomProperties = scanResult.getResourcesWithLeafName("pom.properties");
            if (maybePomProperties.size() == 1) {
                try {
                    final Properties properties = new Properties();
                    properties.load(maybePomProperties.get(0).open());
                    maybePomProperties.get(0).close();
                    return Optional.of(new MavenProperties(properties.getProperty("groupId"),
                        properties.getProperty("artifactId"),
                        properties.getProperty("version")
                        ));
                } catch (IOException e) {
                    // ignored
                }
            }
            return Optional.empty();
        }
    }

    @Nonnull
    protected static Map<String, WebJarInfo> findWebJars(@Nonnull ScanResult scanResult) {
        requireNonNull(scanResult, "Scan result must not be null");
        ResourceList allResources = scanResult.getAllResources();
        Map<String, WebJarInfo> webJars = new HashMap<>(allResources.size());
        for (Resource resource : allResources) {
            final String noPrefix = resource.getPath().substring(WEBJARS_PATH_PREFIX.length() + 1);
            final String webJarName = noPrefix.substring(0, noPrefix.indexOf('/'));
            if (!webJars.containsKey(webJarName)) {
                final ResourceList webJarResources = webJarResources(webJarName, allResources);
                final String maybeWebJarVersion = webJarVersion(webJarName, webJarResources);
                final Optional<MavenProperties> mavenProperties = findMavenInfo(resource.getClasspathElementURI());
                // todo: this doesn't preserve the different URIs for the resources so if for some reason the actual duplicates are different,
                //       then things can get strange because on resource lookup, it can resolve to a difference classpath resource
                //
                // this removes duplicates.
                final Collection<String> paths = Collections.unmodifiableCollection(new HashSet<>(webJarResources.getPaths()));
                webJars.put(webJarName, new WebJarInfo(maybeWebJarVersion, mavenProperties, resource.getClasspathElementURI(), paths));
            }
        }
        return webJars;
    }

    /**
     * @param path The full WebJar path (not {@code null})
     * @return A WebJar tuple (Entry) with key = id and value = version or {@code null} if not a legal WebJar file format
     */
    @Nullable
    public static Entry<String, String> getWebJar(@Nonnull CharSequence path) {
        requireNonNull(path, "Path must not be null");
        Matcher matcher = WEBJAR_EXTRACTOR_PATTERN.matcher(path);
        if (matcher.find()) {
            String id = matcher.group(1);
            String version = matcher.group(2);
            return new SimpleEntry<>(id, version);
        }
        // not a legal WebJar file format
        return null;
    }

    @Nonnull
    private static Map<String, WebJarInfo> scanForWebJars(@Nonnull ClassGraph classGraph) {
        requireNonNull(classGraph, "Class graph must not be null");
        try (ScanResult scanResult = classGraph.acceptPaths(WEBJARS_PATH_PREFIX).scan()) {
            return findWebJars(scanResult);
        }
    }

    public WebJarAssetLocator() {
        this(new ClassGraph());
    }

    public WebJarAssetLocator(@Nullable final ClassLoader classLoader) {
        this(new ClassGraph().overrideClassLoaders(classLoader).ignoreParentClassLoaders());
    }

    public WebJarAssetLocator(@Nonnull final String... whitelistPaths) {
        this(new ClassGraph().acceptPaths(whitelistPaths));
    }

    public WebJarAssetLocator(@Nullable final ClassLoader classLoader, @Nonnull final String... whitelistPaths) {
        this(new ClassGraph().overrideClassLoaders(classLoader).ignoreParentClassLoaders().acceptPaths(whitelistPaths));
    }

    public WebJarAssetLocator(ClassGraph classGraph) {
        this(scanForWebJars(classGraph));
    }

    public WebJarAssetLocator(@Nonnull final Map<String, WebJarInfo> allWebJars) {
        this.allWebJars = allWebJars;

        this.allWebJars.values().stream()
            .filter(webJarInfo -> {
                if (webJarInfo.groupId != null)
                    return webJarInfo.groupId.startsWith("org.webjars.bower");
                else
                    return false;
            })
            .forEach(webJarInfo ->
                Logger.warn("Bower WebJars are deprecated. See: https://github.com/webjars/webjars/issues/2039\nOffending WebJar: " + webJarInfo.groupId + ":" + webJarInfo.artifactId)
            );
    }

    /**
     * Given a distinct path within the WebJar index passed in return the full path of the resource.
     *
     * @param partialPath the path to return e.g. "jquery.js" or "abc/someother.js". This must be a distinct path within the index passed in (not {@code null} or empty).
     * @return a fully qualified path to the resource.
     */
    @Nonnull
    public String getFullPath(@Nonnull final String partialPath) {

        if (isEmpty(partialPath)) {
            throw new IllegalArgumentException("Partial path must not be null or empty");
        }

        List<String> paths = new ArrayList<>(allWebJars.size());

        for (String webJarName : allWebJars.keySet()) {
            try {
                paths.add(getFullPath(webJarName, partialPath));
            } catch (NotFoundException e) {
                // ignored
            }
        }

        if (paths.isEmpty()) {
            throwNotFoundException(partialPath);
        } else if (paths.size() > 1) {
            throwMultipleMatchesException(partialPath, paths);
        }

        return paths.get(0);
    }

    /**
     * Returns the full path of an asset within a specific WebJar
     *
     * @param webjar      The id of the WebJar to search (not {@code null}
     * @param partialPath The partial path to look for (not {@code null}
     * @return a fully qualified path to the resource
     * @throws NotFoundException if webjar or path not found
     */
    @Nonnull
    public String getFullPath(@Nonnull final String webjar, @Nonnull final String partialPath) {

        if (isEmpty(webjar)) {
            throw new IllegalArgumentException("WebJar ID must not be null or empty");
        }

        if (isEmpty(partialPath)) {
            throw new IllegalArgumentException("Partial path must not be null or empty");
        }

        if (allWebJars.containsKey(webjar)) {

            List<String> paths = allWebJars.get(webjar).getContents().stream().filter(path -> path.endsWith(partialPath)).collect(Collectors.toList());

            if (paths.isEmpty()) {
                throwNotFoundException(partialPath);
            }

            if (paths.size() > 1) {
                throwMultipleMatchesException(partialPath, paths);
            }

            return paths.get(0);

        }

        throw new NotFoundException(String.format("WebJar with id %s not found", webjar));
    }

    /**
     * Returns the full path of an asset within a specific WebJar
     *
     * @param webJarName The id of the WebJar to search (must not be {@code null}
     * @param exactPath  The exact path of the file within the WebJar (may be {@code null} for legacy reasons)
     * @return a fully qualified path to the resource of {@code null} if WebJar not found
     */
    @Nullable
    public String getFullPathExact(@Nonnull final String webJarName, @Nullable final String exactPath) {

        if (isEmpty(webJarName)) {
            throw new IllegalArgumentException("WebJar ID must not be null or empty");
        }

        if (isEmpty(exactPath)) {
            return null;
        }

        WebJarInfo webJarInfo = allWebJars.get(webJarName);

        if (webJarInfo == null || webJarInfo.getContents().isEmpty()) {
            return null;
        }

        String version = webJarInfo.getVersion();
        String fullPath;
        if (isEmpty(version)) {
            fullPath = String.format("%s/%s/%s", WEBJARS_PATH_PREFIX, webJarName, exactPath);
        } else {
            fullPath = String.format("%s/%s/%s/%s", WEBJARS_PATH_PREFIX, webJarName, version, exactPath);
        }

        if (webJarInfo.getContents().contains(fullPath)) {
            return fullPath;
        }

        return null;
    }

    @Nonnull
    public Set<String> listAssets() {
        return listAssets("");
    }

    /**
     * List assets within a folder.
     *
     * @param folderPath the root path to the folder.
     * @return a set of folder paths that match.
     */
    @Nonnull
    public Set<String> listAssets(@Nonnull final String folderPath) {
        requireNonNull(folderPath, "Folder path must not be null");
        final String prefix = String.format("%s%s%s", WEBJARS_PATH_PREFIX, folderPath.startsWith("/") ? "" : "/", folderPath);
        return allWebJars.values()
            .stream()
            .flatMap(webJarInfo -> webJarInfo.getContents().stream())
            .filter(path -> path.startsWith(folderPath) || path.startsWith(prefix))
            .collect(Collectors.toSet());
    }

    /**
     * @return A map of the WebJars based on the files in the CLASSPATH where the key is the artifactId and the value is the version
     */
    @Nonnull
    public Map<String, String> getWebJars() {
        Map<String, String> webJars = new HashMap<>(allWebJars.size());
        for (Entry<String, WebJarInfo> entry : allWebJars.entrySet()) {
            webJars.put(entry.getKey(), entry.getValue().getVersion());
        }
        return webJars;
    }

    /**
     * Gets the Group ID given a fullPath
     *
     * @param fullPath the fullPath to the asset in a WebJar, i.e. META-INF/resources/webjars/jquery/2.1.0/jquery.js
     * @return the Group ID for the WebJar or {@code null} if it can't be determined
     */
    @Nullable
    public String groupId(@Nullable final String fullPath) {
        if (isEmpty(fullPath)) {
            return null;
        }
        return allWebJars.values()
            .stream()
            .filter(webJarInfo -> webJarInfo.getContents().contains(fullPath))
            .findFirst()
            .map(WebJarInfo::getGroupId)
            .orElse(null);
    }

    private static boolean isEmpty(@Nullable String str) {
        return str == null || str.trim().isEmpty();
    }

    private static void throwNotFoundException(@Nullable final String partialPath) {
        throw new NotFoundException(
            String.format("%s could not be found. Make sure you've added the corresponding WebJar and please check for typos.", partialPath)
        );
    }

    private static void throwMultipleMatchesException(@Nullable final String partialPath, @Nullable final List<String> matches) {
        throw new MultipleMatchesException(
            String.format("Multiple matches found for %s. Please provide a more specific path, for example by including a version number.", partialPath), matches);
    }

}
