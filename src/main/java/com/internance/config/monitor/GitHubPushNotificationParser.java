package com.internance.config.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Extracts which config applications changed from a GitHub {@code push} webhook
 * payload.
 *
 * <p>The config repo uses the {@code search-paths: '{application}'} layout, so a
 * file lives under {@code <application>/<file>}. The application affected by a
 * changed path is therefore its top-level directory. Only recognised config files
 * ({@code .yml}, {@code .yaml}, {@code .properties}, {@code .json}) count — README
 * edits and other repo housekeeping are ignored.
 */
@Component
public class GitHubPushNotificationParser {

    private static final Set<String> CONFIG_EXTENSIONS = Set.of(".yml", ".yaml", ".properties", ".json");

    /**
     * Parsed result of a push: the branch/label it landed on, the head commit SHA,
     * and the changed config paths grouped by application.
     */
    public record ParsedPush(String label, String commitId, Map<String, Set<String>> pathsByApplication) {}

    /**
     * Parses the push payload. Returns {@link Optional#empty()} when the payload
     * carries no config-file changes (e.g. a {@code ping} event, an empty push, or
     * a push touching only non-config files).
     */
    public Optional<ParsedPush> parse(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return Optional.empty();
        }

        Map<String, Set<String>> byApplication = new LinkedHashMap<>();
        JsonNode commits = root.path("commits");
        if (commits.isArray()) {
            for (JsonNode commit : commits) {
                collectPaths(commit.path("added"), byApplication);
                collectPaths(commit.path("modified"), byApplication);
                collectPaths(commit.path("removed"), byApplication);
            }
        }

        if (byApplication.isEmpty()) {
            return Optional.empty();
        }

        String label = branchFromRef(root.path("ref").asText(null));
        String commitId = root.path("after").asText(null);
        return Optional.of(new ParsedPush(label, commitId, byApplication));
    }

    private void collectPaths(JsonNode paths, Map<String, Set<String>> byApplication) {
        if (!paths.isArray()) {
            return;
        }
        for (JsonNode path : paths) {
            String value = path.asText(null);
            String application = applicationOf(value);
            if (application != null) {
                byApplication
                        .computeIfAbsent(application, k -> new LinkedHashSet<>())
                        .add(value);
            }
        }
    }

    /**
     * Maps a repo-relative path to its config application (the top-level
     * directory), or {@code null} when the path is not a config file under an
     * application directory.
     */
    private String applicationOf(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        int slash = path.indexOf('/');
        if (slash <= 0) {
            return null; // file at repo root (e.g. README.md) — not an application config
        }
        String lower = path.toLowerCase();
        boolean isConfigFile = CONFIG_EXTENSIONS.stream().anyMatch(lower::endsWith);
        return isConfigFile ? path.substring(0, slash) : null;
    }

    private String branchFromRef(String ref) {
        if (ref == null) {
            return null;
        }
        int lastSlash = ref.lastIndexOf('/');
        return lastSlash < 0 ? ref : ref.substring(lastSlash + 1);
    }
}
