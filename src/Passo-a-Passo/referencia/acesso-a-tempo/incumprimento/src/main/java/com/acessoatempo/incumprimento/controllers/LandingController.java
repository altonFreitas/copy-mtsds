package com.acessoatempo.incumprimento.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint {@code /incumprimento/landing}: confirma que o serviço está a responder (convenção comum). */
@RestController
@RequestMapping("/incumprimento")
public class LandingController {

    @GetMapping("/landing")
    public ResponseEntity<String> landing() {
        return ResponseEntity.status(HttpStatus.OK).body("Incumprimento service is working fine...");
    }
}
