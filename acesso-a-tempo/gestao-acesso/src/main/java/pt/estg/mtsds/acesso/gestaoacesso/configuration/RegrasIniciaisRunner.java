package pt.estg.mtsds.acesso.gestaoacesso.configuration;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import pt.estg.mtsds.acesso.gestaoacesso.data.RegraVersaoRepository;
import pt.estg.mtsds.acesso.gestaoacesso.models.Prioridade;
import pt.estg.mtsds.acesso.gestaoacesso.models.RegraVersao;

/**
 * Carrega a versão 1 das regras TMRG (Portaria 153/2017) no arranque, se a
 * tabela regra_versao estiver vazia. Se já houver regras, não faz nada, por
 * isso pode correr em todos os arranques.
 */
@Component
public class RegrasIniciaisRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(RegrasIniciaisRunner.class);

	private static final int VERSAO = 1;
	private static final LocalDate INICIO_VIGENCIA = LocalDate.of(2017, 5, 4);

	private final RegraVersaoRepository repositorio;

	/**
	 * @param repositorio
	 *            repositório das regras
	 */
	public RegrasIniciaisRunner(RegraVersaoRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (repositorio.count() > 0) {
			return;
		}
		repositorio.saveAll(List.of(regra(Prioridade.MUITO_PRIORITARIA, 30), regra(Prioridade.PRIORITARIA, 60),
				regra(Prioridade.NORMAL, 120)));
		log.info("Regras TMRG carregadas: versão {}, em vigor desde {}", VERSAO, INICIO_VIGENCIA);
	}

	private static RegraVersao regra(Prioridade prioridade, int dias) {
		return RegraVersao.builder().versao(VERSAO).prioridade(prioridade).dias(dias).inicioVigencia(INICIO_VIGENCIA)
				.build();
	}
}
