package pt.estg.mtsds.acesso.gestaoacesso.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.gestaoacesso.dtos.PrazoDTO;
import pt.estg.mtsds.acesso.gestaoacesso.services.PrazoService;

/**
 * Consulta do prazo TMRG de um episódio.
 */
@RestController
@RequestMapping("/prazos")
@RequiredArgsConstructor
public class PrazoController {

	private final PrazoService servico;

	/**
	 * Devolve o prazo de um episódio, com os dias consumidos até hoje.
	 *
	 * @param episodioId
	 *            episódio (id do pedido)
	 * @return 200 com o prazo
	 */
	@GetMapping("/{episodioId}")
	public ResponseEntity<PrazoDTO> obter(@PathVariable Long episodioId) {
		return ResponseEntity.ok(servico.obter(episodioId));
	}
}
