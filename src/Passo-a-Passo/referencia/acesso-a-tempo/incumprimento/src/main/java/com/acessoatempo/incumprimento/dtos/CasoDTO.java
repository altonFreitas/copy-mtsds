package com.acessoatempo.incumprimento.dtos;

import com.acessoatempo.incumprimento.enums.EstadoCaso;
import com.acessoatempo.incumprimento.enums.OrigemCaso;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Representação de um caso de incumprimento enviada ao cliente. Tem só o que o cliente precisa:
 * não expõe o campo técnico {@code versao} nem as datas de auditoria (RNF10: a entidade nunca sai do serviço).
 */
@Setter
@Getter
@NoArgsConstructor
public class CasoDTO {

    private Integer id;

    private Long episodioId;

    private String especialidade;

    private OrigemCaso origem;

    private EstadoCaso estado;

    /** 0 = em risco; 1, 2 ou 3 = nível de tratamento (R6). */
    private int nivel;

    private LocalDate dataDeteccao;

    /** Fim do prazo de 5 dias úteis do diretor (só a partir do nível 2). */
    private LocalDate dataLimiteDiretor;
}
