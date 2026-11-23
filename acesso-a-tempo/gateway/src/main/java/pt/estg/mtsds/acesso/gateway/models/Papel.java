package pt.estg.mtsds.acesso.gateway.models;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Papéis aceites no cabeçalho X-Utilizador. Qualquer outro valor é recusado com
 * 401.
 */
public enum Papel {
	MEDICO_FAMILIA, TRIADOR, ADMINISTRATIVO, DIRETOR_SERVICO, GESTOR_ACESSO, UTENTE;

	/**
	 * Converte o texto recebido no cabeçalho no papel correspondente.
	 *
	 * @param nome
	 *            nome do papel, exatamente como na enumeração
	 * @return o papel, ou vazio se o nome não corresponder a nenhum
	 */
	public static Optional<Papel> de(String nome) {
		return Arrays.stream(values()).filter(p -> p.name().equals(nome)).findFirst();
	}

	/**
	 * Lista os papéis válidos para as mensagens de erro.
	 *
	 * @return os nomes de todos os papéis, separados por vírgulas
	 */
	public static String listaValidos() {
		return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
	}
}
