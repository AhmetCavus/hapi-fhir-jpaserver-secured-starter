package ca.uhn.fhir.jpa.starter.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceServerTrustTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PlaygroundJwtConfiguration.class)
			.withPropertyValues(
					"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example/realms/demo",
					"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.example/realms/demo/certs",
					"spring.security.oauth2.resourceserver.jwt.audiences=playground-fhir");

	@Test
	void requiredTrustBindsWithoutContactingAnIssuerAtStartup() {
		runner.run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
	}

	@Test
	void noPlaceholderFlagCanDisableRequiredConfiguration() {
		new ApplicationContextRunner()
				.withUserConfiguration(PlaygroundJwtConfiguration.class)
				.withPropertyValues("spring.security.oauth2.enable=false")
				.run(context -> assertThat(context).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(
			strings = {
				"issuer-uri=",
				"jwk-set-uri=",
				"audiences=",
				"audiences=account",
				"audiences=playground-fhir,account",
				"issuer-uri=not-a-uri",
				"issuer-uri=//issuer.example/realm",
				"issuer-uri=ftp://issuer.example/realm",
				"issuer-uri=http://issuer.example/realm",
				"issuer-uri=https://issuer.example/realm?token=fictional-sentinel",
				"jwk-set-uri=https://attacker.example/keys",
				"jwk-set-uri=https://user:fictional-password@issuer.example/keys",
				"jwk-set-uri=https://issuer.example/keys#fragment",
				"jwk-set-uri=https://issuer.example/a/../keys",
				"jwk-set-uri=https://issuer.example:99999/keys"
			})
	void rejectsMissingOrUnsafeTrust(String setting) {
		runner.withPropertyValues("spring.security.oauth2.resourceserver.jwt." + setting)
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void privateHttpRequiresAnExplicitOptInAndMatchingOrigin() {
		var privateRunner = runner.withPropertyValues(
				"spring.security.oauth2.resourceserver.jwt.issuer-uri=http://192.168.178.131:5555/realms/epa-demo",
				"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://192.168.178.131:5555/realms/epa-demo/certs");
		privateRunner.run(context -> assertThat(context).hasFailed());
		privateRunner
				.withPropertyValues("playground.security.allow-private-http=true")
				.run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
	}
}
