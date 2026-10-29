package br.com.clyvovet.server.atendimento;

import java.time.LocalDate;
import java.util.List;

/**
 * O que a ficha do pet mostra depois do registro.
 *
 * @param qtdObrigacoes quantas obrigacoes o motor criou, todas elas
 * @param proximasDatas as primeiras datas previstas a partir de hoje, em ordem
 */
public record AtendimentoRegistrado(Long consultaId, int qtdObrigacoes, List<LocalDate> proximasDatas) {}
