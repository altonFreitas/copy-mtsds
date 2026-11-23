# 08 — `garantia-acesso`: as regras dos 3 níveis

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **A lógica de 3 níveis, 5 dias úteis e compensação é a mesma. Apenas os nomes das routing keys mudaram (ver capítulo 07). O `switch` no `CasosService` usa agora `prazo.em-risco`, `prazo.ultrapassado-previsto`, `prazo.ultrapassado`.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

**Objetivo:** escrever toda a lógica de negócio: relógio, dias úteis, publicação de eventos, tratamento dos eventos recebidos e a transferência com compensação.
**Baseado em:** aula 04 (`@Service`, `@Autowired`, exceções do serviço) e aula 05 (`RabbitTemplate.convertAndSend`, `@RabbitListener`).

Este é o capítulo mais importante. Escreve-o com calma: **é aqui que está a lógica que vais defender**.

## 8.1 O relógio (RNF07)

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/services/RelogioService.java`**

```java
package com.acessoatempo.incumprimento.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Relógio do serviço (RNF07). Em funcionamento normal devolve a data real. Quando o {@code tmrg} publica
 * {@code relogio.avancado} (só no perfil de demonstração), o serviço passa a usar a data simulada, para que
 * todos os serviços "vejam" o mesmo dia e se possa demonstrar a passagem dos 5 dias úteis sem esperar.
 *
 * <p>Regra de ouro: nenhuma classe do serviço chama {@code LocalDate.now()} diretamente; usa sempre
 * {@link #hoje()}. Assim o tempo pode ser simulado nos testes.
 */
@Service
public class RelogioService {

    private static final Logger log = LoggerFactory.getLogger(RelogioService.class);

    /** null = usa a data real. {@code volatile}: pode ser alterada por uma thread (listener) e lida por outra. */
    private volatile LocalDate dataSimulada;

    /** Data "de hoje" para efeitos de negócio. */
    public LocalDate hoje() {
        return dataSimulada != null ? dataSimulada : LocalDate.now();
    }

    /** Passa a usar uma data simulada. */
    public void definir(LocalDate data) {
        log.info("Relógio simulado: {} -> {}", hoje(), data);
        this.dataSimulada = data;
    }

    /** Volta a usar a data real (usado nos testes). */
    public void repor() {
        this.dataSimulada = null;
    }
}
```

- **Regra de ouro:** nenhuma classe chama `LocalDate.now()` diretamente; usa `relogio.hoje()`.
- Quando o `gestao-acesso` publica `relogio.avancado` (**só no perfil de demonstração**), o serviço passa a usar a **data simulada**. Assim consegues **demonstrar na defesa** a passagem dos 5 dias úteis sem esperar uma semana.
- **`volatile`**: a data é escrita por uma *thread* (o listener) e lida por outras (pedidos HTTP).

## 8.2 Dias úteis (regra R6)

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/services/DiasUteisService.java`**

```java
package com.acessoatempo.incumprimento.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Cálculo de dias úteis (regra R6): o diretor de serviço tem 5 dias úteis para reagendar, sem contar
 * sábados, domingos e feriados nacionais. Os feriados vêm de uma lista configurável no application.yml
 * ({@code incumprimento.feriados}), por isso podem mudar sem alterar o código.
 */
@Service
public class DiasUteisService {

    private final Set<LocalDate> feriados = new HashSet<>();

    /**
     * Injeção por construtor: o Spring passa o valor de {@code incumprimento.feriados} (texto com datas
     * separadas por vírgula; por omissão vazio). Num teste basta fazer {@code new DiasUteisService("2026-12-25")}.
     */
    public DiasUteisService(@Value("${incumprimento.feriados:}") String feriadosConfigurados) {
        Arrays.stream(feriadosConfigurados.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(LocalDate::parse)
                .forEach(feriados::add);
    }

    /** Verdadeiro se for dia útil: não é sábado, nem domingo, nem feriado. */
    public boolean eDiaUtil(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY && !feriados.contains(data);
    }

    /**
     * Soma {@code dias} dias úteis a {@code inicio}. O dia de início não conta: 5 dias úteis a partir de
     * uma segunda-feira (sem feriados) terminam na segunda-feira seguinte.
     */
    public LocalDate adicionarDiasUteis(LocalDate inicio, int dias) {
        LocalDate data = inicio;
        int somados = 0;
        while (somados < dias) {
            data = data.plusDays(1);
            if (eDiaUtil(data)) {
                somados++;
            }
        }
        return data;
    }
}
```

- **R6:** o diretor de serviço tem **5 dias úteis** para reagendar, *"sem contar sábados, domingos e feriados nacionais, que vão estar numa lista configurável"*. Os feriados vêm do `application.yml` (`incumprimento.feriados`).
- **Injeção por construtor** com `@Value("${...}")`: é a única classe do guia que não usa `@Autowired` no campo, porque assim o teste faz `new DiasUteisService("2026-12-25")` sem precisar do Spring.
- `adicionarDiasUteis(início, n)`: **o dia de início não conta**. 5 dias úteis a partir de uma segunda-feira terminam na segunda seguinte.

Teste unitário (puro JUnit, **sem Spring**: corre em milissegundos):

📄 **`incumprimento/src/test/java/com/acessoatempo/incumprimento/services/DiasUteisServiceTest.java`**

```java
package com.acessoatempo.incumprimento.services;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários (sem Spring) do cálculo de dias úteis: não precisam de BD nem de contexto. */
public class DiasUteisServiceTest {

    // 25/12/2026 é uma sexta-feira e é feriado.
    private final DiasUteisService servico = new DiasUteisService("2026-12-25, 2027-01-01");

    @Test
    void cincoDiasUteisSaltamOFimDeSemana() {
        // Segunda 16/11/2026 + 5 dias úteis = segunda 23/11/2026.
        assertEquals(LocalDate.of(2026, 11, 23), servico.adicionarDiasUteis(LocalDate.of(2026, 11, 16), 5));
        // Sexta 20/11/2026 + 5 dias úteis = sexta 27/11/2026.
        assertEquals(LocalDate.of(2026, 11, 27), servico.adicionarDiasUteis(LocalDate.of(2026, 11, 20), 5));
    }

    @Test
    void feriadosNaoContam() {
        // Segunda 21/12/2026 + 5 dias úteis: 22, 23, 24, (25 feriado, 26 e 27 fim de semana), 28, 29 -> terça 29/12.
        assertEquals(LocalDate.of(2026, 12, 29), servico.adicionarDiasUteis(LocalDate.of(2026, 12, 21), 5));
    }

    @Test
    void sabadoDomingoEFeriadoNaoSaoDiasUteis() {
        assertFalse(servico.eDiaUtil(LocalDate.of(2026, 11, 21)));   // sábado
        assertFalse(servico.eDiaUtil(LocalDate.of(2026, 11, 22)));   // domingo
        assertFalse(servico.eDiaUtil(LocalDate.of(2026, 12, 25)));   // feriado
        assertTrue(servico.eDiaUtil(LocalDate.of(2026, 11, 23)));    // segunda
    }

    @Test
    void semFeriadosConfiguradosFunciona() {
        DiasUteisService semFeriados = new DiasUteisService("");
        assertTrue(semFeriados.eDiaUtil(LocalDate.of(2026, 12, 25)));
    }
}
```

## 8.3 Publicar eventos (Producer)

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/services/EventosPublisher.java`**

```java
package com.acessoatempo.incumprimento.services;

import com.acessoatempo.incumprimento.config.MQConfig;
import com.acessoatempo.incumprimento.messages.EventoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Producer: publica eventos na exchange {@code acesso.eventos} (equivale ao {@code OrdersService} da aula,
 * que usava {@code template.convertAndSend}).
 *
 * <p>Detalhe importante: se houver uma transação a decorrer, o evento só é enviado DEPOIS do commit
 * ({@code afterCommit}). Assim nunca se anuncia um facto que acabou por não ser gravado na base de dados
 * (rollback). O problema inverso (a BD grava e o RabbitMQ está em baixo) fica por resolver: a solução
 * completa seria o padrão Transactional Outbox (ver sugestões no explicacao.md).
 */
@Component
public class EventosPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventosPublisher.class);

    /** O Spring Boot cria este RabbitTemplate já com o MessageConverter (JSON) definido no MQConfig. */
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RelogioService relogio;

    /**
     * @param tipo       routing key do evento, ex.: {@code incumprimento.registado}
     * @param episodioId episódio a que o evento diz respeito
     * @param dados      dados do evento (só identificadores e datas, nunca dados pessoais)
     */
    public void publicar(String tipo, Long episodioId, Map<String, Object> dados) {
        EventoMessage mensagem = new EventoMessage(UUID.randomUUID().toString(), tipo, episodioId,
                LocalDateTime.of(relogio.hoje(), LocalTime.now()), dados);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(mensagem);
                }
            });
        } else {
            enviar(mensagem);
        }
    }

    private void enviar(EventoMessage mensagem) {
        try {
            // exchange, routing key, objeto (convertido para JSON pelo MessageConverter)
            rabbitTemplate.convertAndSend(MQConfig.EXCHANGE, mensagem.getTipo(), mensagem);
            log.info("Evento publicado: tipo={} episodioId={} eventoId={}",
                    mensagem.getTipo(), mensagem.getEpisodioId(), mensagem.getEventoId());
        } catch (Exception e) {
            // Já não se pode desfazer a transação. Regista-se o erro para o evento poder ser reenviado.
            log.error("FALHA a publicar o evento {} ({}): {}", mensagem.getTipo(), mensagem.getEventoId(),
                    e.getMessage());
        }
    }
}
```

- Equivale ao `OrdersService` da aula 05: `rabbitTemplate.convertAndSend(exchange, routingKey, objeto)`. O `RabbitTemplate` converte o objeto em JSON com o `MessageConverter` do `MQConfig`.
- **Só envia depois do commit** (`TransactionSynchronization.afterCommit`). Se enviasse antes e a transação fizesse *rollback*, os outros serviços reagiriam a algo que **não aconteceu**.
- ⚠️ **Limitação conhecida (diz-a na defesa):** o problema inverso persiste: a BD grava e o RabbitMQ está em baixo → o evento perde-se (fica só no log). A solução completa é o padrão **Transactional Outbox**: gravar o evento numa tabela na mesma transação e outro processo enviá-lo. Fica como melhoria.

## 8.4 O serviço de negócio

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/services/CasosService.java`**

```java
package com.acessoatempo.incumprimento.services;

import com.acessoatempo.incumprimento.data.CasosRepository;
import com.acessoatempo.incumprimento.data.EventosProcessadosRepository;
import com.acessoatempo.incumprimento.data.InstituicoesRepository;
import com.acessoatempo.incumprimento.data.TransferenciasRepository;
import com.acessoatempo.incumprimento.enums.EstadoCaso;
import com.acessoatempo.incumprimento.enums.EstadoTransferencia;
import com.acessoatempo.incumprimento.enums.OrigemCaso;
import com.acessoatempo.incumprimento.exceptions.CasoNaoEncontradoException;
import com.acessoatempo.incumprimento.exceptions.EventoInvalidoException;
import com.acessoatempo.incumprimento.exceptions.InstituicaoNaoEncontradaException;
import com.acessoatempo.incumprimento.exceptions.ParametroInvalidoException;
import com.acessoatempo.incumprimento.exceptions.TransferenciaNaoEncontradaException;
import com.acessoatempo.incumprimento.exceptions.TransicaoInvalidaException;
import com.acessoatempo.incumprimento.messages.EventoMessage;
import com.acessoatempo.incumprimento.models.CasoIncumprimento;
import com.acessoatempo.incumprimento.models.EventoProcessado;
import com.acessoatempo.incumprimento.models.Instituicao;
import com.acessoatempo.incumprimento.models.Transferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regras de negócio do serviço {@code incumprimento} (secção 2.1, regra R6): tratar cada incumprimento em
 * 3 níveis. (1) registar e reportar; (2) alertar o diretor de serviço, que tem 5 dias úteis para reagendar;
 * (3) propor a consulta noutra instituição, com compensação se for recusada.
 *
 * <p>Como na aula, o controller só recebe pedidos; toda a lógica e as exceções ficam aqui.
 */
@Service
public class CasosService {

    private static final Logger log = LoggerFactory.getLogger(CasosService.class);

    /** R6: o diretor de serviço tem 5 dias úteis para reagendar. */
    static final int DIAS_UTEIS_DIRETOR = 5;

    @Autowired
    private CasosRepository casosRepository;

    @Autowired
    private TransferenciasRepository transferenciasRepository;

    @Autowired
    private InstituicoesRepository instituicoesRepository;

    @Autowired
    private EventosProcessadosRepository eventosProcessadosRepository;

    @Autowired
    private EventosPublisher publisher;

    @Autowired
    private RelogioService relogio;

    @Autowired
    private DiasUteisService diasUteis;

    /** Dias que a outra instituição tem para responder (configurável no application.yml). */
    @Value("${incumprimento.transferencia.prazo-resposta-dias:5}")
    private int prazoRespostaDias;

    // ------------------------------------------------------------------------------------------
    // 1) Eventos recebidos (chamado pelo EventosListener)
    // ------------------------------------------------------------------------------------------

    /**
     * Trata um evento recebido. Tudo corre numa só transação: gravar o caso e marcar o evento como
     * processado. Os eventos que o serviço publica só saem depois do commit (ver EventosPublisher).
     */
    @Transactional
    public void tratarEvento(EventoMessage evento) {
        if (evento == null || evento.getEventoId() == null || evento.getTipo() == null) {
            throw new EventoInvalidoException("Evento sem eventoId ou tipo");
        }
        // Idempotência: ignora o evento se já foi tratado.
        if (eventosProcessadosRepository.existsById(evento.getEventoId())) {
            log.info("Evento {} ({}) já processado: ignorado", evento.getEventoId(), evento.getTipo());
            return;
        }

        switch (evento.getTipo()) {
            case "tmrg.risco-detetado" -> abrirOuEscalar(evento, OrigemCaso.RISCO);
            case "tmrg.incumprimento-previsto" -> abrirOuEscalar(evento, OrigemCaso.INCUMPRIMENTO_PREVISTO);
            case "tmrg.incumprido" -> abrirOuEscalar(evento, OrigemCaso.INCUMPRIDO);
            case "consulta.agendada" -> resolverPorReagendamento(evento);
            case "relogio.avancado" -> relogio.definir(dataDe(evento, "data"));
            default -> log.warn("Evento {} não é tratado por este serviço", evento.getTipo());
        }

        eventosProcessadosRepository.save(
                new EventoProcessado(evento.getEventoId(), evento.getTipo(), LocalDateTime.now()));
    }

    /**
     * Risco, incumprimento previsto ou incumprido: abre um caso ou, se já existe um caso aberto para o
     * episódio, só o faz subir de "em risco" para o nível 1. Nunca há dois casos abertos do mesmo episódio.
     */
    private void abrirOuEscalar(EventoMessage evento, OrigemCaso origem) {
        Long episodioId = episodioDe(evento);
        Optional<CasoIncumprimento> aberto =
                casosRepository.findFirstByEpisodioIdAndEstadoIn(episodioId, EstadoCaso.ABERTOS);

        if (aberto.isEmpty()) {
            CasoIncumprimento caso = new CasoIncumprimento();
            caso.setEpisodioId(episodioId);
            caso.setEspecialidade(textoDe(evento, "especialidade"));
            caso.setOrigem(origem);
            caso.setDataDeteccao(relogio.hoje());
            if (origem == OrigemCaso.RISCO) {
                caso.setEstado(EstadoCaso.EM_RISCO);
                caso.setNivel(0);
                casosRepository.save(caso);
            } else {
                caso.setEstado(EstadoCaso.REGISTADO);
                caso.setNivel(1);
                casosRepository.save(caso);
                publicarRegistado(caso);
            }
            return;
        }

        CasoIncumprimento caso = aberto.get();
        if (caso.getEstado() == EstadoCaso.EM_RISCO && origem != OrigemCaso.RISCO) {
            caso.setOrigem(origem);
            caso.setEstado(EstadoCaso.REGISTADO);
            caso.setNivel(1);
            casosRepository.save(caso);
            publicarRegistado(caso);
        } else {
            log.info("Já existe o caso {} aberto para o episódio {}: evento sem efeito", caso.getId(), episodioId);
        }
    }

    /** Quando a consulta é (re)marcada, o caso aberto deste episódio fica resolvido (nível 2: "vaga recuperada"). */
    private void resolverPorReagendamento(EventoMessage evento) {
        Long episodioId = episodioDe(evento);
        casosRepository.findFirstByEpisodioIdAndEstadoIn(episodioId, EstadoCaso.ABERTOS)
                .ifPresent(caso -> resolver(caso, "CONSULTA_AGENDADA"));
    }

    private void resolver(CasoIncumprimento caso, String motivo) {
        caso.setEstado(EstadoCaso.RESOLVIDO);
        casosRepository.save(caso);
        publisher.publicar("incumprimento.resolvido", caso.getEpisodioId(),
                dados(caso, "casoId", caso.getId(), "motivo", motivo));
    }

    private void publicarRegistado(CasoIncumprimento caso) {
        publisher.publicar("incumprimento.registado", caso.getEpisodioId(),
                dados(caso, "casoId", caso.getId(), "origem", caso.getOrigem().name()));
    }

    // ------------------------------------------------------------------------------------------
    // 2) Operações da API REST (chamadas pelos controllers)
    // ------------------------------------------------------------------------------------------

    /** Lista os casos, filtrando por especialidade e/ou nível (ambos opcionais), já paginados. */
    @Transactional(readOnly = true)
    public Page<CasoIncumprimento> listar(String especialidade, Integer nivel, Pageable pageable) {
        boolean temEsp = especialidade != null && !especialidade.isBlank();
        if (temEsp && nivel != null) {
            return casosRepository.findByEspecialidadeAndNivel(especialidade, nivel, pageable);
        }
        if (temEsp) {
            return casosRepository.findByEspecialidade(especialidade, pageable);
        }
        if (nivel != null) {
            return casosRepository.findByNivel(nivel, pageable);
        }
        return casosRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public CasoIncumprimento obter(Integer id) {
        return casosRepository.findById(id).orElseThrow(() -> new CasoNaoEncontradoException(id));
    }

    /** Nível 2: alerta o diretor de serviço e fixa o prazo de 5 dias úteis para reagendar. */
    @Transactional
    public CasoIncumprimento alertarDiretor(Integer id) {
        CasoIncumprimento caso = obter(id);
        if (caso.getEstado() != EstadoCaso.REGISTADO) {
            throw new TransicaoInvalidaException("O caso " + id + " está em " + caso.getEstado()
                    + ". Só um caso REGISTADO (nível 1) pode ser alertado ao diretor de serviço.");
        }
        caso.setEstado(EstadoCaso.DIRETOR_ALERTADO);
        caso.setNivel(2);
        caso.setDataLimiteDiretor(diasUteis.adicionarDiasUteis(relogio.hoje(), DIAS_UTEIS_DIRETOR));
        casosRepository.save(caso);

        publisher.publicar("incumprimento.diretor-alertado", caso.getEpisodioId(),
                dados(caso, "casoId", caso.getId(), "dataLimiteDiretor", caso.getDataLimiteDiretor().toString()));
        return caso;
    }

    /**
     * Nível 3: propõe a consulta noutra instituição. Só é possível se o caso estiver no nível 2 e os 5 dias
     * úteis do diretor já tiverem passado sem marcação.
     */
    @Transactional
    public Transferencia proporTransferencia(Integer casoId, Integer instituicaoId) {
        CasoIncumprimento caso = obter(casoId);
        if (caso.getEstado() != EstadoCaso.DIRETOR_ALERTADO) {
            throw new TransicaoInvalidaException("O caso " + casoId + " está em " + caso.getEstado()
                    + ". A transferência só pode ser proposta a partir do nível 2 (DIRETOR_ALERTADO).");
        }
        if (!relogio.hoje().isAfter(caso.getDataLimiteDiretor())) {
            throw new TransicaoInvalidaException("O diretor de serviço ainda tem até "
                    + caso.getDataLimiteDiretor() + " para reagendar o caso " + casoId + ".");
        }
        Instituicao instituicao = instituicoesRepository.findById(instituicaoId)
                .orElseThrow(() -> new InstituicaoNaoEncontradaException(instituicaoId));
        if (!instituicao.getEspecialidade().equals(caso.getEspecialidade())) {
            throw new ParametroInvalidoException("A instituição " + instituicaoId + " não atende a especialidade "
                    + caso.getEspecialidade() + ".");
        }

        Transferencia transferencia = new Transferencia();
        transferencia.setCaso(caso);
        transferencia.setInstituicao(instituicao);
        transferencia.setEstado(EstadoTransferencia.PROPOSTA);
        transferencia.setDataProposta(relogio.hoje());
        transferencia.setRespostaAte(relogio.hoje().plusDays(prazoRespostaDias));
        transferenciasRepository.save(transferencia);

        caso.setEstado(EstadoCaso.TRANSFERENCIA_PROPOSTA);
        caso.setNivel(3);
        casosRepository.save(caso);

        publisher.publicar("transferencia.proposta", caso.getEpisodioId(),
                dados(caso, "casoId", caso.getId(), "transferenciaId", transferencia.getId(),
                        "instituicaoId", instituicao.getId(),
                        "respostaAte", transferencia.getRespostaAte().toString()));
        return transferencia;
    }

    /**
     * Regista a resposta da outra instituição. Aceite: a transferência fica concluída e o caso resolvido.
     * Recusada: COMPENSAÇÃO; o episódio mantém-se na lista e o caso é sinalizado à direção clínica.
     */
    @Transactional
    public Transferencia registarResposta(Integer transferenciaId, boolean aceite) {
        Transferencia transferencia = transferenciasRepository.findById(transferenciaId)
                .orElseThrow(() -> new TransferenciaNaoEncontradaException(transferenciaId));
        if (transferencia.getEstado() != EstadoTransferencia.PROPOSTA) {
            throw new TransicaoInvalidaException("A transferência " + transferenciaId + " já tem resposta ("
                    + transferencia.getEstado() + ").");
        }
        CasoIncumprimento caso = transferencia.getCaso();
        transferencia.setDataResposta(relogio.hoje());

        if (aceite) {
            transferencia.setEstado(EstadoTransferencia.ACEITE);
            transferenciasRepository.save(transferencia);
            publisher.publicar("transferencia.aceite", caso.getEpisodioId(),
                    dados(caso, "casoId", caso.getId(), "transferenciaId", transferencia.getId(),
                            "instituicaoId", transferencia.getInstituicao().getId()));
            resolver(caso, "TRANSFERIDO");
        } else {
            transferencia.setEstado(EstadoTransferencia.RECUSADA);
            transferenciasRepository.save(transferencia);
            caso.setEstado(EstadoCaso.SINALIZADO_DIRECAO_CLINICA);
            casosRepository.save(caso);
            publisher.publicar("transferencia.recusada", caso.getEpisodioId(),
                    dados(caso, "casoId", caso.getId(), "transferenciaId", transferencia.getId(),
                            "instituicaoId", transferencia.getInstituicao().getId()));
        }
        return transferencia;
    }

    @Transactional(readOnly = true)
    public List<Instituicao> instituicoes(String especialidade) {
        if (especialidade == null || especialidade.isBlank()) {
            throw new ParametroInvalidoException("O parâmetro 'esp' (especialidade) é obrigatório");
        }
        return instituicoesRepository.findByEspecialidade(especialidade);
    }

    /** Dá como recusadas as propostas sem resposta cujo prazo terminou. Devolve quantas foram. */
    @Transactional
    public int expirarTransferencias() {
        List<Transferencia> expiradas = transferenciasRepository
                .findByEstadoAndRespostaAteBefore(EstadoTransferencia.PROPOSTA, relogio.hoje());
        for (Transferencia t : expiradas) {
            log.warn("Transferência {} sem resposta até {}: dada como recusada", t.getId(), t.getRespostaAte());
            registarResposta(t.getId(), false);
        }
        return expiradas.size();
    }

    // ------------------------------------------------------------------------------------------
    // Utilitários
    // ------------------------------------------------------------------------------------------

    private Long episodioDe(EventoMessage evento) {
        if (evento.getEpisodioId() == null) {
            throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem episodioId");
        }
        return evento.getEpisodioId();
    }

    private String textoDe(EventoMessage evento, String campo) {
        Object valor = evento.getDados() == null ? null : evento.getDados().get(campo);
        if (valor == null || valor.toString().isBlank()) {
            throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem '" + campo + "' em dados");
        }
        return valor.toString();
    }

    private LocalDate dataDe(EventoMessage evento, String campo) {
        try {
            return LocalDate.parse(textoDe(evento, campo));
        } catch (java.time.format.DateTimeParseException e) {
            throw new EventoInvalidoException("Data inválida em '" + campo + "' do evento " + evento.getTipo());
        }
    }

    /** Dados comuns dos eventos publicados: a especialidade do caso mais os pares chave/valor indicados. */
    private Map<String, Object> dados(CasoIncumprimento caso, Object... paresChaveValor) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("especialidade", caso.getEspecialidade());
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            dados.put(paresChaveValor[i].toString(), paresChaveValor[i + 1]);
        }
        return dados;
    }
}
```

O ficheiro tem **duas partes**. Lê-as por esta ordem:

### Parte 1 — eventos recebidos (`tratarEvento`)

| Evento | O que acontece |
|---|---|
| `prazo.em-risco` | Abre caso **EM_RISCO** (nível 0). **Não publica** nada (ainda não é incumprimento). |
| `prazo.ultrapassado-previsto` / `prazo.ultrapassado` | Abre caso **REGISTADO** (nível 1) e publica `caso.aberto`. |
| (os dois acima, se já existe caso `EM_RISCO` do mesmo episódio) | O caso **sobe** para o nível 1 (não se cria um segundo caso). |
| `consulta.agendada` | O caso aberto fica **RESOLVIDO** (vaga recuperada) e publica `caso.resolvido`. |
| `relogio.avancado` | Atualiza o relógio do serviço. |

- Tudo corre numa **única transação** (`@Transactional`), com **idempotência** (como no `historico`).
- **Só pode haver um caso aberto por episódio** (`findFirstByEpisodioIdAndEstadoIn(..., ABERTOS)`).

### Parte 2 — operações da API (os 3 níveis)

| Método | Regra | Se falhar |
|---|---|---|
| `alertarDiretor` (nível 2) | Só se o caso estiver **REGISTADO**. Fixa `dataLimiteDiretor` = hoje + **5 dias úteis**. Publica `caso.diretor-alertado`. | 409 |
| `proporTransferencia` (nível 3) | Só se **DIRETOR_ALERTADO** **e** já passaram os 5 dias úteis; a instituição tem de existir e atender a especialidade. Cria a transferência `PROPOSTA` com prazo de resposta. Publica `transferencia.proposta`. | 409 / 404 / 400 |
| `registarResposta` | Só se a transferência estiver `PROPOSTA`. **Aceite** → caso `RESOLVIDO` (publica `transferencia.aceite` + `caso.resolvido`). **Recusada** → **compensação**: caso `SINALIZADO_DIRECAO_CLINICA` (publica `transferencia.recusada`). | 409 |
| `expirarTransferencias` | As propostas sem resposta no prazo são dadas como **recusadas**. | — |

> 💡 **Defesa — orquestração e compensação (a parte mais difícil):** a transferência tem **vários passos e pode ter de ser desfeita**. Por isso é a **única** parte do sistema com **orquestração** (o `garantia-acesso` coordena); o resto é coreografia. Se a outra instituição recusa, a **compensação** repõe o estado: o episódio **mantém-se na lista** e o caso é **sinalizado à direção clínica** (secção 4.3 da proposta). O **timeout** (`respostaAte` + tarefa agendada) garante que a *saga* nunca fica pendurada para sempre.

**Pormenores técnicos para perceber:**

- **`switch` com `->`** (Java 14+): cada `case` é independente, sem `break`.
- **`dados(caso, "casoId", 1, ...)`** — método auxiliar que monta o mapa de dados do evento (sempre com a `especialidade`). Só identificadores e datas, **nunca dados pessoais**.
- **`@Transactional(readOnly = true)`** nas consultas: otimiza e impede escritas acidentais.
- **`@Value("${incumprimento.transferencia.prazo-resposta-dias:5}")`** — lê o prazo do `application.yml` (valor por omissão 5).

## 8.5 O listener e a tarefa agendada

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/listeners/EventosListener.java`**

```java
package com.acessoatempo.incumprimento.listeners;

import com.acessoatempo.incumprimento.config.MQConfig;
import com.acessoatempo.incumprimento.messages.EventoMessage;
import com.acessoatempo.incumprimento.services.CasosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Consumer do RabbitMQ (equivale ao {@code OrderConfirmationListener} da aula). Recebe os 5 eventos a que
 * o serviço está ligado (ver MQConfig) e entrega-os ao serviço de negócio.
 *
 * <p>Se o método terminar sem erro, o Spring envia o ACK. Se lançar exceção, repete 3 vezes e depois a
 * mensagem vai para a Dead-Letter Queue ({@code incumprimento.eventos.dlq}).
 */
@Component
public class EventosListener {

    private static final Logger log = LoggerFactory.getLogger(EventosListener.class);

    @Autowired
    private CasosService casosService;

    @RabbitListener(queues = MQConfig.QUEUE)
    public void onEvento(EventoMessage evento) {
        log.info("Evento recebido: tipo={} episodioId={} eventoId={}",
                evento.getTipo(), evento.getEpisodioId(), evento.getEventoId());
        casosService.tratarEvento(evento);
    }
}
```

📄 **`incumprimento/src/main/java/com/acessoatempo/incumprimento/services/TransferenciasScheduler.java`**

```java
package com.acessoatempo.incumprimento.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefa agendada: todos os dias, dá como recusadas as propostas de transferência a que a outra instituição
 * não respondeu no prazo. Evita que a "saga" fique pendurada para sempre (compensação por timeout).
 *
 * <p>Nota: com várias réplicas do serviço, a tarefa correria em todas. O {@code incumprimento} corre com
 * uma só réplica; se escalar, é preciso um lock (ex.: ShedLock).
 */
@Component
public class TransferenciasScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransferenciasScheduler.class);

    @Autowired
    private CasosService casosService;

    /** Cron: segundo minuto hora dia mês dia-da-semana. Aqui: todos os dias à 01:00. */
    @Scheduled(cron = "0 0 1 * * *")
    public void expirarTransferencias() {
        int expiradas = casosService.expirarTransferencias();
        log.info("Tarefa de expiração de transferências: {} propostas dadas como recusadas", expiradas);
    }
}
```

- **`@Scheduled(cron = "0 0 1 * * *")`** — todos os dias à 01:00. O cron do Spring tem **6 campos**: segundo, minuto, hora, dia, mês, dia-da-semana.
- ⚠️ Com **várias réplicas** a tarefa correria em todas. O `garantia-acesso` tem **1 réplica**; se escalar, seria preciso um *lock* (p. ex. ShedLock).

## 8.6 Testes das regras

📄 **`incumprimento/src/test/java/com/acessoatempo/incumprimento/services/CasosServiceTest.java`**

```java
package com.acessoatempo.incumprimento.services;

import com.acessoatempo.incumprimento.data.CasosRepository;
import com.acessoatempo.incumprimento.data.EventosProcessadosRepository;
import com.acessoatempo.incumprimento.data.InstituicoesRepository;
import com.acessoatempo.incumprimento.data.TransferenciasRepository;
import com.acessoatempo.incumprimento.enums.EstadoCaso;
import com.acessoatempo.incumprimento.enums.EstadoTransferencia;
import com.acessoatempo.incumprimento.exceptions.EventoInvalidoException;
import com.acessoatempo.incumprimento.exceptions.TransicaoInvalidaException;
import com.acessoatempo.incumprimento.messages.EventoMessage;
import com.acessoatempo.incumprimento.models.CasoIncumprimento;
import com.acessoatempo.incumprimento.models.Instituicao;
import com.acessoatempo.incumprimento.models.Transferencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Testa as regras dos 3 níveis com BD H2 em memória. O {@code EventosPublisher} é "falso" (mock) para
 * não precisar de RabbitMQ: com {@code verify} confirma-se que o evento certo foi publicado.
 */
@SpringBootTest
public class CasosServiceTest {

    @MockitoBean
    private EventosPublisher publisher;

    @Autowired
    private CasosService service;

    @Autowired
    private CasosRepository casosRepository;

    @Autowired
    private TransferenciasRepository transferenciasRepository;

    @Autowired
    private InstituicoesRepository instituicoesRepository;

    @Autowired
    private EventosProcessadosRepository eventosProcessadosRepository;

    @Autowired
    private RelogioService relogio;

    @BeforeEach
    void limpar() {
        transferenciasRepository.deleteAll();
        casosRepository.deleteAll();
        eventosProcessadosRepository.deleteAll();
        relogio.repor();
        if (instituicoesRepository.count() == 0) {
            instituicoesRepository.save(new Instituicao("Hospital Teste", "CARDIOLOGIA"));
            instituicoesRepository.save(new Instituicao("Clínica Teste", "ORTOPEDIA"));
        }
    }

    private EventoMessage evento(String tipo, Long episodioId, String chave, String valor) {
        Map<String, Object> dados = new HashMap<>();
        dados.put(chave, valor);
        return new EventoMessage(UUID.randomUUID().toString(), tipo, episodioId, LocalDateTime.now(), dados);
    }

    private CasoIncumprimento casoRegistado(Long episodioId) {
        service.tratarEvento(evento("tmrg.incumprido", episodioId, "especialidade", "CARDIOLOGIA"));
        return casosRepository.findFirstByEpisodioIdAndEstadoIn(episodioId, EstadoCaso.ABERTOS).orElseThrow();
    }

    @Test
    void incumpridoAbreCasoNivel1EPublicaRegistado() {
        CasoIncumprimento caso = casoRegistado(10L);

        assertEquals(EstadoCaso.REGISTADO, caso.getEstado());
        assertEquals(1, caso.getNivel());
        verify(publisher, times(1)).publicar(eq("incumprimento.registado"), eq(10L), any());
    }

    @Test
    void riscoAbreCasoSemNivelENaoPublicaNada() {
        service.tratarEvento(evento("tmrg.risco-detetado", 11L, "especialidade", "ORTOPEDIA"));

        CasoIncumprimento caso = casosRepository.findFirstByEpisodioIdAndEstadoIn(11L, EstadoCaso.ABERTOS)
                .orElseThrow();
        assertEquals(EstadoCaso.EM_RISCO, caso.getEstado());
        assertEquals(0, caso.getNivel());
        verify(publisher, never()).publicar(any(), any(), any());
    }

    @Test
    void riscoQueDepoisFalhaSobeParaNivel1() {
        service.tratarEvento(evento("tmrg.risco-detetado", 12L, "especialidade", "ORTOPEDIA"));
        service.tratarEvento(evento("tmrg.incumprido", 12L, "especialidade", "ORTOPEDIA"));

        assertEquals(1, casosRepository.count());   // o mesmo caso, não um segundo
        CasoIncumprimento caso = casosRepository.findFirstByEpisodioIdAndEstadoIn(12L, EstadoCaso.ABERTOS)
                .orElseThrow();
        assertEquals(EstadoCaso.REGISTADO, caso.getEstado());
        verify(publisher, times(1)).publicar(eq("incumprimento.registado"), eq(12L), any());
    }

    @Test
    void eventoRepetidoNaoCriaSegundoCaso() {
        EventoMessage e = evento("tmrg.incumprido", 13L, "especialidade", "CARDIOLOGIA");
        service.tratarEvento(e);
        service.tratarEvento(e);

        assertEquals(1, casosRepository.count());
        verify(publisher, times(1)).publicar(eq("incumprimento.registado"), eq(13L), any());
    }

    @Test
    void eventoSemEspecialidadeEInvalido() {
        EventoMessage e = new EventoMessage(UUID.randomUUID().toString(), "tmrg.incumprido", 14L,
                LocalDateTime.now(), new HashMap<>());
        assertThrows(EventoInvalidoException.class, () -> service.tratarEvento(e));
    }

    @Test
    void alertarDiretorDa5DiasUteis() {
        CasoIncumprimento caso = casoRegistado(20L);
        relogio.definir(LocalDate.of(2026, 12, 21));   // segunda-feira; 25/12 é feriado

        CasoIncumprimento alertado = service.alertarDiretor(caso.getId());

        assertEquals(EstadoCaso.DIRETOR_ALERTADO, alertado.getEstado());
        assertEquals(2, alertado.getNivel());
        assertEquals(LocalDate.of(2026, 12, 29), alertado.getDataLimiteDiretor());
        verify(publisher).publicar(eq("incumprimento.diretor-alertado"), eq(20L), any());
    }

    @Test
    void alertarDuasVezesDa409() {
        CasoIncumprimento caso = casoRegistado(21L);
        service.alertarDiretor(caso.getId());
        assertThrows(TransicaoInvalidaException.class, () -> service.alertarDiretor(caso.getId()));
    }

    @Test
    void transferenciaSoDepoisDosCincoDiasUteis() {
        CasoIncumprimento caso = casoRegistado(22L);
        relogio.definir(LocalDate.of(2026, 11, 16));
        service.alertarDiretor(caso.getId());          // limite: 23/11/2026
        Integer instituicao = instituicoesRepository.findByEspecialidade("CARDIOLOGIA").get(0).getId();

        // Ainda dentro do prazo do diretor: 409.
        assertThrows(TransicaoInvalidaException.class, () -> service.proporTransferencia(caso.getId(), instituicao));

        relogio.definir(LocalDate.of(2026, 11, 24));
        Transferencia t = service.proporTransferencia(caso.getId(), instituicao);

        assertEquals(EstadoTransferencia.PROPOSTA, t.getEstado());
        assertEquals(LocalDate.of(2026, 11, 29), t.getRespostaAte());
        assertEquals(3, casosRepository.findById(caso.getId()).orElseThrow().getNivel());
        verify(publisher).publicar(eq("transferencia.proposta"), eq(22L), any());
    }

    @Test
    void transferenciaAceiteResolveOCaso() {
        Transferencia t = transferenciaProposta(23L);

        service.registarResposta(t.getId(), true);

        assertEquals(EstadoCaso.RESOLVIDO, casosRepository.findById(t.getCaso().getId()).orElseThrow().getEstado());
        verify(publisher).publicar(eq("transferencia.aceite"), eq(23L), any());
        verify(publisher).publicar(eq("incumprimento.resolvido"), eq(23L), any());
    }

    @Test
    void transferenciaRecusadaSinalizaADirecaoClinica() {
        Transferencia t = transferenciaProposta(24L);

        service.registarResposta(t.getId(), false);

        assertEquals(EstadoCaso.SINALIZADO_DIRECAO_CLINICA,
                casosRepository.findById(t.getCaso().getId()).orElseThrow().getEstado());
        verify(publisher).publicar(eq("transferencia.recusada"), eq(24L), any());
        // Responder duas vezes à mesma proposta não é permitido.
        assertThrows(TransicaoInvalidaException.class, () -> service.registarResposta(t.getId(), true));
    }

    @Test
    void propostaSemRespostaNoPrazoExpiraComoRecusada() {
        Transferencia t = transferenciaProposta(25L);   // respostaAte = 29/11/2026
        relogio.definir(LocalDate.of(2026, 11, 30));

        assertEquals(1, service.expirarTransferencias());

        assertEquals(EstadoTransferencia.RECUSADA,
                transferenciasRepository.findById(t.getId()).orElseThrow().getEstado());
        verify(publisher).publicar(eq("transferencia.recusada"), eq(25L), any());
    }

    @Test
    void consultaAgendadaResolveOCasoAberto() {
        casoRegistado(26L);
        service.tratarEvento(evento("consulta.agendada", 26L, "dataConsulta", "2026-12-10"));

        assertEquals(EstadoCaso.RESOLVIDO, casosRepository.findAll().get(0).getEstado());
        verify(publisher).publicar(eq("incumprimento.resolvido"), eq(26L), any());
    }

    @Test
    void relogioAvancadoMudaADataDoServico() {
        service.tratarEvento(evento("relogio.avancado", null, "data", "2027-02-01"));
        assertEquals(LocalDate.of(2027, 2, 1), relogio.hoje());
    }

    /** Leva um caso até ao nível 3 com uma transferência proposta (data simulada: 24/11/2026). */
    private Transferencia transferenciaProposta(Long episodioId) {
        CasoIncumprimento caso = casoRegistado(episodioId);
        relogio.definir(LocalDate.of(2026, 11, 16));
        service.alertarDiretor(caso.getId());
        relogio.definir(LocalDate.of(2026, 11, 24));
        Integer instituicao = instituicoesRepository.findByEspecialidade("CARDIOLOGIA").get(0).getId();
        return service.proporTransferencia(caso.getId(), instituicao);
    }
}
```

- **`@MockitoBean EventosPublisher`** — substitui o publisher por um **falso** (*mock*): não precisa de RabbitMQ e permite **`verify(...)`** que o evento certo foi publicado (e quantas vezes).
- ⚠️ Em Spring Boot 3.5 é `@MockitoBean` (nas aulas era `@MockBean`).
- Cada teste conta uma regra do negócio: nível 1, risco sem evento, subida de risco para nível 1, idempotência, evento inválido, 5 dias úteis com feriado, 409 por duplicado, transferência só depois do prazo, aceite, recusada, expirada, `consulta.agendada` e relógio.

## ✅ Checklist do capítulo 08

- [ ] `DiasUteisServiceTest` (4 testes) e `CasosServiceTest` (13 testes) a passar
- [ ] Sabes desenhar no quadro o diagrama de estados do caso (capítulo 06, secção 6.1) e dizer que evento cada seta publica
- [ ] Sabes explicar: orquestração vs coreografia, compensação, timeout, idempotência, "evento só depois do commit"
