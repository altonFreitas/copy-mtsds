package pt.estg.mtsds.acesso.indicadores.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pt.estg.mtsds.acesso.indicadores.data.EventoProcessadoRepository;
import pt.estg.mtsds.acesso.indicadores.data.IndicadorSemanalRepository;
import pt.estg.mtsds.acesso.indicadores.dtos.CumprimentoDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.ListaIndicadorDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.ProjecaoDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.TriagemDTO;
import pt.estg.mtsds.acesso.indicadores.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.indicadores.exceptions.EventoInvalidoException;
import pt.estg.mtsds.acesso.indicadores.messages.EventoMessage;

/**
 * Testes da lógica de negócio dos indicadores, com a BD H2 em memória (perfil
 * dev).
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
class IndicadoresServiceTest {

	// 17 de novembro de 2026 é uma terça-feira da semana ISO 2026-W47.
	private static final LocalDateTime SEMANA_47 = LocalDateTime.of(2026, 11, 17, 10, 0);
	private static final LocalDateTime SEMANA_48 = LocalDateTime.of(2026, 11, 24, 10, 0);

	@Autowired
	private IndicadoresService service;

	@Autowired
	private IndicadorSemanalRepository indicadorRepository;

	@Autowired
	private EventoProcessadoRepository eventoProcessadoRepository;

	@BeforeEach
	void limpar() {
		indicadorRepository.deleteAll();
		eventoProcessadoRepository.deleteAll();
	}

	/**
	 * Evento de teste com o envelope do grupo. O tipo seria a routing key; aqui
	 * define-se diretamente. Com especialidade nula, o evento é inválido.
	 */
	private EventoMessage evento(String tipo, LocalDateTime quando, String especialidade) {
		return EventoMessage.builder().eventoId(UUID.randomUUID().toString()).tipo(tipo).dataHoraEvento(quando)
				.pedidoId(1L).especialidade(especialidade).build();
	}

	@Test
	void entradasESaidasAtualizamOTamanhoDaLista() {
		service.processar(evento("episodio.inscrito", SEMANA_47, "CARDIOLOGIA"));
		service.processar(evento("episodio.inscrito", SEMANA_47, "CARDIOLOGIA"));
		service.processar(evento("episodio.inscrito", SEMANA_47, "CARDIOLOGIA"));
		service.processar(evento("episodio.saida", SEMANA_48, "CARDIOLOGIA"));

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
		EventoMessage e = evento("episodio.inscrito", SEMANA_47, "ORTOPEDIA");
		service.processar(e);
		service.processar(e); // mesmo eventoId: não pode contar duas vezes

		assertEquals(1, service.lista("ORTOPEDIA", null).getEntradas());
	}

	@Test
	void eventoSemEspecialidadeEInvalido() {
		EventoMessage e = evento("episodio.inscrito", SEMANA_47, null);
		assertThrows(EventoInvalidoException.class, () -> service.processar(e));
	}

	@Test
	void taxaDeCumprimentoEMediaDeTriagem() {
		for (int i = 0; i < 4; i++) {
			service.processar(evento("episodio.inscrito", SEMANA_47, "UROLOGIA"));
		}
		service.processar(evento("tmrg.incumprido", SEMANA_47, "UROLOGIA"));

		List<CumprimentoDTO> cumprimento = service.cumprimento("2026");
		assertEquals(1, cumprimento.size());
		assertEquals(75.0, cumprimento.get(0).getTaxaCumprimento());

		// Triagem 10 dias depois do pedido (pedido a 07/11, triagem a 17/11).
		EventoMessage triado = evento("pedido.triado", SEMANA_47, "UROLOGIA");
		triado.setDataPedido(LocalDate.of(2026, 11, 7));
		service.processar(triado);
		TriagemDTO triagem = service.triagem("UROLOGIA");
		assertEquals(1, triagem.getTriagens());
		assertEquals(10.0, triagem.getMediaDiasTriagem());
	}

	@Test
	void projecaoUsaAsMediasSemanais() {
		// Semana 47: 4 entradas. Semana 48: 2 entradas e 2 saídas. Atual = 6 - 2 = 4;
		// médias 3 e 1.
		for (int i = 0; i < 4; i++) {
			service.processar(evento("episodio.inscrito", SEMANA_47, "OFTALMOLOGIA"));
		}
		for (int i = 0; i < 2; i++) {
			service.processar(evento("episodio.inscrito", SEMANA_48, "OFTALMOLOGIA"));
			service.processar(evento("episodio.saida", SEMANA_48, "OFTALMOLOGIA"));
		}
		ProjecaoDTO p = service.projecao("OFTALMOLOGIA", 4);
		assertEquals(4, p.getTamanhoAtual());
		assertEquals(3.0, p.getMediaEntradasSemana());
		assertEquals(1.0, p.getMediaSaidasSemana());
		assertEquals(12.0, p.getTamanhoProjetado()); // 4 + 4 x (3 - 1)
	}

	@Test
	void parametrosInvalidosDao400() {
		assertThrows(DadosInvalidosException.class, () -> service.lista("", null));
		assertThrows(DadosInvalidosException.class, () -> service.lista("CARDIOLOGIA", "semana-47"));
		assertThrows(DadosInvalidosException.class, () -> service.projecao("CARDIOLOGIA", 0));
		assertThrows(DadosInvalidosException.class, () -> service.cumprimento("abc"));
	}
}
