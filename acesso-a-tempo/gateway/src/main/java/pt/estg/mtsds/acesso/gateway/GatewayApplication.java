package pt.estg.mtsds.acesso.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada único do sistema: encaminha os pedidos para os serviços
 * registados no Eureka, depois de validar o traceId e o cabeçalho X-Utilizador.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}
}
