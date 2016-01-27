package org.webjars;

import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;

import java.lang.reflect.InvocationTargetException;

public class BundleActivator implements org.osgi.framework.BundleActivator, BundleListener {
    private static BundleContext bundleContext;

    static ClassLoader getResourceLocator(ClassLoader classLoader) {
        return bundleContext != null ? new OSGIResourceLocatorImpl(bundleContext) : classLoader;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        bundleContext = context;
        bundleContext.addBundleListener(this);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        bundleContext.removeBundleListener(this);
        bundleContext = null;
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        switch(event.getType ()) {
            case BundleEvent.INSTALLED:
            case BundleEvent.UNINSTALLED:
            case BundleEvent.UPDATED:
                try {
                    Class.forName("org.webjars.RequireJS").getMethod("clearCache").invoke(null);
                } catch ( NoSuchMethodException | IllegalAccessException | ClassNotFoundException | InvocationTargetException ignore ) {
                    // ignore
                }
            default:
                // ignore
        }
    }
}
