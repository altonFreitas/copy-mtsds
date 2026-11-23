package pt.estg.mtsds.acesso.incumprimento.services;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.incumprimento.data.CasosRepository;
import pt.estg.mtsds.acesso.incumprimento.data.EventosProcessadosRepository;
import pt.estg.mtsds.acesso.incumprimento.data.InstituicoesRepository;
import pt.estg.mtsds.acesso.incumprimento.data.TransferenciasRepository;
import pt.estg.mtsds.acesso.incumprimento.enums.EstadoCaso;
import pt.estg.mtsds.acesso.incumprimento.enums.EstadoTransferencia;
import pt.estg.mtsds.acesso.incumprimento.enums.OrigemCaso;
import pt.estg.mtsds.acesso.incumprimento.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.incumprimento.exceptions.EventoInvalidoException;
import pt.estg.mtsds.acesso.incumprimento.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.incumprimento.exceptions.TransicaoInvalidaException;
import pt.estg.mtsds.acesso.incumprimento.messages.EventoMessage;
import pt.estg.mtsds.acesso.incumprimento.models.CasoIncumprimento;
import pt.estg.mtsds.acesso.incumprimento.models.EventoProcessado;
import pt.estg.mtsds.acesso.incumprimento.models.Instituicao;
import pt.estg.mtsds.acesso.incumprimento.models.Transferencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Regras de negócio do serviço {@code incumprimento} (secção 2.1, regra R6):
 * tratar cada incumprimento em 3 níveis. (1) registar e reportar; (2) alertar o
 * diretor de serviço, que tem 5 dias úteis para reagendar; (3) propor a
 * consulta noutra instituição, com compensação se for recusada.
 *
 * <p>
 * Como na aula, o controller só recebe pedidos; toda a lógica e as exceções
 * ficam aqui.
 */
@Service
@RequiredArgsConstructor
public class CasosService {

	private static final Logger log = LoggerFactory.getLogger(CasosService.class);

	/** R6: o diretor de serviço tem 5 dias úteis para reagendar. */
	static final int DIAS_UTEIS_DIRETOR = 5;

	private final CasosRepository casosRepository;

	private final TransferenciasRepository transferenciasRepository;

	private final InstituicoesRepository instituicoesRepository;

	private final EventosProcessadosRepository eventosProcessadosRepository;

	private final EventosPublisher publisher;

	private final RelogioService relogio;

	private final DiasUteisService diasUteis;

	/**
	 * Dias que a outra instituição tem para responder (configurável no
	 * application.yml).
	 */
	@Value("${incumprimento.transferencia.prazo-resposta-dias:5}")
	private int prazoRespostaDias;

	// ------------------------------------------------------------------------------------------
	// 1) Eventos recebidos (chamado pelo EventosListener)
	// ------------------------------------------------------------------------------------------

	/**
	 * Trata um evento recebido. Tudo corre numa só transação: gravar o caso e
	 * marcar o evento como processado. Os eventos que o serviço publica só saem
	 * depois do commit (ver EventosPublisher).
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
			case "prazo.em-risco" -> abrirOuEscalar(evento, OrigemCaso.RISCO);
			case "prazo.ultrapassado-previsto" -> abrirOuEscalar(evento, OrigemCaso.INCUMPRIMENTO_PREVISTO);
			case "prazo.ultrapassado" -> abrirOuEscalar(evento, OrigemCaso.INCUMPRIDO);
			case "consulta.agendada" -> resolverPorReagendamento(evento);
			case "relogio.avancado" -> relogio.definir(dataDoRelogio(evento));
			default -> log.warn("Evento {} não é tratado por este serviço", evento.getTipo());
		}

		eventosProcessadosRepository
				.save(new EventoProcessado(evento.getEventoId(), evento.getTipo(), LocalDateTime.now()));
	}

	/**
	 * Risco, incumprimento previsto ou incumprido: abre um caso ou, se já existe um
	 * caso aberto para o episódio, só o faz subir de "em risco" para o nível 1.
	 * Nunca há dois casos abertos do mesmo episódio.
	 */
	private void abrirOuEscalar(EventoMessage evento, OrigemCaso origem) {
		Long episodioId = episodioDe(evento);
		Optional<CasoIncumprimento> aberto = casosRepository.findFirstByEpisodioIdAndEstadoIn(episodioId,
				EstadoCaso.ABERTOS);

		if (aberto.isEmpty()) {
			CasoIncumprimento caso = new CasoIncumprimento();
			caso.setEpisodioId(episodioId);
			caso.setEspecialidade(especialidadeDe(evento));
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

	/**
	 * Quando a consulta é (re)marcada, o caso aberto deste episódio fica resolvido
	 * (nível 2: "vaga recuperada").
	 */
	private void resolverPorReagendamento(EventoMessage evento) {
		Long episodioId = episodioDe(evento);
		casosRepository.findFirstByEpisodioIdAndEstadoIn(episodioId, EstadoCaso.ABERTOS)
				.ifPresent(caso -> resolver(caso, "CONSULTA_AGENDADA"));
	}

	private void resolver(CasoIncumprimento caso, String motivo) {
		caso.setEstado(EstadoCaso.RESOLVIDO);
		casosRepository.save(caso);
		publisher.publicar("caso.resolvido", corpo(caso).motivo(motivo).build());
	}

	private void publicarRegistado(CasoIncumprimento caso) {
		publisher.publicar("caso.aberto", corpo(caso).origem(caso.getOrigem().name()).build());
	}

	// ------------------------------------------------------------------------------------------
	// 2) Operações da API REST (chamadas pelos controllers)
	// ------------------------------------------------------------------------------------------

	/**
	 * Lista os casos, filtrando por especialidade e/ou nível (ambos opcionais), já
	 * paginados.
	 */
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
		return casosRepository.findById(id)
				.orElseThrow(() -> new NaoEncontradoException("CASO_NAO_ENCONTRADO", "Caso " + id + " não encontrado"));
	}

	/**
	 * Nível 2: alerta o diretor de serviço e fixa o prazo de 5 dias úteis para
	 * reagendar.
	 */
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

		publisher.publicar("caso.diretor-alertado", corpo(caso).dataLimiteDiretor(caso.getDataLimiteDiretor()).build());
		return caso;
	}

	/**
	 * Nível 3: propõe a consulta noutra instituição. Só é possível se o caso
	 * estiver no nível 2 e os 5 dias úteis do diretor já tiverem passado sem
	 * marcação.
	 */
	@Transactional
	public Transferencia proporTransferencia(Integer casoId, Integer instituicaoId) {
		CasoIncumprimento caso = obter(casoId);
		if (caso.getEstado() != EstadoCaso.DIRETOR_ALERTADO) {
			throw new TransicaoInvalidaException("O caso " + casoId + " está em " + caso.getEstado()
					+ ". A transferência só pode ser proposta a partir do nível 2 (DIRETOR_ALERTADO).");
		}
		if (!relogio.hoje().isAfter(caso.getDataLimiteDiretor())) {
			throw new TransicaoInvalidaException("O diretor de serviço ainda tem até " + caso.getDataLimiteDiretor()
					+ " para reagendar o caso " + casoId + ".");
		}
		Instituicao instituicao = instituicoesRepository.findById(instituicaoId)
				.orElseThrow(() -> new NaoEncontradoException("INSTITUICAO_NAO_ENCONTRADA",
						"Instituição " + instituicaoId + " não encontrada"));
		if (!instituicao.getEspecialidade().equals(caso.getEspecialidade())) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO",
					"A instituição " + instituicaoId + " não atende a especialidade " + caso.getEspecialidade() + ".");
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

		publisher.publicar("transferencia.proposta", corpo(caso).transferenciaId(transferencia.getId())
				.instituicaoId(instituicao.getId()).respostaAte(transferencia.getRespostaAte()).build());
		return transferencia;
	}

	/**
	 * Regista a resposta da outra instituição. Aceite: a transferência fica
	 * concluída e o caso resolvido. Recusada: COMPENSAÇÃO; o episódio mantém-se na
	 * lista e o caso é sinalizado à direção clínica.
	 */
	@Transactional
	public Transferencia registarResposta(Integer transferenciaId, boolean aceite) {
		Transferencia transferencia = transferenciasRepository.findById(transferenciaId)
				.orElseThrow(() -> new NaoEncontradoException("TRANSFERENCIA_NAO_ENCONTRADA",
						"Transferência " + transferenciaId + " não encontrada"));
		if (transferencia.getEstado() != EstadoTransferencia.PROPOSTA) {
			throw new TransicaoInvalidaException(
					"A transferência " + transferenciaId + " já tem resposta (" + transferencia.getEstado() + ").");
		}
		CasoIncumprimento caso = transferencia.getCaso();
		transferencia.setDataResposta(relogio.hoje());

		if (aceite) {
			transferencia.setEstado(EstadoTransferencia.ACEITE);
			transferenciasRepository.save(transferencia);
			publisher.publicar("transferencia.aceite", corpo(caso).transferenciaId(transferencia.getId())
					.instituicaoId(transferencia.getInstituicao().getId()).build());
			resolver(caso, "TRANSFERIDO");
		} else {
			transferencia.setEstado(EstadoTransferencia.RECUSADA);
			transferenciasRepository.save(transferencia);
			caso.setEstado(EstadoCaso.SINALIZADO_DIRECAO_CLINICA);
			casosRepository.save(caso);
			publisher.publicar("transferencia.recusada", corpo(caso).transferenciaId(transferencia.getId())
					.instituicaoId(transferencia.getInstituicao().getId()).build());
		}
		return transferencia;
	}

	@Transactional(readOnly = true)
	public List<Instituicao> instituicoes(String especialidade) {
		if (especialidade == null || especialidade.isBlank()) {
			throw new DadosInvalidosException("PARAMETRO_INVALIDO", "O parâmetro 'esp' (especialidade) é obrigatório");
		}
		return instituicoesRepository.findByEspecialidade(especialidade);
	}

	/**
	 * Dá como recusadas as propostas sem resposta cujo prazo terminou. Devolve
	 * quantas foram.
	 */
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

	/**
	 * Identificador do episódio do evento: aceita {@code episodioId} ou
	 * {@code pedidoId} (o nome usado pelo tmrg).
	 */
	private Long episodioDe(EventoMessage evento) {
		if (evento.identificador() == null) {
			throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem episodioId nem pedidoId");
		}
		return evento.identificador();
	}

	private String especialidadeDe(EventoMessage evento) {
		if (evento.getEspecialidade() == null || evento.getEspecialidade().isBlank()) {
			throw new EventoInvalidoException("Evento " + evento.getTipo() + " sem 'especialidade'");
		}
		return evento.getEspecialidade();
	}

	/**
	 * Data do evento {@code relogio.avancado}: campo {@code dataAtual} (ou
	 * {@code data}), formato AAAA-MM-DD.
	 */
	private LocalDate dataDoRelogio(EventoMessage evento) {
		if (evento.dataDoRelogio() == null) {
			throw new EventoInvalidoException("Evento relogio.avancado sem 'dataAtual'");
		}
		return evento.dataDoRelogio();
	}

	/**
	 * Ponto de partida de todos os eventos publicados: episódio, especialidade e
	 * caso (só identificadores).
	 */
	private EventoMessage.EventoMessageBuilder corpo(CasoIncumprimento caso) {
		return EventoMessage.builder().episodioId(caso.getEpisodioId()).especialidade(caso.getEspecialidade())
				.casoId(caso.getId());
	}
}
