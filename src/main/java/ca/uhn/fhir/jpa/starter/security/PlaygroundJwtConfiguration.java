package ca.uhn.fhir.jpa.starter.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResourceServerTrustProperties.class)
public class PlaygroundJwtConfiguration {
	@Bean
	JwtDecoder jwtDecoder(ResourceServerTrustProperties trust, Environment environment) {
		boolean allowPrivateHttp =
				environment.getProperty("playground.security.allow-private-http", Boolean.class, false);
		URI issuer = trustedUri(trust.issuerUri(), allowPrivateHttp);
		URI jwks = trustedUri(trust.jwkSetUri(), allowPrivateHttp);
		if (!issuer.getScheme().equals(jwks.getScheme())
				|| !issuer.getRawAuthority().equals(jwks.getRawAuthority())) {
			throw ResourceServerTrustProperties.invalidConfiguration();
		}

		var client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		var requests = new JdkClientHttpRequestFactory(client);
		requests.setReadTimeout(Duration.ofSeconds(5));
		var decoder = NimbusJwtDecoder.withJwkSetUri(trust.jwkSetUri())
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.restOperations(new RestTemplate(requests))
				.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(Duration.ZERO),
				new JwtIssuerValidator(trust.issuerUri()),
				PlaygroundJwtConfiguration::accessTokenClaims));
		return decoder;
	}

	private static OAuth2TokenValidatorResult accessTokenClaims(Jwt jwt) {
		Object scope = jwt.getClaims().get("scope");
		if (jwt.getExpiresAt() == null
				|| jwt.getAudience() == null
				|| !jwt.getAudience().contains(ResourceServerTrustProperties.RESOURCE_AUDIENCE)
				|| !"Bearer".equals(jwt.getClaims().get("typ"))
				|| (scope != null && !(scope instanceof String))) {
			return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access Token rejected", null));
		}
		return OAuth2TokenValidatorResult.success();
	}

	private static URI trustedUri(String value, boolean allowPrivateHttp) {
		final URI uri;
		try {
			uri = URI.create(value);
		} catch (IllegalArgumentException exception) {
			throw ResourceServerTrustProperties.invalidConfiguration();
		}
		String host = uri.getHost();
		if (host == null
				|| (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))
				|| uri.getPort() > 65535
				|| uri.getRawUserInfo() != null
				|| uri.getRawQuery() != null
				|| uri.getRawFragment() != null
				|| uri.getPath() == null
				|| uri.getPath().isBlank()
				|| !uri.normalize().equals(uri)) {
			throw ResourceServerTrustProperties.invalidConfiguration();
		}
		boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
		boolean privateAddress = host.matches("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")
				|| host.matches("192\\.168\\.\\d{1,3}\\.\\d{1,3}")
				|| host.matches("172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3}");
		if (!uri.getScheme().equals("https")
				&& !(uri.getScheme().equals("http") && (loopback || (allowPrivateHttp && privateAddress)))) {
			throw ResourceServerTrustProperties.invalidConfiguration();
		}
		return uri;
	}
}
