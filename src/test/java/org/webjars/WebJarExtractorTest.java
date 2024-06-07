package org.webjars;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;
import static org.webjars.WebJarAssetLocator.WEBJARS_PATH_PREFIX;

@RunWith(MockitoJUnitRunner.class)
public class WebJarExtractorTest {

    static {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    }

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
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        extractor.extractWebJarTo("jquery", createTmpDir());
        assertFileExists(new File(tmpDir, "jquery/jquery.js"));
    }

    @Test
    public void extractWebJarShouldNotExtractWhenFileExists() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        File cacheDir = createTmpDir();
        File file = new File(cacheDir, "jquery/jquery.js");
        createFile(file, "Hello");
        extractor.extractWebJarTo("jquery", cacheDir);
        assertFileContains(file, "Hello");
    }

    @Test
    public void extractWebJarShouldExtractWhenFileExistsAndWebJarVersionIsSnapshot() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        File cacheDir = createTmpDir();
        File file = new File(cacheDir, "wip/caramba.js");
        createFile(file, "Hello");
        extractor.extractWebJarTo("wip", cacheDir);
        assertFileContains(file, "var just = 'do it';");
    }

    @Test
    public void extractAllWebJarsShouldExtractWhenFileDoesntExist() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(createClassLoader());
        extractor.extractAllWebJarsTo(createTmpDir());
        assertFileExists(new File(tmpDir, "jquery/jquery.js"));
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
        assertFileExists(new File(tmpDir, "multiple/1.0.0/module/multiple_module.js"));
        assertFileExists(new File(tmpDir, "multiple/2.0.0/module/multiple_module.js"));
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
        loader = new URLClassLoader(new URL[]{getTestResource("no-permissions.jar")});
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
    public void getJsonNodeModuleIdShouldGetTheRightNameForUtil() throws Exception {
        ClassLoader classLoader = createClassLoader();
        String utilPackageJsonPath = WEBJARS_PATH_PREFIX + "/util/0.10.3/package.json";
        InputStream utilPackageJsonInputStream = classLoader.getResourceAsStream(utilPackageJsonPath);
        String utilPackageJson;
        try (Scanner scanner = new Scanner(utilPackageJsonInputStream, StandardCharsets.UTF_8.name())) {
            utilPackageJson = scanner.useDelimiter("\\A").next();
        }
        utilPackageJsonInputStream.close();
        String moduleId = WebJarExtractor.getJsonModuleId(utilPackageJson);
        assertEquals("util", moduleId);
    }

    @Test
    public void getJsonNodeModuleIdShouldGetTheRightNameForRxjs() throws Exception {
        ClassLoader classLoader = createClassLoader();
        String packageJsonPath = WEBJARS_PATH_PREFIX + "/rxjs/5.0.0-beta.12/package.json";
        InputStream packageJsonInputStream = classLoader.getResourceAsStream(packageJsonPath);
        String packageJson;
        try (Scanner scanner = new Scanner(packageJsonInputStream, StandardCharsets.UTF_8.name())) {
            packageJson = scanner.useDelimiter("\\A").next();
        }
        packageJsonInputStream.close();
        String moduleId = WebJarExtractor.getJsonModuleId(packageJson);
        assertEquals("rxjs", moduleId);
    }

    @Test
    public void extractAllWebJarsShouldExtractToDirectoriesWithRealPackageNames() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(new URLClassLoader(
                new URL[]{
                        getTestResource("github-com-polymerelements-marked-element-2.3.0.jar")
                }, null));

        extractor.extractAllBowerComponentsTo(createTmpDir());

        assertEquals("Bower WebJars should be extracted to directories with their names taken from bower.json when `extractAllBowerModulesTo` method is called",
                Collections.singleton("marked-element"), listDirectoryContents(tmpDir));
    }

    @Test
    public void moduleIdShouldWorkWhenMultipleMetaFilesExist() throws Exception {
        WebJarExtractor extractor = new WebJarExtractor(new URLClassLoader(new URL[]{getClasspathResource("org.webjars.npm", "angular__http")}));

        extractor.extractAllNodeModulesTo(createTmpDir());
        assertEquals(Collections.singleton("@angular"), listDirectoryContents(tmpDir));
    }

    private URL getTestResource(String resourceName) {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        assertNotNull(String.format("Failed to get resource with name '%s' from test class resources", resourceName), resource);
        return resource;
    }

    private URL getClasspathResource(String packageName, String artifactName) throws MalformedURLException {
        URL urlWithPath = this.getClass().getClassLoader().getResource("META-INF/maven/" + packageName + "/" + artifactName + "/pom.xml");
        assertNotNull(String.format("Failed to get URL for %s %s", packageName, artifactName), urlWithPath);
        URL url = new URL(urlWithPath.getPath().split("!")[0]);
        return url;
    }

    private Set<String> listDirectoryContents(File directory) {
        File[] directoryContents = directory.listFiles();
        assertNotNull(String.format("Unable to list directory '%s' contents", directory), directoryContents);

        Set<String> result = new HashSet<>();
        for (File file : directoryContents) {
            result.add(file.getName());
        }
        return result;
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
        List<File> files = new ArrayList<>();
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
        List<File> results = new ArrayList<>();
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
        try (Writer writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void assertFileContains(File file, String content) throws Exception {
        assertFileExists(file);
        StringBuilder sb = new StringBuilder();

        try (Reader reader = new FileReader(file)) {
            char[] buffer = new char[4096];
            int read = reader.read(buffer);
            while (read > 0) {
                sb.append(buffer, 0, read);
                read = reader.read(buffer);
            }
        }

        assertEquals(content, sb.toString());
    }

}
