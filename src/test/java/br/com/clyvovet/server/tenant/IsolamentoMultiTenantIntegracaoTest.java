package br.com.clyvovet.server.tenant;

import br.com.clyvovet.server.obrigacao.Obrigacao;
import br.com.clyvovet.server.obrigacao.ObrigacaoRepository;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.pet.PetRepository;
import br.com.clyvovet.server.support.CenarioClinico;
import br.com.clyvovet.server.tutor.TutorRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolamento por clinica: o usuario da clinica A nao alcanca dado da B.
 *
 * <p>E o teste que mais importa do conjunto. O isolamento nao esta escrito em
 * nenhuma consulta — vem do filtro autoEnabled do Hibernate — entao nada no
 * codigo de um repositorio novo lembra o programador de aplica-lo. So um teste
 * de integração de verdade, contra o banco, mostra se ele continua valendo.
 *
 * <p><b>O cenario e fabricado aqui, e essa e a correcao que importa.</b> Antes a
 * classe pegava as duas primeiras clinicas que existissem e desistia por
 * {@code assumeTrue} quando havia menos de duas — e o caso mais afiado, o
 * {@code findById} atravessando clinica, desistia de novo quando a segunda
 * clinica nao tinha pet. Numa base sem seed, o teste de seguranca da aplicacao
 * passava sem executar uma linha do que afirma. Agora ele cria as duas clinicas,
 * com pet e obrigacao em cada, e nao ha estado de base em que ele deixe de
 * exercitar o vazamento que existe para impedir.
 *
 * <p>Roda contra o Oracle configurado e desfaz tudo ao final. Onde nao houver
 * banco (CI sem segredo, por exemplo) a classe inteira e pulada, em vez de
 * falhar por motivo que nao e o dela.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle configurado nas variaveis de ambiente")
class IsolamentoMultiTenantIntegracaoTest {

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private TutorRepository tutorRepository;

    @Autowired
    private ObrigacaoRepository obrigacaoRepository;

    @Autowired
    private EntityManager entityManager;

    private CenarioClinico cenario;
    private CenarioClinico.Cenario a;
    private CenarioClinico.Cenario b;

    /**
     * O tenant precisa estar posto antes do primeiro uso do EntityManager pelos
     * repositorios: e no primeiro acesso que a sessao do Hibernate abre e o filtro
     * entra em cena. A montagem em si e por consulta nativa, que nao passa pelo
     * filtro — e por isso consegue criar as duas clinicas de uma vez.
     */
    @BeforeEach
    void montarDuasClinicas() {
        cenario = new CenarioClinico(entityManager);

        a = cenario.clinicaCompleta("Clinica A do teste");
        b = cenario.clinicaCompleta("Clinica B do teste");

        cenario.novaObrigacao(a.clinica(), a.pet(), "PREVISTA", 30, false);
        cenario.novaObrigacao(b.clinica(), b.pet(), "PREVISTA", 30, false);

        entityManager.flush();
        entityManager.clear();

        TenantContext.set(a.clinica());
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a listagem paginada nunca mistura clinicas")
    void listagemDePetsSoTrazAClinicaDoUsuario() {
        List<Long> clinicasVistas = petRepository.findAll(PageRequest.of(0, 100))
                .getContent().stream()
                .map(pet -> pet.getClinica().getId())
                .distinct()
                .toList();

        assertThat(clinicasVistas)
                .as("uma listagem paginada nunca pode misturar clinicas")
                .containsExactly(a.clinica());
    }

    /**
     * O caso mais afiado, e o que mais tempo passou sem rodar de verdade.
     *
     * <p>O pet da clinica B existe porque este teste o criou. Antes ele dependia
     * de a segunda clinica da base ter algum pet, e desistia em silencio quando
     * nao tinha — justamente o teste que prova que {@code applyToLoadByKey} esta
     * ligado, sem o qual {@code GET /pets/{id}} vaza entre clinicas.
     */
    @Test
    @DisplayName("findById nao alcanca pet de outra clinica")
    void buscaPorIdNaoAlcancaPetDeOutraClinica() {
        // consulta nativa nao passa pelo filtro do Hibernate: e assim que o teste
        // consegue um id que ele proprio nao deveria conseguir enxergar
        Long petDaOutraClinica = cenario.id(
                "select min(id_pet) from TB_CLV_PET where id_clinica = " + b.clinica());

        assertThat(petDaOutraClinica)
                .as("a montagem tem que ter criado o pet da clinica B")
                .isNotNull();

        assertThat(petRepository.findById(petDaOutraClinica))
                .as("applyToLoadByKey existe justamente para o find() por id nao vazar")
                .isEmpty();

        assertThat(petRepository.findById(a.pet()))
                .as("e o pet da propria clinica tem que continuar alcancavel — "
                        + "um filtro que esconde tudo passaria na assercao de cima")
                .isPresent();
    }

    @Test
    @DisplayName("tutor de outra clinica nao aparece na listagem")
    void tutorDeOutraClinicaNaoApareceNaListagem() {
        List<Long> clinicasVistas = tutorRepository.findAll(PageRequest.of(0, 100))
                .getContent().stream()
                .map(tutor -> tutor.getClinica().getId())
                .distinct()
                .toList();

        assertThat(clinicasVistas).containsExactly(a.clinica());
    }

    /**
     * Obrigacao carrega id_clinica proprio, mas aponta para um pet. Se o filtro
     * falhasse, o vazamento apareceria aqui de duas formas: linha de outra
     * clinica na lista, ou EntityNotFound ao carregar o proxy do pet.
     */
    @Test
    @DisplayName("obrigacao de outra clinica nao aparece nem pela lista nem pelo pet")
    void obrigacaoDeOutraClinicaNaoApareceNemPelaListaNemPeloPet() {
        List<Obrigacao> pagina = obrigacaoRepository
                .findAll(PageRequest.of(0, 100)).getContent();

        assertThat(pagina)
                .as("a montagem criou uma obrigacao na clinica A: a pagina nao pode vir vazia")
                .isNotEmpty();
        assertThat(pagina.stream().map(o -> o.getClinica().getId()).distinct())
                .containsExactly(a.clinica());
        assertThat(pagina.stream().map(Obrigacao::getPet).map(Pet::getClinica))
                .allSatisfy(clinica -> assertThat(clinica.getId()).isEqualTo(a.clinica()));
    }

    @Test
    @DisplayName("sem tenant, nenhum dado e visivel")
    void semTenantNenhumDadoEVisivel() {
        TenantContext.clear();
        entityManager.clear();

        assertThat(petRepository.findAll(PageRequest.of(0, 10)).getContent())
                .as("sem clinica provada, a resposta correta e nenhuma linha")
                .isEmpty();
    }
}
