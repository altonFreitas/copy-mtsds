package com.acessoatempo.indicadores.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste do endpoint landing, igual ao {@code LandingControllerTest} da aula 02. O {@code MockMvc}
 * envia pedidos HTTP "de mentira" ao controller e permite fazer verificações (assertions) à resposta.
 *
 * <p>Nota Spring Boot 4: a annotation {@code @AutoConfigureMockMvc} mudou de package
 * (antes era {@code org.springframework.boot.test.autoconfigure.web.servlet}).
 */
@SpringBootTest
@AutoConfigureMockMvc
public class LandingControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    public void getLanding() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/indicadores/landing").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string(equalTo("Indicadores service is working fine...")));
    }
}
