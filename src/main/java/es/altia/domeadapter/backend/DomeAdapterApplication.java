package es.altia.domeadapter.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DomeAdapterApplication {

	public static void main(String[] args) {
		SpringApplication.run(DomeAdapterApplication.class, args);
	}

}
