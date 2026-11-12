package ca.uhn.fhir.jpa.starter.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.Basic;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

/** Explicit local setup through HAPI's DAO; this exposes no write or seed HTTP route. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "playground.demo.seed", havingValue = "true")
public class PlaygroundDemoResourceConfiguration {
	@Bean
	ApplicationRunner playgroundDemoResourceSeeder(FhirContext context, DaoRegistry registry) {
		return arguments -> {
			if (context.getVersion().getVersion() != FhirVersionEnum.R4) {
				throw new IllegalStateException("The Playground fixture requires FHIR R4");
			}
			String json = new ClassPathResource("demo/playground_demo.json").getContentAsString(StandardCharsets.UTF_8);
			Basic fixture = context.newJsonParser().parseResource(Basic.class, json);
			fixture.setId("Basic/playground-demo");
			seed(registry.getResourceDao(Basic.class), fixture);
		};
	}

	static void seed(IFhirResourceDao<Basic> dao, Basic fixture) {
		var request = new SystemRequestDetails();
		Basic existing;
		try {
			existing = dao.read(new IdType("Basic", "playground-demo"), request);
		} catch (ResourceNotFoundException notFound) {
			dao.update(fixture, request);
			return;
		}
		Basic comparison = existing.copy();
		comparison.setId("Basic/playground-demo");
		comparison.setMeta(null);
		if (!comparison.equalsDeep(fixture)) {
			throw new IllegalStateException(
					"Playground fixture conflicts with an existing resource; refusing to overwrite");
		}
	}
}
