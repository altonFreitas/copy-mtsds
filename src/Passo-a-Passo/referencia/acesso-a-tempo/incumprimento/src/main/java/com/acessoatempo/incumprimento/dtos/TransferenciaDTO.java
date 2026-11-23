package com.acessoatempo.incumprimento.dtos;

import com.acessoatempo.incumprimento.enums.EstadoTransferencia;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Representação de uma proposta de transferência enviada ao cliente. */
@Setter
@Getter
@NoArgsConstructor
public class TransferenciaDTO {

    private Integer id;

    /** Vem de {@code transferencia.caso.id} (o ModelMapper faz a ligação pelo nome casoId). */
    private Integer casoId;

    private Integer instituicaoId;

    private String instituicaoNome;

    private EstadoTransferencia estado;

    private LocalDate dataProposta;

    private LocalDate respostaAte;

    private LocalDate dataResposta;
}
