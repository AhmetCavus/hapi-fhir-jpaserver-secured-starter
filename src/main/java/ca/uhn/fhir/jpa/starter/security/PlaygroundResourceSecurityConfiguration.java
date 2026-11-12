package ca.uhn.fhir.jpa.starter.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collections;

@Configuration(proxyBeanMethods = false)
public class PlaygroundResourceSecurityConfiguration {
	static final String DEMO_PATH = "/fhir/Basic/playground-demo";
	static final String READ_AUTHORITY = "SCOPE_demo.fhir.read";
	static final String DECISION_HEADER = "X-Playground-Decision";
	static final String REASON_HEADER = "X-Playground-Reason";

	@Bean
	@Order(2)
	SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
		// This chain accepts only bearer credentials, never application cookies or form login.
		return http.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable)
				.requestCache(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(
						requests -> requests.requestMatchers(PlaygroundResourceSecurityConfiguration::isDemoRead)
								.hasAuthority(READ_AUTHORITY)
								.anyRequest()
								.denyAll())
				.exceptionHandling(errors -> errors.authenticationEntryPoint(
								(request, response, exception) -> unauthorized(request, response))
						.accessDeniedHandler((request, response, exception) -> forbidden(request, response)))
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint((request, response, exception) -> unauthorized(request, response))
						.accessDeniedHandler((request, response, exception) -> forbidden(request, response)))
				.headers(headers -> headers.addHeaderWriter((request, response) -> {
					var authentication = SecurityContextHolder.getContext().getAuthentication();
					if (isDemoRead(request)
							&& response.getStatus() == 200
							&& authentication instanceof JwtAuthenticationToken
							&& authentication.getAuthorities().stream()
									.anyMatch(authority -> READ_AUTHORITY.equals(authority.getAuthority()))) {
						report(response, "allowed", Reason.DEMO_READ_ALLOWED);
					}
				}))
				.build();
	}

	@Bean
	WebSecurityCustomizer safeRejectedRequests() {
		return web -> web.requestRejectedHandler((request, response, exception) -> {
			response.setStatus(400);
			report(response, "denied", Reason.OPERATION_NOT_ALLOWED);
		});
	}

	static boolean isDemoRead(HttpServletRequest request) {
		return "GET".equals(request.getMethod())
				&& (request.getContextPath() + DEMO_PATH).equals(request.getRequestURI())
				&& request.getQueryString() == null;
	}

	private static void unauthorized(HttpServletRequest request, HttpServletResponse response) {
		response.setStatus(401);
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		boolean missing =
				Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION)).isEmpty();
		report(response, "denied", missing ? Reason.MISSING_TOKEN : Reason.INVALID_TOKEN);
	}

	private static void forbidden(HttpServletRequest request, HttpServletResponse response) {
		response.setStatus(403);
		report(response, "denied", isDemoRead(request) ? Reason.INSUFFICIENT_PERMISSION : Reason.OPERATION_NOT_ALLOWED);
	}

	private static void report(HttpServletResponse response, String decision, Reason reason) {
		response.setHeader(DECISION_HEADER, decision);
		response.setHeader(REASON_HEADER, reason.value);
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
	}

	private enum Reason {
		DEMO_READ_ALLOWED("demo-read-allowed"),
		MISSING_TOKEN("missing-token"),
		INVALID_TOKEN("invalid-token"),
		INSUFFICIENT_PERMISSION("insufficient-permission"),
		OPERATION_NOT_ALLOWED("operation-not-allowed");

		private final String value;

		Reason(String value) {
			this.value = value;
		}
	}
}
