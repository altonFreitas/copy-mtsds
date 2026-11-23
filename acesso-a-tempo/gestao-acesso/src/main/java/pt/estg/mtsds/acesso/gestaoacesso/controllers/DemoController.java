package pt.estg.mtsds.acesso.gestaoacesso.controllers;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pt.estg.mtsds.acesso.gestaoacesso.dtos.AvancarRelogioDTO;
import pt.estg.mtsds.acesso.gestaoacesso.dtos.RelogioAvancadoDTO;
import pt.estg.mtsds.acesso.gestaoacesso.dtos.RelogioDTO;
import pt.estg.mtsds.acesso.gestaoacesso.services.RelogioService;
import pt.estg.mtsds.acesso.gestaoacesso.services.ResultadoVerificacao;
import pt.estg.mtsds.acesso.gestaoacesso.services.VerificacaoPrazosService;

/**
 * Relógio de demonstração: permite avançar o tempo para ver os prazos a mudar
 * de situação sem esperar dias reais. Só existe com o perfil "demo".
 */
@Slf4j
@Profile("demo")
@RestController
@RequestMapping("/demo/relogio")
@RequiredArgsConstructor
public class DemoController {

	private final RelogioService relogioService;
	private final VerificacaoPrazosService verificacaoPrazosService;

	/**
	 * Avança o relógio e corre logo a verificação dos prazos para a nova data.
	 *
	 * @param pedido
	 *            data para onde o relógio passa
	 * @return a nova data e os pedidos que passaram a EM_RISCO e a INCUMPRIDO
	 */
	@PutMapping
	public ResponseEntity<RelogioAvancadoDTO> avancar(@Valid @RequestBody AvancarRelogioDTO pedido) {
		relogioService.avancarPara(pedido.data());
		ResultadoVerificacao resultado = verificacaoPrazosService.verificar();
		log.info("Relógio avançado para {}", pedido.data());
		return ResponseEntity.ok(new RelogioAvancadoDTO(relogioService.hoje(), resultado.passaramEmRisco(),
				resultado.passaramIncumpridos()));
	}

	/**
	 * Repõe o relógio, para o serviço voltar a usar a data real.
	 *
	 * @return a data real
	 */
	@DeleteMapping
	public ResponseEntity<RelogioDTO> repor() {
		relogioService.repor();
		log.info("Relógio reposto para a data real");
		return ResponseEntity.ok(new RelogioDTO(relogioService.hoje()));
	}
}
