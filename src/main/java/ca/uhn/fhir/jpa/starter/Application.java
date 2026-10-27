package ca.uhn.fhir.jpa.starter;

import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.jpa.starter.annotations.OnEitherVersion;
import ca.uhn.fhir.jpa.starter.cdshooks.StarterCdsHooksConfig;
import ca.uhn.fhir.jpa.starter.cr.StarterCrDstu3Config;
import ca.uhn.fhir.jpa.starter.cr.StarterCrR4Config;
import ca.uhn.fhir.jpa.starter.mdm.MdmConfig;
import ca.uhn.fhir.jpa.subscription.channel.config.SubscriptionChannelConfig;
import ca.uhn.fhir.jpa.subscription.match.config.SubscriptionProcessorConfig;
import ca.uhn.fhir.jpa.subscription.match.config.WebsocketDispatcherConfig;
import ca.uhn.fhir.jpa.subscription.submit.config.SubscriptionSubmitterConfig;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;

@ServletComponentScan(basePackageClasses = {RestfulServer.class})
@SpringBootApplication(exclude = {ThymeleafAutoConfiguration.class, QuartzAutoConfiguration.class})
@Import({
	StarterCrR4Config.class,
	StarterCrDstu3Config.class,
	StarterCdsHooksConfig.class,
	SubscriptionSubmitterConfig.class,
	SubscriptionProcessorConfig.class,
	SubscriptionChannelConfig.class,
	WebsocketDispatcherConfig.class,
	MdmConfig.class,
	JpaBatch2Config.class,
	Batch2JobsConfig.class
})
public class Application extends SpringBootServletInitializer {

	private static final Logger ourLog = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) {
		ServletWebServerApplicationContext context =
				(ServletWebServerApplicationContext) SpringApplication.run(Application.class, args);
		ServerProperties serverProperties = context.getBean(ServerProperties.class);
		String scheme = Ssl.isEnabled(serverProperties.getSsl()) ? "https" : "http";
		String contextPath = context.getServletContext().getContextPath();
		String baseUrl = scheme + "://localhost:" + context.getWebServer().getPort() + contextPath;

		ourLog.info("\n\nStarted successfully\n- {}\n- {}/fhir/metadata\n", baseUrl, baseUrl);
	}

	@Autowired
	AutowireCapableBeanFactory beanFactory;

	@Bean
	@Conditional(OnEitherVersion.class)
	public ServletRegistrationBean hapiServletRegistration(RestfulServer restfulServer) {
		ServletRegistrationBean servletRegistrationBean = new ServletRegistrationBean();
		beanFactory.autowireBean(restfulServer);
		servletRegistrationBean.setServlet(restfulServer);
		servletRegistrationBean.addUrlMappings("/fhir/*");
		servletRegistrationBean.setLoadOnStartup(1);

		return servletRegistrationBean;
	}
}
