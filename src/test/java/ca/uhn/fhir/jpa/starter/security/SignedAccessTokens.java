package ca.uhn.fhir.jpa.starter.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

/** Real cryptography and a local trusted JWKS endpoint, shared by focused and full-server tests. */
final class SignedAccessTokens implements AutoCloseable {
	private final RSAKey trusted;
	private final RSAKey forged;
	private final HttpServer server;
	final String issuer;
	final String jwksUri;

	SignedAccessTokens() throws Exception {
		trusted = new RSAKeyGenerator(2048).keyID("trusted-key").generate();
		forged = new RSAKeyGenerator(2048).keyID("trusted-key").generate();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/test";
		jwksUri = issuer + "/certs";
		server.createContext("/realms/test/certs", exchange -> {
			byte[] json = new JWKSet(trusted.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, json.length);
			exchange.getResponseBody().write(json);
			exchange.close();
		});
		server.start();
	}

	String valid() throws Exception {
		return token(claims -> {});
	}

	String token(Consumer<JWTClaimsSet.Builder> changes) throws Exception {
		return sign(claims(changes), trusted, JWSAlgorithm.RS256, "trusted-key");
	}

	String invalid(String variant) throws Exception {
		if (variant.equals("malformed")) {
			return "fictional-invalid.jwt";
		}
		JWTClaimsSet claims = claims(builder -> {
			switch (variant) {
				case "issuer" -> builder.issuer("https://untrusted.example/realm");
				case "audience", "epa" -> builder.audience("epa-auth-server");
				case "missing-audience" -> builder.claim("aud", null);
				case "expired" -> builder.expirationTime(Date.from(Instant.now().minusSeconds(120)));
				case "future" -> builder.notBeforeTime(Date.from(Instant.now().plusSeconds(120)));
				case "missing-expiry" -> builder.expirationTime(null);
				case "id-token" -> builder.claim("typ", "ID");
				case "missing-purpose" -> builder.claim("typ", null);
				case "invalid-scope" -> builder.claim("scope", java.util.List.of("demo.fhir.read"));
				default -> {}
			}
		});
		if (variant.equals("unsigned")) {
			return new PlainJWT(claims).serialize();
		}
		if (variant.equals("hmac")) {
			var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			jwt.sign(new MACSigner(new byte[32]));
			return jwt.serialize();
		}
		return sign(
				claims,
				variant.equals("signature") ? forged : trusted,
				variant.equals("algorithm") ? JWSAlgorithm.RS512 : JWSAlgorithm.RS256,
				variant.equals("unknown-key") ? "unknown-key" : "trusted-key");
	}

	private JWTClaimsSet claims(Consumer<JWTClaimsSet.Builder> changes) {
		var now = Instant.now();
		var claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject("fictional-resource-owner")
				.audience("playground-fhir")
				.issueTime(Date.from(now))
				.notBeforeTime(Date.from(now.minusSeconds(1)))
				.expirationTime(Date.from(now.plusSeconds(300)))
				.claim("typ", "Bearer")
				.claim("scope", "openid demo.fhir.read");
		changes.accept(claims);
		return claims.build();
	}

	private String sign(JWTClaimsSet claims, RSAKey key, JWSAlgorithm algorithm, String keyId) throws Exception {
		var jwt = new SignedJWT(
				new JWSHeader.Builder(algorithm)
						.keyID(keyId)
						.type(JOSEObjectType.JWT)
						.build(),
				claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
