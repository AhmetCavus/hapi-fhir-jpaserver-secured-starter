package ca.uhn.fhir.jpa.starter.security;

import ca.uhn.fhir.jpa.starter.Application;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Actual embedded Tomcat, security filters, HAPI servlet and disposable H2 database. */
@ActiveProfiles("test")
@SpringBootTest(
		classes = Application.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"server.address=127.0.0.1",
			"spring.datasource.url=jdbc:h2:mem:playground_security",
			"playground.demo.seed=true",
			"hapi.fhir.fhir_version=R4",
			"hapi.fhir.cr.enabled=false",
			"hapi.fhir.mdm_enabled=false",
			"hapi.fhir.subscription.websocket_enabled=false"
		})
class PlaygroundResourceSecurityIT {
	private static final SignedAccessTokens TOKENS = tokens();
	private static final HttpClient CLIENT =
			HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final List<String> SECRETS = new ArrayList<>();
	private static final ListAppender<ILoggingEvent> LOGS = new ListAppender<>();

	static {
		LOGS.start();
		((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(LOGS);
	}

	@LocalServerPort
	int port;

	private static SignedAccessTokens tokens() {
		try {
			return new SignedAccessTokens();
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to start isolated signed-token issuer", exception);
		}
	}

	@DynamicPropertySource
	static void trust(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TOKENS.issuer);
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> TOKENS.jwksUri);
	}

	@AfterAll
	static void cleanup() {
		try {
			String output =
					LOGS.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
			assertThat(SECRETS.stream().noneMatch(output::contains))
					.as("routine logs exclude secret sentinels")
					.isTrue();
			assertThat(output.contains("fictional-resource-owner")).isFalse();
		} finally {
			((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(LOGS);
			LOGS.stop();
			TOKENS.close();
		}
	}

	private HttpResponse<String> send(String method, String path, String token) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
				.method(method, HttpRequest.BodyPublishers.noBody());
		if (token != null) {
			SECRETS.add(token);
			request.header("Authorization", "Bearer " + token);
		}
		return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private void denial(HttpResponse<String> response, int status, String reason) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.body().isEmpty()).isTrue();
		assertThat(response.headers().firstValue("X-Playground-Decision")).contains("denied");
		assertThat(response.headers().firstValue("X-Playground-Reason")).contains(reason);
	}

	@Test
	void allowedRequestReachesTheRealSeededJpaResource() throws Exception {
		var response = send("GET", "/fhir/Basic/playground-demo", TOKENS.valid());
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("X-Playground-Decision")).contains("allowed");
		assertThat(response.headers().firstValue("X-Playground-Reason")).contains("demo-read-allowed");
		JsonNode resource = JSON.readTree(response.body());
		assertThat(resource.path("resourceType").asText()).isEqualTo("Basic");
		assertThat(resource.path("id").asText()).isEqualTo("playground-demo");
		var names = new ArrayList<String>();
		resource.fieldNames().forEachRemaining(names::add);
		assertThat(Set.copyOf(names)).isSubsetOf(Set.of("resourceType", "id", "meta", "code"));
		assertThat(resource.path("code").path("text").asText()).contains("Synthetic non-clinical");
		assertThat(response.headers().allValues("set-cookie").isEmpty()).isTrue();
	}

	@Test
	void missingCredentialsAndPermissionHaveDistinctDenials() throws Exception {
		denial(send("GET", "/fhir/Basic/playground-demo", null), 401, "missing-token");
		denial(
				send("GET", "/fhir/Basic/playground-demo", TOKENS.token(claims -> claims.claim("scope", "openid"))),
				403,
				"insufficient-permission");
	}

	@ParameterizedTest
	@ValueSource(
			strings = {
				"malformed",
				"signature",
				"unknown-key",
				"algorithm",
				"hmac",
				"unsigned",
				"issuer",
				"audience",
				"missing-audience",
				"epa",
				"expired",
				"future",
				"missing-expiry",
				"id-token",
				"missing-purpose",
				"invalid-scope"
			})
	void realServletRejectsInvalidSignedTokensWithoutDisclosingClaims(String variant) throws Exception {
		denial(send("GET", "/fhir/Basic/playground-demo", TOKENS.invalid(variant)), 401, "invalid-token");
	}

	@ParameterizedTest
	@CsvSource({
		"HEAD,/fhir/Basic/playground-demo",
		"POST,/fhir/Basic/playground-demo",
		"PUT,/fhir/Basic/playground-demo",
		"PATCH,/fhir/Basic/playground-demo",
		"DELETE,/fhir/Basic/playground-demo",
		"OPTIONS,/fhir/Basic/playground-demo",
		"GET,/fhir/Patient",
		"GET,/fhir/Basic/other",
		"GET,/fhir/Basic",
		"GET,/fhir/metadata",
		"GET,/fhir/Basic/playground-demo/_history",
		"GET,/fhir/Basic/playground-demo/_history/1",
		"POST,/fhir",
		"POST,/fhir/Basic/_search",
		"GET,/fhir/_history",
		"GET,/fhir/$graphql",
		"GET,/fhir/.well-known/smart-configuration",
		"GET,/.well-known/smart-configuration",
		"GET,/fhir/Basic/playground-demo?&_include=*",
		"GET,/fhir/$export",
		"GET,/mcp/messages",
		"POST,/mcp/messages",
		"GET,/sse",
		"GET,/cds-services",
		"POST,/cds-services/test",
		"GET,/fhir/swagger-ui/index.html",
		"GET,/fhir/api-docs",
		"GET,/actuator/health",
		"GET,/actuator/env",
		"GET,/actuator/health/readiness/",
		"GET,/",
		"GET,/error"
	})
	void alternativeRoutesCannotBypassTheSecurityFilter(String method, String path) throws Exception {
		denial(send(method, path, TOKENS.valid()), 403, "operation-not-allowed");
	}

	@ParameterizedTest
	@ValueSource(strings = {"batch", "transaction"})
	void fhirBundlesCannotTunnelAnOtherwiseAllowedRead(String type) throws Exception {
		String body = "{\"resourceType\":\"Bundle\",\"type\":\"" + type
				+ "\",\"entry\":[{\"request\":{\"method\":\"GET\",\"url\":\"Basic/playground-demo\"}}]}";
		String token = TOKENS.valid();
		SECRETS.add(token);
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fhir"))
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/fhir+json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		denial(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()), 403, "operation-not-allowed");
	}

	@ParameterizedTest
	@ValueSource(
			strings = {
				"/fhir//Basic/playground-demo",
				"/fhir/Basic/./playground-demo",
				"/fhir/Basic%2fplayground-demo",
				"/fhir/Basic/playground-demo;ignored",
				"/fhir/Basic/%70layground-demo"
			})
	void nonCanonicalPathsDoNotAuthorizeTheRead(String path) throws Exception {
		var response = send("GET", path, TOKENS.valid());
		assertThat(response.statusCode()).isIn(400, 403);
	}

	@ParameterizedTest
	@CsvSource({
		"GET,/actuator/health/liveness",
		"HEAD,/actuator/health/liveness",
		"GET,/actuator/health/readiness",
		"HEAD,/actuator/health/readiness"
	})
	void realHealthEndpointsRemainStatusOnlyAndIgnoreMalformedBearer(String method, String path) throws Exception {
		var response = send(method, path, "invalid token");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("X-Playground-Decision")).isEmpty();
		if (method.equals("GET")) {
			assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
		}
	}

	@Test
	void callerControlledHeadersAndBodiesNeverBecomeRoutineLogEvidence() throws Exception {
		String sentinel = "fictional-sensitive-request-content";
		SECRETS.add(sentinel);
		String token = TOKENS.valid();
		SECRETS.add(token);
		var read = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fhir/Basic/playground-demo"))
				.header("Authorization", "Bearer " + token)
				.header("User-Agent", sentinel)
				.header("Cookie", "fictional_cookie=" + sentinel)
				.GET()
				.build();
		assertThat(CLIENT.send(read, HttpResponse.BodyHandlers.ofString()).statusCode())
				.isEqualTo(200);
		var write = HttpRequest.newBuilder(read.uri())
				.header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofString(sentinel))
				.build();
		denial(CLIENT.send(write, HttpResponse.BodyHandlers.ofString()), 403, "operation-not-allowed");
	}
}
