package br.com.clyvovet.server.veterinario;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VeterinarioRepository extends JpaRepository<Veterinario, Long> {

    Page<Veterinario> findByClinicaId(Long clinicaId, Pageable pageable);

    Optional<Veterinario> findByEmail(String email);

    /**
     * Trava a linha do veterinario ate o fim da transacao.
     *
     * <p>E o que impede dois tutores de fecharem o mesmo horario. Sem a trava,
     * as duas transacoes leem a agenda vazia, as duas concluem que o slot esta
     * livre e as duas inserem: a checagem passa em ambas porque cada uma le um
     * instantaneo anterior a insercao da outra. Com ela, a segunda espera, le a
     * agenda ja com o agendamento da primeira e recebe o conflito tratado.
     *
     * <p>Serializa por veterinario, nao por clinica: dois tutores marcando com
     * profissionais diferentes nao se esbarram.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Veterinario v where v.id = :id")
    Optional<Veterinario> bloquearParaAgenda(@Param("id") Long id);
}
