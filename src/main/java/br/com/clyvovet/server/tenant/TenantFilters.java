package br.com.clyvovet.server.tenant;

/**
 * Nome e condicoes do filtro Hibernate de multi-tenancy.
 * Constantes porque valores de anotacao precisam ser resolvidos em tempo de compilacao.
 *
 * <p>Quatro condicoes porque nem toda tabela carrega id_clinica. As que nao
 * carregam alcancam o tenant pela cadeia de FKs ate TB_CLV_PET. Elas precisam
 * ser filtradas tambem: com applyToLoadByKey ligado, uma linha de outra clinica
 * que passasse pelo filtro estouraria ao carregar o proxy do pet dono dela.
 */
public final class TenantFilters {

    /** Nome do @FilterDef declarado em {@code Clinica}. */
    public static final String TENANT = "tenantFilter";

    /** Parametro do filtro, resolvido por {@link TenantIdResolver}. */
    public static final String PARAM_ID_CLINICA = "idClinica";

    /** Tabelas que carregam id_clinica: pet, tutor, colaborador, veterinario. */
    public static final String CONDICAO = "id_clinica = :idClinica";

    /**
     * Catalogo de protocolo: id_clinica NULL significa protocolo global, valido
     * para todas as clinicas. Filtrar sem tolerar o NULL esconderia justamente
     * os protocolos padrao — e quebraria a obrigacao que aponta para eles.
     */
    public static final String CONDICAO_CATALOGO =
            "(id_clinica is null or id_clinica = :idClinica)";

    /** Filhos diretos do pet: consulta, agendamento, vacina, alergia, condicao, sensor. */
    public static final String CONDICAO_VIA_PET =
            "id_pet in (select p.id_pet from TB_CLV_PET p where p.id_clinica = :idClinica)";

    /** Netos, pendurados na consulta: anamnese, prescricao, exame. */
    public static final String CONDICAO_VIA_CONSULTA =
            "id_consulta in (select c.id_consulta from TB_CLV_CONSULTA c, TB_CLV_PET p"
                    + " where p.id_pet = c.id_pet and p.id_clinica = :idClinica)";

    /** Leitura de IoT, pendurada no sensor do pet. */
    public static final String CONDICAO_VIA_SENSOR =
            "id_sensor in (select s.id_sensor from TB_CLV_SENSOR_IOT s, TB_CLV_PET p"
                    + " where p.id_pet = s.id_pet and p.id_clinica = :idClinica)";

    /** Alerta de IoT, pendurado na leitura. */
    public static final String CONDICAO_VIA_LEITURA =
            "id_leitura in (select l.id_leitura from TB_CLV_LEITURA_IOT l,"
                    + " TB_CLV_SENSOR_IOT s, TB_CLV_PET p"
                    + " where s.id_sensor = l.id_sensor and p.id_pet = s.id_pet"
                    + " and p.id_clinica = :idClinica)";

    private TenantFilters() {
    }
}
