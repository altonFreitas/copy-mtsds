package pt.estg.mtsds.acesso.indicadores.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.indicadores.dtos.CumprimentoDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.ListaIndicadorDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.ProjecaoDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.TriagemDTO;
import pt.estg.mtsds.acesso.indicadores.services.IndicadoresService;

/**
 * API REST dos indicadores (Anexo A.6). O controller só recebe o pedido, chama
 * o serviço e devolve a resposta; os erros são tratados no ExceptionHandlers.
 * Devolvem valores agregados, por isso não são paginados.
 * <p>
 * Os parâmetros obrigatórios são declarados com required = false e validados no
 * serviço: o handler do molde não trata um parâmetro em falta (daria 500).
 */
@RestController
@RequestMapping("/indicadores")
@RequiredArgsConstructor
public class IndicadoresController {

	private final IndicadoresService indicadoresService;

	/** GET /indicadores/lista?esp=CARDIOLOGIA&semana=2026-W47 */
	@GetMapping("/lista")
	public ResponseEntity<ListaIndicadorDTO> lista(@RequestParam(name = "esp", required = false) String esp,
			@RequestParam(name = "semana", required = false) String semana) {
		return ResponseEntity.ok(indicadoresService.lista(esp, semana));
	}

	/** GET /indicadores/cumprimento?periodo=2026 */
	@GetMapping("/cumprimento")
	public ResponseEntity<List<CumprimentoDTO>> cumprimento(
			@RequestParam(name = "periodo", required = false) String periodo) {
		return ResponseEntity.ok(indicadoresService.cumprimento(periodo));
	}

	/** GET /indicadores/triagem?esp=CARDIOLOGIA */
	@GetMapping("/triagem")
	public ResponseEntity<TriagemDTO> triagem(@RequestParam(name = "esp", required = false) String esp) {
		return ResponseEntity.ok(indicadoresService.triagem(esp));
	}

	/** GET /indicadores/projecao?esp=CARDIOLOGIA&semanas=4 */
	@GetMapping("/projecao")
	public ResponseEntity<ProjecaoDTO> projecao(@RequestParam(name = "esp", required = false) String esp,
			@RequestParam(name = "semanas", defaultValue = "4") int semanas) {
		return ResponseEntity.ok(indicadoresService.projecao(esp, semanas));
	}
}
