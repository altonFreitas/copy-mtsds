package com.acessoatempo.indicadores.controllers;

import com.acessoatempo.indicadores.dtos.CumprimentoDTO;
import com.acessoatempo.indicadores.dtos.ListaIndicadorDTO;
import com.acessoatempo.indicadores.dtos.ProjecaoDTO;
import com.acessoatempo.indicadores.dtos.TriagemDTO;
import com.acessoatempo.indicadores.services.IndicadoresService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API REST dos indicadores (Anexo A.6). O controller só recebe o pedido, chama o serviço e devolve a
 * resposta. Os erros são tratados no {@code ExceptionHandlers}. Estes endpoints devolvem valores
 * agregados e por isso NÃO são paginados (secção 4.4.1).
 *
 * <p>Códigos HTTP: 200 OK; 400 Bad Request (parâmetro em falta ou inválido).
 */
@RestController
@RequestMapping("/indicadores")
public class IndicadoresController {

    @Autowired
    private IndicadoresService indicadoresService;

    /** GET /indicadores/lista?esp=CARDIOLOGIA&semana=2026-W47 */
    @GetMapping("/lista")
    public ResponseEntity<ListaIndicadorDTO> lista(
            @RequestParam("esp") String esp,
            @RequestParam(name = "semana", required = false) String semana) {
        return ResponseEntity.status(HttpStatus.OK).body(indicadoresService.lista(esp, semana));
    }

    /** GET /indicadores/cumprimento?periodo=2026 */
    @GetMapping("/cumprimento")
    public ResponseEntity<List<CumprimentoDTO>> cumprimento(@RequestParam("periodo") String periodo) {
        return ResponseEntity.status(HttpStatus.OK).body(indicadoresService.cumprimento(periodo));
    }

    /** GET /indicadores/triagem?esp=CARDIOLOGIA */
    @GetMapping("/triagem")
    public ResponseEntity<TriagemDTO> triagem(@RequestParam("esp") String esp) {
        return ResponseEntity.status(HttpStatus.OK).body(indicadoresService.triagem(esp));
    }

    /** GET /indicadores/projecao?esp=CARDIOLOGIA&semanas=4 */
    @GetMapping("/projecao")
    public ResponseEntity<ProjecaoDTO> projecao(
            @RequestParam("esp") String esp,
            @RequestParam(name = "semanas", defaultValue = "4") int semanas) {
        return ResponseEntity.status(HttpStatus.OK).body(indicadoresService.projecao(esp, semanas));
    }
}
