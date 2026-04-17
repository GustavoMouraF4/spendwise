# Especificação: Padrões para Testes Unitários e de Integração

> **Tipo de documento:** Spec-Driven Development (SDD)
> **Status:** Approved v1.1
> **Última atualização:** 2026-04-17
> **Stack:** Java 17+ / Spring Boot / JPA / Flyway
> **Ferramentas-base:** JUnit 5 (Jupiter), Mockito, AssertJ, MockMvc, Testcontainers, Spring Cloud Contract
> **Audiência:** Engenheiros de software, QA engineers, revisores de PR

---

## 1. Objetivo

Definir regras determinísticas para projetar, nomear e organizar testes automatizados, de modo que:

- Cada teste tenha escopo único e claro (unidade vs. integração).
- Seja previsível onde cada tipo de teste deve ser escrito.
- Um agente automatizado (ou desenvolvedor) consiga gerar testes novos seguindo o mesmo padrão do restante da base.
- O pipeline de CI execute os tipos de teste em estágios apropriados.

---

## 2. Taxonomia (O que é cada coisa)

| Camada | Escopo | Usa Spring? | Usa BD real? | Latência típica |
|---|---|---|---|---|
| **Teste unitário** | 1 classe, colaboradores mockados | Não | Não | <50 ms |
| **Teste de slice** | 1 camada (JPA, Web, etc.) | Parcial (`@DataJpaTest`, `@WebMvcTest`) | Depende | 200-800 ms |
| **Teste de integração** | 2+ componentes reais da aplicação | Sim (`@SpringBootTest`) | Sim (Testcontainers) | 1-5 s |
| **Teste end-to-end** | Aplicação inteira + dependências externas | Sim | Sim | 5-30 s |

Esta spec cobre as **três primeiras camadas**. E2E será tratado em documento separado.

---

## 3. Princípios Norteadores

1. **Testes são parte do contrato do código, não um apêndice.** Todo PR com lógica nova sem teste correspondente deve ser rejeitado.
2. **Feedback rápido importa.** Testes unitários devem rodar em segundos; slice em dezenas de segundos; integração em minutos.
3. **Um teste, uma intenção.** Cada método de teste verifica uma afirmação.
4. **Determinismo absoluto.** Testes flaky são bugs — nunca silenciados com `@RepeatedTest` ou retries.
5. **Legibilidade > DRY.** Duplicação controlada em testes é aceitável para deixar o cenário explícito.

---

## 4. Estrutura de Diretórios

```
src/
├── main/java/com/empresa/projeto/
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── web/
└── test/java/com/empresa/projeto/
    ├── domain/              # Testes unitários (puro domínio)
    ├── application/         # Testes unitários de casos de uso (com Mockito)
    ├── infrastructure/      # Testes de slice JPA + integrações
    └── web/                 # Testes de slice Web (@WebMvcTest)
test/resources/
├── application-test.yml
└── fixtures/                # JSONs, SQLs de seed
```

- **DEVE** espelhar o pacote da classe sob teste.
- Testes de integração com Spring completo **PODEM** ficar em um pacote dedicado `com.empresa.projeto.integration` ou com sufixo `IT` e runner separado (ver §9).

---

## 5. Nomenclatura

### 5.1 Classes

- **Teste unitário:** `<ClasseSobTeste>Test`
  - Exemplo: `CustomerServiceTest`.
- **Teste de integração:** `<ClasseOuFeature>IT` (sufixo `IT`)
  - Exemplo: `CustomerControllerIT`, `OrderCheckoutIT`.
- **Teste de slice JPA:** `<Repository>Test` (é rápido, trata-se como unitário com BD em memória/TC).
  - Exemplo: `CustomerRepositoryTest`.

### 5.2 Métodos

- **DEVE** usar nomes descritivos em inglês no padrão: `should<Comportamento>_when<Condição>()`.
  - Exemplo: `shouldReturnCustomer_whenIdExists()`, `shouldThrowNotFound_whenIdIsMissing()`.
- **PODE** usar `@DisplayName` com frase em português/inglês para relatórios mais legíveis:
  ```java
  @Test
  @DisplayName("Deve retornar cliente quando o ID existir")
  void shouldReturnCustomer_whenIdExists() { ... }
  ```

---

## 6. Estrutura Interna (AAA)

Todo teste **DEVE** seguir o padrão **Arrange / Act / Assert**, com linhas em branco separando os blocos:

```java
@Test
void shouldCalculateDiscount_whenCustomerIsPremium() {
    // Arrange
    Customer customer = CustomerFixtures.premium();
    Order order = OrderFixtures.of(customer, new BigDecimal("1000.00"));

    // Act
    BigDecimal discount = discountCalculator.calculate(order);

    // Assert
    assertThat(discount).isEqualByComparingTo("100.00");
}
```

- **DEVE** usar **AssertJ** (`assertThat(...)`) em vez de asserts nativos do JUnit — melhor legibilidade e mensagens de erro.
- **NÃO DEVE** ter múltiplas chamadas de `act` no mesmo teste.

---

## 7. Testes Unitários — JUnit 5 + Mockito

### 7.1 Regras

- **DEVE** testar uma única classe; todos os colaboradores são mocks.
- **NÃO DEVE** subir `ApplicationContext` do Spring.
- **NÃO DEVE** acessar BD, rede, filesystem ou relógio do sistema.
- **DEVE** usar `@ExtendWith(MockitoExtension.class)`.
- **DEVE** usar `@Mock` / `@InjectMocks` em vez de `Mockito.mock()` manual (mais legível).
- Para relógio, **DEVE** injetar `java.time.Clock` e usar `Clock.fixed(...)` nos testes.

### 7.2 Boas práticas Mockito

- **Stubs:** `when(...).thenReturn(...)` apenas no que é relevante para o teste.
- **Verifications:** `verify(...)` apenas quando o comportamento é parte do contrato (efeito colateral observável). Evite over-verification.
- **Argument captors:** use `ArgumentCaptor` para inspecionar objetos complexos passados a mocks.
- **NÃO DEVE** usar `Mockito.mock(Class, RETURNS_DEEP_STUBS)`. Indica design ruim.
- **NÃO DEVE** mockar tipos de valor (DTOs, entidades, value objects) — crie instâncias reais.
- **NÃO DEVE** mockar classes do JDK (`List`, `Map`, `String`, etc.).

### 7.3 Exemplo canônico

```java
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private NotificationGateway notificationGateway;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void shouldCreateCustomer_whenEmailIsUnique() {
        // Arrange
        CreateCustomerCommand command = new CreateCustomerCommand("ana@example.com", "Ana");
        when(customerRepository.existsByEmail(command.email())).thenReturn(false);
        when(customerRepository.save(any(Customer.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        // Act
        Customer created = customerService.create(command);

        // Assert
        assertThat(created.getEmail()).isEqualTo("ana@example.com");
        verify(notificationGateway).sendWelcomeEmail(created);
    }

    @Test
    void shouldThrowConflict_whenEmailAlreadyExists() {
        // Arrange
        CreateCustomerCommand command = new CreateCustomerCommand("ana@example.com", "Ana");
        when(customerRepository.existsByEmail(command.email())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> customerService.create(command))
            .isInstanceOf(EmailAlreadyInUseException.class);

        verifyNoInteractions(notificationGateway);
    }
}
```

### 7.4 Testes parametrizados

Usar `@ParameterizedTest` quando houver múltiplos cenários com a mesma estrutura:

```java
@ParameterizedTest
@CsvSource({
    "100.00, BRONZE, 0.00",
    "100.00, SILVER, 5.00",
    "100.00, GOLD,   10.00"
})
void shouldCalculateDiscount_basedOnTier(BigDecimal total, Tier tier, BigDecimal expected) {
    // ...
}
```

### 7.5 Fixtures

- **DEVE** concentrar criação de objetos complexos em classes `*Fixtures` ou builders:
  ```java
  public final class CustomerFixtures {
      public static Customer.Builder aCustomer() {
          return Customer.builder()
              .email("ana@example.com")
              .fullName("Ana")
              .isActive(true);
      }
      public static Customer premium() {
          return aCustomer().tier(Tier.GOLD).build();
      }
  }
  ```
- **NÃO DEVE** usar ObjectMother gigantes com dezenas de métodos — prefira builders fluentes.

---

## 8. Testes de Slice (Spring parcial)

São testes "meio-caminho": sobem apenas parte do contexto Spring. Úteis para testar integração com uma única tecnologia.

### 8.1 `@DataJpaTest` — Camada de persistência

- **DEVE** usar para testar queries customizadas, mapeamento JPA e constraints.
- **RECOMENDADO:** combinar com **Testcontainers** (PostgreSQL real), pois H2 diverge do PostgreSQL em tipos (`UUID`, `JSONB`, arrays) e funções.

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = Replace.NONE) // impede troca para H2
class CustomerRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void shouldFindByEmail_whenCustomerExists() {
        customerRepository.save(CustomerFixtures.aCustomer().email("ana@example.com").build());

        Optional<Customer> result = customerRepository.findByEmail("ana@example.com");

        assertThat(result).isPresent();
    }
}
```

### 8.2 `@WebMvcTest` — Camada web

- **DEVE** usar para testar controllers isoladamente com `MockMvc`.
- Dependências do controller são mockadas com `@MockBean`.

```java
@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CustomerService customerService;

    @Test
    void shouldReturn404_whenCustomerNotFound() throws Exception {
        when(customerService.findById(1L)).thenThrow(new CustomerNotFoundException());

        mockMvc.perform(get("/customers/1"))
            .andExpect(status().isNotFound());
    }
}
```

### 8.3 Outras slices úteis

- `@JsonTest` — serialização/desserialização Jackson.
- `@RestClientTest` — clientes HTTP com `MockRestServiceServer`.
- `@JdbcTest` — quando usar `JdbcTemplate` direto em vez de JPA.

---

## 9. Testes de Integração — Opções Avaliadas

Esta seção documenta as opções consideradas, seus trade-offs e quais foram **adotadas** pelo time. Decisões consolidadas no §10 e §17.

**Resumo das decisões:**

- ✅ **Adotada:** Opção A (`@SpringBootTest` + Testcontainers com BD real).
- ✅ **Adotada:** MockMvc como cliente HTTP nos testes de integração (em vez de REST Assured).
- ✅ **Adotada:** Opção G (Spring Cloud Contract) para contratos entre serviços.
- 🕓 **Reservada para uso futuro:** Opção F (Testcontainers para Kafka/Redis/LocalStack) — ativar quando essas tecnologias entrarem na stack.
- ❌ **Rejeitadas:** Opções B (H2), C (REST Assured), D (WebTestClient), H (Karate).

### Opção A — `@SpringBootTest` + Testcontainers (DB real) ✅ ADOTADA

**O quê:** sobe o contexto completo da aplicação, usando BD real (PostgreSQL) em container e bindando dinamicamente.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
class CustomerCheckoutIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;

    @Test
    void shouldPersistCustomerAndReturn201() throws Exception {
        mockMvc.perform(post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "ana@example.com", "fullName": "Ana" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists());
    }
}
```

- **Prós:** alta fidelidade (BD real, Flyway roda de verdade), poucas surpresas em produção, `@ServiceConnection` (Spring Boot 3.1+) elimina boilerplate.
- **Contras:** mais lento (~2-5s por classe com reuso de container), exige Docker na máquina do dev e no CI.

### Opção B — `@SpringBootTest` + H2 in-memory ❌ REJEITADA

**O quê:** mesmo que A, mas com H2 em modo PostgreSQL.

- **Prós:** mais rápido, sem dependência de Docker.
- **Contras:** divergências sutis (tipos, funções, sintaxe de `UPSERT`, `JSONB` não suportado). **NÃO RECOMENDADO** para projeto que usa PostgreSQL em produção.
- **Motivo da rejeição:** risco de "passa no teste, falha em produção" por divergência de dialeto.

### Opção C — REST Assured (testes de API declarativos) ❌ REJEITADA

**O quê:** biblioteca BDD-ish para testar APIs HTTP, combinável com `@SpringBootTest(webEnvironment = RANDOM_PORT)`.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class CustomerApiIT {

    @LocalServerPort int port;

    @BeforeEach
    void setup() { RestAssured.port = port; }

    @Test
    void shouldCreateCustomer() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"ana@example.com\",\"fullName\":\"Ana\"}")
        .when()
            .post("/customers")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("email", equalTo("ana@example.com"));
    }
}
```

- **Prós:** sintaxe natural, ótimo para contratos de API; permite schema validation (JSON Schema).
- **Contras:** curva de aprendizado se o time já domina `MockMvc`.
- **Motivo da rejeição:** o time adotou **MockMvc** como cliente padrão para manter consistência com os testes de slice `@WebMvcTest`.

### Opção D — `WebTestClient` (stack reativa / não-reativa) ❌ REJEITADA

**O quê:** cliente do Spring WebFlux (funciona também em MVC).

- **Prós:** API fluente moderna, boa para Webflux/Kotlin coroutines; suporta streaming.
- **Contras:** menos familiar para times 100% MVC.
- **Motivo da rejeição:** a stack não é reativa; MockMvc já cobre o caso.

### Opção E — WireMock (mock de serviços externos) ✅ ADOTADA (quando aplicável)

**O quê:** simula APIs externas (pagamento, e-mail, etc.) respondendo a chamadas HTTP reais da aplicação.

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class PaymentIntegrationIT {

    @Test
    void shouldChargeCustomer() {
        stubFor(post("/charges")
            .willReturn(okJson("{\"status\":\"approved\"}")));
        // ... chama o serviço da aplicação que internamente bate em /charges
    }
}
```

- **Prós:** não depende de sandbox do fornecedor; cenários de erro (timeouts, 500) são triviais.
- **Contras:** exige definir os stubs; risco de "mock drift" em relação ao provedor real — mitigar com contract tests.

### Opção F — Testcontainers para dependências não-BD 🕓 RESERVADA PARA USO FUTURO

Containers para Kafka, RabbitMQ, Redis, LocalStack (AWS), Keycloak, etc. Mesma filosofia da Opção A aplicada a outros backends.

- **Status:** **manter esta opção "armada"** no spec. Quando o projeto incorporar qualquer uma dessas tecnologias, ativar sem necessidade de nova discussão arquitetural.
- **Gatilho de ativação:** primeira história que introduzir mensageria, cache distribuído, integração AWS ou autenticação externa.
- **Ação preparatória:** manter as dependências do Testcontainers já declaradas no `pom.xml` (`testcontainers-bom`) para facilitar a inclusão dos módulos específicos.

### Opção G — Spring Cloud Contract (Contract Testing) ✅ ADOTADA

**O quê:** contratos consumer-driven entre microserviços, gerando stubs e testes automaticamente.

- **Prós:** ideal em arquitetura com múltiplos serviços; elimina mocks desatualizados; produz stubs publicáveis no Artifactory/Nexus.
- **Contras:** overhead inicial para introduzir a cultura de contratos.
- **Escopo de uso:** toda comunicação síncrona (HTTP) entre serviços internos do time **DEVE** ser coberta por contrato. Comunicação com sistemas legados ou terceiros **PODE** ser coberta com WireMock enquanto um contrato formal não for viável.

### Opção H — Karate ❌ REJEITADA

**O quê:** framework all-in-one em DSL própria (Gherkin-like) para APIs, incluindo mock server.

- **Prós:** excelente para testes end-to-end e QA não-dev.
- **Contras:** sai do ecossistema Java puro; cenários complexos em código ficam verbosos.
- **Motivo da rejeição:** duplicaria responsabilidades já cobertas por MockMvc + Spring Cloud Contract.

---

## 10. Decisões Consolidadas (stack oficial do projeto)

| Necessidade | Ferramenta adotada |
|---|---|
| Teste unitário | **JUnit 5 + Mockito + AssertJ** |
| Slice de repositório | **`@DataJpaTest` + Testcontainers PostgreSQL** |
| Slice de controller | **`@WebMvcTest` + MockMvc** |
| Integração completa (API) | **`@SpringBootTest` + Testcontainers + MockMvc** (Opção A) |
| Mock de dependência externa HTTP | **WireMock** |
| Contract testing entre serviços | **Spring Cloud Contract** |
| Mensageria / cache / cloud (futuro) | **Testcontainers** (Opção F, ativar quando necessário) |
| Gerenciamento de migrations em teste | **Flyway** (mesma config da produção) |

### 10.1 Configuração-chave Testcontainers (com reuso em CI)

- **DEVE** habilitar reuso de container localmente **e no CI**, via propriedade de sistema ou arquivo `~/.testcontainers.properties`:
  ```
  testcontainers.reuse.enable=true
  ```
- **DEVE** usar `@ServiceConnection` (Spring Boot 3.1+) para evitar `@DynamicPropertySource` manual.
- **DEVE** declarar o container como `static` + marcar com `.withReuse(true)` para reaproveitamento entre classes e builds:
  ```java
  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withReuse(true);
  ```
- **DEVE** usar **uma única JVM** no Surefire/Failsafe (`forkCount=1`, `reuseForks=true`) para maximizar o reuso.
- **DEVE**, no pipeline de CI, usar runner com Docker persistente (Docker-in-Docker com cache ou Docker socket montado) para que o reuso funcione entre jobs da mesma máquina.
- **RECOMENDADO:** cachear a imagem `postgres:16-alpine` no CI para evitar pull a cada build.

### 10.2 Configuração-chave Flyway nos testes

- **DEVE** usar **Flyway** como gerenciador único de migrations, tanto em produção quanto nos testes de integração (mesma stack).
- **DEVE** rodar migrations reais contra o Testcontainers (**NÃO USAR** `hibernate.ddl-auto=create`).
- **DEVE** manter em perfil `test`:
  ```yaml
  spring:
    flyway:
      enabled: true
      locations: classpath:db/migration
      validate-on-migrate: true
    jpa:
      hibernate:
        ddl-auto: validate
  ```
- **DEVE** reutilizar os mesmos scripts `V__`/`R__` da pasta `src/main/resources/db/migration` — sem scripts divergentes para teste.
- **PODE** adicionar scripts `afterMigrate__*.sql` apenas com dados-semente de testes, desde que em `src/test/resources/db/migration` e não interfiram em produção.

---

## 11. Organização no Maven / Gradle

### 11.1 Separação unitário x integração

- **DEVE** executar testes unitários no **Surefire** (fase `test`).
- **DEVE** executar testes de integração no **Failsafe** (fase `verify`), identificados pelo sufixo `IT`.

```xml
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <includes>
      <include>**/*IT.java</include>
    </includes>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### 11.2 Pipeline de CI (sugestão de estágios)

1. **`test`** — unitários + slices (paralelo, rápido). Falha cedo.
2. **`verify`** — testes de integração com Testcontainers.
3. **`e2e`** (job separado) — testes contra ambiente de staging.

---

## 12. Cobertura

- **DEVE** medir com **JaCoCo**.
- **Meta mínima consolidada do projeto: 70% de cobertura de linhas** (agregado).
- **DEVE** falhar o build quando a cobertura agregada cair abaixo de 70%, configurado via `jacoco-maven-plugin`:
  ```xml
  <plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
      <execution>
        <goals><goal>prepare-agent</goal></goals>
      </execution>
      <execution>
        <id>report</id>
        <phase>verify</phase>
        <goals><goal>report</goal></goals>
      </execution>
      <execution>
        <id>check</id>
        <phase>verify</phase>
        <goals><goal>check</goal></goals>
        <configuration>
          <rules>
            <rule>
              <element>BUNDLE</element>
              <limits>
                <limit>
                  <counter>LINE</counter>
                  <value>COVEREDRATIO</value>
                  <minimum>0.70</minimum>
                </limit>
              </limits>
            </rule>
          </rules>
        </configuration>
      </execution>
    </executions>
  </plugin>
  ```
- **RECOMENDADO** (não obrigatório) buscar patamares mais altos nas camadas críticas:
  - Domínio: alvo de **90%**.
  - Application/Services: alvo de **85%**.
  - Controllers: alvo de **75%**.
- **NÃO DEVE** perseguir cobertura como meta absoluta — testes de baixo valor inflam o número sem proteger o sistema.

---

## 13. Antipadrões (Proibidos)

- `Thread.sleep(...)` para aguardar comportamento assíncrono → use **Awaitility**.
- Tests que dependem de ordem (`@TestMethodOrder` fora de cenários muito específicos).
- Mocks de classes concretas próprias quando uma interface existe — mock a interface.
- Assertivas genéricas (`assertNotNull(resultado)`) que não capturam a intenção.
- `@Disabled` sem comentário explicando o motivo e um ticket de reativação.
- Dependência entre testes (compartilhar estado em campos estáticos mutáveis).
- Testes que usam dados de produção.
- `catch (Exception e) { fail(); }` — use `assertThatThrownBy` (AssertJ).

---

## 14. Checklist de Revisão de PR

- [ ] Teste segue estrutura AAA com linhas em branco separando blocos.
- [ ] Nome do método no padrão `should..._when...()`.
- [ ] Classe termina com `Test` (unitário/slice) ou `IT` (integração).
- [ ] Usa AssertJ em vez de `assertEquals` etc.
- [ ] Testes unitários não sobem contexto Spring.
- [ ] Testes de integração usam Testcontainers, não H2.
- [ ] Nenhum `Thread.sleep`.
- [ ] Mocks são de interfaces ou classes "de fronteira", não de DTOs/entidades.
- [ ] Fixtures/builders usados em vez de grandes blocos `new ...(...)`.
- [ ] `verify()` usado apenas quando efeito colateral é parte do contrato.
- [ ] Testes rodam localmente sem configuração manual adicional.

---

## 15. Dependências-base (Maven)

```xml
<dependencies>
  <!-- Spring Boot + JUnit 5 + Mockito + AssertJ já vêm juntos -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>

  <!-- Testcontainers -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
  </dependency>

  <!-- Spring Cloud Contract (contract testing entre serviços) -->
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-verifier</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-contract-stub-runner</artifactId>
    <scope>test</scope>
  </dependency>

  <!-- WireMock (mock de serviços externos quando Contract não se aplica) -->
  <dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <scope>test</scope>
  </dependency>

  <!-- Awaitility (aguardar condições assíncronas sem Thread.sleep) -->
  <dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

---

## 16. Referências

- *Growing Object-Oriented Software, Guided by Tests*, Freeman & Pryce.
- *Unit Testing Principles, Practices, and Patterns*, Vladimir Khorikov.
- Documentação oficial: JUnit 5, Mockito, AssertJ, Testcontainers, Spring Framework Testing.
- Martin Fowler — *Test Pyramid*, *Integration Test*, *Contract Test*.

---

## 17. Decisões Consolidadas

Esta seção registra as escolhas arquiteturais formalmente aprovadas pelo time:

| Tema | Decisão | Responsável | Data |
|---|---|---|---|
| Estratégia de teste de integração | **Opção A** — `@SpringBootTest` + Testcontainers com PostgreSQL real | Gustavo | 2026-04-17 |
| Testcontainers para dependências não-BD (Kafka, Redis, LocalStack, etc.) | **Opção F** — mantida reservada para uso futuro, ativar quando a tecnologia entrar na stack | Gustavo | 2026-04-17 |
| Ferramenta principal para testes de API | **MockMvc** (consistência com `@WebMvcTest`; REST Assured rejeitado) | Gustavo | 2026-04-17 |
| Contract testing entre serviços | **Spring Cloud Contract** — adotado | Gustavo | 2026-04-17 |
| Cobertura mínima do projeto | **70% (agregado, aferido por JaCoCo, quebra o build abaixo disso)** | Gustavo | 2026-04-17 |
| Reuso de Testcontainers no CI | **Sim** — `testcontainers.reuse.enable=true` + `forkCount=1` + cache de imagens no runner | Gustavo | 2026-04-17 |
| Gerenciador de migrations em teste | **Flyway** — mesma configuração e mesmos scripts de produção | Gustavo | 2026-04-17 |

---

## 18. Histórico de Revisões

| Versão | Data | Autor | Descrição |
|---|---|---|---|
| 1.0 | 2026-04-17 | Gustavo | Versão inicial do spec com opções de integração em aberto. |
| 1.1 | 2026-04-17 | Gustavo | Consolidadas decisões: Opção A adotada, Opção F reservada, MockMvc como cliente HTTP, Spring Cloud Contract adotado, cobertura mínima de 70%, reuso de Testcontainers no CI, Flyway como gerenciador de migrations em teste. |
