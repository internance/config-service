package com.internance.config.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internance.config.monitor.GitHubPushNotificationParser.ParsedPush;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubPushNotificationParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GitHubPushNotificationParser parser = new GitHubPushNotificationParser();

    @Test
    void groupsChangedConfigFilesByApplication() throws Exception {
        String payload = """
                {
                  "ref": "refs/heads/main",
                  "after": "abc123",
                  "commits": [
                    {
                      "added": ["auth-service/auth-service-prod.yml"],
                      "modified": ["gateway-service/gateway-service.yml", "auth-service/auth-service.yml"],
                      "removed": []
                    }
                  ]
                }
                """;

        Optional<ParsedPush> result = parser.parse(objectMapper.readTree(payload));

        assertThat(result).isPresent();
        ParsedPush push = result.get();
        assertThat(push.label()).isEqualTo("main");
        assertThat(push.commitId()).isEqualTo("abc123");
        assertThat(push.pathsByApplication()).containsOnlyKeys("auth-service", "gateway-service");
        assertThat(push.pathsByApplication().get("auth-service"))
                .containsExactlyInAnyOrder("auth-service/auth-service-prod.yml", "auth-service/auth-service.yml");
    }

    @Test
    void ignoresRootAndNonConfigFiles() throws Exception {
        String payload = """
                {
                  "ref": "refs/heads/main",
                  "after": "abc123",
                  "commits": [
                    { "added": ["README.md"], "modified": ["auth-service/notes.txt"], "removed": [] }
                  ]
                }
                """;

        assertThat(parser.parse(objectMapper.readTree(payload))).isEmpty();
    }

    @Test
    void returnsEmptyForPushWithoutCommits() throws Exception {
        // e.g. a GitHub "ping" payload carries no commits array
        String payload = """
                { "zen": "Keep it simple.", "hook_id": 1 }
                """;

        assertThat(parser.parse(objectMapper.readTree(payload))).isEmpty();
    }
}
