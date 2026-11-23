package pt.estg.mtsds.acesso.gateway.configuration;

import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

import pt.estg.mtsds.acesso.gateway.models.Papel;

/**
 * Regras de autorização lidas do application.yml (prefixo acesso.autorizacao).
 *
 * @param caminhosLivres
 *            padrões de caminho que passam sem cabeçalho X-Utilizador
 * @param permissoes
 *            operações que alteram dados e os papéis que as podem fazer
 */
@ConfigurationProperties(prefix = "acesso.autorizacao")
public record AutorizacaoProperties(List<String> caminhosLivres, List<Permissao> permissoes) {

	/** Sem configuração, nenhum caminho é livre e só os GET passam. */
	public AutorizacaoProperties {
		caminhosLivres = caminhosLivres == null ? List.of() : List.copyOf(caminhosLivres);
		permissoes = permissoes == null ? List.of() : List.copyOf(permissoes);
	}

	/**
	 * Uma operação permitida.
	 *
	 * @param metodo
	 *            método HTTP
	 * @param caminho
	 *            padrão do caminho, por exemplo /pedidos/{@literal *}/triagem
	 * @param papeis
	 *            papéis autorizados
	 */
	public record Permissao(HttpMethod metodo, String caminho, Set<Papel> papeis) {
	}
}
