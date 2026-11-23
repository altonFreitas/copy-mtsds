package pt.estg.mtsds.acesso.indicadores.data;

/**
 * Projeção Spring Data: os getters têm o mesmo nome dos aliases da query (as
 * entradas, as incumpridos...). O Spring preenche-a sozinho, sem ser preciso
 * criar uma classe para um resultado que não é uma entidade.
 */
public interface CumprimentoProjection {

	String getEspecialidade();

	Long getEntradas();

	Long getIncumpridos();
}
