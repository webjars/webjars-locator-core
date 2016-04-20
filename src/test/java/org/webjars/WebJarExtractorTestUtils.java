package org.webjars;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.webjars.WebJarAssetLocator.WEBJARS_PATH_PREFIX;

public class WebJarExtractorTestUtils {
    public static URLClassLoader createClassLoader() throws Exception {
        // find all webjar urls on the classpath
        final Set<URL> urls = WebJarAssetLocator.listParentURLsWithResource(
                new ClassLoader[]{WebJarExtractorTest.class.getClassLoader()},
                WEBJARS_PATH_PREFIX);
        List<URL> webjarUrls = new ArrayList<URL>();
        for (URL url : urls) {
            if (url.getProtocol().equals("jar")) {
                String path = url.getPath();
                webjarUrls.add(URI.create(path.substring(0, path.indexOf("!"))).toURL());
            } else if (url.getProtocol().equals("file")) {
                File file = new File(url.getPath());
                // go up from META-INF/resources/webjars
                File base = file.getParentFile().getParentFile().getParentFile();
                webjarUrls.add(base.toURI().toURL());
            }
        }

        return new URLClassLoader(webjarUrls.toArray(new URL[webjarUrls.size()]), null);
    }

    public static File createTmpDir() throws IOException {
        File tmpDir = File.createTempFile("webjarextractortest-", "");
        tmpDir.delete();
        tmpDir.mkdir();
        return tmpDir;
    }

    public static void deleteDir(File dir) {
        if (dir != null) {
            if (dir.isDirectory()) {
                for (File file : dir.listFiles()) {
                    deleteDir(file);
                }
            }
            dir.delete();
        }
    }



}
