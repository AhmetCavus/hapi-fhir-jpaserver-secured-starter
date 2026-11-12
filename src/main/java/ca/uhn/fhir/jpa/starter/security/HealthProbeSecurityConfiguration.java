package ca.uhn.fhir.jpa.starter.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/** Keeps the two data-free probes independent of bearer authentication. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class HealthProbeSecurityConfiguration {

	private static final String LIVENESS_PATH = "/actuator/health/liveness";
	private static final String READINESS_PATH = "/actuator/health/readiness";

	@Bean
	@Order(1)
	SecurityFilterChain healthProbeSecurityFilterChain(HttpSecurity http) throws Exception {
		var paths = PathPatternRequestMatcher.withDefaults();
		var probes = new OrRequestMatcher(
				paths.matcher(HttpMethod.GET, LIVENESS_PATH),
				paths.matcher(HttpMethod.HEAD, LIVENESS_PATH),
				paths.matcher(HttpMethod.GET, READINESS_PATH),
				paths.matcher(HttpMethod.HEAD, READINESS_PATH));

		// A stale or malformed bearer header must not prevent a data-free probe.
		// JWT filters belong only to the authenticated application chain below.
		return http.securityMatcher(probes)
				.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
				.build();
	}
}
