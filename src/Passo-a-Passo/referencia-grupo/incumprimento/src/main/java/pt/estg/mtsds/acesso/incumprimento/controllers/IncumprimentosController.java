package pt.estg.mtsds.acesso.incumprimento.controllers;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.incumprimento.dtos.CasoDTO;
import pt.estg.mtsds.acesso.incumprimento.dtos.PaginaDTO;
import pt.estg.mtsds.acesso.incumprimento.dtos.TransferenciaDTO;
import pt.estg.mtsds.acesso.incumprimento.dtos.TransferenciaRequestDTO;
import pt.estg.mtsds.acesso.incumprimento.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.incumprimento.models.CasoIncumprimento;
import pt.estg.mtsds.acesso.incumprimento.models.Transferencia;
import pt.estg.mtsds.acesso.incumprimento.services.CasosService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST dos casos de incumprimento (Anexo A.5). O controller só recebe
 * pedidos, converte entidades em DTO com o ModelMapper e devolve a resposta; a
 * lógica está no {@code CasosService} e os erros no {@code ExceptionHandlers}.
 *
 * <p>
 * Códigos HTTP: 200 OK; 202 Accepted (transferência proposta: o processo
 * termina por eventos); 400 dados inválidos; 404 não encontrado; 409 regra de
 * negócio violada.
 */
@RestController
@RequestMapping("/incumprimentos")
@RequiredArgsConstructor
public class IncumprimentosController {

	/** Tamanho máximo de página (secção 4.4.1): acima disto a resposta é 400. */
	private static final int TAMANHO_MAXIMO = 100;

	private final CasosService casosService;

	/** ModelMapper configurado em config/ModelMapperConfig. */
	private final ModelMapper modelMapper;

	/** GET /incumprimentos?esp=CARDIOLOGIA&nivel=2&page=0&size=20 */
	@GetMapping
	public ResponseEntity<PaginaDTO<CasoDTO>> listar(@RequestParam(name = "esp", required = false) String esp,
			@RequestParam(name = "nivel", required = false) Integer nivel,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {

		if (page < 0) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO", "O parâmetro 'page' tem de ser 0 ou maior");
		}
		if (size < 1 || size > TAMANHO_MAXIMO) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO",
					"O parâmetro 'size' tem de estar entre 1 e " + TAMANHO_MAXIMO);
		}
		if (nivel != null && (nivel < 0 || nivel > 3)) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO", "O parâmetro 'nivel' tem de estar entre 0 e 3");
		}

		// Ordena por data de deteção (os mais antigos primeiro) e depois por id, para a
		// ordem ser estável.
		Page<CasoIncumprimento> casos = casosService.listar(esp, nivel,
				PageRequest.of(page, size, Sort.by("dataDeteccao", "id")));

		PaginaDTO<CasoDTO> resposta = PaginaDTO.de(casos, c -> modelMapper.map(c, CasoDTO.class));
		return ResponseEntity.status(HttpStatus.OK).body(resposta);
	}

	/** GET /incumprimentos/{id} */
	@GetMapping("/{id}")
	public ResponseEntity<CasoDTO> obter(@PathVariable("id") Integer id) {
		return ResponseEntity.status(HttpStatus.OK).body(modelMapper.map(casosService.obter(id), CasoDTO.class));
	}

	/** PUT /incumprimentos/{id}/alerta-diretor (nível 2) */
	@PutMapping("/{id}/alerta-diretor")
	public ResponseEntity<CasoDTO> alertarDiretor(@PathVariable("id") Integer id) {
		return ResponseEntity.status(HttpStatus.OK)
				.body(modelMapper.map(casosService.alertarDiretor(id), CasoDTO.class));
	}

	/**
	 * POST /incumprimentos/{id}/transferencias (nível 3). Devolve 202: o resultado
	 * chega por eventos.
	 */
	@PostMapping("/{id}/transferencias")
	public ResponseEntity<TransferenciaDTO> proporTransferencia(@PathVariable("id") Integer id,
			@Valid @RequestBody TransferenciaRequestDTO pedido) {
		Transferencia transferencia = casosService.proporTransferencia(id, pedido.getInstituicaoId());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(modelMapper.map(transferencia, TransferenciaDTO.class));
	}
}
