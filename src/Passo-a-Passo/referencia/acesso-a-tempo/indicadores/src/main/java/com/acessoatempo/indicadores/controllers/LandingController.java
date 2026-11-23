package com.acessoatempo.indicadores.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint {@code /indicadores/landing}: confirma que o serviço está a responder (convenção comum). */
@RestController
@RequestMapping("/indicadores")
public class LandingController {

    @GetMapping("/landing")
    public ResponseEntity<String> landing() {
        return ResponseEntity.status(HttpStatus.OK).body("Indicadores service is working fine...");
    }
}
