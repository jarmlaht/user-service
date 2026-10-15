package com.vinsguru.netflux.user;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

// One key pair per JVM so every cached test context, and the JWT verification in tests, uses the same key
final class TestRsaKeys {

    private static final Path PRIVATE_KEY_PATH = Path.of("target", "test-keys", "private-key.pem");
    private static final KeyPair KEY_PAIR = generateAndWrite();

    private TestRsaKeys() {
    }

    static Path privateKeyPath() {
        return PRIVATE_KEY_PATH.toAbsolutePath();
    }

    static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    private static KeyPair generateAndWrite() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var keyPair = generator.generateKeyPair();
            var encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
            var pem = "-----BEGIN PRIVATE KEY-----\n"
                    + encoder.encodeToString(keyPair.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            Files.createDirectories(PRIVATE_KEY_PATH.getParent());
            Files.writeString(PRIVATE_KEY_PATH, pem, StandardCharsets.US_ASCII);
            return keyPair;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
