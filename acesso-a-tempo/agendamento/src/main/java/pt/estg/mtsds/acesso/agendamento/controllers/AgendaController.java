package pt.estg.mtsds.acesso.agendamento.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.agendamento.dtos.AgendaDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.CriarAgendaDTO;
import pt.estg.mtsds.acesso.agendamento.services.AgendamentoService;

/**
 * Criação das agendas e das suas vagas.
 */
@RestController
@RequestMapping("/agendas")
@RequiredArgsConstructor
public class AgendaController {

	private final AgendamentoService servico;

	/**
	 * Cria uma agenda com uma vaga livre por hora.
	 *
	 * @param utilizador
	 *            cabeçalho X-Utilizador; o id fica como autor da agenda
	 * @param dto
	 *            especialidade, dia e horas
	 * @return 201 com a agenda e as vagas criadas
	 */
	@PostMapping
	public ResponseEntity<AgendaDTO> criar(@RequestHeader(CabecalhoUtilizador.NOME) String utilizador,
			@Valid @RequestBody CriarAgendaDTO dto) {
		AgendaDTO agenda = servico.criarAgenda(dto, CabecalhoUtilizador.autor(utilizador));
		return ResponseEntity.status(HttpStatus.CREATED).body(agenda);
	}
}
