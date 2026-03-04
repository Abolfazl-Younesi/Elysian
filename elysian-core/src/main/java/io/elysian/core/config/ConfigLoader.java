package io.elysian.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Singleton config loader. Reads {@code elysian.yaml} from the classpath by default.
 * Override path via system property {@code -Delysian.config=/path/to/elysian.yaml}.
 */
public final class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String DEFAULT_RESOURCE = "elysian.yaml";
    private static final String SYS_PROP = "elysian.config";

    private static volatile ElysianConfig instance;

    private ConfigLoader() {}

    /** Returns the singleton {@link ElysianConfig}, loading it on first call. */
    public static ElysianConfig get() {
        if (instance == null) {
            synchronized (ConfigLoader.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    /** Force-reload the config (useful in tests). */
    public static synchronized void reload() {
        instance = null;
    }

    // -----------------------------------------------------------------------

    private static ElysianConfig load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();

        String override = System.getProperty(SYS_PROP);
        try {
            if (override != null) {
                LOG.info("Loading config from override path: {}", override);
                return mapper.readValue(Files.newInputStream(Paths.get(override)), ElysianConfig.class);
            }
            InputStream stream = ConfigLoader.class.getClassLoader()
                    .getResourceAsStream(DEFAULT_RESOURCE);
            if (stream == null) {
                throw new IllegalStateException(
                        "Cannot find '" + DEFAULT_RESOURCE + "' on classpath. "
                        + "Copy elysian.yaml to src/main/resources/ or set -D" + SYS_PROP);
            }
            LOG.info("Loading config from classpath resource: {}", DEFAULT_RESOURCE);
            return mapper.readValue(stream, ElysianConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ELySian config", e);
        }
    }
}
