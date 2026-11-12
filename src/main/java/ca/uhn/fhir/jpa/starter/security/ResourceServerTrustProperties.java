package ca.uhn.fhir.jpa.starter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Trust is configured by the server operator, never by an incoming token. */
@ConfigurationProperties("spring.security.oauth2.resourceserver.jwt")
public record ResourceServerTrustProperties(String issuerUri, String jwkSetUri, List<String> audiences) {
	public static final String RESOURCE_AUDIENCE = "playground-fhir";

	public ResourceServerTrustProperties {
		if (issuerUri == null
				|| issuerUri.isBlank()
				|| jwkSetUri == null
				|| jwkSetUri.isBlank()
				|| !List.of(RESOURCE_AUDIENCE).equals(audiences)) {
			throw invalidConfiguration();
		}
		audiences = List.copyOf(audiences);
	}

	static IllegalArgumentException invalidConfiguration() {
		return new IllegalArgumentException(
				"Resource Server issuer, JWKS and playground-fhir audience must be configured safely");
	}
}
