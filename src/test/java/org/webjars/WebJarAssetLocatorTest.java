package org.webjars;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;

public class WebJarAssetLocatorTest {

    @Test
    public void get_paths_of_asset_in_nested_folder() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String jsPath = locator.getFullPath("angular-translate.js");

        assertEquals("META-INF/resources/webjars/angular-translate/2.1.0/angular-translate.js", jsPath);
    }

    @Test
    public void get_full_path_of_asset_in_root_folder() {
        String jsFullPath = new WebJarAssetLocator().getFullPath("jquery.js");

        assertEquals("META-INF/resources/webjars/jquery/2.1.0/jquery.js", jsFullPath);
    }

    @Test
    public void lookup_asset_multiple_times() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String jsPath1 = locator.getFullPath("jquery.js");
        String jsPath2 = locator.getFullPath("jquery.js");
        assertEquals("META-INF/resources/webjars/jquery/2.1.0/jquery.js", jsPath1);
        assertEquals("META-INF/resources/webjars/jquery/2.1.0/jquery.js", jsPath2);
    }

    @Test
    public void get_a_file_when_another_file_exists_that_starts_with_the_same_string() {
        String fooJsPath = new WebJarAssetLocator().getFullPath("foo.js");
        assertEquals("META-INF/resources/webjars/foo/1.0.0/foo.js", fooJsPath);
    }

    @Test
    public void get_full_path_from_partial_path_with_folders() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String jsPath1 = locator.getFullPath("i18n/angular-locale_en.js");
        String jsPath2 = locator.getFullPath("/1.2.11/i18n/angular-locale_en.js");
        String jsPath3 = locator.getFullPath("angularjs/1.2.11/i18n/angular-locale_en.js");
        String jsPath4 = locator.getFullPath("/angularjs/1.2.11/i18n/angular-locale_en.js");

        String expected = "META-INF/resources/webjars/angularjs/1.2.11/i18n/angular-locale_en.js";
        assertEquals(expected, jsPath1);
        assertEquals(expected, jsPath2);
        assertEquals(expected, jsPath3);
        assertEquals(expected, jsPath4);
    }

    @Test
    public void should_throw_exception_when_asset_not_found() {
        try {
            new WebJarAssetLocator().getFullPath("asset-unknown.js");
            fail("Exception should have been thrown!");
        } catch (IllegalArgumentException e) {
            assertEquals("asset-unknown.js could not be found. Make sure you've added the corresponding WebJar and please check for typos.", e.getMessage());
        }

        try {
            new WebJarAssetLocator().getFullPath("unknown.js");
            fail("Exception should have been thrown!");
        } catch (IllegalArgumentException e) {
            assertEquals("unknown.js could not be found. Make sure you've added the corresponding WebJar and please check for typos.", e.getMessage());
        }
    }

    @Test
    public void should_distinguish_between_multiple_versions() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String v1Path = locator.getFullPath("1.0.0/multiple.js");
        String v2Path = locator.getFullPath("2.0.0/multiple.js");
        String moduleV2Path = locator.getFullPath("2.0.0/module/multiple_module.js");

        assertEquals("META-INF/resources/webjars/multiple/1.0.0/multiple.js", v1Path);
        assertEquals("META-INF/resources/webjars/multiple/2.0.0/multiple.js", v2Path);
        assertEquals("META-INF/resources/webjars/multiple/2.0.0/module/multiple_module.js", moduleV2Path);
    }

    @Test
    public void should_accept_paths_with_spaces() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String path1 = locator.getFullPath("space space.js");
        assertEquals("META-INF/resources/webjars/spaces/1.0.0/space space.js", path1);
    }

    @Test
    public void should_work_with_classpath_containing_spaces() throws java.net.MalformedURLException, NoSuchMethodException, IllegalAccessException, java.lang.reflect.InvocationTargetException {
        WebJarAssetLocator locator = buildAssetLocatorWithPath(new File("src/test/resources/space space").toURI().toURL());
        String path = locator.getFullPath("spaces/2.0.0/spaces.js");
        assertEquals(path, "META-INF/resources/webjars/spaces/2.0.0/spaces.js");
    }

    @Test
    public void should_work_with_classpath_containing_escaped_spaces() throws java.net.MalformedURLException, NoSuchMethodException, IllegalAccessException, java.lang.reflect.InvocationTargetException {
        // this kind of escaped path is often created via URI.toUrl(), and should also work
        WebJarAssetLocator locator = buildAssetLocatorWithPath(new File("src/test/resources/space space").toURI().toURL());
        String path = locator.getFullPath("spaces/2.0.0/spaces.js");
        assertEquals(path, "META-INF/resources/webjars/spaces/2.0.0/spaces.js");
    }

    private WebJarAssetLocator buildAssetLocatorWithPath(URL url)
            throws MalformedURLException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{url}, ClassLoader.getSystemClassLoader());
        return new WebJarAssetLocator(WebJarAssetLocator.getFullPathIndex(Pattern.compile(".*"), classLoader));
    }

    @Test
    public void should_throw_exceptions_when_several_matches_found() {
        try {
            new WebJarAssetLocator().getFullPath("multiple.js");
            fail("Exception should have been thrown!");
        } catch (MultipleMatchesException e) {
            assertEquals("Multiple matches found for multiple.js. Please provide a more specific path, for example by including a version number.", e.getMessage());
            assertThat(e.getMatches(), contains("META-INF/resources/webjars/multiple/2.0.0/multiple.js", "META-INF/resources/webjars/multiple/1.0.0/multiple.js"));
        }
    }

    @Test
    public void should_throw_exceptions_when_all_assets_match() throws Exception {
        try {
            new WebJarAssetLocator(new HashSet<String>(Arrays.asList("a/multi.js", "b/multi.js"))).getFullPath("multi.js");
        } catch (MultipleMatchesException e) {
            assertThat(e.getMatches(), contains("b/multi.js", "a/multi.js"));
        }
    }

    @Test
    public void should_throw_exceptions_when_several_matches_found_with_folder_in_path() {
        try {
            new WebJarAssetLocator().getFullPath("module/multiple_module.js");
            fail("Exception should have been thrown!");
        } catch (IllegalArgumentException e) {
            assertEquals("Multiple matches found for module/multiple_module.js. Please provide a more specific path, for example by including a version number.", e.getMessage());
        }
    }

    @Test
    public void should_list_assets_in_folder() {
        String fullPathPrefix = "META-INF/resources/webjars/multiple/1.0.0/";
        Set<String> assets = new WebJarAssetLocator().listAssets("/multiple/1.0.0");

        assertThat(assets, hasItems(fullPathPrefix + "multiple.js", fullPathPrefix + "module/multiple_module.js"));
    }

    @Test
    public void should_find_assets_in_a_given_webjar() {
        // resolving bootstrap.js out of the bootstrap webjar should work
        String bootstrapJsPath = new WebJarAssetLocator().getFullPath("bootstrap", "bootstrap.js");
        assertEquals(bootstrapJsPath, "META-INF/resources/webjars/bootstrap/3.1.1/js/bootstrap.js");

        // resolving bootstrap.js out of the bootswatch-yeti webjar should work
        String bootswatchYetiJsPath = new WebJarAssetLocator().getFullPath("bootswatch-yeti", "bootstrap.js");
        assertEquals(bootswatchYetiJsPath, "META-INF/resources/webjars/bootswatch-yeti/3.1.1/js/bootstrap.js");

        // resolving a more specific path out of the bootstrap webjar should work
        String moreSpecificBootstrapJsPath = new WebJarAssetLocator().getFullPath("bootstrap", "js/bootstrap.js");
        assertEquals(moreSpecificBootstrapJsPath, "META-INF/resources/webjars/bootstrap/3.1.1/js/bootstrap.js");

        // resolving a non-existent file out of the bootstrap webjar should fail
        try {
            new WebJarAssetLocator().getFullPath("bootstrap", "asdf.js");
            fail("Exception should have been thrown!");
        } catch (IllegalArgumentException e) {
            assertEquals("asdf.js could not be found. Make sure you've added the corresponding WebJar and please check for typos.", e.getMessage());
        }

        // resolving the bootstrap.js file out of the jquery webjar should fail
        try {
            new WebJarAssetLocator().getFullPath("jquery", "bootstrap.js");
            fail("Exception should have been thrown!");
        } catch (IllegalArgumentException e) {
            assertEquals("bootstrap.js could not be found. Make sure you've added the corresponding WebJar and please check for typos.", e.getMessage());
        }
    }

    @Test
    public void should_parse_a_webjar_from_a_path() {
        Map.Entry<String, String> webjar = WebJarAssetLocator.getWebJar("META-INF/resources/webjars/foo/1.0.0/asdf.js");
        assertEquals(webjar.getKey(), "foo");
        assertEquals(webjar.getValue(), "1.0.0");
    }

    @Test
    public void invalid_webjar_path_should_return_null() {
        assertNull(WebJarAssetLocator.getWebJar("foo/1.0.0/asdf.js"));
    }

    @Test
    public void should_get_a_list_of_webjars() {
        Map<String, String> webjars = new WebJarAssetLocator().getWebJars();

        assertEquals(webjars.size(), 20); // this is the pom.xml ones plus the test resources (spaces, foo, bar-node, multiple)
        assertEquals(webjars.get("bootstrap"), "3.1.1");
        assertEquals(webjars.get("less-node"), "1.6.0");
        assertEquals(webjars.get("jquery"), "2.1.0");
        assertEquals(webjars.get("angularjs"), "1.2.11");
    }

    @Test
    public void should_match_when_full_path_given() throws Exception {
        WebJarAssetLocator locator = new WebJarAssetLocator();

        assertEquals("META-INF/resources/webjars/bootstrap/3.1.1/less/.csscomb.json", locator.getFullPath("META-INF/resources/webjars/bootstrap/3.1.1/less/.csscomb.json"));
    }

    @Test
    public void should_throw_exceptions_when_several_matches_found_different_dependencies() {
        try {
            WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
            webJarAssetLocator.getFullPath("angular.js");
            fail("Exception should have been thrown!"); // because it is in both dependencies
        } catch (MultipleMatchesException e) {
            assertEquals("Multiple matches found for angular.js. Please provide a more specific path, for example by including a version number.", e.getMessage());
        }
    }

    @Test
    public void should_NOT_throw_exceptions_when_several_matches_found_different_dependencies_with_narrow_down() {
        try {
            WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
            webJarAssetLocator.getFullPath("angularjs", "angular.js");
        } catch (MultipleMatchesException e) {
            fail("Exception should NOT have been thrown!"); // because it is supposed to look in first dependency only.
        }
    }

    @Test
    public void should_throw_an_exception_when_several_matches_are_found_in_a_single_dependency() {
        try {
            WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
            webJarAssetLocator.getFullPath("babel-core", "browser.js");
            fail("Exception should have been thrown!");
        } catch (MultipleMatchesException e) {
            assertEquals("Multiple matches found for browser.js. Please provide a more specific path, for example by including a version number.", e.getMessage());
        }
    }

    @Test
    public void should_get_a_full_path_from_an_artifact_and_a_path() {
        try {
            WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
            String fullPath = webJarAssetLocator.getFullPathExact("babel-core", "browser.js");
            assertEquals("META-INF/resources/webjars/babel-core/6.0.16/browser.js", fullPath);
        } catch (MultipleMatchesException e) {
            fail("Exception should NOT have been thrown!");
        }
    }

    @Test
    public void should_get_null_from_an_artifact_and_a_path_which_does_not_exist() {
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
        String fullPath = webJarAssetLocator.getFullPathExact("babel-core", "foo.js");
        assertNull(fullPath);
    }
}
