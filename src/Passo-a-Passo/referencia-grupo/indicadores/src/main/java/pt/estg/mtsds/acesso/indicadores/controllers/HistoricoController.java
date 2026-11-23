package pt.estg.mtsds.acesso.indicadores.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.estg.mtsds.acesso.indicadores.dtos.EventoHistoricoDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.PaginaDTO;
import pt.estg.mtsds.acesso.indicadores.models.EventoHistorico;
import pt.estg.mtsds.acesso.indicadores.services.HistoricoService;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints do serviço {@code historico} (Runbook v2.1, secção 9.6). Fase 1:
 * os três primeiros endpoints. Fase 2: /indicadores/... calculados a partir dos
 * eventos.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "historico", description = "Auditoria e histórico de eventos dos episódios")
public class HistoricoController {

	private final HistoricoService historicoService;

	@Operation(summary = "Todos os eventos de um episódio, por ordem")
	@ApiResponse(responseCode = "200", description = "Lista de eventos")
	@ApiResponse(responseCode = "404", description = "Nenhum evento para este episódio")
	@GetMapping("/historico/episodios/{id}")
	public ResponseEntity<List<EventoHistoricoDTO>> porEpisodio(@PathVariable Long id) {
		return ResponseEntity.ok(
				historicoService.porEpisodio(id).stream().map(this::paraDTO).toList());
	}

	@Operation(summary = "Eventos por tipo e/ou período, paginados")
	@ApiResponse(responseCode = "200", description = "Página de eventos")
	@ApiResponse(responseCode = "400", description = "Parâmetros inválidos")
	@GetMapping("/historico/eventos")
	public ResponseEntity<PaginaDTO<EventoHistoricoDTO>> porTipoEPeriodo(
			@RequestParam(required = false) String tipo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
			Pageable pageable) {
		Page<EventoHistorico> pagina = historicoService.porTipoEPeriodo(tipo, de, ate, pageable);
		return ResponseEntity.ok(PaginaDTO.de(pagina, this::paraDTO));
	}

	@Operation(summary = "Eventos de um mesmo pedido (mesmo traceId)")
	@ApiResponse(responseCode = "200", description = "Lista de eventos")
	@ApiResponse(responseCode = "404", description = "Nenhum evento para este traceId")
	@GetMapping("/historico/traces/{traceId}")
	public ResponseEntity<List<EventoHistoricoDTO>> porTrace(@PathVariable String traceId) {
		return ResponseEntity.ok(
				historicoService.porTrace(traceId).stream().map(this::paraDTO).toList());
	}

	private EventoHistoricoDTO paraDTO(EventoHistorico e) {
		return new EventoHistoricoDTO(e.getEventoId(), e.getRoutingKey(), e.getEpisodioId(),
				e.getTraceId(), e.getAutor(), e.getDataHoraEvento(), e.getConteudo());
	}
}
