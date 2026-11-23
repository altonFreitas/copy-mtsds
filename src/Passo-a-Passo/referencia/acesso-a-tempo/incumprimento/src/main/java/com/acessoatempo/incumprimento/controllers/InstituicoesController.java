package com.acessoatempo.incumprimento.controllers;

import com.acessoatempo.incumprimento.dtos.InstituicaoDTO;
import com.acessoatempo.incumprimento.services.CasosService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Instituições de destino por especialidade. Lista pequena e fechada: não é paginada (secção 4.4.1). */
@RestController
@RequestMapping("/instituicoes")
public class InstituicoesController {

    @Autowired
    private CasosService casosService;

    @Autowired
    private ModelMapper modelMapper;

    /** GET /instituicoes?esp=CARDIOLOGIA */
    @GetMapping
    public ResponseEntity<List<InstituicaoDTO>> listar(@RequestParam("esp") String esp) {
        List<InstituicaoDTO> resposta = casosService.instituicoes(esp).stream()
                .map(i -> modelMapper.map(i, InstituicaoDTO.class))
                .toList();
        return ResponseEntity.status(HttpStatus.OK).body(resposta);
    }
}
