package pt.estg.mtsds.acesso.gestaoacesso.data;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.estg.mtsds.acesso.gestaoacesso.models.EpisodioPrazo;
import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Acesso à tabela episodio_prazo. A chave é o id do pedido.
 */
public interface EpisodioPrazoRepository extends JpaRepository<EpisodioPrazo, Long> {

	/**
	 * Episódios abertos cujo alarme de risco ainda não tocou e cuja data do alarme
	 * já chegou.
	 *
	 * @param hoje
	 *            data da verificação
	 * @return os episódios em que o alarme deve tocar agora
	 */
	List<EpisodioPrazo> findByEncerradoEmIsNullAndAlarmeRiscoEmIsNullAndDataAlarmeLessThanEqual(LocalDate hoje);

	/**
	 * Episódios abertos, com uma situação diferente da indicada e com a data-limite
	 * antes de uma data. O próprio dia-limite fica de fora.
	 *
	 * @param situacao
	 *            situação a excluir
	 * @param data
	 *            data a comparar com a data-limite
	 * @return os episódios abertos noutra situação cujo prazo já acabou
	 */
	List<EpisodioPrazo> findByEncerradoEmIsNullAndSituacaoNotAndDataLimiteBefore(SituacaoPrazo situacao,
			LocalDate data);

	/**
	 * Episódios de uma especialidade à espera de marcação: sem vaga marcada e sem
	 * encerramento.
	 *
	 * @param especialidade
	 *            especialidade pedida
	 * @return os episódios dessa especialidade ainda sem consulta marcada
	 */
	List<EpisodioPrazo> findByEspecialidadeAndVagaIdIsNullAndEncerradoEmIsNull(String especialidade);

	/**
	 * Episódios com uma vaga reservada pela marcação automática, mas ainda sem a
	 * confirmação do agendamento.
	 *
	 * @param vagaId
	 *            vaga reservada
	 * @return os episódios com essa vaga e sem marcadoEm
	 */
	List<EpisodioPrazo> findByVagaIdAndMarcadoEmIsNull(Long vagaId);
}
