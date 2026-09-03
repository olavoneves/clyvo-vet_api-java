package br.com.clyvovet.server.protocolo;

import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.enums.ProtocoloEspecie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtocoloRepository extends JpaRepository<Protocolo, Long> {

    @EntityGraph(attributePaths = "clinica")
    Page<Protocolo> findByFlAtivoTrue(Pageable pageable);

    @EntityGraph(attributePaths = "clinica")
    Page<Protocolo> findByDsCategoriaAndFlAtivoTrue(ProtocoloCategoria categoria, Pageable pageable);

    @EntityGraph(attributePaths = "clinica")
    Page<Protocolo> findByDsEspecieAndFlAtivoTrue(ProtocoloEspecie especie, Pageable pageable);
}
