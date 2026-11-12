package ca.uhn.fhir.jpa.starter.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(HealthProbeSecurityConfigurationTest.TestConfiguration.class)
@WebAppConfiguration
class HealthProbeSecurityConfigurationTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JwtDecoder decoder;

    MockMvc mvc;

    @BeforeEach
    void configureRequests() {
        reset(decoder);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
    }

    @ParameterizedTest
    @CsvSource({
        "GET, /actuator/health/liveness",
        "HEAD, /actuator/health/liveness",
        "GET, /actuator/health/readiness",
        "HEAD, /actuator/health/readiness"
    })
    void exactReadOnlyProbesAllowAnonymousRequests(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isOk());
        verifyNoInteractions(decoder);
    }

    @ParameterizedTest
    @MethodSource("probeRequestsWithInvalidBearerHeaders")
    void probeRequestsDoNotEnterJwtAuthentication(String method, String path, String authorization)
            throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk());
        verifyNoInteractions(decoder);
    }

    static Stream<Arguments> probeRequestsWithInvalidBearerHeaders() {
        return Stream.of("GET", "HEAD")
                .flatMap(method -> Stream.of("/actuator/health/liveness", "/actuator/health/readiness")
                        .flatMap(path -> Stream.of("Bearer invalid.jwt", "Bearer invalid token", "Bearer")
                                .map(header -> Arguments.of(method, path, header))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/actuator/health",
        "/actuator/health/readiness/",
        "/actuator/health/readiness/details",
        "/actuator/health/liveness-extra",
        "/actuator/env",
        "/fhir/metadata",
        "/fhir/Patient",
        "/fhir/Basic/playground-demo",
        "/mcp/messages",
        "/application-probe"
    })
    void everyOtherPathStillRequiresAuthentication(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        verifyNoInteractions(decoder);
    }

    @ParameterizedTest
    @CsvSource({
        "POST, /actuator/health/liveness",
        "POST, /actuator/health/readiness",
        "POST, /fhir/Basic/playground-demo"
    })
    void healthPermissionDoesNotPermitAnonymousWrites(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isForbidden());
        verifyNoInteractions(decoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/fhir/Basic/playground-demo", "/application-probe"})
    void protectedRequestsStillInvokeJwtValidation(String path) throws Exception {
        var now = Instant.now();
        var jwt = Jwt.withTokenValue("accepted-test-token")
                .header("alg", "RS256")
                .subject("synthetic-test-user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        when(decoder.decode("accepted-test-token")).thenReturn(jwt);

        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer accepted-test-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"synthetic\":true}"));
        verify(decoder).decode("accepted-test-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/fhir/Basic/playground-demo", "/application-probe"})
    void rejectedTokensCannotAccessProtectedEndpoints(String path) throws Exception {
        when(decoder.decode("rejected-test-token")).thenThrow(new BadJwtException("Rejected test token"));

        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer rejected-test-token"))
                .andExpect(status().isUnauthorized());
        verify(decoder).decode("rejected-test-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer invalid token", "Bearer"})
    void malformedBearerHeadersRemainRejectedOutsideProbes(String authorization) throws Exception {
        mvc.perform(get("/fhir/metadata").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(decoder);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(HealthProbeSecurityConfiguration.class)
    static class TestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/actuator/health/liveness", "/actuator/health/readiness"})
        Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @GetMapping({"/fhir/Basic/playground-demo", "/application-probe"})
        Map<String, Boolean> protectedContent() {
            return Map.of("synthetic", true);
        }
    }
}
