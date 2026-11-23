package pt.estg.mtsds.acesso.agendamento.controllers;

import pt.estg.mtsds.acesso.agendamento.exceptions.DadosInvalidosException;

/**
 * Leitura do cabeçalho X-Utilizador, no formato id;PAPEL. A gateway já validou
 * o formato e as permissões; aqui só se tira o id, que fica como autor.
 */
final class CabecalhoUtilizador {

	/** Nome do cabeçalho. */
	static final String NOME = "X-Utilizador";

	private CabecalhoUtilizador() {
	}

	/**
	 * Tira o id do utilizador do cabeçalho.
	 *
	 * @param valor
	 *            valor do cabeçalho, por exemplo 8;GESTOR_ACESSO
	 * @return o id, por exemplo 8
	 * @throws DadosInvalidosException
	 *             se o valor não tiver um id antes do ponto e vírgula
	 */
	static String autor(String valor) {
		int separador = valor.indexOf(';');
		String id = (separador < 0 ? valor : valor.substring(0, separador)).trim();
		if (id.isEmpty()) {
			throw new DadosInvalidosException("X_UTILIZADOR_MAL_FORMADO",
					"O cabeçalho " + NOME + " tem de ter o formato id;PAPEL, mas veio '" + valor + "'.");
		}
		return id;
	}
}
