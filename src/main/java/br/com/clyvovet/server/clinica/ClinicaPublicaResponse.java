package br.com.clyvovet.server.clinica;

/**
 * A clinica como quem ainda nao tem conta pode ve-la.
 *
 * <p>O cadastro de tutor exige {@code clinicaId}, e quem esta se cadastrando
 * nao tem token para listar clinicas — o aplicativo ficava sem forma de
 * descobrir o numero. Esta projecao existe para fechar esse buraco sem abrir o
 * cadastro inteiro: nome e cidade bastam para a pessoa reconhecer a clinica
 * onde ja e atendida.
 *
 * <p>Deliberadamente <b>sem</b> CNPJ, endereco completo, telefone e ticket
 * medio. {@link ClinicaResponse} continua atras de autenticacao: uma tela de
 * cadastro nao e motivo para publicar o cadastro comercial da rede inteira.
 */
public record ClinicaPublicaResponse(Long id, String nome, String cidade, String estado) {

    public static ClinicaPublicaResponse from(Clinica c) {
        return new ClinicaPublicaResponse(c.getId(), c.getNome(), c.getCidade(), c.getEstado());
    }
}
