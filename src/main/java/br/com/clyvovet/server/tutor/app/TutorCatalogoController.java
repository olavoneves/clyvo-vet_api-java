package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.especie.EspecieResponse;
import br.com.clyvovet.server.especie.EspecieService;
import br.com.clyvovet.server.raca.RacaResponse;
import br.com.clyvovet.server.raca.RacaService;
import br.com.clyvovet.server.veterinario.VeterinarioResponse;
import br.com.clyvovet.server.veterinario.VeterinarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As listas de apoio dos formularios do aplicativo, e nada alem disso.
 *
 * <p>Especie, raca e veterinario sao catalogo clinico: quem os edita muda o
 * cuidado de todos os pets, e por isso o CRUD deles continua fechado em
 * {@code API_ROTAS_CLINICAS}. Mas o tutor precisa <b>ler</b> os tres para
 * conseguir cadastrar um pet e marcar uma consulta — {@code racaId} e
 * {@code veterinarioId} sao obrigatorios no corpo, e sem uma lista de onde
 * escolher o aplicativo so poderia chutar o numero ou embutir uma copia fixa
 * das opcoes, que envelhece calada assim que a clinica cadastra a proxima raca.
 *
 * <p>Leitura, e so leitura: nao ha POST, PUT nem DELETE nesta classe. O tutor
 * escolhe dentro do catalogo; quem escreve nele continua sendo a clinica.
 *
 * <p>A lista de veterinarios e a da clinica do proprio tutor, tirada do token e
 * nunca de parametro — pela mesma razao que {@link PosseDoTutor} existe. O
 * tutor marca com quem o atende, e a agenda das outras clinicas nao lhe diz
 * respeito.
 */
@RestController
@RequestMapping("/tutor/catalogo")
@RequiredArgsConstructor
@Tag(name = "Tutor: catálogo",
        description = "Listas de apoio dos formulários do aplicativo, somente leitura")
public class TutorCatalogoController {

    private final PosseDoTutor posse;
    private final EspecieService especieService;
    private final RacaService racaService;
    private final VeterinarioService veterinarioService;

    @GetMapping("/especies")
    @Operation(summary = "Espécies disponíveis",
            description = "Alimenta o primeiro passo do formulário de pet.")
    public Page<EspecieResponse> especies(Pageable pageable) {
        posse.autenticado();
        return especieService.findAll(pageable);
    }

    @GetMapping("/racas")
    @Operation(summary = "Raças disponíveis, opcionalmente de uma espécie",
            description = "Sem especieId devolve todas; com ele, só as da espécie escolhida.")
    public Page<RacaResponse> racas(@RequestParam(required = false) Long especieId,
                                    Pageable pageable) {
        posse.autenticado();
        return especieId == null
                ? racaService.findAll(pageable)
                : racaService.findByEspecie(especieId, pageable);
    }

    @GetMapping("/veterinarios")
    @Operation(summary = "Veterinários da clínica do tutor autenticado",
            description = "A clínica sai do token: não há filtro por clínica no parâmetro.")
    public Page<VeterinarioResponse> veterinarios(Pageable pageable) {
        return veterinarioService.findByClinica(posse.autenticado().idClinica(), pageable);
    }
}
