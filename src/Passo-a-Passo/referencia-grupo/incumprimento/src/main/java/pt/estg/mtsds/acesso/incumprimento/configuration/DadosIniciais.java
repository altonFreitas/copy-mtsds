package pt.estg.mtsds.acesso.incumprimento.configuration;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.incumprimento.data.InstituicoesRepository;
import pt.estg.mtsds.acesso.incumprimento.models.Instituicao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carrega as instituições de destino simuladas no arranque, se a tabela estiver
 * vazia. {@code CommandLineRunner} é executado pelo Spring Boot logo depois de
 * a aplicação arrancar.
 */
@Component
@RequiredArgsConstructor
public class DadosIniciais implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DadosIniciais.class);

	private final InstituicoesRepository instituicoesRepository;

	@Override
	public void run(String... args) {
		if (instituicoesRepository.count() > 0) {
			return;
		}
		instituicoesRepository.saveAll(List.of(new Instituicao("Hospital Simulado Norte", "CARDIOLOGIA"),
				new Instituicao("Hospital Simulado Sul", "CARDIOLOGIA"),
				new Instituicao("Hospital Simulado Norte", "ORTOPEDIA"),
				new Instituicao("Clínica Simulada Centro", "ORTOPEDIA"),
				new Instituicao("Clínica Simulada Centro", "OFTALMOLOGIA"),
				new Instituicao("Hospital Simulado Sul", "UROLOGIA")));
		log.info("Instituições simuladas criadas: {}", instituicoesRepository.count());
	}
}
