package pt.estg.mtsds.acesso.gestaoacesso.services;

import java.time.LocalDate;

import pt.estg.mtsds.acesso.gestaoacesso.models.SituacaoPrazo;

/**
 * Cálculo dos prazos TMRG em dias seguidos (de calendário). Só funções puras:
 * não acede à base de dados nem ao relógio, por isso a data de hoje é sempre um
 * parâmetro.
 * <p>
 * Regras do README: R1 (a contagem começa na data do pedido), R2 (data-limite),
 * R4 (incumprimento), R5 (incumprimento previsto) e R7 (prioridade que sobe com
 * a nova data-limite já ultrapassada). O alerta "em risco" aos 75% do prazo é
 * regra do grupo.
 */
public final class CalculadoraPrazos {

	private CalculadoraPrazos() {
	}

	/**
	 * R1 e R2: data-limite = data do pedido + prazo. Numa reavaliação, chama-se de
	 * novo com a mesma data do pedido e o novo prazo; a triagem não reinicia a
	 * contagem.
	 *
	 * @param dataPedido
	 *            data do registo do pedido
	 * @param dias
	 *            prazo da prioridade, em dias (maior que zero)
	 * @return último dia em que a consulta ainda cumpre o prazo
	 * @throws IllegalArgumentException
	 *             se a data for nula ou os dias não forem positivos
	 */
	public static LocalDate dataLimite(LocalDate dataPedido, int dias) {
		exigirData(dataPedido, "dataPedido");
		exigirDiasPositivos(dias);
		return dataPedido.plusDays(dias);
	}

	/**
	 * R1 e alerta aos 75% (regra do grupo): data do alarme = data do pedido + 75%
	 * do prazo, arredondado para baixo. Para 30, 60 e 120 dias dá 22, 45 e 90.
	 *
	 * @param dataPedido
	 *            data do registo do pedido
	 * @param dias
	 *            prazo da prioridade, em dias (maior que zero)
	 * @return primeiro dia em que o episódio fica em risco
	 * @throws IllegalArgumentException
	 *             se a data for nula ou os dias não forem positivos
	 */
	public static LocalDate dataAlarme(LocalDate dataPedido, int dias) {
		exigirData(dataPedido, "dataPedido");
		exigirDiasPositivos(dias);
		// Divisão inteira: com dias positivos, dias * 3 / 4 é floor(dias * 0,75) sem
		// erros de vírgula flutuante
		return dataPedido.plusDays(dias * 3L / 4);
	}

	/**
	 * R4 e R7: situação do prazo num dia.
	 * <ul>
	 * <li>antes da data do alarme: {@link SituacaoPrazo#DENTRO_PRAZO};</li>
	 * <li>da data do alarme até à data-limite, inclusive:
	 * {@link SituacaoPrazo#EM_RISCO};</li>
	 * <li>depois da data-limite: {@link SituacaoPrazo#INCUMPRIDO} (R4; o próprio
	 * dia-limite ainda está cumprido).</li>
	 * </ul>
	 * Numa reavaliação que sobe a prioridade, as datas recalculadas podem já ter
	 * passado: o resultado é logo INCUMPRIDO (R7).
	 *
	 * @param dataAlarme
	 *            primeiro dia em risco
	 * @param dataLimite
	 *            último dia dentro do prazo
	 * @param hoje
	 *            dia em que se avalia a situação
	 * @return a situação nesse dia
	 * @throws IllegalArgumentException
	 *             se alguma data for nula ou o alarme for depois da data-limite
	 */
	public static SituacaoPrazo situacaoEm(LocalDate dataAlarme, LocalDate dataLimite, LocalDate hoje) {
		exigirData(dataAlarme, "dataAlarme");
		exigirData(dataLimite, "dataLimite");
		exigirData(hoje, "hoje");
		if (dataAlarme.isAfter(dataLimite)) {
			throw new IllegalArgumentException(
					"A data do alarme (" + dataAlarme + ") não pode ser depois da data-limite (" + dataLimite + ").");
		}
		if (hoje.isAfter(dataLimite)) {
			return SituacaoPrazo.INCUMPRIDO;
		}
		if (hoje.isBefore(dataAlarme)) {
			return SituacaoPrazo.DENTRO_PRAZO;
		}
		return SituacaoPrazo.EM_RISCO;
	}

	/**
	 * R5: incumprimento previsto quando a consulta é marcada para depois da
	 * data-limite. Uma consulta no próprio dia-limite cumpre o prazo.
	 *
	 * @param dataLimite
	 *            último dia dentro do prazo
	 * @param dataConsulta
	 *            dia da consulta marcada
	 * @return true se a consulta fica fora do prazo
	 * @throws IllegalArgumentException
	 *             se alguma data for nula
	 */
	public static boolean incumprimentoPrevisto(LocalDate dataLimite, LocalDate dataConsulta) {
		exigirData(dataLimite, "dataLimite");
		exigirData(dataConsulta, "dataConsulta");
		return dataConsulta.isAfter(dataLimite);
	}

	private static void exigirData(LocalDate data, String nome) {
		if (data == null) {
			throw new IllegalArgumentException("A data " + nome + " é obrigatória e veio vazia.");
		}
	}

	private static void exigirDiasPositivos(int dias) {
		if (dias <= 0) {
			throw new IllegalArgumentException(
					"O prazo tem de ser um número de dias maior que zero, mas veio " + dias + ".");
		}
	}
}
