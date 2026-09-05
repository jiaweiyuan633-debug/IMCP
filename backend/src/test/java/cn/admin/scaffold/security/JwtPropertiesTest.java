package cn.admin.scaffold.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtPropertiesTest {

    private JwtProperties productionProperties() {
        JwtProperties properties = new JwtProperties();
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        properties.setEnvironment(environment);
        return properties;
    }

    @Test
    void rejectsMissingSecret() {
        JwtProperties properties = productionProperties();
        properties.setSecret(null);
        assertThrows(IllegalStateException.class, properties::validateSecret);
    }

    @Test
    void rejectsKnownDefaultSecretInProduction() {
        JwtProperties properties = productionProperties();
        properties.setSecret("dev-only-jwt-secret-please-override-0123456789abcdef");
        assertThrows(IllegalStateException.class, properties::validateSecret);
    }

    @Test
    void rejectsPlaceholderSecretInProduction() {
        JwtProperties properties = productionProperties();
        properties.setSecret("super-secret-change-me-in-prod-0123456789");
        assertThrows(IllegalStateException.class, properties::validateSecret);
    }

    @Test
    void rejectsShortSecretInProduction() {
        JwtProperties properties = productionProperties();
        properties.setSecret("short");
        assertThrows(IllegalStateException.class, properties::validateSecret);
    }

    @Test
    void acceptsStrongIndependentSecretInProduction() {
        JwtProperties properties = productionProperties();
        properties.setSecret("a-strong-independent-secret-key-0123456789abcdef");
        assertDoesNotThrow(properties::validateSecret);
    }

    @Test
    void allowsKnownDefaultSecretInDevProfile() {
        JwtProperties properties = new JwtProperties();
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        properties.setEnvironment(environment);
        properties.setSecret("dev-only-jwt-secret-please-override-0123456789abcdef");
        assertDoesNotThrow(properties::validateSecret);
    }
}
