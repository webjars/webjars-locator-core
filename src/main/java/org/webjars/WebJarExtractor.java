package org.webjars;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static org.webjars.CloseQuietly.closeQuietly;
import static org.webjars.WebJarAssetLocator.WEBJARS_PATH_PREFIX;

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

    private static final Logger log = LoggerFactory.getLogger(WebJarExtractor.class);

    private final Cache cache;
    private final ClassLoader classLoader;

    public WebJarExtractor() {
        this(WebJarExtractor.class.getClassLoader());
    }

    public WebJarExtractor(ClassLoader classLoader) {
        this(NO_CACHE, classLoader);
    }

    public WebJarExtractor(Cache cache, ClassLoader classLoader) {
        this.cache = cache;
        this.classLoader = classLoader;
    }

    /**
     * Extract all WebJars.
     *
     * @param to The directory to extract to.
     * @throws java.io.IOException There was a problem extracting the WebJars
     */
    public void extractAllWebJarsTo(File to) throws IOException {
        extractWebJarsTo(null, false, to);
    }

    /**
     * Extract the given WebJar to the given location.
     * The WebJar will be extracted, without its version in the path, to the given directory.
     *
     * @param name The name of the WebJar to extract.
     * @param to   The location to extract it to. All WebJars will be merged into this location.
     * @throws java.io.IOException There was a problem extracting the WebJars
     */
    public void extractWebJarTo(String name, File to) throws IOException {
        extractWebJarsTo(name, false, to);
    }

    /**
     * Extract the node_modules of all WebJars and merge them into the same folder.
     *
     * @param to The location to extract it to. All WebJars will be merged into this location.
     * @throws java.io.IOException There was a problem extracting the WebJars
     */
    public void extractAllNodeModulesTo(File to) throws IOException {
        extractWebJarsTo(null, true, to);
    }

    /**
     * A generalised form for extracting WebJars.
     *
     * @param name        If null then all WebJars are extracted, otherwise the name of a single WebJars.
     * @param nodeModules If true then only WebJars containing a package.json at the root will be extracted.
     * @param to          The location to extract it to. All WebJars will be merged into this location.
     * @throws java.io.IOException There was a problem extracting the WebJars
     */
    private void extractWebJarsTo(String name, boolean nodeModules, File to) throws IOException {
        String fullPath = WEBJARS_PATH_PREFIX + "/";
        String searchPath;
        if (name != null) {
            searchPath = fullPath + name + "/";
        } else {
            searchPath = fullPath;
        }
        for (URL url : WebJarAssetLocator.listParentURLsWithResource(new ClassLoader[]{classLoader}, searchPath)) {
            if ("jar".equals(url.getProtocol())) {

                String urlPath = url.getPath();
                File file = new File(URI.create(urlPath.substring(0, urlPath.indexOf("!"))));
                ZipFile zipFile = new ZipFile(file, "utf-8");

                log.debug("Loading webjars from {}", file);

                try {
                    // Find all the webjars inside this webjar. This set contains paths to all webjars.
                    Collection<JarFileWebJar> webJars = findWebJarsInJarFile(zipFile);

                    for (JarFileWebJar webJar: webJars) {
                        // Only extract if this webjar is the requested webjar name
                        if (name == null || (webJar.name.equals(name))) {
                            String webJarId = null;
                            // If it's a node module and we requested node modules, then set the web jar id, otherwise
                            // if we didn't request node modules, then just use the name.
                            if (nodeModules && webJar.moduleId != null) {
                                webJarId = webJar.moduleId;
                            } else if (!nodeModules) {
                                webJarId = webJar.name;
                            }

                            if (webJarId != null) {
                                // Copy all the entries
                                for (Map.Entry<String, ZipArchiveEntry> entry: webJar.entries.entrySet()) {
                                    String entryName = entry.getKey();
                                    if (!entry.getValue().isDirectory()) {
                                        String relativeName = webJarId + File.separator + entryName;
                                        copyZipEntry(zipFile, entry.getValue(), new File(to, relativeName), relativeName);
                                    }
                                }
                            }

                        }
                    }
                } finally {
                    closeQuietly(zipFile);
                }
            } else if ("file".equals(url.getProtocol())) {
                File file;
                try {
                    file = new File(url.toURI());
                } catch (URISyntaxException e) {
                    file = new File(url.getPath());
                }
                log.debug("Found file system webjar: {}", file);
                File[] webjars;
                if (name == null) {
                    webjars = file.listFiles();
                } else {
                    webjars = new File[] {file};
                }
                if (webjars != null) {
                    for (File webjar: webjars) {
                        if (webjar.isDirectory()) {
                            File[] versions = webjar.listFiles();
                            if (versions != null) {
                                for (File version: versions) {
                                    if (version.isDirectory()) {
                                        String moduleId;
                                        if (nodeModules) {
                                            moduleId = getFileNodeModuleIdEntry(new File(version, PACKAGE_JSON));
                                        } else {
                                            moduleId = webjar.getName();
                                        }
                                        if (moduleId != null) {
                                            File copyTo = new File(to, moduleId);
                                            copyDirectory(version, copyTo, webjar.getName());
                                        }

                                    } else {
                                        log.debug("Filesystem webjar version {} is not a directory", version);
                                    }
                                }
                            } else {
                                log.debug("Filesystem webjar has no versions: {}", webjar);
                            }
                        } else {
                            log.debug("Filesystem webjar {} is not a directory", webjar);
                        }
                    }
                } else {
                    log.debug("Filesystem webjar has no webjars: {}", file);
                }
            } else {
                log.debug("Ignoring given unsupported protocol for: {}", url);
            }
        }
    }

    private void ensureIsDirectory(File dir) {
        if (dir.exists() && !dir.isDirectory()) {
            log.debug("Destination directory is not a directory, deleting {}", dir);
            // Delete the old file
            boolean isDeleted = dir.delete();
            if (!isDeleted) {
                log.debug("Destination directory {} wasn't deleted", dir);
            }
        }
        boolean created = dir.mkdirs();
        if (!created) {
            log.debug("Destination directory {} didn't need creation", dir);
        }
    }

    private void copyDirectory(File directory, File to, String key) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file: files) {
                File copyTo = new File(to, file.getName());

                String relativeName;
                if (key.isEmpty()) {
                    relativeName = file.getName();
                } else {
                    relativeName = key + "/" + file.getName();
                }

                if (file.isDirectory()) {
                    copyDirectory(file, copyTo, relativeName);
                } else {
                    Cacheable forCache = new Cacheable(file.getPath(), file.lastModified());
                    log.debug("Checking whether {} is up to date at {}", relativeName, copyTo);
                    // Check for modification
                    if (!copyTo.exists() || !cache.isUpToDate(relativeName, forCache)) {
                        log.debug("Up to date check failed, copying {} to {}", relativeName, copyTo);
                        ensureIsDirectory(copyTo.getParentFile());
                        Files.copy(file.toPath(), copyTo.toPath(), StandardCopyOption.COPY_ATTRIBUTES,
                                StandardCopyOption.REPLACE_EXISTING);

                        cache.put(relativeName, forCache);
                    }
                }
            }
        }
    }

    private class JarFileWebJar {
        final String name;
        final String version;
        final Map<String, ZipArchiveEntry> entries = new HashMap<String, ZipArchiveEntry>();

        String moduleId;

        public JarFileWebJar(String name, String version) {
            this.name = name;
            this.version = version;
        }
    }

    private Collection<JarFileWebJar> findWebJarsInJarFile(ZipFile zipFile) throws IOException {
        Map<String, JarFileWebJar> webJars = new HashMap<String, JarFileWebJar>();

        // Loop through all the entries in the jar file, and extract every entry that is a webjar entry into a set
        // of WebJar name/versions to JarFileWebJars
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            if (entry.getName().startsWith(WEBJARS_PATH_PREFIX + "/")) {
                String webJarPath = entry.getName().substring(WEBJARS_PATH_PREFIX.length() + 1);
                String[] nameVersion = webJarPath.split("/", 3);
                if (nameVersion.length == 3) {
                    String name = nameVersion[0];
                    String version = nameVersion[1];
                    String path = nameVersion[2];
                    String key = name + "/" + version;
                    JarFileWebJar webJar = webJars.get(key);
                    if (webJar == null) {
                        webJar = new JarFileWebJar(name, version);
                        webJars.put(key, webJar);
                    }
                    webJar.entries.put(path, entry);
                }
            }
        }

        // Determine module ids
        for (JarFileWebJar webJar: webJars.values()) {
            ZipArchiveEntry entry = webJar.entries.get(PACKAGE_JSON);
            if (entry != null) {
                String packageJson = copyAndClose(zipFile.getInputStream(entry));
                webJar.moduleId = getJsonNodeModuleId(packageJson);
            }
        }

        return webJars.values();
    }

    private void copyZipEntry(ZipFile zipFile, ZipArchiveEntry entry, File copyTo, String key) throws IOException {
        Cacheable forCache = new Cacheable(entry.getName(), entry.getTime());

        log.debug("Checking whether {} is up to date at {}", entry.getName(), copyTo);

        // Check for modification
        if (!copyTo.exists() || !cache.isUpToDate(key, forCache)) {

            log.debug("Up to date check failed, copying {} to {}", entry.getName(), copyTo);
            ensureIsDirectory(copyTo.getParentFile());
            copyAndClose(zipFile.getInputStream(entry), copyTo);

            if (SystemUtils.IS_OS_UNIX) {
                int mode = entry.getUnixMode();
                if (mode > 0) {
                    Files.setPosixFilePermissions(copyTo.toPath(), toPerms(mode));
                }
            }

            cache.put(key, forCache);
        }
    }

    private String getFileNodeModuleIdEntry(File packageJsonFile) throws IOException {
        String moduleId = null;
        if (packageJsonFile.exists()) {
            String packageJson = copyAndClose(new FileInputStream(packageJsonFile));
            moduleId = getJsonNodeModuleId(packageJson);
        }
        return moduleId;
    }

    String getJsonNodeModuleId(String packageJson) throws IOException {
        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createParser(packageJson);

        String moduleId = null;

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("package.json is not a valid JSON object");
        }

        while (moduleId == null) {
            parser.nextToken(); // name
            String fieldName = parser.getCurrentName();
            parser.nextToken(); // value

            // skip tokens whose parents are not the root
            if (!parser.getParsingContext().getParent().inRoot()) {
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    parser.nextToken();
                }
                continue;
            }

            if (fieldName.equals("name")) {
                moduleId = parser.getText();
            }
        }

        parser.close();

        return moduleId;
    }

    /**
     * A cache for extracting WebJar assets.
     */
    public interface Cache {
        /**
         * Whether the file is up to date.
         *
         * @param key       The key to check.
         * @param cacheable The cacheable to check.
         * @return Whether the file is up to date.
         */
        public boolean isUpToDate(String key, Cacheable cacheable);

        /**
         * Put the given file in the cache.
         *
         * @param key       The key to put it at.
         * @param cacheable The cacheable.
         */
        public void put(String key, Cacheable cacheable);
    }

    private static class NoCache implements Cache {
        public boolean isUpToDate(String key, Cacheable cacheable) {
            return false;
        }

        public void put(String key, Cacheable cacheable) {
        }
    }

    public static Cache NO_CACHE = new NoCache();

    /**
     * An in memory cache.
     */
    public static class MemoryCache implements Cache {

        private final Map<String, Cacheable> cache = new HashMap<String, Cacheable>();

        public boolean isUpToDate(String key, Cacheable cacheable) {
            return cacheable.equals(cache.get(key));
        }

        @Override
        public void put(String key, Cacheable cacheable) {
            cache.put(key, cacheable);
        }
    }


    public static final class Cacheable {
        private final String path;
        private final long lastModified;

        public Cacheable(String path, long lastModified) {
            this.path = path;
            this.lastModified = lastModified;
        }

        public String getPath() {
            return path;
        }

        public long getLastModified() {
            return lastModified;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Cacheable cacheable = (Cacheable) o;

            return (lastModified == cacheable.lastModified) && path.equals(cacheable.path);

        }

        @Override
        public int hashCode() {
            int result = path.hashCode();
            result = 31 * result + (int) (lastModified ^ (lastModified >>> 32));
            return result;
        }
    }

    private static void copyAndClose(InputStream source, File to) throws IOException {
        OutputStream dest = new FileOutputStream(to);
        try {
            byte[] buffer = new byte[8192];
            int read = source.read(buffer);
            while (read > 0) {
                dest.write(buffer, 0, read);
                read = source.read(buffer);
            }
            dest.flush();
        } finally {
            closeQuietly(source);
            closeQuietly(dest);
        }

    }

    private static String copyAndClose(InputStream source) throws IOException {
        StringBuilder sb = new StringBuilder();
        final Reader is = new InputStreamReader(source, "UTF-8");
        try {
            char[] buffer = new char[8192];
            int read = is.read(buffer, 0, buffer.length);
            sb.append(buffer, 0, read);
        } finally {
            closeQuietly(source);
            closeQuietly(is);
        }
        return sb.toString();
    }

    private static Set<PosixFilePermission> toPerms(int mode) {
        Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
        if ((mode & 0400) > 0) {
            perms.add(PosixFilePermission.OWNER_READ);
        }
        if ((mode & 0200) > 0) {
            perms.add(PosixFilePermission.OWNER_WRITE);
        }
        if ((mode & 0100) > 0) {
            perms.add(PosixFilePermission.OWNER_EXECUTE);
        }
        if ((mode & 0040) > 0) {
            perms.add(PosixFilePermission.GROUP_READ);
        }
        if ((mode & 0020) > 0) {
            perms.add(PosixFilePermission.GROUP_WRITE);
        }
        if ((mode & 0010) > 0) {
            perms.add(PosixFilePermission.GROUP_EXECUTE);
        }
        if ((mode & 0004) > 0) {
            perms.add(PosixFilePermission.OTHERS_READ);
        }
        if ((mode & 0002) > 0) {
            perms.add(PosixFilePermission.OTHERS_WRITE);
        }
        if ((mode & 0001) > 0) {
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
        }
        return perms;
    }

}
