package br.com.clyvovet.server.tutor;

import br.com.clyvovet.server.enums.CanalPreferencial;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TutorRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 20) String telefone,
        @Size(max = 20) String telefoneEmergencia,
        @NotBlank @Size(min = 6, max = 100) String senha,
        CanalPreferencial canalPreferencial,
        // POST /tutores é público: não há token, logo não há tenant de onde
        // deduzir a clínica. Só vale no cadastro — o update ignora este campo.
        @NotNull Long clinicaId
) {}
