package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.standalone.StandaloneCommandManager;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AddonClassLoaderRegistrationTest {
    @Test
    void loadsWrapperThroughTheCommandClassLoader() throws Exception {
        URL classes = Paths.get("target/test-classes").toAbsolutePath().toUri().toURL();
        try (URLClassLoader loader = new ChildFirstClassLoader(new URL[]{classes}, getClass().getClassLoader())) {
            Class<?> commandClass = loader.loadClass("io.github.projectunified.craftcommand.example.standalone.AddonCommand");
            Object command = commandClass.getDeclaredConstructor().newInstance();

            StandaloneCommandManager manager = new StandaloneCommandManager();
            manager.register(command);

            assertNotNull(manager.getCommand("addon"));
        }
    }

    /**
     * Loads addon classes from the addon first, while the shared runtime stays loaded by the parent.
     */
    private static final class ChildFirstClassLoader extends URLClassLoader {
        ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("io.github.projectunified.craftcommand.example.standalone.AddonCommand")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
