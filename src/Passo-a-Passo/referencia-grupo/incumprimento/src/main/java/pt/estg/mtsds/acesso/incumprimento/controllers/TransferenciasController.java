package pt.estg.mtsds.acesso.incumprimento.controllers;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.incumprimento.dtos.RespostaTransferenciaDTO;
import pt.estg.mtsds.acesso.incumprimento.dtos.TransferenciaDTO;
import pt.estg.mtsds.acesso.incumprimento.services.CasosService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resposta da instituição externa a uma proposta de transferência (Anexo A.5).
 * Em produção seria a outra instituição a chamar este endpoint; nos testes é o
 * Postman a simular essa resposta.
 */
@RestController
@RequestMapping("/transferencias")
@RequiredArgsConstructor
public class TransferenciasController {

	private final CasosService casosService;

	private final ModelMapper modelMapper;

	/** PUT /transferencias/{id}/resposta com o corpo {"aceite": true} */
	@PutMapping("/{id}/resposta")
	public ResponseEntity<TransferenciaDTO> registarResposta(@PathVariable("id") Integer id,
			@Valid @RequestBody RespostaTransferenciaDTO resposta) {
		return ResponseEntity.status(HttpStatus.OK)
				.body(modelMapper.map(casosService.registarResposta(id, resposta.getAceite()), TransferenciaDTO.class));
	}
}
