package org.webjars;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.catalina.webresources.WarResourceSet;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatEmbeddedWebappClassLoader;
import org.webjars.WebJarAssetLocator.WebJarInfo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class WebJarAssetLocatorTest {

    private HashMap<String, WebJarAssetLocator.WebJarInfo> withList(List<String> paths) throws URISyntaxException {
        HashMap<String, WebJarAssetLocator.WebJarInfo> webJars = new HashMap<>();
        WebJarAssetLocator.WebJarInfo webJarInfo = new WebJarAssetLocator.WebJarInfo("1.0.0", Optional.of(new MavenProperties("foo", "fooId", "x.y.z")), new URI("asdf"), paths);
        webJars.put("foo", webJarInfo);
        return webJars;
    }

    @Test
    public void should_find_full_path() throws Exception {
        WebJarAssetLocator locator = new WebJarAssetLocator(withList(asList("META-INF/resources/myapp/app.js", "assets/users/login.css")));

        assertThat(locator.getFullPath("app.js"), is("META-INF/resources/myapp/app.js"));
        assertThat(locator.getFullPath("myapp/app.js"), is("META-INF/resources/myapp/app.js"));
        assertThat(locator.getFullPath("login.css"), is("assets/users/login.css"));
        assertThat(locator.getFullPath("users/login.css"), is("assets/users/login.css"));
    }

    @Test
    public void should_list_assets() throws Exception {
        WebJarAssetLocator locator = new WebJarAssetLocator(withList(asList("META-INF/resources/myapp/app.js", "assets/users/login.css", "META-INF/resources/webjars/third_party/1.5.2/file.js")));

        assertThat(locator.listAssets("META-INF"), hasItems("META-INF/resources/myapp/app.js", "META-INF/resources/webjars/third_party/1.5.2/file.js"));
        assertThat(locator.listAssets("assets"), hasItem("assets/users/login.css"));
        assertThat(locator.listAssets("third_party"), hasItem("META-INF/resources/webjars/third_party/1.5.2/file.js"));
        assertThat(locator.listAssets(), hasItems("META-INF/resources/myapp/app.js", "assets/users/login.css", "META-INF/resources/webjars/third_party/1.5.2/file.js"));
    }

    @Test
    public void get_paths_of_asset_in_nested_folder() {
        WebJarAssetLocator locator = new WebJarAssetLocator();

        String jsPath1 = locator.getFullPath("angular-translate.js");
        assertThat(jsPath1, is("META-INF/resources/webjars/angular-translate/2.1.0/angular-translate.js"));

        String jsPath2 = locator.getFullPath("require.js");
        assertThat(jsPath2, is("META-INF/resources/webjars/requirejs/2.3.6/require.js"));
    }

    @Test
    public void get_full_path_of_asset_in_root_folder() {
        String jsFullPath = new WebJarAssetLocator().getFullPath("jquery.js");

        assertThat(jsFullPath, is("META-INF/resources/webjars/jquery/2.1.0/jquery.js"));
    }

    @Test
    public void lookup_asset_multiple_times() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String jsPath1 = locator.getFullPath("jquery.js");
        String jsPath2 = locator.getFullPath("jquery.js");
        assertThat(jsPath1, is("META-INF/resources/webjars/jquery/2.1.0/jquery.js"));
        assertThat(jsPath2, is("META-INF/resources/webjars/jquery/2.1.0/jquery.js"));
    }

    @Test
    public void get_a_file_when_another_file_exists_that_starts_with_the_same_string() {
        String fooJsPath = new WebJarAssetLocator().getFullPath("foo.js");
        assertThat(fooJsPath, is("META-INF/resources/webjars/foo/1.0.0/foo.js"));
    }

    @Test
    public void get_full_path_from_partial_path_with_folders() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String jsPath1 = locator.getFullPath("i18n/angular-locale_en.js");
        String jsPath2 = locator.getFullPath("/1.2.11/i18n/angular-locale_en.js");
        String jsPath3 = locator.getFullPath("angularjs/1.2.11/i18n/angular-locale_en.js");
        String jsPath4 = locator.getFullPath("/angularjs/1.2.11/i18n/angular-locale_en.js");

        Matcher<String> expected = is("META-INF/resources/webjars/angularjs/1.2.11/i18n/angular-locale_en.js");
        assertThat(jsPath1, expected);
        assertThat(jsPath2, expected);
        assertThat(jsPath3, expected);
        assertThat(jsPath4, expected);
    }

    @Test
    public void should_throw_exception_when_asset_not_found() {
        try {
            new WebJarAssetLocator().getFullPath("asset-unknown.js");
            throw new AssertionError("Exception should have been thrown!");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("asset-unknown.js could not be found. Make sure you've added the corresponding WebJar and please check for typos."));
        }

        try {
            new WebJarAssetLocator().getFullPath("unknown.js");
            throw new AssertionError("Exception should have been thrown!");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("unknown.js could not be found. Make sure you've added the corresponding WebJar and please check for typos."));
        }
    }

    @Test
    public void should_distinguish_between_multiple_versions() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String v1Path = locator.getFullPath("1.0.0/multiple.js");
        String v2Path = locator.getFullPath("2.0.0/multiple.js");
        String moduleV2Path = locator.getFullPath("2.0.0/module/multiple_module.js");

        assertThat(v1Path, is("META-INF/resources/webjars/multiple/1.0.0/multiple.js"));
        assertThat(v2Path, is("META-INF/resources/webjars/multiple/2.0.0/multiple.js"));
        assertThat(moduleV2Path, is("META-INF/resources/webjars/multiple/2.0.0/module/multiple_module.js"));
    }

    @Test
    public void should_accept_paths_with_spaces() {
        WebJarAssetLocator locator = new WebJarAssetLocator();
        String path1 = locator.getFullPath("space space.js");
        assertThat(path1, is("META-INF/resources/webjars/spaces/1.0.0/space space.js"));
    }

    @Test
    public void should_work_with_classpath_containing_spaces() throws java.net.MalformedURLException {
        WebJarAssetLocator locator = buildAssetLocatorWithPath(new File("src/test/resources/space space").toURI().toURL());
        String path = locator.getFullPath("spaces/2.0.0/spaces.js");
        assertThat(path, is("META-INF/resources/webjars/spaces/2.0.0/spaces.js"));
    }

    @Test
    public void should_work_with_classpath_containing_escaped_spaces() throws java.net.MalformedURLException {
        // this kind of escaped path is often created via URI.toUrl(), and should also work
        WebJarAssetLocator locator = buildAssetLocatorWithPath(new File("src/test/resources/space space").toURI().toURL());
        String path = locator.getFullPath("spaces/2.0.0/spaces.js");
        assertThat(path, is("META-INF/resources/webjars/spaces/2.0.0/spaces.js"));
    }

    private WebJarAssetLocator buildAssetLocatorWithPath(URL url) {
        URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{url}, ClassLoader.getSystemClassLoader());
        return new WebJarAssetLocator(classLoader);
    }

    @Test
    public void should_throw_exceptions_when_several_matches_found() {
        try {
            new WebJarAssetLocator().getFullPath("multiple.js");
            throw new AssertionError("Exception should have been thrown!");
        } catch (MultipleMatchesException e) {
            assertThat(e.getMessage(), is("Multiple matches found for multiple.js. Please provide a more specific path, for example by including a version number."));
            assertThat(e.getMatches(), hasItems("META-INF/resources/webjars/multiple/2.0.0/multiple.js", "META-INF/resources/webjars/multiple/1.0.0/multiple.js"));
        }
    }

    @Test
    public void should_throw_exceptions_when_all_assets_match() {
        try {
            new WebJarAssetLocator(withList(Arrays.asList("a/multi.js", "b/multi.js"))).getFullPath("multi.js");
        } catch (MultipleMatchesException e) {
            assertThat(e.getMatches(), hasItems("b/multi.js", "a/multi.js"));
        } catch (URISyntaxException e) {
            throw new AssertionError("should not fail");
        }
    }

    @Test
    public void should_throw_exceptions_when_several_matches_found_with_folder_in_path() {
        try {
            new WebJarAssetLocator().getFullPath("module/multiple_module.js");
            throw new AssertionError("Exception should have been thrown!");
        } catch (MultipleMatchesException e) {
            assertThat(e.getMessage(), is("Multiple matches found for module/multiple_module.js. Please provide a more specific path, for example by including a version number."));
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
        assertThat(bootstrapJsPath, is("META-INF/resources/webjars/bootstrap/3.1.1/js/bootstrap.js"));

        // resolving bootstrap.js out of the bootswatch-yeti webjar should work
        String bootswatchYetiJsPath = new WebJarAssetLocator().getFullPath("bootswatch-yeti", "bootstrap.js");
        assertThat(bootswatchYetiJsPath, is("META-INF/resources/webjars/bootswatch-yeti/3.1.1/js/bootstrap.js"));

        // resolving a more specific path out of the bootstrap webjar should work
        String moreSpecificBootstrapJsPath = new WebJarAssetLocator().getFullPath("bootstrap", "js/bootstrap.js");
        assertThat(moreSpecificBootstrapJsPath, is("META-INF/resources/webjars/bootstrap/3.1.1/js/bootstrap.js"));

        // resolving a non-existent file out of the bootstrap webjar should fail
        try {
            new WebJarAssetLocator().getFullPath("bootstrap", "asdf.js");
            throw new AssertionError("Exception should have been thrown!");
        } catch (NotFoundException e) {
            assertThat(e.getMessage(), is("asdf.js could not be found. Make sure you've added the corresponding WebJar and please check for typos."));
        }

        // resolving the bootstrap.js file out of the jquery webjar should fail
        try {
            new WebJarAssetLocator().getFullPath("jquery", "bootstrap.js");
            throw new AssertionError("Exception should have been thrown!");
        } catch (NotFoundException e) {
            assertThat(e.getMessage(), is("bootstrap.js could not be found. Make sure you've added the corresponding WebJar and please check for typos."));
        }
    }

    @Test
    public void should_parse_a_webjar_from_a_path() {
        Map.Entry<String, String> webjar1 = WebJarAssetLocator.getWebJar("META-INF/resources/webjars/foo/1.0.0/asdf.js");
        assert webjar1 != null;
        assertThat(webjar1.getKey(), is("foo"));
        assertThat(webjar1.getValue(), is("1.0.0"));

        Map.Entry<String, String> webjar2 = WebJarAssetLocator.getWebJar("META-INF/resources/webjars/virtual-keyboard/1.30.1/dist/js/jquery.keyboard.min.js");
        assert webjar2 != null;
        assertThat(webjar2.getKey(), is("virtual-keyboard"));
        assertThat(webjar2.getValue(), is("1.30.1"));
    }

    @Test
    public void invalid_webjar_path_should_return_null() {
        assertThat(WebJarAssetLocator.getWebJar("foo/1.0.0/asdf.js"), is(nullValue()));
    }

    @Test
    public void should_get_a_list_of_webjars() {
        Map<String, String> webjars = new WebJarAssetLocator().getWebJars();

        assertThat(webjars.size(), is(38)); // this is the pom.xml ones plus the test resources (spaces, foo, bar-node, multiple)
        assertThat(webjars.get("bootstrap"), is("3.1.1"));
        assertThat(webjars.get("less-node"), is("1.6.0"));
        assertThat(webjars.get("jquery"), is("2.1.0"));
        assertThat(webjars.get("angularjs"), is("1.2.11"));
        assertThat(webjars.get("virtual-keyboard"), is("1.30.1"));
    }

    @Test
    public void should_match_when_full_path_given() {
        WebJarAssetLocator locator = new WebJarAssetLocator();

        assertThat(locator.getFullPath("META-INF/resources/webjars/bootstrap/3.1.1/less/.csscomb.json"), is("META-INF/resources/webjars/bootstrap/3.1.1/less/.csscomb.json"));
    }

    @Test
    public void should_throw_exceptions_when_several_matches_found_different_dependencies() {
        try {
            WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
            webJarAssetLocator.getFullPath("angular.js");
            throw new AssertionError("Exception should have been thrown!"); // because it is in both dependencies
        } catch (MultipleMatchesException e) {
            assertThat(e.getMessage(), is("Multiple matches found for angular.js. Please provide a more specific path, for example by including a version number."));
        }
    }

    @Test
    public void should_NOT_throw_exceptions_when_several_matches_found_different_dependencies_with_narrow_down() {
        try {
            WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
            webJarAssetLocator.getFullPath("angularjs", "angular.js");
        } catch (MultipleMatchesException e) {
            throw new AssertionError("Exception should NOT have been thrown!"); // because it is supposed to look in first dependency only.
        }
    }

    @Test
    public void should_throw_an_exception_when_several_matches_are_found_in_a_single_dependency() {
        try {
            WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
            webJarAssetLocator.getFullPath("babel-core", "browser.js");
            throw new AssertionError("Exception should have been thrown!");
        } catch (MultipleMatchesException e) {
            assertThat(e.getMessage(), is("Multiple matches found for browser.js. Please provide a more specific path, for example by including a version number."));
        }
    }

    @Test
    public void should_get_a_full_path_from_an_artifact_and_a_path() {
        try {
            WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
            String fullPath1 = webJarAssetLocator.getFullPathExact("babel-core", "browser.js");
            assertThat(fullPath1, is("META-INF/resources/webjars/babel-core/6.0.16/browser.js"));

            String fullPath2 = webJarAssetLocator.getFullPathExact("virtual-keyboard", "dist/js/jquery.keyboard.min.js");
            assertThat(fullPath2, is("META-INF/resources/webjars/virtual-keyboard/1.30.1/dist/js/jquery.keyboard.min.js"));
        } catch (MultipleMatchesException e) {
            throw new AssertionError("Exception should NOT have been thrown!");
        }
    }

    @Test
    public void should_get_null_from_an_artifact_and_a_path_which_does_not_exist() {
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
        String fullPath = webJarAssetLocator.getFullPathExact("babel-core", "foo.js");
        assertThat(fullPath, is(nullValue()));
    }

    @Test
    public void should_work_with_war_files() throws IOException, LifecycleException {
        URL fatjar = WebJarAssetLocatorTest.class.getClassLoader().getResource("fatjar.war");
        assert fatjar != null;
        String fatJarWarFile = fatjar.getFile();

        URL fatJarWarUrl = new URL("jar:file:" + fatJarWarFile + "!/");

        URLClassLoader fatJarWarClassLoader = new URLClassLoader(new URL[]{fatJarWarUrl}, null);

        TomcatEmbeddedWebappClassLoader tomcatEmbeddedWebappClassLoader = new TomcatEmbeddedWebappClassLoader(fatJarWarClassLoader);

        StandardContext context = new StandardContext();
        context.setName("test");
        StandardRoot resources = new StandardRoot();
        resources.setContext(context);
        resources.addJarResources(new WarResourceSet(resources, "/", fatJarWarFile));
        resources.start();
        tomcatEmbeddedWebappClassLoader.setResources(resources);
        tomcatEmbeddedWebappClassLoader.start();

        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator(tomcatEmbeddedWebappClassLoader);

        tomcatEmbeddedWebappClassLoader.destroy();

        assertThat(webJarAssetLocator.getFullPath("jquery.js"), is("META-INF/resources/webjars/jquery/1.10.2/jquery.js"));
    }

    // The org.webjars.npm:virtual-keyboard:1.30.1 jar contains root files named META-INF, resources, webjars which are not directories
    @Test
    public void should_work_with_a_bad_jar() {
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
        assertThat(webJarAssetLocator.getFullPathExact("virtual-keyboard", "dist/js/jquery.keyboard.min.js"), is(notNullValue()));
    }

    @Test
    public void should_work_with_directoryless_jar() {
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
        Set<String> webjars = webJarAssetLocator.getWebJars().keySet();
        assertThat(webjars.contains("vaadin-form-layout"), is(true));
    }

    @Test
    public void should_get_groupids() {
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();

        assertThat(webJarAssetLocator.groupId("META-INF/resources/webjars/iron-flex-layout/iron-flex-layout.d.ts"), is("org.webjars.bowergithub.polymerelements"));
        assertThat(webJarAssetLocator.groupId("META-INF/resources/webjars/angular-ui-router/0.2.15/release/angular-ui-router.js"), is("org.webjars.npm"));
        assertThat(webJarAssetLocator.groupId("META-INF/resources/webjars/bootstrap/3.1.1/css/bootstrap.css"), is("org.webjars"));
    }

    @Test
    public void should_not_npe_in_getFullPathExact() {
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
        assertThat(webJarAssetLocator.getFullPathExact("asdfasdf", "asdfasdf"), is(nullValue()));
    }

    // https://github.com/webjars/webjars-locator-core/issues/35
    // prevent duplicate entries
    @Test
    public void issue_35() {
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator();
        assertThat(webJarAssetLocator.getFullPath("jquery", "jquery.js"), is("META-INF/resources/webjars/jquery/2.1.0/jquery.js"));
    }


    @Test
    public void should_not_npe_in_getFullPath() {
        Map<String, WebJarInfo> allWebJars = new HashMap<>();
        allWebJars.put("webjar", new WebJarInfo("version", Optional.of(new MavenProperties("groupId", "artifactId", "version")), null, emptyList()));
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator(allWebJars);
        String partialPath = "partialPath";

        try {
            webJarAssetLocator.getFullPath("webjar", "partialPath");
        } catch (NotFoundException e) {
            assertThat(e.getMessage(), is(partialPath + " could not be found. Make sure you've added the corresponding WebJar and please check for typos."));
        }
    }

    @Test
    public void should_get_maven_info() {
        Map<String, WebJarInfo> allWebJars = new HashMap<>();
        allWebJars.put("webjar", new WebJarInfo("version", Optional.of(new MavenProperties("foo", "bar", "1.2.3")), null, emptyList()));
        WebJarAssetLocator webJarAssetLocator = new WebJarAssetLocator(allWebJars);

        Optional<WebJarInfo> info = webJarAssetLocator.getAllWebJars().entrySet().stream().filter(e -> e.getKey().equals("webjar")).map(Map.Entry::getValue).findFirst();
        if (!info.isPresent()) {
            throw new AssertionError("Could not retrieve web jar info for 'webjar'");
        } else {
            org.hamcrest.MatcherAssert.assertThat(info.get().groupId, is("foo"));
            org.hamcrest.MatcherAssert.assertThat(info.get().artifactId, is("bar"));
            org.hamcrest.MatcherAssert.assertThat(info.get().version, is("version"));
        }
    }
}
