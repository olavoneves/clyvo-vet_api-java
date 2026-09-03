package br.com.clyvovet.server.tenant;

import br.com.clyvovet.server.obrigacao.Obrigacao;
import br.com.clyvovet.server.obrigacao.ObrigacaoRepository;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.pet.PetRepository;
import br.com.clyvovet.server.tutor.TutorRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    private Long clinicaA;
    private Long clinicaB;

    /**
     * O tenant precisa estar posto antes do primeiro uso do EntityManager: e no
     * primeiro acesso que a sessao do Hibernate abre e o filtro entra em cena.
     */
    @BeforeEach
    void escolherDuasClinicas() {
        List<Long> clinicas = entityManager
                .createNativeQuery("select id_clinica from TB_CLV_CLINICA order by id_clinica", Long.class)
                .getResultList();

        org.junit.jupiter.api.Assumptions.assumeTrue(clinicas.size() >= 2,
                "o teste de isolamento exige pelo menos duas clinicas cadastradas");

        clinicaA = clinicas.get(0);
        clinicaB = clinicas.get(1);
        TenantContext.set(clinicaA);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    void listagemDePetsSoTrazAClinicaDoUsuario() {
        List<Long> clinicasVistas = petRepository.findAll(PageRequest.of(0, 100))
                .getContent().stream()
                .map(pet -> pet.getClinica().getId())
                .distinct()
                .toList();

        assertThat(clinicasVistas)
                .as("uma listagem paginada nunca pode misturar clinicas")
                .containsExactly(clinicaA);
    }

    @Test
    void buscaPorIdNaoAlcancaPetDeOutraClinica() {
        // consulta nativa nao passa pelo filtro do Hibernate: e assim que o teste
        // consegue um id que ele proprio nao deveria conseguir enxergar
        Long petDaOutraClinica = idNativo(
                "select min(id_pet) from TB_CLV_PET where id_clinica = " + clinicaB);
        org.junit.jupiter.api.Assumptions.assumeTrue(petDaOutraClinica != null,
                "a segunda clinica nao tem pets para comparar");

        assertThat(petRepository.findById(petDaOutraClinica))
                .as("applyToLoadByKey existe justamente para o find() por id nao vazar")
                .isEmpty();
    }

    @Test
    void tutorDeOutraClinicaNaoApareceNaListagem() {
        List<Long> clinicasVistas = tutorRepository.findAll(PageRequest.of(0, 100))
                .getContent().stream()
                .map(tutor -> tutor.getClinica().getId())
                .distinct()
                .toList();

        assertThat(clinicasVistas).containsExactly(clinicaA);
    }

    /**
     * Obrigacao carrega id_clinica proprio, mas aponta para um pet. Se o filtro
     * falhasse, o vazamento apareceria aqui de duas formas: linha de outra
     * clinica na lista, ou EntityNotFound ao carregar o proxy do pet.
     */
    @Test
    void obrigacaoDeOutraClinicaNaoApareceNemPelaListaNemPeloPet() {
        List<Obrigacao> pagina = obrigacaoRepository
                .findAll(PageRequest.of(0, 100)).getContent();

        assertThat(pagina.stream().map(o -> o.getClinica().getId()).distinct())
                .containsExactly(clinicaA);
        assertThat(pagina.stream().map(Obrigacao::getPet).map(Pet::getClinica))
                .allSatisfy(clinica -> assertThat(clinica.getId()).isEqualTo(clinicaA));
    }

    @Test
    void semTenantNenhumDadoEVisivel() {
        TenantContext.clear();
        entityManager.clear();

        assertThat(petRepository.findAll(PageRequest.of(0, 10)).getContent())
                .as("sem clinica provada, a resposta correta e nenhuma linha")
                .isEmpty();
    }

    private Long idNativo(String sql) {
        Object resultado = entityManager.createNativeQuery(sql).getSingleResult();
        return resultado == null ? null : ((Number) resultado).longValue();
    }
}
