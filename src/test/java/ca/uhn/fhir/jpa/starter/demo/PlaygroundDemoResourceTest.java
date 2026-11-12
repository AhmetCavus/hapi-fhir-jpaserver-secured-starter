package ca.uhn.fhir.jpa.starter.demo;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.Basic;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaygroundDemoResourceTest {
	private Basic fixture() throws Exception {
		String json = new ClassPathResource("demo/playground_demo.json").getContentAsString(StandardCharsets.UTF_8);
		Basic fixture = FhirContext.forR4Cached().newJsonParser().parseResource(Basic.class, json);
		fixture.setId("Basic/playground-demo");
		assertThat(fixture.hasSubject()).isFalse();
		assertThat(fixture.hasAuthor()).isFalse();
		assertThat(fixture.getCode().getCodingFirstRep().getSystem())
				.isEqualTo("https://example.invalid/playground/demo");
		assertThat(fixture.getCode().getText()).contains("Synthetic non-clinical");
		return fixture;
	}

	@SuppressWarnings("unchecked")
	private IFhirResourceDao<Basic> dao() {
		return mock(IFhirResourceDao.class);
	}

	@Test
	void seedsThroughTheInternalDaoOnlyWhenAbsent() throws Exception {
		var dao = dao();
		when(dao.read(any(IdType.class), any(SystemRequestDetails.class)))
				.thenThrow(new ResourceNotFoundException("missing"));
		Basic fixture = fixture();
		PlaygroundDemoResourceConfiguration.seed(dao, fixture);
		verify(dao).update(any(Basic.class), any(SystemRequestDetails.class));
	}

	@Test
	void repeatedSeedingDoesNotCreateAnotherVersion() throws Exception {
		var dao = dao();
		var existing = fixture();
		existing.setId("Basic/playground-demo/_history/1");
		existing.getMeta().setVersionId("1");
		when(dao.read(any(IdType.class), any(SystemRequestDetails.class))).thenReturn(existing);
		PlaygroundDemoResourceConfiguration.seed(dao, fixture());
		verify(dao, never()).update(any(Basic.class), any(SystemRequestDetails.class));
	}

	@Test
	void refusesToOverwriteConflictingExistingData() throws Exception {
		var dao = dao();
		Basic existing = fixture();
		existing.getCode().setText("different existing data");
		when(dao.read(any(IdType.class), any(SystemRequestDetails.class))).thenReturn(existing);
		assertThatThrownBy(() -> PlaygroundDemoResourceConfiguration.seed(dao, fixture()))
				.isInstanceOf(IllegalStateException.class);
		verify(dao, never()).update(any(Basic.class), any(SystemRequestDetails.class));
	}
}
