package br.com.clyvovet.server.config;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.clinica.ClinicaRepository;
import br.com.clyvovet.server.colaborador.Colaborador;
import br.com.clyvovet.server.colaborador.ColaboradorRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A senha do master vem da configuracao (MASTER_PASSWORD, guardada no Key
 * Vault), nunca de um literal no codigo: um literal versionado vale para
 * qualquer instancia publicada.
 */
class DataInitializerTest {

    private ColaboradorRepository colaboradores;
    private ClinicaRepository clinicas;
    private PasswordEncoder encoder;
    private EntityManager entityManager;

    @BeforeEach
    void preparar() {
        colaboradores = mock(ColaboradorRepository.class);
        clinicas = mock(ClinicaRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        entityManager = mock(EntityManager.class);
        when(entityManager.unwrap(Session.class)).thenReturn(mock(Session.class));

        when(colaboradores.findByEmail("master@clyvovet.com")).thenReturn(Optional.empty());
        when(clinicas.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(new Clinica())));
    }

    @Test
    void criaMasterComASenhaConfigurada() {
        inicializador("senha-vinda-do-cofre").run(new DefaultApplicationArguments());

        ArgumentCaptor<Colaborador> salvo = ArgumentCaptor.forClass(Colaborador.class);
        verify(colaboradores).save(salvo.capture());
        assertThat(encoder.matches("senha-vinda-do-cofre", salvo.getValue().getSenhaHash())).isTrue();
        assertThat(encoder.matches("master", salvo.getValue().getSenhaHash())).isFalse();
    }

    @Test
    void semSenhaConfiguradaNaoCriaMaster() {
        inicializador("").run(new DefaultApplicationArguments());

        verify(colaboradores, never()).save(any());
    }

    private DataInitializer inicializador(String senhaMaster) {
        return new DataInitializer(colaboradores, clinicas, encoder, entityManager, senhaMaster);
    }
}
