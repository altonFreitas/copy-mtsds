package com.acessoatempo.incumprimento.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Corpo de {@code POST /incumprimentos/{id}/transferencias}: a instituição a quem se propõe a consulta. */
@Setter
@Getter
@NoArgsConstructor
public class TransferenciaRequestDTO {

    /** {@code @NotNull}: se faltar, o Spring responde logo 400 (ver @Valid no controller). */
    @NotNull(message = "instituicaoId é obrigatório")
    private Integer instituicaoId;
}
