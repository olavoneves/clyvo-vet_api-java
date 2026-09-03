package br.com.clyvovet.server.config;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.clinica.ClinicaRepository;
import br.com.clyvovet.server.colaborador.Colaborador;
import br.com.clyvovet.server.colaborador.ColaboradorRepository;
import br.com.clyvovet.server.tenant.TenantFilters;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String EMAIL_MASTER = "master@clyvovet.com";

    private final ColaboradorRepository colaboradorRepository;
    private final ClinicaRepository clinicaRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // bootstrap roda fora de qualquer requisicao: nao ha tenant, e a busca
        // pelo master precisa enxergar todas as clinicas
        entityManager.unwrap(Session.class).disableFilter(TenantFilters.TENANT);

        if (colaboradorRepository.findByEmail(EMAIL_MASTER).isPresent()) return;

        Optional<Clinica> clinica = clinicaRepository.findAll(PageRequest.of(0, 1)).stream().findFirst();
        if (clinica.isEmpty()) {
            log.warn("Nenhuma clinica cadastrada: usuario master nao criado. "
                    + "Cadastre uma clinica (POST /clinicas) e reinicie a aplicacao.");
            return;
        }

        Colaborador master = new Colaborador();
        master.setNome("master");
        master.setEmail(EMAIL_MASTER);
        master.setSenhaHash(passwordEncoder.encode("master"));
        master.setCargo("master");
        master.setClinica(clinica.get());
        colaboradorRepository.save(master);

        log.info("Usuario master criado na clinica {}", clinica.get().getId());
    }
}
