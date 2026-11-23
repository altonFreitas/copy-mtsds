# 09 — `garantia-acesso`: API REST e testes

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **Os endpoints passaram de `/incumprimentos/` para `/casos/`. A lógica dos testes é a mesma; atualiza os paths e as routing keys nos testes. Referência correta: `referencia-grupo/incumprimento/`.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** expor os 6 endpoints (Anexo A.5) e ter os 26 testes a passar.
**Baseado em:** aula 04 (`CustomersController`, `@PathVariable`, `@RequestBody`, `ResponseEntity`) e aula 02 (`MockMvc`).

## 9.1 Os endpoints

| Método | Endpoint | O que faz | Códigos |
|---|---|---|---|
| GET | `/incumprimento/landing` | Confirma que responde | 200 |
| GET | `/incumprimentos?esp=&nivel=&page=&size=` | Casos por especialidade e nível, **paginado** | 200, 400 |
| GET | `/casos/{id}` | Consulta um caso | 200, 404 |
| PUT | `/casos/{id}/alerta-diretor` | **Nível 2**: alerta o diretor (5 dias úteis) | 200, 404, 409 |
| POST | `/casos/{id}/transferencias` | **Nível 3**: propõe a consulta noutra instituição | **202**, 400, 404, 409 |
| PUT | `/transferencias/{id}/resposta` | Regista a resposta da instituição | 200, 400, 404, 409 |
| GET | `/instituicoes?esp=` | Instituições de destino por especialidade | 200, 400 |

São 3 controllers (um por "recurso"): `IncumprimentosController`, `TransferenciasController`, `InstituicoesController`, mais o `LandingController` que já tens.

## 9.2 Controllers

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/controllers/IncumprimentosController.java`**

```java
package com.acessoatempo.incumprimento.controllers;

import com.acessoatempo.incumprimento.dtos.CasoDTO;
import com.acessoatempo.incumprimento.dtos.PaginaDTO;
import com.acessoatempo.incumprimento.dtos.TransferenciaDTO;
import com.acessoatempo.incumprimento.dtos.TransferenciaRequestDTO;
import com.acessoatempo.incumprimento.exceptions.ParametroInvalidoException;
import com.acessoatempo.incumprimento.models.CasoIncumprimento;
import com.acessoatempo.incumprimento.models.Transferencia;
import com.acessoatempo.incumprimento.services.CasosService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST dos casos de incumprimento (Anexo A.5). O controller só recebe pedidos, converte entidades em
 * DTO com o ModelMapper e devolve a resposta; a lógica está no {@code CasosService} e os erros no
 * {@code ExceptionHandlers}.
 *
 * <p>Códigos HTTP: 200 OK; 202 Accepted (transferência proposta: o processo termina por eventos);
 * 400 dados inválidos; 404 não encontrado; 409 regra de negócio violada.
 */
@RestController
@RequestMapping("/incumprimentos")
public class IncumprimentosController {

    /** Tamanho máximo de página (secção 4.4.1): acima disto a resposta é 400. */
    private static final int TAMANHO_MAXIMO = 100;

    @Autowired
    private CasosService casosService;

    /** ModelMapper configurado em config/ModelMapperConfig. */
    @Autowired
    private ModelMapper modelMapper;

    /** GET /incumprimentos?esp=CARDIOLOGIA&nivel=2&page=0&size=20 */
    @GetMapping
    public ResponseEntity<PaginaDTO<CasoDTO>> listar(
            @RequestParam(name = "esp", required = false) String esp,
            @RequestParam(name = "nivel", required = false) Integer nivel,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        if (page < 0) {
            throw new ParametroInvalidoException("O parâmetro 'page' tem de ser 0 ou maior");
        }
        if (size < 1 || size > TAMANHO_MAXIMO) {
            throw new ParametroInvalidoException(
                    "O parâmetro 'size' tem de estar entre 1 e " + TAMANHO_MAXIMO);
        }
        if (nivel != null && (nivel < 0 || nivel > 3)) {
            throw new ParametroInvalidoException("O parâmetro 'nivel' tem de estar entre 0 e 3");
        }

        // Ordena por data de deteção (os mais antigos primeiro) e depois por id, para a ordem ser estável.
        Page<CasoIncumprimento> casos = casosService.listar(esp, nivel,
                PageRequest.of(page, size, Sort.by("dataDeteccao", "id")));

        PaginaDTO<CasoDTO> resposta = new PaginaDTO<>(
                casos.getContent().stream().map(c -> modelMapper.map(c, CasoDTO.class)).toList(),
                casos.getNumber(), casos.getSize(), casos.getTotalElements(), casos.getTotalPages());
        return ResponseEntity.status(HttpStatus.OK).body(resposta);
    }

    /** GET /casos/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<CasoDTO> obter(@PathVariable("id") Integer id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(modelMapper.map(casosService.obter(id), CasoDTO.class));
    }

    /** PUT /casos/{id}/alerta-diretor (nível 2) */
    @PutMapping("/{id}/alerta-diretor")
    public ResponseEntity<CasoDTO> alertarDiretor(@PathVariable("id") Integer id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(modelMapper.map(casosService.alertarDiretor(id), CasoDTO.class));
    }

    /** POST /casos/{id}/transferencias (nível 3). Devolve 202: o resultado chega por eventos. */
    @PostMapping("/{id}/transferencias")
    public ResponseEntity<TransferenciaDTO> proporTransferencia(
            @PathVariable("id") Integer id,
            @Valid @RequestBody TransferenciaRequestDTO pedido) {
        Transferencia transferencia = casosService.proporTransferencia(id, pedido.getInstituicaoId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(modelMapper.map(transferencia, TransferenciaDTO.class));
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/controllers/TransferenciasController.java`**

```java
package com.acessoatempo.incumprimento.controllers;

import com.acessoatempo.incumprimento.dtos.RespostaTransferenciaDTO;
import com.acessoatempo.incumprimento.dtos.TransferenciaDTO;
import com.acessoatempo.incumprimento.services.CasosService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resposta da instituição externa a uma proposta de transferência (Anexo A.5). Em produção seria a outra
 * instituição a chamar este endpoint; nos testes é o Postman a simular essa resposta.
 */
@RestController
@RequestMapping("/transferencias")
public class TransferenciasController {

    @Autowired
    private CasosService casosService;

    @Autowired
    private ModelMapper modelMapper;

    /** PUT /transferencias/{id}/resposta  com o corpo {"aceite": true} */
    @PutMapping("/{id}/resposta")
    public ResponseEntity<TransferenciaDTO> registarResposta(
            @PathVariable("id") Integer id,
            @Valid @RequestBody RespostaTransferenciaDTO resposta) {
        return ResponseEntity.status(HttpStatus.OK).body(
                modelMapper.map(casosService.registarResposta(id, resposta.getAceite()), TransferenciaDTO.class));
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/controllers/InstituicoesController.java`**

```java
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
```

**O que faz / para a defesa:**

- **Paginação (RNF12):** `page` começa em **0**, `size` por omissão **20** e máximo **100**; acima disso a resposta é **400**. Ordena por `dataDeteccao` e `id` para a ordem ser **estável** entre páginas.
- **`@PathVariable("id")`**, **`@RequestParam("esp")`**, **`@RequestBody`** — leem, respetivamente, o `/{id}` do caminho, o `?esp=` e o JSON do corpo (aula 04). O nome entre aspas evita depender de `-parameters`.
- **`@Valid @RequestBody`** — valida o corpo com as anotações do DTO (`@NotNull`).
- **O ModelMapper** converte entidade → DTO: `modelMapper.map(caso, CasoDTO.class)` (aula 04, *customers 7/12*). Para listas usa-se `stream().map(...).toList()`.
- **`HttpStatus.ACCEPTED` (202)** na transferência: o pedido foi aceite mas o **processo termina por eventos** (secção 4.4.1).
- **Instituições não são paginadas** — lista pequena e fechada (secção 4.4.1).

## 9.3 Testes dos endpoints

📄 **`incumprimento/src/test/java/com/acessoatempo/incumprimento/controllers/IncumprimentosControllerTest.java`**

```java
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
        mvc.perform(MockMvcRequestBuilders.get("/casos/9999")
                        .header("X-Trace-Id", "abc123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.servico").value("incumprimento"))
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.erro").value("CASO_NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.caminho").value("/casos/9999"))
                .andExpect(jsonPath("$.traceId").value("abc123"));
    }

    @Test
    public void getPorIdDevolveODto() throws Exception {
        CasoIncumprimento c = guardarCaso(7L, "CARDIOLOGIA", EstadoCaso.REGISTADO, 1);

        mvc.perform(MockMvcRequestBuilders.get("/casos/" + c.getId()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodioId").value(7))
                .andExpect(jsonPath("$.estado").value("REGISTADO"))
                .andExpect(jsonPath("$.versao").doesNotExist());   // campo técnico não sai no DTO
    }

    @Test
    public void alertarDiretorDa200ESegundaVezDa409() throws Exception {
        CasoIncumprimento c = guardarCaso(8L, "CARDIOLOGIA", EstadoCaso.REGISTADO, 1);

        mvc.perform(MockMvcRequestBuilders.put("/casos/" + c.getId() + "/alerta-diretor")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value(2))
                .andExpect(jsonPath("$.dataLimiteDiretor").exists());

        mvc.perform(MockMvcRequestBuilders.put("/casos/" + c.getId() + "/alerta-diretor")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("TRANSICAO_INVALIDA"));
    }

    @Test
    public void transferenciaDa202ERespostaDa200() throws Exception {
        CasoIncumprimento c = guardarCaso(9L, "CARDIOLOGIA", EstadoCaso.REGISTADO, 1);
        relogio.definir(LocalDate.of(2026, 11, 16));
        mvc.perform(MockMvcRequestBuilders.put("/casos/" + c.getId() + "/alerta-diretor"))
                .andExpect(status().isOk());
        relogio.definir(LocalDate.of(2026, 11, 24));   // já passaram os 5 dias úteis

        // Instituições simuladas criadas no arranque (DadosIniciais).
        Integer instituicaoId = instituicoesRepository.findByEspecialidade("CARDIOLOGIA").get(0).getId();
        String corpo = "{\"instituicaoId\": " + instituicaoId + "}";
        String resposta = mvc.perform(MockMvcRequestBuilders.post("/casos/" + c.getId() + "/transferencias")
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
        mvc.perform(MockMvcRequestBuilders.post("/casos/" + c.getId() + "/transferencias")
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
```

| Teste | Garante |
|---|---|
| `listaPaginadaEFiltrada` | Filtros e paginação (`totalElementos`, `totalPaginas`) |
| `tamanhoDePaginaAcimaDe100Da400` | O limite de 100 |
| `casoInexistenteDa404ComFormatoComum` | O formato de erro (incluindo `traceId` vindo do cabeçalho) |
| `getPorIdDevolveODto` | O DTO não expõe `versao` |
| `alertarDiretorDa200ESegundaVezDa409` | 200 e depois 409 (regra de negócio) |
| `transferenciaDa202ERespostaDa200` | O fluxo do nível 3 completo, via HTTP |
| `corpoSemInstituicaoDa400` | `@Valid` |
| `instituicoesPorEspecialidade` | Filtro obrigatório e dados iniciais |

## 9.4 Correr os testes

Botão direito em `src/test` → *Run Tests*. Totais esperados no módulo: **26 testes** = 1 landing + 4 dias úteis + 13 serviço + 8 controllers.

## 9.5 Testar com o Postman (com o serviço a correr)

Cria uma *collection* "Incumprimento" com estes pedidos (a Parte 1 trata da coleção geral; esta é a tua):

1. **Simular o `gestao-acesso`** no RabbitMQ (`Publish message` na exchange `acesso.eventos`, `content_type` = `application/json`):
   - *Routing key:* `prazo.ultrapassado`
   - *Payload:* `{"eventoId":"t-001","tipo":"tmrg.incumprido","episodioId":101,"ocorridoEm":"2026-11-16T09:00:00","dados":{"especialidade":"CARDIOLOGIA"}}`
2. `GET http://localhost:8085/incumprimentos?esp=CARDIOLOGIA` → 1 caso, `REGISTADO`, nível 1.
3. `PUT http://localhost:8085/casos/1/alerta-diretor` → nível 2 e `dataLimiteDiretor`.
4. `POST http://localhost:8085/casos/1/transferencias` com `{"instituicaoId": 1}` → **409** (ainda dentro dos 5 dias úteis).
5. **Avançar o relógio:** publica `relogio.avancado` com `{"eventoId":"r-001","tipo":"relogio.avancado","ocorridoEm":"2026-11-16T09:00:00","dados":{"data":"2027-02-01"}}`.
6. Repete o passo 4 → **202** com a transferência `PROPOSTA`.
7. `PUT http://localhost:8085/transferencias/1/resposta` com `{"aceite": true}` → 200, `ACEITE`; o caso fica `RESOLVIDO`.
8. `GET http://localhost:8085/casos/999` → **404** no formato comum.

## ✅ Checklist do capítulo 09

- [ ] 6 endpoints (+ landing) a funcionar no Postman, incluindo o 409 e o 202
- [ ] 26 testes a passar
- [ ] **`garantia-acesso` concluído**
- [ ] Sabes explicar cada código HTTP do serviço e o que é a paginação
