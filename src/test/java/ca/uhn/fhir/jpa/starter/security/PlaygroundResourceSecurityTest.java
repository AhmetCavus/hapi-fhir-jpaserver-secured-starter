package ca.uhn.fhir.jpa.starter.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaygroundResourceSecurityTest {
	static SignedAccessTokens tokens;
	static AnnotationConfigWebApplicationContext context;
	static MockMvc mvc;

	@BeforeAll
	static void start() throws Exception {
		tokens = new SignedAccessTokens();
		context = new AnnotationConfigWebApplicationContext();
		context.setServletContext(new MockServletContext());
		context.getEnvironment()
				.getPropertySources()
				.addFirst(new MapPropertySource(
						"trusted-test-issuer",
						Map.of(
								"spring.security.oauth2.resourceserver.jwt.issuer-uri", tokens.issuer,
								"spring.security.oauth2.resourceserver.jwt.jwk-set-uri", tokens.jwksUri,
								"spring.security.oauth2.resourceserver.jwt.audiences", "playground-fhir")));
		context.register(TestConfiguration.class);
		context.refresh();
		mvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();
	}

	@AfterAll
	static void stop() {
		if (context != null) {
			context.close();
		}
		if (tokens != null) {
			tokens.close();
		}
	}

	@Test
	void realSignedAccessTokenWithPermissionReadsOnlyTheDemo() throws Exception {
		mvc.perform(get(PlaygroundResourceSecurityConfiguration.DEMO_PATH)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.valid()))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"synthetic\":true}"))
				.andExpect(header().string("X-Playground-Decision", "allowed"))
				.andExpect(header().string("X-Playground-Reason", "demo-read-allowed"))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
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
	void actualDecoderRejectsUnsuitableCredentials(String variant) throws Exception {
		mvc.perform(get(PlaygroundResourceSecurityConfiguration.DEMO_PATH)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.invalid(variant)))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string(""))
				.andExpect(header().string("X-Playground-Decision", "denied"))
				.andExpect(header().string("X-Playground-Reason", "invalid-token"))
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
	}

	@Test
	void cookieAndTokenQueryCannotSubstituteForBearerAuthentication() throws Exception {
		mvc.perform(get(PlaygroundResourceSecurityConfiguration.DEMO_PATH)
						.header(
								HttpHeaders.COOKIE,
								"playground_context=fictional-context; epa_session=fictional-session"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string(""))
				.andExpect(header().string("X-Playground-Reason", "missing-token"));
		mvc.perform(get(PlaygroundResourceSecurityConfiguration.DEMO_PATH).queryParam("access_token", tokens.valid()))
				.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "openid", "demo.fhir.reader"})
	void validTokenWithoutExactPermissionIsForbidden(String scope) throws Exception {
		mvc.perform(get(PlaygroundResourceSecurityConfiguration.DEMO_PATH)
						.header(
								HttpHeaders.AUTHORIZATION,
								"Bearer " + tokens.token(claims -> claims.claim("scope", scope))))
				.andExpect(status().isForbidden())
				.andExpect(content().string(""))
				.andExpect(header().string("X-Playground-Reason", "insufficient-permission"));
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
		"GET,/fhir/Patient/fictional",
		"GET,/fhir/Basic/other",
		"GET,/fhir/Basic",
		"GET,/fhir/Basic/playground-demo/",
		"GET,/fhir/Basic/playground-demo/_history",
		"GET,/fhir/Basic/playground-demo/_history/1",
		"GET,/fhir/metadata",
		"POST,/fhir",
		"GET,/fhir/$export",
		"GET,/fhir/Basic/playground-demo/$everything",
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
		"GET,/error",
		"POST,/actuator/health/readiness"
	})
	void validCredentialsCannotAccessAnyOtherOperation(String method, String path) throws Exception {
		mvc.perform(request(HttpMethod.valueOf(method), path)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.valid()))
				.andExpect(status().isForbidden())
				.andExpect(content().string(""))
				.andExpect(header().string("X-Playground-Reason", "operation-not-allowed"));
	}

	@Test
	void queryBasedAlternateRepresentationsAreNotDeclaredOperations() throws Exception {
		mvc.perform(get(PlaygroundResourceSecurityConfiguration.DEMO_PATH)
						.queryParam("_include", "*")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.valid()))
				.andExpect(status().isForbidden())
				.andExpect(header().string("X-Playground-Reason", "operation-not-allowed"));
	}

	@ParameterizedTest
	@CsvSource({
		"GET,/actuator/health/liveness",
		"HEAD,/actuator/health/liveness",
		"GET,/actuator/health/readiness",
		"HEAD,/actuator/health/readiness"
	})
	void probesAreIndependentOfInvalidBearerCredentials(String method, String path) throws Exception {
		mvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid token"))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist("X-Playground-Decision"));
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	@Import({
		HealthProbeSecurityConfiguration.class,
		PlaygroundResourceSecurityConfiguration.class,
		PlaygroundJwtConfiguration.class
	})
	static class TestConfiguration {
		@Bean
		DemoController demoController() {
			return new DemoController();
		}
	}

	@RestController
	static class DemoController {
		@GetMapping("/fhir/Basic/playground-demo")
		Map<String, Boolean> demo() {
			return Map.of("synthetic", true);
		}

		@GetMapping({"/actuator/health/liveness", "/actuator/health/readiness"})
		Map<String, String> health() {
			return Map.of("status", "UP");
		}
	}
}
