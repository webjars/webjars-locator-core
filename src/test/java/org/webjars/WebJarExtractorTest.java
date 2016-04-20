package org.webjars;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.webjars.WebJarExtractor.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.webjars.WebJarAssetLocator.WEBJARS_PATH_PREFIX;

@RunWith(MockitoJUnitRunner.class)
public class WebJarExtractorTest {

    static {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
    }

    @Mock
    private Cache mockCache;

    private File tmpDir;
    private URLClassLoader loader;

    @Test
    public void webJarShouldBeExtractable() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        extractor.extractWebJarTo("jquery", createTmpDir());
        assertOnlyContains("jquery/jquery.js", "jquery/jquery.min.js", "jquery/jquery.min.map", "jquery/webjars-requirejs.js");
    }

    @Test
    public void webJarWithSubPathsShouldBeExtractable() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        extractor.extractWebJarTo("bootstrap", createTmpDir());
        assertFileExists(new File(tmpDir, "bootstrap/css/bootstrap.css"));
        assertFileExists(new File(tmpDir, "bootstrap/js/bootstrap.js"));
    }

    @Test
    public void allWebJarsShouldBeExtractable() throws Exception {
        new WebJarExtractor(createClassLoader()).extractAllWebJarsTo(createTmpDir());
        assertFileExists(new File(tmpDir, "jquery/jquery.js"));
        assertFileExists(new File(tmpDir, "bootstrap/css/bootstrap.css"));
        assertFileExists(new File(tmpDir, "bootstrap/js/bootstrap.js"));
    }

    @Test
    public void extractWebJarShouldExtractWhenFileDoesntExist() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(mockCache, createClassLoader());

        extractor.extractWebJarTo("jquery", createTmpDir());

        assertFileExists(new File(tmpDir, "jquery/jquery.js"));
        verify(mockCache).put(eq("jquery/jquery.js"), any(Cacheable.class));
    }

    @Test
    public void extractWebJarShouldExtractWhenFileDoesntExistButCacheUpToDate() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(mockCache, createClassLoader());
        when(mockCache.isUpToDate(eq("jquery.js"), any(Cacheable.class))).thenReturn(true);

        extractor.extractWebJarTo("jquery", createTmpDir());

        assertFileExists(new File(tmpDir, "jquery/jquery.js"));
        verify(mockCache).put(eq("jquery/jquery.js"), any(Cacheable.class));
    }

    @Test
    public void extractWebJarShouldNotExtractWhenWhenUpToDate() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(mockCache, createClassLoader());
        File file = new File(createTmpDir(), "jquery/jquery.js");
        createFile(file, "Hello");
        when(mockCache.isUpToDate(eq("jquery/jquery.js"), any(Cacheable.class))).thenReturn(true);

        extractor.extractWebJarTo("jquery", createTmpDir());

        assertFileContains(file, "Hello");
        verify(mockCache, never()).put(eq("jquery/jquery.js"), any(Cacheable.class));
    }

    @Test
    public void extractAllWebJarsShouldExtractWhenFileDoesntExist() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(mockCache, createClassLoader());

        extractor.extractAllWebJarsTo(createTmpDir());

        assertFileExists(new File(tmpDir, "jquery/jquery.js"));
        verify(mockCache).put(eq("jquery/jquery.js"), any(Cacheable.class));
    }

    @Test
    public void extractAllWebJarsShouldExtractWhenFileDoesntExistButCacheUpToDate() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(mockCache, createClassLoader());
        when(mockCache.isUpToDate(eq("jquery/jquery.js"), any(Cacheable.class))).thenReturn(true);

        extractor.extractAllWebJarsTo(createTmpDir());

        assertFileExists(new File(tmpDir, "jquery/jquery.js"));
        verify(mockCache).put(eq("jquery/jquery.js"), any(Cacheable.class));
    }

    @Test
    public void extractAllWebJarsShouldNotExtractWhenWhenUpToDate() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(mockCache, createClassLoader());
        File file = new File(createTmpDir(), "jquery/jquery.js");
        createFile(file, "Hello");
        when(mockCache.isUpToDate(eq("jquery/jquery.js"), any(Cacheable.class))).thenReturn(true);

        extractor.extractAllWebJarsTo(createTmpDir());

        assertFileContains(file, "Hello");
        verify(mockCache, never()).put(eq("jquery/jquery.js"), any(Cacheable.class));
    }

    @Test
    public void extractAllNodeModulesToShouldExtractOnlyTheModules() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        extractor.extractAllNodeModulesTo(createTmpDir());
        assertFileExists(new File(tmpDir, "less/lib/less/tree/alpha.js"));
    }

    @Test
    public void extractAllWebJarsFromClassDirectories() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        extractor.extractAllWebJarsTo(createTmpDir());
        assertFileExists(new File(tmpDir, "foo/foo.js"));
        assertFileExists(new File(tmpDir, "multiple/module/multiple_module.js"));
        assertFileExists(new File(tmpDir, "multiple/multiple.js"));
        assertFileExists(new File(tmpDir, "spaces/space space.js"));
    }

    @Test
    public void extractAllNodeModulesFromClassDirectories() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        extractor.extractAllNodeModulesTo(createTmpDir());
        assertFileExists(new File(tmpDir, "bar/bar.js"));
    }

    @Test
    public void dontSetPermissionsWhenJarHasNoPermissions() throws Exception {
        // Same jar as permissions-jar.jar, except created by jar, not ZipInfo, so it doesn't have any
        // permissions in it, so we expect the permissions not be carried through
        loader = new URLClassLoader(new URL[]{
                this.getClass().getClassLoader().getResource("no-permissions.jar")
        });
        WebJarExtractor extractor = new WebJarExtractor(loader);
        extractor.extractWebJarTo("permissions-jar", createTmpDir());
        assertFileReadable(new File(tmpDir, "permissions-jar/bin/all"));
        assertFileWritable(new File(tmpDir, "permissions-jar/bin/all"));
        assertFileReadable(new File(tmpDir, "permissions-jar/bin/owneronlyread"));
        assertFileWritable(new File(tmpDir, "permissions-jar/bin/owneronlyread"));
    }

    @Test
    public void extractAllWebJarsShouldExtractSelect2() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        extractor.extractAllWebJarsTo(createTmpDir());
        assertFileExists(new File(tmpDir, "select2/select2.js"));
    }

    @Test
    public void getJsonNodeModuleIdShouldGetTheRightName() throws Exception {
        ClassLoader classLoader = createClassLoader();
        WebJarExtractor extractor = new WebJarExtractor(classLoader);
        String utilPackageJsonPath = WEBJARS_PATH_PREFIX + "/util/0.10.3/package.json";
        InputStream utilPackageJsonInputStream = classLoader.getResourceAsStream(utilPackageJsonPath);
        String utilPackageJson = new String(IOUtils.toByteArray(utilPackageJsonInputStream));
        utilPackageJsonInputStream.close();
        String moduleId = extractor.getJsonNodeModuleId(utilPackageJson);
        assertEquals("util", moduleId);
    }

    private URLClassLoader createClassLoader() throws Exception {
        if (loader == null) {
            loader = WebJarExtractorTestUtils.createClassLoader();
        }
        return loader;
    }

    private File createTmpDir() throws Exception {
        if (tmpDir == null) {
            tmpDir = WebJarExtractorTestUtils.createTmpDir();
        }
        return tmpDir;
    }

    @After
    public void deleteTmpDirectory() throws Exception {
        WebJarExtractorTestUtils.deleteDir(tmpDir);
    }

    @After
    public void closeLoader() throws Exception {
        if (loader != null) {
            // close() is only available in Java 1.7.
            // loader.close();
            loader = null;
        }
    }

    private void assertOnlyContains(String... paths) {
        List<File> files = new ArrayList<File>();
        for (String path : paths) {
            File file = new File(tmpDir, path);
            assertFileExists(file);
            files.add(file);
        }

        List<File> allFiles = getAllFiles(tmpDir);
        allFiles.removeAll(files);
        if (!allFiles.isEmpty()) {
            printTmpDirStructure();
            fail("Unexpected file in tmp dir: " + allFiles.get(0));
        }
    }

    private void assertFileExists(File file) {
        try {
            assertTrue("File " + file + " doesn't exist", file.exists());
            assertTrue("File " + file + " is not a regular file", file.isFile());
            assertTrue("File " + file + " is empty", file.length() > 0);
        } catch (AssertionError e) {
            printTmpDirStructure();
            throw e;
        }
    }

    private void assertFileReadable(File file) {
        assertTrue("File " + file + " doesn't exist", file.exists());
        assertTrue("File " + file + " is not readable", file.canRead());
    }

    private void assertFileWritable(File file) {
        assertTrue("File " + file + " doesn't exist", file.exists());
        assertTrue("File " + file + " is not writable", file.canWrite());
    }

    private void printTmpDirStructure() {
        System.out.print("Temporary directory " + tmpDir.getParent() + "/");
        listFiles(tmpDir, "");
    }

    private List<File> getAllFiles(File dir) {
        List<File> results = new ArrayList<File>();
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                results.addAll(getAllFiles(file));
            } else {
                results.add(file);
            }
        }
        return results;
    }

    private void listFiles(File file, String indent) {
        System.out.println(indent + file.getName());
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                listFiles(child, indent + "  ");
            }
        }
    }

    private void createFile(File file, String content) throws Exception {
        file.getParentFile().mkdirs();
        Writer writer = new FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    private void assertFileContains(File file, String content) throws Exception {
        assertFileExists(file);
        StringBuilder sb = new StringBuilder();

        Reader reader = new FileReader(file);
        try {
            char[] buffer = new char[4096];
            int read = reader.read(buffer);
            while (read > 0) {
                sb.append(buffer, 0, read);
                read = reader.read(buffer);
            }
        } finally {
            reader.close();
        }

        assertEquals(content, sb.toString());
    }

}
