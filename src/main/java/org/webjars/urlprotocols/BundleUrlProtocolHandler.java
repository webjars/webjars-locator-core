package org.webjars.urlprotocols;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.webjars.OSGIResourceLocatorImpl;

import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class BundleUrlProtocolHandler implements UrlProtocolHandler {
    @Override
    public boolean accepts ( String protocol ) {
        return "bundle".equals ( protocol );
    }

    @Override
    public Set<String> getAssetPaths ( URL url, Pattern filterExpr, ClassLoader... classLoaders ) {
        Set<String> rt = new HashSet<> ();

        for ( ClassLoader classLoader : classLoaders ) {
            if ( classLoader instanceof OSGIResourceLocatorImpl ) {
                BundleContext bundleContext = ( (OSGIResourceLocatorImpl) classLoader ).getBundleContext ();
                for ( Bundle bundle : bundleContext.getBundles () ) {
                    Enumeration<URL> entries = bundle.findEntries ( url.getPath (), null, true );
                    if ( entries != null ) {
                        while( entries.hasMoreElements () ) {
                            rt.add ( entries.nextElement ().getPath () );
                        }
                    }
                }
            }
        }

        return rt;
    }
}
