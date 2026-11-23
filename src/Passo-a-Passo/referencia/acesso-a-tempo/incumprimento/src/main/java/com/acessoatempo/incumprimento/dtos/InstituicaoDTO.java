package com.acessoatempo.incumprimento.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Instituição de destino devolvida por {@code GET /instituicoes}. */
@Setter
@Getter
@NoArgsConstructor
public class InstituicaoDTO {

    private Integer id;

    private String nome;

    private String especialidade;
}
