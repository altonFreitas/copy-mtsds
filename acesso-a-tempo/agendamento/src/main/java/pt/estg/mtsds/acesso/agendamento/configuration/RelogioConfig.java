package pt.estg.mtsds.acesso.agendamento.configuration;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio injetado em vez de LocalDate.now(): os testes usam um relógio fixo e
 * o tmrg pode avançar o tempo no perfil de demonstração (RNF07).
 */
@Configuration
public class RelogioConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
