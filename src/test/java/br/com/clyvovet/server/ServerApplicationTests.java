package br.com.clyvovet.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * O contexto sobe inteiro: fiacao, mapeamento JPA e migrations do Flyway.
 *
 * <p>Nao ha como levantar o contexto sem banco — o datasource nao tem valor
 * padrao e o ddl-auto e validate — entao a classe leva a mesma guarda dos
 * outros testes de integracao. Sem ela o teste falhava em toda maquina sem
 * Oracle configurado, e um build permanentemente vermelho nao e lido por
 * ninguem.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle configurado nas variaveis de ambiente")
class ServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
