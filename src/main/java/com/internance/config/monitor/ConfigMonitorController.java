package com.internance.config.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internance.config.monitor.GitHubPushNotificationParser.ParsedPush;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Webhook endpoint the config Git repo (GitHub) calls on every push. When config
 * files change, it publishes a {@link com.internance.config.event.ConfigChangedEvent}
 * per affected application to Kafka through common-lib's {@code EventPublisher},
 * so downstream services can refresh without polling.
 *
 * <p>Point the repository's webhook at {@code POST /monitor} (content type
 * {@code application/json}). Set {@code config.monitor.webhook-secret} to the same
 * secret configured on the webhook to enforce {@code X-Hub-Signature-256}
 * verification; leave it blank to accept unauthenticated calls (e.g. behind a
 * trusted network).
 */
@RestController
@EnableConfigurationProperties(ConfigMonitorProperties.class)
public class ConfigMonitorController {

    private static final Logger log = LoggerFactory.getLogger(ConfigMonitorController.class);
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final GitHubPushNotificationParser parser;
    private final ConfigChangePublisher publisher;
    private final ConfigMonitorProperties properties;
    private final String defaultLabel;

    public ConfigMonitorController(
            ObjectMapper objectMapper,
            GitHubPushNotificationParser parser,
            ConfigChangePublisher publisher,
            ConfigMonitorProperties properties,
            @Value("${spring.cloud.config.server.git.default-label:main}") String defaultLabel) {
        this.objectMapper = objectMapper;
        this.parser = parser;
        this.publisher = publisher;
        this.properties = properties;
        this.defaultLabel = defaultLabel;
    }

    @PostMapping("/monitor")
    public ResponseEntity<Map<String, Object>> onPush(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] body) {

        if (body == null || body.length == 0) {
            return ignored("empty body");
        }
        verifySignature(body, signature);

        // GitHub sends a "ping" when the webhook is created — acknowledge and skip.
        if ("ping".equalsIgnoreCase(event)) {
            return ignored("ping");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed webhook payload", e);
        }

        Optional<ParsedPush> parsed = parser.parse(root);
        if (parsed.isEmpty()) {
            return ignored("no config changes");
        }

        ParsedPush push = parsed.get();
        if (push.label() != null && !push.label().equals(defaultLabel)) {
            return ignored("branch " + push.label() + " is not the served label " + defaultLabel);
        }

        Set<String> applications = push.pathsByApplication().keySet();
        push.pathsByApplication()
                .forEach((application, paths) -> publisher.publish(application, push.label(), paths, push.commitId()));

        return ResponseEntity.ok(Map.of("status", "published", "applications", applications));
    }

    private ResponseEntity<Map<String, Object>> ignored(String reason) {
        log.debug("Ignoring webhook call: {}", reason);
        return ResponseEntity.ok(Map.of("status", "ignored", "reason", reason));
    }

    /**
     * Verifies the GitHub HMAC-SHA256 signature when a webhook secret is
     * configured. No-op when the secret is blank.
     */
    private void verifySignature(byte[] body, String signature) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return;
        }
        if (signature == null || !signature.startsWith(SIGNATURE_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or malformed signature");
        }
        String expected = SIGNATURE_PREFIX + hmacSha256(secret, body);
        // Constant-time comparison to avoid leaking the signature via timing.
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature mismatch");
        }
    }

    private static String hmacSha256(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to compute signature", e);
        }
    }
}
