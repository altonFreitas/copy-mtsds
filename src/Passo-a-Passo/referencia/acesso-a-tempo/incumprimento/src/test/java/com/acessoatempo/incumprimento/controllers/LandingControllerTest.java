package com.acessoatempo.incumprimento.controllers;

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

/** Teste do endpoint landing (igual ao LandingControllerTest da aula 02). */
@SpringBootTest
@AutoConfigureMockMvc
public class LandingControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    public void getLanding() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/incumprimento/landing").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string(equalTo("Incumprimento service is working fine...")));
    }
}
