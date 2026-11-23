# 05 — `historico`: API REST e testes

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **Os endpoints mudaram: `/indicadores/lista`, `/cumprimento`, `/triagem`, `/projecao` → `/historico/episodios/{id}`, `/historico/eventos`, `/historico/traces/{traceId}`. Segue `referencia-grupo/indicadores/` para os novos endpoints.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** expor os 4 endpoints e ter os 12 testes a passar.
**Baseado em:** aula 04 (`CustomersController`, `ResponseEntity`) e aula 02 (`MockMvc`, `jsonPath`).

## 5.1 Os endpoints (Anexo A.6)

| Método | Endpoint | O que devolve | Códigos |
|---|---|---|---|
| GET | `/indicadores/landing` | Confirma que responde | 200 |
| GET | `/historico/episodios/{id}?esp=&semana=` | Tamanho da lista, entradas e saídas | 200, 400 |
| GET | `/historico/eventos?tipo=prazo.ultrapassado?periodo=` | Taxa de cumprimento por especialidade | 200, 400 |
| GET | `/historico/eventos?tipo=pedido.triado?esp=` | Tempo médio de triagem | 200, 400 |
| GET | `/historico/eventos?esp=&semanas=` | Projeção do tamanho da lista | 200, 400 |

**Não são paginados** porque devolvem valores agregados (secção 4.4.1 da proposta).

## 5.2 O controller

No package `controllers` (já tens o `LandingController`).

📄 **`indicadores/src/main/java/com/acessoatempo/indicadores/controllers/IndicadoresController.java`**

```java
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

    /** GET /historico/episodios/{id}?esp=CARDIOLOGIA&semana=2026-W47 */
    @GetMapping("/lista")
    public ResponseEntity<ListaIndicadorDTO> lista(
            @RequestParam("esp") String esp,
            @RequestParam(name = "semana", required = false) String semana) {
        return ResponseEntity.status(HttpStatus.OK).body(indicadoresService.lista(esp, semana));
    }

    /** GET /historico/eventos?tipo=prazo.ultrapassado?periodo=2026 */
    @GetMapping("/cumprimento")
    public ResponseEntity<List<CumprimentoDTO>> cumprimento(@RequestParam("periodo") String periodo) {
        return ResponseEntity.status(HttpStatus.OK).body(indicadoresService.cumprimento(periodo));
    }

    /** GET /historico/eventos?tipo=pedido.triado?esp=CARDIOLOGIA */
    @GetMapping("/triagem")
    public ResponseEntity<TriagemDTO> triagem(@RequestParam("esp") String esp) {
        return ResponseEntity.status(HttpStatus.OK).body(indicadoresService.triagem(esp));
    }

    /** GET /historico/eventos?esp=CARDIOLOGIA&semanas=4 */
    @GetMapping("/projecao")
    public ResponseEntity<ProjecaoDTO> projecao(
            @RequestParam("esp") String esp,
            @RequestParam(name = "semanas", defaultValue = "4") int semanas) {
        return ResponseEntity.status(HttpStatus.OK).body(indicadoresService.projecao(esp, semanas));
    }
}
```

**O que faz / para a defesa:**

- O controller **só recebe o pedido, chama o serviço e devolve a resposta**. Não tem `try/catch`: os erros sobem como exceções e o `ExceptionHandlers` (capítulo 03) responde.
  (Na aula `customers` havia `try/catch` no controller; a aula `loan_proposals` já usa `@ControllerAdvice`, que é o que a proposta pede.)
- **`@RequestParam("esp")`** lê `?esp=...`. Sem `required = false`, se faltar o Spring responde **400**. Escreve o nome entre aspas para não depender de o compilador guardar os nomes dos parâmetros.
- **`defaultValue = "4"`** — as `semanas` da projeção têm valor por omissão.

## 5.3 Os cálculos (o que o serviço faz)

| Indicador | Fórmula | Nota |
|---|---|---|
| Tamanho da lista | `entradas − saídas` (acumulado até à semana pedida) | |
| Taxa de cumprimento | `100 × (entradas − incumpridos) / entradas` | Aproximação da Fase 1 😉 |
| Tempo médio de triagem | `dias entre pedido e triagem / nº de triagens` | A triagem tardia come o prazo (R1, R7) |
| Projeção | `tamanho + semanas × (média entradas − média saídas)`, nunca negativa | |

> 🤝 A **taxa de cumprimento** está simplificada (conta episódios que entraram e não falharam). Na Fase 2 convém afiná-la com a equipa do `gestao-acesso`. Sabe explicar isto como limitação conhecida.

## 5.4 Os testes

### Testes do serviço (lógica com BD H2)

📄 **`indicadores/src/test/java/com/acessoatempo/indicadores/services/IndicadoresServiceTest.java`**

```java
package com.acessoatempo.indicadores.services;

import com.acessoatempo.indicadores.data.EventosProcessadosRepository;
import com.acessoatempo.indicadores.data.IndicadoresRepository;
import com.acessoatempo.indicadores.dtos.CumprimentoDTO;
import com.acessoatempo.indicadores.dtos.ListaIndicadorDTO;
import com.acessoatempo.indicadores.dtos.ProjecaoDTO;
import com.acessoatempo.indicadores.dtos.TriagemDTO;
import com.acessoatempo.indicadores.exceptions.EventoInvalidoException;
import com.acessoatempo.indicadores.exceptions.ParametroInvalidoException;
import com.acessoatempo.indicadores.messages.EventoMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Testes da lógica de negócio dos indicadores, com BD H2 em memória (ver src/test/resources). */
@SpringBootTest
public class IndicadoresServiceTest {

    // 17 de novembro de 2026 é uma terça-feira da semana ISO 2026-W47.
    private static final LocalDateTime SEMANA_47 = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final LocalDateTime SEMANA_48 = LocalDateTime.of(2026, 11, 24, 10, 0);

    @Autowired
    private IndicadoresService service;

    @Autowired
    private IndicadoresRepository indicadoresRepository;

    @Autowired
    private EventosProcessadosRepository eventosProcessadosRepository;

    @BeforeEach
    void limpar() {
        indicadoresRepository.deleteAll();
        eventosProcessadosRepository.deleteAll();
    }

    /** Cria um evento de teste com UUID novo. */
    private EventoMessage evento(String tipo, LocalDateTime quando, Map<String, Object> dados) {
        return new EventoMessage(UUID.randomUUID().toString(), tipo, 1L, quando, dados);
    }

    private Map<String, Object> esp(String especialidade) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("especialidade", especialidade);
        return dados;
    }

    @Test
    void entradasESaidasAtualizamOTamanhoDaLista() {
        service.processar(evento("episodio.inscrito", SEMANA_47, esp("CARDIOLOGIA")));
        service.processar(evento("episodio.inscrito", SEMANA_47, esp("CARDIOLOGIA")));
        service.processar(evento("episodio.inscrito", SEMANA_47, esp("CARDIOLOGIA")));
        service.processar(evento("episodio.saida", SEMANA_48, esp("CARDIOLOGIA")));

        ListaIndicadorDTO total = service.lista("CARDIOLOGIA", null);
        assertEquals(3, total.getEntradas());
        assertEquals(1, total.getSaidas());
        assertEquals(2, total.getTamanhoLista());

        // Na semana 47 ainda não tinha saído ninguém.
        ListaIndicadorDTO s47 = service.lista("CARDIOLOGIA", "2026-W47");
        assertEquals(3, s47.getEntradas());
        assertEquals(0, s47.getSaidas());
        assertEquals(3, s47.getTamanhoLista());
    }

    @Test
    void eventoRepetidoEIgnorado() {
        EventoMessage e = evento("episodio.inscrito", SEMANA_47, esp("ORTOPEDIA"));
        service.processar(e);
        service.processar(e);   // mesmo eventoId: não pode contar duas vezes

        assertEquals(1, service.lista("ORTOPEDIA", null).getEntradas());
    }

    @Test
    void eventoSemEspecialidadeEInvalido() {
        EventoMessage e = evento("episodio.inscrito", SEMANA_47, new HashMap<>());
        assertThrows(EventoInvalidoException.class, () -> service.processar(e));
    }

    @Test
    void taxaDeCumprimentoEMediaDeTriagem() {
        for (int i = 0; i < 4; i++) {
            service.processar(evento("episodio.inscrito", SEMANA_47, esp("UROLOGIA")));
        }
        service.processar(evento("tmrg.incumprido", SEMANA_47, esp("UROLOGIA")));

        List<CumprimentoDTO> cumprimento = service.cumprimento("2026");
        assertEquals(1, cumprimento.size());
        assertEquals(75.0, cumprimento.get(0).getTaxaCumprimento());

        // Triagem 10 dias depois do pedido (pedido a 07/11, triagem a 17/11).
        Map<String, Object> dados = esp("UROLOGIA");
        dados.put("dataPedido", "2026-11-07");
        service.processar(evento("pedido.triado", SEMANA_47, dados));
        TriagemDTO triagem = service.triagem("UROLOGIA");
        assertEquals(1, triagem.getTriagens());
        assertEquals(10.0, triagem.getMediaDiasTriagem());
    }

    @Test
    void projecaoUsaAsMediasSemanais() {
        // Semana 47: 4 entradas. Semana 48: 2 entradas e 2 saídas. Atual = 6 - 2 = 4; médias 3 e 1.
        for (int i = 0; i < 4; i++) {
            service.processar(evento("episodio.inscrito", SEMANA_47, esp("OFTALMOLOGIA")));
        }
        for (int i = 0; i < 2; i++) {
            service.processar(evento("episodio.inscrito", SEMANA_48, esp("OFTALMOLOGIA")));
            service.processar(evento("episodio.saida", SEMANA_48, esp("OFTALMOLOGIA")));
        }
        ProjecaoDTO p = service.projecao("OFTALMOLOGIA", 4);
        assertEquals(4, p.getTamanhoAtual());
        assertEquals(3.0, p.getMediaEntradasSemana());
        assertEquals(1.0, p.getMediaSaidasSemana());
        assertEquals(12.0, p.getTamanhoProjetado());   // 4 + 4 x (3 - 1)
    }

    @Test
    void parametrosInvalidosDao400() {
        assertThrows(ParametroInvalidoException.class, () -> service.lista("", null));
        assertThrows(ParametroInvalidoException.class, () -> service.lista("CARDIOLOGIA", "semana-47"));
        assertThrows(ParametroInvalidoException.class, () -> service.projecao("CARDIOLOGIA", 0));
        assertThrows(ParametroInvalidoException.class, () -> service.cumprimento("abc"));
    }
}
```

### Testes dos endpoints (códigos HTTP e formato JSON)

📄 **`indicadores/src/test/java/com/acessoatempo/indicadores/controllers/IndicadoresControllerTest.java`**

```java
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
        mvc.perform(MockMvcRequestBuilders.get("/historico/episodios/{id}?esp=SEM_DADOS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidade").value("SEM_DADOS"))
                .andExpect(jsonPath("$.tamanhoLista").value(0));
    }

    @Test
    public void listaSemEspecialidadeDa400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/historico/episodios/{id}").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.servico").value("indicadores"))
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    public void semanasNaoNumericoDa400ComFormatoComum() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/historico/eventos?esp=X&semanas=abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("PEDIDO_INVALIDO"));
    }

    @Test
    public void periodoInvalidoDa400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/historico/eventos?tipo=prazo.ultrapassado?periodo=abc")
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
```

**O que cada grupo garante:**

| Teste | Regra que protege |
|---|---|
| `entradasESaidasAtualizamOTamanhoDaLista` | Os contadores e o tamanho da lista por semana |
| `eventoRepetidoEIgnorado` | Idempotência (não contar duas vezes) |
| `eventoSemEspecialidadeEInvalido` | Eventos maus não passam em silêncio |
| `taxaDeCumprimentoEMediaDeTriagem` | As fórmulas |
| `projecaoUsaAsMediasSemanais` | A projeção |
| `listaSemEspecialidadeDa400` / `periodoInvalidoDa400` | Validação e formato de erro comum |
| `urlInexistenteDa404EnaoDa500` | Que o handler geral não transforma 404 em 500 |

## 5.5 Correr tudo

1. Botão direito em `src/test` → *Run Tests*: **12 testes** devem passar (1 landing + 6 serviço + 5 controller).
2. Com o serviço a correr e alguns eventos publicados (capítulo 04), no Postman:
   - `GET http://localhost:8086/historico/episodios/{id}?esp=CARDIOLOGIA`
   - `GET http://localhost:8086/historico/eventos?tipo=prazo.ultrapassado?periodo=2026`
   - `GET http://localhost:8086/historico/eventos?tipo=pedido.triado?esp=CARDIOLOGIA`
   - `GET http://localhost:8086/historico/eventos?esp=CARDIOLOGIA&semanas=4`
   - `GET http://localhost:8086/historico/episodios/{id}` → **400** com o formato de erro comum.

## ✅ Checklist do capítulo 05

- [ ] 4 endpoints a responder; sem `esp` dá 400 com JSON de erro
- [ ] 12 testes a passar
- [ ] Sabes explicar porque os indicadores **não** são paginados e porque o serviço **não publica** eventos
- [ ] **`historico` concluído.** (É o serviço mais independente da tua parte: se os colegas ainda não publicaram eventos, simula-os como no capítulo 04.)
