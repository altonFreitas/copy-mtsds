package pt.estg.mtsds.acesso.agendamento.services;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.agendamento.data.AgendaRepository;
import pt.estg.mtsds.acesso.agendamento.data.MarcacaoRepository;
import pt.estg.mtsds.acesso.agendamento.data.VagaRepository;
import pt.estg.mtsds.acesso.agendamento.dtos.AgendaDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.CriarAgendaDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.MarcacaoDTO;
import pt.estg.mtsds.acesso.agendamento.dtos.VagaDTO;
import pt.estg.mtsds.acesso.agendamento.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.agendamento.exceptions.NaoEncontradoException;
import pt.estg.mtsds.acesso.agendamento.exceptions.RegraNegocioException;
import pt.estg.mtsds.acesso.agendamento.exceptions.TransicaoInvalidaException;
import pt.estg.mtsds.acesso.agendamento.models.Agenda;
import pt.estg.mtsds.acesso.agendamento.models.EstadoMarcacao;
import pt.estg.mtsds.acesso.agendamento.models.EstadoVaga;
import pt.estg.mtsds.acesso.agendamento.models.Marcacao;
import pt.estg.mtsds.acesso.agendamento.models.OrigemMarcacao;
import pt.estg.mtsds.acesso.agendamento.models.Vaga;

/**
 * Agendas, vagas e marcações. É aqui que se garante que cada vaga tem uma só
 * marcação e que cada episódio tem no máximo uma marcação AGENDADA.
 * <p>
 * Devolve sempre DTO, montados dentro da transação: as vagas e as marcações têm
 * relações LAZY, que não se podem ler depois de a transação fechar.
 */
@Service
@RequiredArgsConstructor
public class AgendamentoService {

	private final AgendaRepository agendaRepositorio;
	private final VagaRepository vagaRepositorio;
	private final MarcacaoRepository marcacaoRepositorio;

	/**
	 * Cria uma agenda com uma vaga LIVRE por cada hora pedida.
	 *
	 * @param dto
	 *            especialidade, dia e horas das vagas
	 * @param autor
	 *            id de quem cria a agenda
	 * @return a agenda criada, com as vagas por ordem de hora
	 * @throws RegraNegocioException
	 *             VAGA_DUPLICADA, se já houver uma vaga dessa especialidade num dos
	 *             instantes pedidos
	 */
	@Transactional
	public AgendaDTO criarAgenda(CriarAgendaDTO dto, String autor) {
		List<LocalDateTime> datasHoras = dto.horas().stream().sorted().map(dto.data()::atTime).toList();
		List<Vaga> existentes = vagaRepositorio.findByEspecialidadeAndDataHoraIn(dto.especialidade(), datasHoras);
		if (!existentes.isEmpty()) {
			List<LocalDateTime> repetidas = existentes.stream().map(Vaga::getDataHora).sorted().toList();
			throw new RegraNegocioException("VAGA_DUPLICADA", "Já existem vagas de " + dto.especialidade() + " em "
					+ repetidas + ". Retire essas horas do pedido e repita.");
		}

		Agenda agenda = agendaRepositorio
				.save(Agenda.builder().especialidade(dto.especialidade()).data(dto.data()).criadaPor(autor).build());
		List<Vaga> vagas = vagaRepositorio.saveAll(datasHoras.stream().map(dataHora -> Vaga.builder().agenda(agenda)
				.especialidade(dto.especialidade()).dataHora(dataHora).estado(EstadoVaga.LIVRE).build()).toList());

		List<VagaDTO> vagasDTO = vagas.stream().sorted(Comparator.comparing(Vaga::getDataHora))
				.map(v -> new VagaDTO(v.getId(), v.getDataHora(), v.getEstado())).toList();
		return new AgendaDTO(agenda.getId(), agenda.getEspecialidade(), agenda.getData(), vagasDTO);
	}

	/**
	 * Marca uma vaga livre para um episódio que ainda não tem marcação ativa. A
	 * vaga passa a OCUPADA; se outra marcação a ocupar ao mesmo tempo, o bloqueio
	 * otimista da vaga faz falhar uma das duas.
	 *
	 * @param episodioId
	 *            episódio da lista de espera
	 * @param vagaId
	 *            vaga a marcar
	 * @param origem
	 *            AUTOMATICA ou MANUAL
	 * @param autor
	 *            id de quem marca, ou o nome do serviço na automática
	 * @return a marcação criada
	 * @throws NaoEncontradoException
	 *             VAGA_NAO_ENCONTRADA, se a vaga não existir
	 * @throws RegraNegocioException
	 *             VAGA_OCUPADA ou EPISODIO_JA_MARCADO
	 */
	@Transactional
	public MarcacaoDTO marcar(Long episodioId, Long vagaId, OrigemMarcacao origem, String autor) {
		Vaga vaga = vagaRepositorio.findById(vagaId).orElseThrow(() -> new NaoEncontradoException("VAGA_NAO_ENCONTRADA",
				"Não existe a vaga " + vagaId + ". Confirme o id devolvido na criação da agenda."));
		if (vaga.getEstado() != EstadoVaga.LIVRE) {
			throw new RegraNegocioException("VAGA_OCUPADA",
					"A vaga " + vagaId + " já está ocupada. Escolha outra vaga livre da mesma especialidade.");
		}
		marcacaoRepositorio.marcacaoAtiva(episodioId).ifPresent(ativa -> {
			throw new RegraNegocioException("EPISODIO_JA_MARCADO", "O episódio " + episodioId + " já tem a marcação "
					+ ativa.getId() + " agendada. Registe-a ou cancele-a antes de marcar outra.");
		});

		vaga.setEstado(EstadoVaga.OCUPADA);
		Marcacao marcacao = marcacaoRepositorio
				.save(Marcacao.builder().episodioId(episodioId).vaga(vaga).origem(origem).autor(autor).build());
		return paraDTO(marcacao);
	}

	/**
	 * Regista o resultado de uma consulta agendada.
	 *
	 * @param marcacaoId
	 *            marcação a fechar
	 * @param resultado
	 *            REALIZADA ou FALTA
	 * @return a marcação com o novo estado
	 * @throws DadosInvalidosException
	 *             se o resultado não for REALIZADA nem FALTA
	 * @throws NaoEncontradoException
	 *             MARCACAO_NAO_ENCONTRADA, se a marcação não existir
	 * @throws TransicaoInvalidaException
	 *             se a marcação já não estiver AGENDADA
	 */
	@Transactional
	public MarcacaoDTO registarRealizacao(Long marcacaoId, EstadoMarcacao resultado) {
		if (resultado != EstadoMarcacao.REALIZADA && resultado != EstadoMarcacao.FALTA) {
			throw new DadosInvalidosException("RESULTADO_INVALIDO",
					"O resultado tem de ser REALIZADA ou FALTA, mas veio " + resultado + ".");
		}
		Marcacao marcacao = procurar(marcacaoId);
		if (marcacao.getEstado() != EstadoMarcacao.AGENDADA) {
			throw new TransicaoInvalidaException("A marcação " + marcacaoId + " está " + marcacao.getEstado()
					+ " e só uma marcação AGENDADA pode passar a " + resultado + ".");
		}
		marcacao.setEstado(resultado);
		return paraDTO(marcacao);
	}

	/**
	 * Lê uma marcação.
	 *
	 * @param marcacaoId
	 *            marcação a ler
	 * @return a marcação
	 * @throws NaoEncontradoException
	 *             MARCACAO_NAO_ENCONTRADA, se a marcação não existir
	 */
	@Transactional(readOnly = true)
	public MarcacaoDTO obterMarcacao(Long marcacaoId) {
		return paraDTO(procurar(marcacaoId));
	}

	private Marcacao procurar(Long marcacaoId) {
		return marcacaoRepositorio.findById(marcacaoId)
				.orElseThrow(() -> new NaoEncontradoException("MARCACAO_NAO_ENCONTRADA",
						"Não existe a marcação " + marcacaoId + ". Confirme o id da marcação."));
	}

	private static MarcacaoDTO paraDTO(Marcacao marcacao) {
		Vaga vaga = marcacao.getVaga();
		return new MarcacaoDTO(marcacao.getId(), marcacao.getEpisodioId(), vaga.getId(), vaga.getDataHora(),
				vaga.getEspecialidade(), marcacao.getEstado(), marcacao.getOrigem());
	}
}
