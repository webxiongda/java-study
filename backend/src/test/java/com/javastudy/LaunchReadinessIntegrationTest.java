package com.javastudy;

import static org.assertj.core.api.Assertions.assertThat;

import com.javastudy.dto.AuthDtos;
import com.javastudy.dto.LearningDtos;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LaunchReadinessIntegrationTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointIsAvailableWithoutAuthentication() {
        var response = restTemplate.getForEntity("/api/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok");
        assertThat(response.getBody()).containsEntry("service", "java-study-backend");
    }

    @Test
    void protectedSummaryRejectsAnonymousRequests() {
        var response = restTemplate.getForEntity("/api/summary", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "未登录");
    }

    @Test
    void registeredUserCanReadCoreLearningApis() {
        var auth = registerUser();

        var summary = restTemplate.exchange(
            "/api/summary",
            HttpMethod.GET,
            bearer(auth.token()),
            LearningDtos.SummaryDto.class
        );
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody()).isNotNull();
        assertThat(summary.getBody().total()).isEqualTo(60);
        assertThat(summary.getBody().currentChapter()).isNotNull();

        var chapter = restTemplate.exchange(
            "/api/chapters/1",
            HttpMethod.GET,
            bearer(auth.token()),
            LearningDtos.ChapterContent.class
        );
        assertThat(chapter.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chapter.getBody()).isNotNull();
        assertThat(chapter.getBody().no()).isEqualTo(1);
        assertThat(chapter.getBody().content()).containsKeys("theory", "demo", "check", "project");
    }

    private AuthDtos.AuthResponse registerUser() {
        var username = "launch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var request = new AuthDtos.AuthRequest(username, "launch-readiness-secret");
        var response = restTemplate.postForEntity("/api/auth/register", request, AuthDtos.AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().user().username()).isEqualTo(username);
        return response.getBody();
    }

    private HttpEntity<Void> bearer(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
