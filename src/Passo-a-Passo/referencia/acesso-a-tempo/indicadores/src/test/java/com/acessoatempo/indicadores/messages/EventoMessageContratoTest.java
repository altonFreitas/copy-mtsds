package com.acessoatempo.indicadores.messages;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de CONTRATO: o JSON que os outros serviços do grupo publicam (envelope {@code MensagemBase} + campos do
 * evento ao mesmo nível, sem "tipo" nem "dados") tem de ser lido por este serviço, e o que este serviço publica
 * tem de ter o mesmo formato. Usa o mesmo conversor do {@code MQConfig}, sem precisar de RabbitMQ.
 */
public class EventoMessageContratoTest {

    private final JacksonJsonMessageConverter conversor = new JacksonJsonMessageConverter();

    /** Faz o que o listener faz: o Spring indica o tipo esperado (o do parâmetro do método) e converte o JSON. */
    private EventoMessage ler(String json) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType("application/json");
        propriedades.setInferredArgumentType(EventoMessage.class);
        return (EventoMessage) conversor.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), propriedades));
    }

    @Test
    void leUmEventoNoFormatoDoGrupoMesmoComCamposQueNaoConhece() {
        // Como o tmrg o deve publicar: MensagemBase + campos do episódio. "situacao" e "campoFuturo" são
        // desconhecidos para este serviço e têm de ser ignorados.
        String json = """
                {"eventoId":"e-123","traceId":"abc123","autor":"sistema",
                 "dataHoraEvento":"2026-12-01T01:00:00.123456",
                 "pedidoId":101,"especialidade":"CARDIOLOGIA","prioridade":"PRIORITARIA",
                 "dataLimite":"2026-11-30","dataAlarme":"2026-11-15",
                 "situacao":"INCUMPRIDO","campoFuturo":{"x":1}}
                """;

        EventoMessage evento = ler(json);

        assertEquals("e-123", evento.getEventoId());
        assertEquals("abc123", evento.getTraceId());
        assertEquals("sistema", evento.getAutor());
        assertNotNull(evento.getDataHoraEvento());
        assertEquals(2026, evento.getDataHoraEvento().getYear());
        assertEquals(101L, evento.identificador());            // veio como pedidoId
        assertEquals("CARDIOLOGIA", evento.getEspecialidade());
        assertEquals(LocalDate.of(2026, 11, 30), evento.getDataLimite());
        assertNull(evento.getTipo());                          // o tipo não vem no JSON: é a routing key
    }

    @Test
    void leOEventoDoRelogioComODataAtualDoRelogioDTO() {
        EventoMessage evento = ler("""
                {"eventoId":"r-1","dataHoraEvento":"2026-12-01T10:00:00","dataAtual":"2027-02-01"}
                """);
        assertEquals(LocalDate.of(2027, 2, 1), evento.dataDoRelogio());
    }

    @Test
    void oQuePublicaTemOEnvelopeDoGrupoEOsCamposAoMesmoNivel() {
        EventoMessage evento = EventoMessage.builder()
                .eventoId("e-9").traceId("t-9").autor("incumprimento")
                .dataHoraEvento(java.time.LocalDateTime.of(2026, 12, 1, 10, 0))
                .tipo("incumprimento.registado")       // não pode ir no JSON
                .episodioId(101L).pedidoId(101L).especialidade("CARDIOLOGIA").casoId(7).build();

        String json = new String(conversor.toMessage(evento, new MessageProperties()).getBody(),
                StandardCharsets.UTF_8);

        // envelope
        assertTrue(json.contains("\"eventoId\":\"e-9\""), json);
        assertTrue(json.contains("\"traceId\":\"t-9\""), json);
        assertTrue(json.contains("\"autor\":\"incumprimento\""), json);
        assertTrue(json.contains("\"dataHoraEvento\""), json);
        // campos do evento ao mesmo nível, com os dois nomes do identificador
        assertTrue(json.contains("\"pedidoId\":101"), json);
        assertTrue(json.contains("\"episodioId\":101"), json);
        assertTrue(json.contains("\"especialidade\":\"CARDIOLOGIA\""), json);
        // não há "tipo" (é a routing key), nem mapa "dados", nem campos nulos
        assertFalse(json.contains("\"tipo\""), json);
        assertFalse(json.contains("\"dados\""), json);
        assertFalse(json.contains("null"), json);
    }
}
