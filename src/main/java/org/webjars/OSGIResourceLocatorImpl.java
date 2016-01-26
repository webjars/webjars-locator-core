package org.webjars;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class OSGIResourceLocatorImpl extends ClassLoader {
    private final BundleContext bundleContext;

    public OSGIResourceLocatorImpl ( BundleContext bundleContext ) {
        this.bundleContext = bundleContext;
    }

    public BundleContext getBundleContext () {
        return bundleContext;
    }

    @Override
    public URL getResource ( String name ) {
        URL url = null;

        for ( Bundle bundle : bundleContext.getBundles () ) {
            url = bundle.getResource ( name );
            if ( url != null ) {
                break;
            }
        }

        return url;
    }

    @Override
    public InputStream getResourceAsStream ( String path ) {
        URL url = getResource ( path );
        try {
            return ( url != null ) ? url.openStream () : null;
        } catch ( IOException e ) {
            throw new RuntimeException ( e );
        }
    }

    @Override
    public Class<?> loadClass ( String name ) throws ClassNotFoundException {
        throw new UnsupportedOperationException ();
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
    protected URL findResource ( String name ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    protected Enumeration<URL> findResources ( String name ) throws IOException {
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
        throw new UnsupportedOperationException ();
    }

    @Override
    public void setPackageAssertionStatus ( String packageName, boolean enabled ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public void setClassAssertionStatus ( String className, boolean enabled ) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public void clearAssertionStatus () {
        throw new UnsupportedOperationException ();
    }

    @Override
    public Enumeration<URL> getResources ( String resource ) throws IOException {
        List<URL> urls = new ArrayList<> ();

        for ( Bundle bundle : bundleContext.getBundles () ) {
            Enumeration<URL> resources = bundle.getResources ( resource );
            if ( resources != null ) {
                urls.addAll ( Collections.list ( resources ) );
            }
        }

        return Collections.enumeration ( urls );
    }
}
