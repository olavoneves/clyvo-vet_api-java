package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.enums.PetPorte;
import br.com.clyvovet.server.enums.PetSexo;
import br.com.clyvovet.server.enums.PetStatus;
import br.com.clyvovet.server.pet.PetRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * O cadastro de pet como o aplicativo do tutor o envia: sem {@code tutorId}.
 *
 * <p>E o {@link PetRequest} menos um campo, e o campo que falta e o ponto todo.
 * Aceitar {@code tutorId} e ignora-lo funcionaria igual, mas deixaria no
 * contrato um campo que parece ser obedecido e nao e — e o dia em que alguem
 * repassasse esse request adiante sem reler o controller, ele voltaria a ser.
 * Aqui o campo nao existe, entao nao ha o que ignorar.
 *
 * <p>Um {@code tutorId} enviado assim mesmo cai no descarte de campo
 * desconhecido do Jackson: o vinculo sai de {@link #comTutor} e de mais lugar
 * nenhum.
 */
public record PetDoTutorRequest(
        @NotBlank @Size(max = 100) String nome,
        LocalDate dtNascimento,
        PetSexo sexo,
        @Size(max = 30) String microchip,
        @Size(max = 30) String rga,
        @Size(max = 80) String pelagem,
        PetPorte porte,
        Boolean castrado,
        PetStatus status,
        @Size(max = 2000) String observacaoGeral,
        @NotNull Long racaId
) {

    /** O mesmo cadastro, com o dono que veio do token. */
    public PetRequest comTutor(Long idTutor) {
        return new PetRequest(nome, dtNascimento, sexo, microchip, rga, pelagem,
                porte, castrado, status, observacaoGeral, idTutor, racaId);
    }
}
