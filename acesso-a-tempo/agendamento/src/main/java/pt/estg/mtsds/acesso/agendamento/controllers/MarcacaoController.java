package pt.estg.mtsds.acesso.agendamento.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.agendamento.dtos.MarcacaoDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.RegistarRealizacaoDTO;
import pt.estg.mtsds.acesso.agendamento.services.AgendamentoService;

/**
 * Consulta das marcações e registo do resultado da consulta.
 */
@RestController
@RequestMapping("/marcacoes")
@RequiredArgsConstructor
public class MarcacaoController {

	private final AgendamentoService servico;

	/**
	 * Lê uma marcação.
	 *
	 * @param id
	 *            marcação a ler
	 * @return 200 com a marcação
	 */
	@GetMapping("/{id}")
	public ResponseEntity<MarcacaoDTO> obter(@PathVariable Long id) {
		return ResponseEntity.ok(servico.obterMarcacao(id));
	}

	/**
	 * Regista se a consulta foi realizada ou se o utente faltou.
	 *
	 * @param id
	 *            marcação a fechar
	 * @param dto
	 *            resultado: REALIZADA ou FALTA
	 * @return 200 com a marcação atualizada
	 */
	@PutMapping("/{id}/realizacao")
	public ResponseEntity<MarcacaoDTO> registarRealizacao(@PathVariable Long id,
			@Valid @RequestBody RegistarRealizacaoDTO dto) {
		return ResponseEntity.ok(servico.registarRealizacao(id, dto.resultado()));
	}
}
