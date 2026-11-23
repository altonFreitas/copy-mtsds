package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import pt.estg.mtsds.acesso.gestaoacesso.data.RelogioRepository;
import pt.estg.mtsds.acesso.gestaoacesso.exceptions.DadosInvalidosException;
import pt.estg.mtsds.acesso.gestaoacesso.models.Relogio;

/**
 * Data de hoje do serviço. Sem linha na tabela relogio, é a data real dada pelo
 * {@link Clock}; com linha, é a data guardada, que a demonstração pode avançar.
 */
@Service
@RequiredArgsConstructor
public class RelogioService {

	private final RelogioRepository repositorio;
	private final Clock clock;

	/**
	 * Data de hoje para o serviço.
	 *
	 * @return a data guardada no relógio, ou a data real se não houver nenhuma
	 */
	public LocalDate hoje() {
		return repositorio.findById(Relogio.ID).map(Relogio::getDataAtual).orElseGet(() -> LocalDate.now(clock));
	}

	/**
	 * Avança o relógio para uma nova data, criando a linha se ainda não existir.
	 * Avançar para o próprio dia de hoje é aceite e não muda nada.
	 *
	 * @param novaData
	 *            data para onde o relógio passa
	 * @return a data gravada
	 * @throws DadosInvalidosException
	 *             se a data for nula ou anterior a {@link #hoje()}
	 */
	@Transactional
	public LocalDate avancarPara(LocalDate novaData) {
		if (novaData == null) {
			throw new DadosInvalidosException("DATA_RELOGIO_EM_FALTA",
					"Indique a data para onde o relógio deve avançar.");
		}
		LocalDate hoje = hoje();
		if (novaData.isBefore(hoje)) {
			throw new DadosInvalidosException("DATA_RELOGIO_ANTERIOR",
					"O relógio só anda para a frente: a data " + novaData + " é anterior a hoje (" + hoje
							+ "). Indique uma data igual ou posterior, ou reponha o relógio para voltar à data real.");
		}
		Relogio relogio = repositorio.findById(Relogio.ID).orElseGet(() -> Relogio.builder().build());
		relogio.setDataAtual(novaData);
		repositorio.save(relogio);
		return novaData;
	}

	/**
	 * Apaga a data guardada, para o serviço voltar a usar a data real.
	 */
	@Transactional
	public void repor() {
		repositorio.deleteById(Relogio.ID);
	}
}
