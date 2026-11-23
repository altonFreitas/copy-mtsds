package com.acessoatempo.incumprimento.controllers;

import com.acessoatempo.incumprimento.data.CasosRepository;
import com.acessoatempo.incumprimento.data.InstituicoesRepository;
import com.acessoatempo.incumprimento.data.TransferenciasRepository;
import com.acessoatempo.incumprimento.enums.EstadoCaso;
import com.acessoatempo.incumprimento.enums.OrigemCaso;
import com.acessoatempo.incumprimento.models.CasoIncumprimento;
import com.acessoatempo.incumprimento.services.EventosPublisher;
import com.acessoatempo.incumprimento.services.RelogioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes HTTP dos endpoints: códigos de estado, formato JSON e formato comum de erro. */
@SpringBootTest
@AutoConfigureMockMvc
public class IncumprimentosControllerTest {

    @MockitoBean
    private EventosPublisher publisher;   // sem RabbitMQ nos testes

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CasosRepository casosRepository;

    @Autowired
    private TransferenciasRepository transferenciasRepository;

    @Autowired
    private InstituicoesRepository instituicoesRepository;

    @Autowired
    private RelogioService relogio;

    @BeforeEach
    void limpar() {
        transferenciasRepository.deleteAll();
        casosRepository.deleteAll();
        relogio.repor();
    }

    private CasoIncumprimento guardarCaso(Long episodioId, String esp, EstadoCaso estado, int nivel) {
        CasoIncumprimento c = new CasoIncumprimento();
        c.setEpisodioId(episodioId);
        c.setEspecialidade(esp);
        c.setOrigem(OrigemCaso.INCUMPRIDO);
        c.setEstado(estado);
        c.setNivel(nivel);
        c.setDataDeteccao(LocalDate.of(2026, 11, 16));
        return casosRepository.save(c);
    }

    @Test
    public void listaPaginadaEFiltrada() throws Exception {
        guardarCaso(1L, "CARDIOLOGIA", EstadoCaso.REGISTADO, 1);
        guardarCaso(2L, "CARDIOLOGIA", EstadoCaso.DIRETOR_ALERTADO, 2);
        guardarCaso(3L, "ORTOPEDIA", EstadoCaso.REGISTADO, 1);

        mvc.perform(MockMvcRequestBuilders.get("/incumprimentos?esp=CARDIOLOGIA&size=1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        mvc.perform(MockMvcRequestBuilders.get("/incumprimentos?nivel=1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    public void tamanhoDePaginaAcimaDe100Da400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/incumprimentos?size=101").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("PARAMETRO_INVALIDO"));
    }

    @Test
    public void casoInexistenteDa404ComFormatoComum() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/incumprimentos/9999")
                        .header("X-Trace-Id", "abc123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.servico").value("incumprimento"))
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.erro").value("CASO_NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.caminho").value("/incumprimentos/9999"))
                .andExpect(jsonPath("$.traceId").value("abc123"));
    }

    @Test
    public void getPorIdDevolveODto() throws Exception {
        CasoIncumprimento c = guardarCaso(7L, "CARDIOLOGIA", EstadoCaso.REGISTADO, 1);

        mvc.perform(MockMvcRequestBuilders.get("/incumprimentos/" + c.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodioId").value(7))
                .andExpect(jsonPath("$.estado").value("REGISTADO"))
                .andExpect(jsonPath("$.versao").doesNotExist());   // campo técnico não sai no DTO
    }

    @Test
    public void alertarDiretorDa200ESegundaVezDa409() throws Exception {
        CasoIncumprimento c = guardarCaso(8L, "CARDIOLOGIA", EstadoCaso.REGISTADO, 1);

        mvc.perform(MockMvcRequestBuilders.put("/incumprimentos/" + c.getId() + "/alerta-diretor")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value(2))
                .andExpect(jsonPath("$.dataLimiteDiretor").exists());

        mvc.perform(MockMvcRequestBuilders.put("/incumprimentos/" + c.getId() + "/alerta-diretor")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("TRANSICAO_INVALIDA"));
    }

    @Test
    public void transferenciaDa202ERespostaDa200() throws Exception {
        CasoIncumprimento c = guardarCaso(9L, "CARDIOLOGIA", EstadoCaso.REGISTADO, 1);
        relogio.definir(LocalDate.of(2026, 11, 16));
        mvc.perform(MockMvcRequestBuilders.put("/incumprimentos/" + c.getId() + "/alerta-diretor"))
                .andExpect(status().isOk());
        relogio.definir(LocalDate.of(2026, 11, 24));   // já passaram os 5 dias úteis

        // Instituições simuladas criadas no arranque (DadosIniciais).
        Integer instituicaoId = instituicoesRepository.findByEspecialidade("CARDIOLOGIA").get(0).getId();
        String corpo = "{\"instituicaoId\": " + instituicaoId + "}";
        String resposta = mvc.perform(MockMvcRequestBuilders.post("/incumprimentos/" + c.getId() + "/transferencias")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PROPOSTA"))
                .andExpect(jsonPath("$.casoId").value(c.getId()))
                .andExpect(jsonPath("$.instituicaoId").value(instituicaoId))
                .andReturn().getResponse().getContentAsString();

        Integer transferenciaId = com.jayway.jsonpath.JsonPath.read(resposta, "$.id");
        mvc.perform(MockMvcRequestBuilders.put("/transferencias/" + transferenciaId + "/resposta")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aceite\": true}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEITE"));
    }

    @Test
    public void corpoSemInstituicaoDa400() throws Exception {
        CasoIncumprimento c = guardarCaso(10L, "CARDIOLOGIA", EstadoCaso.DIRETOR_ALERTADO, 2);
        mvc.perform(MockMvcRequestBuilders.post("/incumprimentos/" + c.getId() + "/transferencias")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void instituicoesPorEspecialidade() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/instituicoes?esp=CARDIOLOGIA").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].especialidade").value("CARDIOLOGIA"));
        mvc.perform(MockMvcRequestBuilders.get("/instituicoes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
