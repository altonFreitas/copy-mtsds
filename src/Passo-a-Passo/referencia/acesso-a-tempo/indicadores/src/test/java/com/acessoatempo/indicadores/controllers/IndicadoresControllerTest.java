package com.acessoatempo.indicadores.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes dos endpoints de indicadores: verifica códigos HTTP e o formato JSON (como na aula 02). */
@SpringBootTest
@AutoConfigureMockMvc
public class IndicadoresControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    public void listaDeEspecialidadeSemDadosDevolveZeros() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/indicadores/lista?esp=SEM_DADOS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidade").value("SEM_DADOS"))
                .andExpect(jsonPath("$.tamanhoLista").value(0));
    }

    @Test
    public void listaSemEspecialidadeDa400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/indicadores/lista").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.servico").value("indicadores"))
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    public void semanasNaoNumericoDa400ComFormatoComum() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/indicadores/projecao?esp=X&semanas=abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("PEDIDO_INVALIDO"));
    }

    @Test
    public void periodoInvalidoDa400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/indicadores/cumprimento?periodo=abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("PARAMETRO_INVALIDO"));
    }

    @Test
    public void urlInexistenteDa404EnaoDa500() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/indicadores/nao-existe").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
