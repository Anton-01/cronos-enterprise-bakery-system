package com.ninsky.cronos.infrastructure.config.kms;

import com.ninsky.cronos.infrastructure.security.kms.AwsKmsAdapter;
import com.ninsky.cronos.infrastructure.security.kms.AzureKmsAdapter;
import com.ninsky.cronos.infrastructure.security.kms.GcpKmsAdapter;
import com.ninsky.cronos.infrastructure.security.kms.KmsPort;
import com.ninsky.cronos.infrastructure.security.kms.LocalDevKmsAdapter;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kms")
@Getter
@Setter
public class KmsConfig {

    /** {@code gcp} | {@code aws} | {@code azure} | {@code local} (dev/sandbox only, see {@link LocalDevKmsAdapter}). */
    private String provider = "local";

    /** KMS-wrapped Data Encryption Key, unwrapped once at startup by {@code FieldEncryptionService}. */
    private String wrappedDataKey;

    private final Gcp gcp = new Gcp();
    private final Local local = new Local();

    @Getter @Setter
    public static class Gcp {
        private String projectId;
        private String location;
        private String keyRing;
        private String keyName;
    }

    @Getter @Setter
    public static class Local {
        private String masterKey;
    }

    @Bean
    public KmsPort kmsPort() {
        return switch (provider.toLowerCase()) {
            case "gcp" -> new GcpKmsAdapter(gcp.getProjectId(), gcp.getLocation(), gcp.getKeyRing(), gcp.getKeyName());
            case "aws" -> new AwsKmsAdapter();
            case "azure" -> new AzureKmsAdapter();
            case "local" -> new LocalDevKmsAdapter(local.getMasterKey());
            default -> throw new IllegalArgumentException("Unknown kms.provider: " + provider);
        };
    }
}
