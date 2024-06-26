webjars-locator-core
====================

This project provides a means to locate assets within WebJars.

[Check out the JavaDoc](https://javadocs.dev/org.webjars/webjars-locator-core/latest)

[![Latest Release](https://img.shields.io/maven-central/v/org.webjars/webjars-locator-core.svg)](https://mvnrepository.com/artifact/org.webjars/webjars-locator-core) [![CodeQL](https://github.com/webjars/webjars-locator-core/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/webjars/webjars-locator-core/actions/workflows/codeql-analysis.yml) [![.github/workflows/test.yml](https://github.com/webjars/webjars-locator-core/actions/workflows/test.yml/badge.svg)](https://github.com/webjars/webjars-locator-core/actions/workflows/test.yml) 

Obtain the full path of an asset
--------------------------------

> Find the specified partial path in any WebJar on the classpath:

    WebJarAssetLocator locator = new WebJarAssetLocator();
    String fullPathToBootstrap = locator.getFullPath("bootstrap.js");

> Find the specified partial path in a specific WebJar:

    WebJarAssetLocator locator = new WebJarAssetLocator();
    String fullPathToBootstrap = locator.getFullPath("bootstrap", "bootstrap.js");

> Get the full path to a file in a specific WebJar:

    WebJarAssetLocator locator = new WebJarAssetLocator();
    String fullPathToBootstrap = locator.getFullPathExact("bootstrap", "js/bootstrap.js");

Obtain all of the assets within a base folder
---------------------------------------------

    WebJarAssetLocator locator = new WebJarAssetLocator();
    Set<String> fullPathsOfAssets = locator.listAssets("/multiple/1.0.0");

Advanced usage
--------------

The locator can also be configured with the class loaders that it should use for looking up resources and filter the types of resources that should be included for searching. Please visit the source code for more information.
