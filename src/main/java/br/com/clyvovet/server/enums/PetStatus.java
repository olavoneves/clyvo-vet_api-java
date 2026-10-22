package br.com.clyvovet.server.enums;

/**
 * Estado do pet.
 *
 * <p>Os quatro primeiros sao estado clinico do animal. {@code INATIVO} nao e:
 * significa que o tutor removeu o pet do aplicativo. Foi acrescentado na V14
 * porque nenhum dos outros servia — gravar OBITO ou PERDIDO porque alguem tocou
 * em "remover" seria escrever um fato clinico falso no prontuario, e a coorte
 * do painel le esse prontuario.
 *
 * <p>O motor de protocolo nao olha para {@code INATIVO}: as obrigacoes de um pet
 * inativado continuam existindo e continuam contando, e a clinica continua
 * enxergando o pet. O valor so esconde o pet das listagens do proprio tutor.
 */
public enum PetStatus {
    ATIVO, EM_TRATAMENTO, OBITO, PERDIDO, INATIVO
}
