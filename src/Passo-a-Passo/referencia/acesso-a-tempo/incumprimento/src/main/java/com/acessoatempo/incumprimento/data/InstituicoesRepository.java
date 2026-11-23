package com.acessoatempo.incumprimento.data;

import com.acessoatempo.incumprimento.models.Instituicao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repositório das instituições de destino (lista pequena e fechada, por isso sem paginação). */
public interface InstituicoesRepository extends JpaRepository<Instituicao, Integer> {

    List<Instituicao> findByEspecialidade(String especialidade);
}
