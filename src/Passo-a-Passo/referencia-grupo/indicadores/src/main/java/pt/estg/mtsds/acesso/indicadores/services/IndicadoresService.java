package pt.estg.mtsds.acesso.indicadores.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pt.estg.mtsds.acesso.indicadores.data.CumprimentoProjection;
import pt.estg.mtsds.acesso.indicadores.data.EventoProcessadoRepository;
import pt.estg.mtsds.acesso.indicadores.data.IndicadorSemanalRepository;
import pt.estg.mtsds.acesso.indicadores.dtos.CumprimentoDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.ListaIndicadorDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.ProjecaoDTO;
import pt.estg.mtsds.acesso.indicadores.dtos.TriagemDTO;
import pt.estg.mtsds.acesso.indicadores.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.indicadores.exceptions.EventoInvalidoException;
import pt.estg.mtsds.acesso.indicadores.messages.EventoMessage;
import pt.estg.mtsds.acesso.indicadores.messages.RoutingKeys;
import pt.estg.mtsds.acesso.indicadores.models.EventoProcessado;
import pt.estg.mtsds.acesso.indicadores.models.IndicadorSemanal;

/**
 * Regras de negócio do serviço indicadores: (1) atualizar os contadores a
 * partir dos eventos e (2) calcular os indicadores pedidos pelos endpoints. O
 * controller só recebe pedidos e devolve respostas; a lógica fica aqui.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndicadoresService {

	private static final String SEMANA_REGEX = "\\d{4}-W\\d{2}";

	private final IndicadorSemanalRepository indicadorRepository;

	private final EventoProcessadoRepository eventoProcessadoRepository;

	// ---------------------------------------------------------------------
	// 1) Processamento de eventos (chamado pelo EventosListener)
	// ---------------------------------------------------------------------

	/**
	 * Processa um evento. A atualização do contador e o registo do evento como
	 * processado acontecem na mesma transação: ficam os dois gravados ou nenhum.
	 *
	 * @param evento
	 *            o evento recebido; o tipo é a routing key
	 */
	@Transactional
	public void processar(EventoMessage evento) {
		if (evento == null || evento.getEventoId() == null || evento.getTipo() == null
				|| evento.getDataHoraEvento() == null) {
			throw new EventoInvalidoException("Evento sem eventoId, tipo (routing key) ou dataHoraEvento");
		}

		// Idempotência: o RabbitMQ pode entregar o mesmo evento mais do que uma vez.
		if (eventoProcessadoRepository.existsById(evento.getEventoId())) {
			log.info("Evento {} ({}) já processado: ignorado", evento.getEventoId(), evento.getTipo());
			return;
		}

		switch (evento.getTipo()) {
			case RoutingKeys.EPISODIO_INSCRITO -> {
				IndicadorSemanal linha = linhaDe(evento);
				linha.setEntradas(linha.getEntradas() + 1);
				indicadorRepository.save(linha);
			}
			case RoutingKeys.EPISODIO_SAIDA -> {
				IndicadorSemanal linha = linhaDe(evento);
				linha.setSaidas(linha.getSaidas() + 1);
				indicadorRepository.save(linha);
			}
			case RoutingKeys.TMRG_INCUMPRIDO -> {
				IndicadorSemanal linha = linhaDe(evento);
				linha.setIncumpridos(linha.getIncumpridos() + 1);
				indicadorRepository.save(linha);
			}
			case RoutingKeys.PEDIDO_TRIADO -> {
				IndicadorSemanal linha = linhaDe(evento);
				LocalDate dataPedido = dataPedidoDe(evento);
				long dias = ChronoUnit.DAYS.between(dataPedido, evento.getDataHoraEvento().toLocalDate());
				linha.setTriagens(linha.getTriagens() + 1);
				linha.setDiasTriagemTotal(linha.getDiasTriagemTotal() + Math.max(dias, 0));
				indicadorRepository.save(linha);
			}
			default ->
				// Os restantes eventos fazem parte do contrato dos 17 eventos, mas ainda
				// não alteram nenhum contador.
				log.debug("Evento {} sem efeito nos contadores atuais", evento.getTipo());
		}

		eventoProcessadoRepository
				.save(EventoProcessado.builder().eventoId(evento.getEventoId()).tipo(evento.getTipo()).build());
	}

	/** Encontra (ou cria) a linha da especialidade e da semana do evento. */
	private IndicadorSemanal linhaDe(EventoMessage evento) {
		String esp = evento.getEspecialidade();
		if (esp == null || esp.isBlank()) {
			throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem 'especialidade'");
		}
		String semana = semanaIso(evento.getDataHoraEvento());
		return indicadorRepository.findByEspecialidadeAndSemana(esp, semana)
				.orElseGet(() -> IndicadorSemanal.builder().especialidade(esp).semana(semana).build());
	}

	/** Data em que o pedido foi registado (campo dataPedido de pedido.triado). */
	private LocalDate dataPedidoDe(EventoMessage evento) {
		if (evento.getDataPedido() == null) {
			throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem 'dataPedido'");
		}
		return evento.getDataPedido();
	}

	/** Converte uma data na semana ISO, no formato 2026-W47. */
	static String semanaIso(LocalDateTime data) {
		int ano = data.get(IsoFields.WEEK_BASED_YEAR);
		int semana = data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		return String.format("%d-W%02d", ano, semana);
	}

	// ---------------------------------------------------------------------
	// 2) Cálculo dos indicadores (chamado pelo IndicadoresController)
	// ---------------------------------------------------------------------

	/**
	 * Tamanho da lista, entradas e saídas. Sem semana, usa todo o período.
	 *
	 * @param especialidade
	 *            obrigatória
	 * @param semana
	 *            opcional, no formato 2026-W47
	 * @return os indicadores de lista
	 */
	@Transactional(readOnly = true)
	public ListaIndicadorDTO lista(String especialidade, String semana) {
		exigirEspecialidade(especialidade);
		if (semana != null && !semana.matches(SEMANA_REGEX)) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO", "Semana inválida: use o formato 2026-W47");
		}
		long entradas = 0;
		long saidas = 0;
		long entradasTotal = 0;
		long saidasTotal = 0;
		for (IndicadorSemanal l : indicadorRepository.findByEspecialidade(especialidade)) {
			// Comparar o texto 2026-W05 funciona: o formato é ordenável.
			if (semana == null || l.getSemana().compareTo(semana) <= 0) {
				entradasTotal += l.getEntradas();
				saidasTotal += l.getSaidas();
			}
			if (semana == null || l.getSemana().equals(semana)) {
				entradas += l.getEntradas();
				saidas += l.getSaidas();
			}
		}
		return new ListaIndicadorDTO(especialidade, semana, entradas, saidas, entradasTotal - saidasTotal);
	}

	/**
	 * Taxa de cumprimento por especialidade num período.
	 *
	 * @param periodo
	 *            ano (2026) ou semana (2026-W47)
	 * @return uma linha por especialidade
	 */
	@Transactional(readOnly = true)
	public List<CumprimentoDTO> cumprimento(String periodo) {
		if (periodo == null || !periodo.matches("\\d{4}(-W\\d{2})?")) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO",
					"Período inválido: use um ano (2026) ou uma semana (2026-W47)");
		}
		return indicadorRepository.cumprimentoPorEspecialidade(periodo).stream().map(this::paraCumprimentoDTO).toList();
	}

	private CumprimentoDTO paraCumprimentoDTO(CumprimentoProjection p) {
		long entradas = p.getEntradas() == null ? 0 : p.getEntradas();
		long incumpridos = p.getIncumpridos() == null ? 0 : p.getIncumpridos();
		double taxa = entradas == 0 ? 100.0 : 100.0 * (entradas - incumpridos) / entradas;
		return new CumprimentoDTO(p.getEspecialidade(), entradas, incumpridos, arredondar(taxa));
	}

	/**
	 * Tempo médio entre o pedido e a triagem.
	 *
	 * @param especialidade
	 *            obrigatória
	 * @return triagens e média de dias
	 */
	@Transactional(readOnly = true)
	public TriagemDTO triagem(String especialidade) {
		exigirEspecialidade(especialidade);
		long triagens = 0;
		long dias = 0;
		for (IndicadorSemanal l : indicadorRepository.findByEspecialidade(especialidade)) {
			triagens += l.getTriagens();
			dias += l.getDiasTriagemTotal();
		}
		double media = triagens == 0 ? 0.0 : (double) dias / triagens;
		return new TriagemDTO(especialidade, triagens, arredondar(media));
	}

	/**
	 * Projeção do tamanho da lista daqui a algumas semanas, com base nas médias
	 * semanais.
	 *
	 * @param especialidade
	 *            obrigatória
	 * @param semanas
	 *            entre 1 e 52
	 * @return a projeção
	 */
	@Transactional(readOnly = true)
	public ProjecaoDTO projecao(String especialidade, int semanas) {
		exigirEspecialidade(especialidade);
		if (semanas < 1 || semanas > 52) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO", "O número de semanas deve estar entre 1 e 52");
		}
		List<IndicadorSemanal> linhas = indicadorRepository.findByEspecialidade(especialidade);
		long entradas = linhas.stream().mapToLong(IndicadorSemanal::getEntradas).sum();
		long saidas = linhas.stream().mapToLong(IndicadorSemanal::getSaidas).sum();
		long tamanhoAtual = entradas - saidas;
		double mediaEntradas = linhas.isEmpty() ? 0.0 : (double) entradas / linhas.size();
		double mediaSaidas = linhas.isEmpty() ? 0.0 : (double) saidas / linhas.size();
		double projetado = Math.max(0.0, tamanhoAtual + semanas * (mediaEntradas - mediaSaidas));
		return new ProjecaoDTO(especialidade, tamanhoAtual, arredondar(mediaEntradas), arredondar(mediaSaidas), semanas,
				arredondar(projetado));
	}

	private void exigirEspecialidade(String especialidade) {
		if (especialidade == null || especialidade.isBlank()) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO", "O parâmetro 'esp' (especialidade) é obrigatório");
		}
	}

	private double arredondar(double valor) {
		return Math.round(valor * 100.0) / 100.0;
	}
}
