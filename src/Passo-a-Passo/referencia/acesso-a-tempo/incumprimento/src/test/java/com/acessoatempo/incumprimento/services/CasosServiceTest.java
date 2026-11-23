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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

    /**
     * Cria um evento com o envelope do grupo (eventoId, dataHoraEvento...) e UM campo de negócio. O tipo seria a
     * routing key da mensagem; aqui define-se diretamente. {@code episodioId == null} cria um evento sem episódio.
     */
    private EventoMessage evento(String tipo, Long episodioId, String campo, String valor) {
        EventoMessage.EventoMessageBuilder evento = EventoMessage.builder()
                .eventoId(UUID.randomUUID().toString())
                .tipo(tipo)
                .dataHoraEvento(LocalDateTime.now())
                .pedidoId(episodioId);   // o tmrg chama "pedidoId" ao identificador do episódio
        switch (campo) {
            case "especialidade" -> evento.especialidade(valor);
            case "dataConsulta" -> evento.dataConsulta(LocalDate.parse(valor));
            case "dataAtual" -> evento.dataAtual(LocalDate.parse(valor));
            case "data" -> evento.data(LocalDate.parse(valor));
            default -> throw new IllegalArgumentException("Campo de teste desconhecido: " + campo);
        }
        return evento.build();
    }

    /** Confirma que o evento publicado é do episódio indicado (substitui o antigo {@code eq(episodioId)}). */
    private static EventoMessage comEpisodio(long episodioId) {
        return argThat(corpo -> corpo != null && Long.valueOf(episodioId).equals(corpo.getEpisodioId()));
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
        verify(publisher, times(1)).publicar(eq("incumprimento.registado"), comEpisodio(10L));
    }

    @Test
    void riscoAbreCasoSemNivelENaoPublicaNada() {
        service.tratarEvento(evento("tmrg.risco-detetado", 11L, "especialidade", "ORTOPEDIA"));

        CasoIncumprimento caso = casosRepository.findFirstByEpisodioIdAndEstadoIn(11L, EstadoCaso.ABERTOS)
                .orElseThrow();
        assertEquals(EstadoCaso.EM_RISCO, caso.getEstado());
        assertEquals(0, caso.getNivel());
        verify(publisher, never()).publicar(any(), any());
    }

    @Test
    void riscoQueDepoisFalhaSobeParaNivel1() {
        service.tratarEvento(evento("tmrg.risco-detetado", 12L, "especialidade", "ORTOPEDIA"));
        service.tratarEvento(evento("tmrg.incumprido", 12L, "especialidade", "ORTOPEDIA"));

        assertEquals(1, casosRepository.count());   // o mesmo caso, não um segundo
        CasoIncumprimento caso = casosRepository.findFirstByEpisodioIdAndEstadoIn(12L, EstadoCaso.ABERTOS)
                .orElseThrow();
        assertEquals(EstadoCaso.REGISTADO, caso.getEstado());
        verify(publisher, times(1)).publicar(eq("incumprimento.registado"), comEpisodio(12L));
    }

    @Test
    void eventoRepetidoNaoCriaSegundoCaso() {
        EventoMessage e = evento("tmrg.incumprido", 13L, "especialidade", "CARDIOLOGIA");
        service.tratarEvento(e);
        service.tratarEvento(e);

        assertEquals(1, casosRepository.count());
        verify(publisher, times(1)).publicar(eq("incumprimento.registado"), comEpisodio(13L));
    }

    @Test
    void eventoSemEspecialidadeEInvalido() {
        EventoMessage e = EventoMessage.builder().eventoId(UUID.randomUUID().toString()).tipo("tmrg.incumprido")
                .pedidoId(14L).dataHoraEvento(LocalDateTime.now()).build();
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
        verify(publisher).publicar(eq("incumprimento.diretor-alertado"), comEpisodio(20L));
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
        verify(publisher).publicar(eq("transferencia.proposta"), comEpisodio(22L));
    }

    @Test
    void transferenciaAceiteResolveOCaso() {
        Transferencia t = transferenciaProposta(23L);

        service.registarResposta(t.getId(), true);

        assertEquals(EstadoCaso.RESOLVIDO, casosRepository.findById(t.getCaso().getId()).orElseThrow().getEstado());
        verify(publisher).publicar(eq("transferencia.aceite"), comEpisodio(23L));
        verify(publisher).publicar(eq("incumprimento.resolvido"), comEpisodio(23L));
    }

    @Test
    void transferenciaRecusadaSinalizaADirecaoClinica() {
        Transferencia t = transferenciaProposta(24L);

        service.registarResposta(t.getId(), false);

        assertEquals(EstadoCaso.SINALIZADO_DIRECAO_CLINICA,
                casosRepository.findById(t.getCaso().getId()).orElseThrow().getEstado());
        verify(publisher).publicar(eq("transferencia.recusada"), comEpisodio(24L));
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
        verify(publisher).publicar(eq("transferencia.recusada"), comEpisodio(25L));
    }

    @Test
    void consultaAgendadaResolveOCasoAberto() {
        casoRegistado(26L);
        service.tratarEvento(evento("consulta.agendada", 26L, "dataConsulta", "2026-12-10"));

        assertEquals(EstadoCaso.RESOLVIDO, casosRepository.findAll().get(0).getEstado());
        verify(publisher).publicar(eq("incumprimento.resolvido"), comEpisodio(26L));
    }

    @Test
    void relogioAvancadoMudaADataDoServico() {
        // "dataAtual" é o nome do campo no RelogioDTO do tmrg
        service.tratarEvento(evento("relogio.avancado", null, "dataAtual", "2027-02-01"));
        assertEquals(LocalDate.of(2027, 2, 1), relogio.hoje());
    }

    @Test
    void relogioAvancadoAindaAceitaOCampoData() {
        service.tratarEvento(evento("relogio.avancado", null, "data", "2027-03-01"));
        assertEquals(LocalDate.of(2027, 3, 1), relogio.hoje());
    }

    @Test
    void relogioAvancadoSemDataEInvalido() {
        EventoMessage semData = EventoMessage.builder().eventoId(UUID.randomUUID().toString())
                .tipo("relogio.avancado").dataHoraEvento(LocalDateTime.now()).build();
        assertThrows(EventoInvalidoException.class, () -> service.tratarEvento(semData));
    }

    @Test
    void oIdentificadorPodeVirComoPedidoIdOuComoEpisodioId() {
        // como o tmrg: só "pedidoId"
        service.tratarEvento(evento("tmrg.incumprido", 30L, "especialidade", "CARDIOLOGIA"));
        // como a proposta: só "episodioId"
        EventoMessage comEpisodioId = EventoMessage.builder().eventoId(UUID.randomUUID().toString())
                .tipo("tmrg.incumprido").dataHoraEvento(LocalDateTime.now()).episodioId(31L)
                .especialidade("ORTOPEDIA").build();
        service.tratarEvento(comEpisodioId);

        assertEquals(2, casosRepository.count());
        assertEquals(30L, casosRepository.findFirstByEpisodioIdAndEstadoIn(30L, EstadoCaso.ABERTOS)
                .orElseThrow().getEpisodioId());
        assertEquals(31L, casosRepository.findFirstByEpisodioIdAndEstadoIn(31L, EstadoCaso.ABERTOS)
                .orElseThrow().getEpisodioId());
    }

    @Test
    void eventoSemEpisodioNemPedidoEInvalido() {
        EventoMessage semId = EventoMessage.builder().eventoId(UUID.randomUUID().toString())
                .tipo("tmrg.incumprido").dataHoraEvento(LocalDateTime.now()).especialidade("CARDIOLOGIA").build();
        assertThrows(EventoInvalidoException.class, () -> service.tratarEvento(semId));
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
