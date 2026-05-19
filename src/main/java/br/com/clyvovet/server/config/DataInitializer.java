package br.com.clyvovet.server.config;

import br.com.clyvovet.server.colaborador.Colaborador;
import br.com.clyvovet.server.colaborador.ColaboradorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ColaboradorRepository colaboradorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (colaboradorRepository.findByEmail("master").isPresent()) return;

        Colaborador master = new Colaborador();
        master.setNome("master");
        master.setEmail("master");
        master.setSenhaHash(passwordEncoder.encode("master"));
        master.setCargo("master");
        colaboradorRepository.save(master);
    }
}
