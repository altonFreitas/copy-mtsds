package pt.estg.mtsds.acesso.incumprimento.dtos;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta paginada comum (RNF12): page a partir de 0, size 20 por omissão,
 * máximo 100.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginaDTO<T> {

	private List<T> conteudo;
	private int pagina;
	private int tamanho;
	private long totalElementos;
	private int totalPaginas;

	/** Converte uma página de entidades numa página de DTO. */
	public static <E, D> PaginaDTO<D> de(Page<E> pagina, Function<E, D> conversor) {
		return new PaginaDTO<>(pagina.getContent().stream().map(conversor).toList(), pagina.getNumber(),
				pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
	}
}
