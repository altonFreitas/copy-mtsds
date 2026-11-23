package com.acessoatempo.indicadores.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Formato de erro comum a todos os serviços (secção 4.4.1 da proposta). Se a Parte 4 entregar uma
 * classe comum, substitui esta cópia local.
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ErroDTO {

    /** Serviço onde o erro ocorreu (ajuda a seguir erros em cascata). */
    private String servico;

    /** Código HTTP (400, 404, 409...). */
    private int estado;

    /** Código estável do erro, por exemplo {@code PARAMETRO_INVALIDO}. */
    private String erro;

    /** Mensagem descritiva: o que falhou, porquê e o que fazer (RNF11). */
    private String mensagem;

    private String caminho;

    private LocalDateTime dataHora;

    /** Identificador gerado na Gateway para seguir o pedido nos logs de todos os serviços. */
    private String traceId;
}
