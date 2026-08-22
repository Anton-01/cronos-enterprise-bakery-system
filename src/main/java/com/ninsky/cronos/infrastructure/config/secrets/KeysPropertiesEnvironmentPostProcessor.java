package com.ninsky.cronos.infrastructure.config.secrets;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Loads DB/JWT/mail/KMS secrets from an external {@code keys.properties} file so they never have
 * to live in application.yml (tracked by git) or be re-exported by hand every session.
 * <p>
 * Path resolution, highest priority first:
 * <ol>
 *   <li>{@code -Dcronos.keys.file=...} JVM system property</li>
 *   <li>{@code CRONOS_KEYS_FILE} environment variable — the portable override; on {@code qa} this
 *       is <b>required</b> on any OS other than macOS, since there is no sane hardcoded default
 *       for every developer's machine</li>
 *   <li>Profile default — {@code prod} uses {@value #PROD_DEFAULT_PATH} (see class docs below for
 *       the permissions a server operator must set on it); {@code qa} falls back to the
 *       maintainer's local macOS path as a convenience only</li>
 *   <li>Anything else (no {@code qa}/{@code prod} profile, no override set): no-op — plain local
 *       dev keeps working exactly as before, via whatever env vars/IDE run config are already in
 *       place</li>
 * </ol>
 * {@code qa} and {@code prod} are fail-fast: if the profile is active and the resolved file is
 * missing, startup aborts immediately with an actionable message instead of booting with blank
 * secrets. Any explicit override (system property or env var) is fail-fast on every profile, since
 * a user who set it clearly expects the file to be there.
 * <p>
 * Production deployment note: {@value #PROD_DEFAULT_PATH} must be created by the server operator
 * outside of any git-tracked or web-served directory, owned by the service account the JVM runs
 * as, and mode {@code 600} (owner read/write only). This class only warns about overly permissive
 * files on POSIX systems — it does not create or chmod the file itself.
 */
public class KeysPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "keysPropertiesFile";
    private static final String SYSTEM_PROPERTY = "cronos.keys.file";
    private static final String ENV_VAR = "CRONOS_KEYS_FILE";
    private static final String PROD_DEFAULT_PATH = "/etc/cronos/keys.properties";
    private static final String QA_MACOS_DEFAULT_PATH = "/Users/anton/Codification-back-files/pkt-k/scts/keys.properties";

    private final Log log;

    public KeysPropertiesEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(getClass());
    }

    @Override
    public int getOrder() {
        // After ConfigDataEnvironmentPostProcessor (HIGHEST_PRECEDENCE + 10) so
        // spring.profiles.active from application.yml/env is already resolved.
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean prod = activeProfiles.contains("prod");
        boolean qa = activeProfiles.contains("qa");

        String override = resolveOverride();
        String resolvedPath = override;
        boolean mandatory = override != null || prod || qa;

        if (resolvedPath == null) {
            if (prod) {
                resolvedPath = PROD_DEFAULT_PATH;
            } else if (qa) {
                if (isMac()) {
                    resolvedPath = QA_MACOS_DEFAULT_PATH;
                } else {
                    throw new IllegalStateException(
                            "Perfil 'qa' activo: no se pudo determinar la ruta de keys.properties. "
                                    + "Define la variable de entorno " + ENV_VAR + " apuntando al archivo "
                                    + "(la ruta por defecto solo aplica en macOS). Ver keys.properties.example.");
                }
            } else {
                return;
            }
        }

        Path path = Path.of(resolvedPath);
        if (!Files.isRegularFile(path)) {
            if (mandatory) {
                throw new IllegalStateException(
                        "No se encontro el archivo de credenciales en '" + path + "'. Verifica "
                                + ENV_VAR + " (o -D" + SYSTEM_PROPERTY + "), o crea el archivo a partir de "
                                + "keys.properties.example.");
            }
            return;
        }

        warnIfPermissionsTooOpen(path, prod);

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de credenciales '" + path + "'", e);
        }

        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, props));

        log.info("Credenciales externas cargadas desde: " + path);
    }

    private String resolveOverride() {
        String sysProp = System.getProperty(SYSTEM_PROPERTY);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        String env = System.getenv(ENV_VAR);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return null;
    }

    private boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    private void warnIfPermissionsTooOpen(Path path, boolean prod) {
        if (!prod) {
            return;
        }
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
            if (perms.contains(PosixFilePermission.GROUP_READ) || perms.contains(PosixFilePermission.OTHERS_READ)) {
                log.warn(path + " es legible por group/other. Corrige con: chmod 600 " + path);
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // Sistema de archivos sin permisos POSIX (p. ej. Windows): no aplica.
        }
    }
}
