/**
 * Contains operational and version information about the Keycloak webhook extension.
 * <p>
 * This utility class provides metadata about the extension such as version number
 * and maintainer information. Version is resolved from Maven-generated pom.properties
 * at runtime and falls back to "unknown" if not available.
 *
 * @author chintan
 */
package io.binarybrew.keycloak.webhook.constant;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class OperationalInfo {

    public static final String VERSION = resolveVersion();

    /**
     * Reads the project version from the Maven-generated {@code pom.properties} embedded
     * in the JAR at {@code META-INF/maven/io.binarybrew.keycloak.webhook/keycloak-webhook/pom.properties}.
     * Falls back to {@code "unknown"} if the file is absent (e.g. during IDE hot-runs).
     */
    private static String resolveVersion() {
        try (InputStream is = OperationalInfo.class.getResourceAsStream(
                "/META-INF/maven/io.binarybrew.keycloak.webhook/keycloak-client-webhook/pom.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                return props.getProperty("version", "unknown");
            }
        } catch (IOException ignored) {}
        return "unknown";
    }

    /**
     * Returns an immutable map of operational metadata about this extension.
     * <p>
     * Contains version number, maintainer details, and organization information.
     *
     * @return Unmodifiable map with keys: version, maintainer, email, website, organization
     */
    public static Map<String, String> operationalInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("version",      VERSION);
        info.put("maintainer",   "Chintan Buch");
        info.put("email",        "c@mrbu.ch");
        info.put("website",      "https://mrbu.ch");
        info.put("organization", "BinaryBrew");
        return Collections.unmodifiableMap(info);
    }
}
