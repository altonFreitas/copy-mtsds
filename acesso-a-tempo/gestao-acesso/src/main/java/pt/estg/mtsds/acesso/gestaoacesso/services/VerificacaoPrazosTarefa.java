package pt.estg.mtsds.acesso.gestaoacesso.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Tarefa agendada que corre a verificação dos prazos todos os dias à 01:00,
 * hora de Lisboa.
 */
@Component
@RequiredArgsConstructor
public class VerificacaoPrazosTarefa {

	private static final Logger log = LoggerFactory.getLogger(VerificacaoPrazosTarefa.class);

	private final VerificacaoPrazosService verificacaoPrazosService;

	/**
	 * Corre a verificação diária e regista quantos episódios mudaram de situação. O
	 * fuso é fixo, para a hora não depender do fuso do servidor ou do contentor.
	 */
	@Scheduled(cron = "0 0 1 * * *", zone = "Europe/Lisbon")
	public void verificarDiariamente() {
		ResultadoVerificacao resultado = verificacaoPrazosService.verificar();
		log.info("Verificação de prazos concluída: {} episódios passaram a EM_RISCO e {} a INCUMPRIDO.",
				resultado.passaramEmRisco().size(), resultado.passaramIncumpridos().size());
	}
}
