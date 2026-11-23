package pt.estg.mtsds.acesso.indicadores.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * Confirma que o serviço responde (slide 04). O caminho usa o nome do serviço:
 * GET /indicadores/landing, GET /lista-espera/landing, ...
 */
@Slf4j
@RestController
public class LandingController {

	@Value("${spring.application.name}")
	private String nomeServico;

	@GetMapping("/${spring.application.name}/landing")
	public ResponseEntity<String> landing() {
		log.info("Pedido de landing recebido");
		return ResponseEntity.ok("O serviço " + nomeServico + " está a funcionar.");
	}
}
