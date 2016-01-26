package org.webjars;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class ClassLoaderList extends ClassLoader {
    private final List<ClassLoader> classLoaders = new ArrayList<> ();

    public ClassLoaderList ( ClassLoader[] loaders ) {
        classLoaders.addAll ( Arrays.asList ( loaders ) );
    }

    @Override
    public Class<?> loadClass ( String name ) throws ClassNotFoundException {
        return getClass ().getClassLoader ().loadClass ( name );
    }

    @Override
    protected Class<?> loadClass ( String name, boolean resolve ) throws ClassNotFoundException {
        throw new UnsupportedOperationException ();
    }

    @Override
    protected Object getClassLoadingLock ( String className ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    protected Class<?> findClass ( String name ) throws ClassNotFoundException {
        throw new UnsupportedOperationException ();
    }

    @Override
    public URL getResource ( String name ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public Enumeration<URL> getResources ( String name ) throws IOException {
        List<URL> urls = new ArrayList<> ();

        for ( ClassLoader classLoader : classLoaders ) {
            Enumeration<URL> resources = classLoader.getResources ( name );
            if ( resources != null ) {
                urls.addAll ( Collections.list ( resources ) );
            }
        }

        return Collections.enumeration ( urls );
    }

    @Override
    protected URL findResource ( String name ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    protected Enumeration<URL> findResources ( String name ) throws IOException {
        throw new UnsupportedOperationException ();
    }

    @Override
    public InputStream getResourceAsStream ( String name ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    protected Package definePackage ( String name, String specTitle, String specVersion, String specVendor,
            String implTitle, String implVersion, String implVendor, URL sealBase ) throws IllegalArgumentException {
        throw new UnsupportedOperationException ();
    }

    @Override
    protected Package getPackage ( String name ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    protected Package[] getPackages () {
        throw new UnsupportedOperationException ();
    }

    @Override
    protected String findLibrary ( String libname ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public void setDefaultAssertionStatus ( boolean enabled ) {
        super.setDefaultAssertionStatus ( enabled );
    }

    @Override
    public void setPackageAssertionStatus ( String packageName, boolean enabled ) {
        super.setPackageAssertionStatus ( packageName, enabled );
    }

    @Override
    public void setClassAssertionStatus ( String className, boolean enabled ) {
        super.setClassAssertionStatus ( className, enabled );
    }

    @Override
    public void clearAssertionStatus () {
        super.clearAssertionStatus ();
    }
}
