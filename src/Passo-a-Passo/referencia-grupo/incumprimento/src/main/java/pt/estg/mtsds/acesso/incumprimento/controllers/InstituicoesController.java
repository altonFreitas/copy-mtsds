package pt.estg.mtsds.acesso.incumprimento.controllers;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.incumprimento.dtos.InstituicaoDTO;
import pt.estg.mtsds.acesso.incumprimento.services.CasosService;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Instituições de destino por especialidade. Lista pequena e fechada: não é
 * paginada (secção 4.4.1).
 */
@RestController
@RequestMapping("/instituicoes")
@RequiredArgsConstructor
public class InstituicoesController {

	private final CasosService casosService;

	private final ModelMapper modelMapper;

	/**
	 * GET /instituicoes?esp=CARDIOLOGIA. O esp é validado no serviço (o handler do
	 * molde não trata um parâmetro em falta e daria 500).
	 */
	@GetMapping
	public ResponseEntity<List<InstituicaoDTO>> listar(@RequestParam(name = "esp", required = false) String esp) {
		List<InstituicaoDTO> resposta = casosService.instituicoes(esp).stream()
				.map(i -> modelMapper.map(i, InstituicaoDTO.class)).toList();
		return ResponseEntity.status(HttpStatus.OK).body(resposta);
	}
}
