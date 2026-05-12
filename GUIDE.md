# Guia de Implementação — Spending System com Evolution API

## Visão Geral

Este projeto recebe mensagens de WhatsApp via **Evolution API (instância Cloud)** e registra gastos no PostgreSQL.

```
WhatsApp → Evolution API Cloud → Webhook POST → seu Spring Boot → PostgreSQL
                                                        ↓
                                             Evolution API REST ← resposta ao usuário
```

A instância Cloud é 100% HTTP: você **não instala Baileys**, apenas consome a REST API e recebe webhooks.

---

## Stack do Projeto

| Camada         | Tecnologia                        |
|----------------|-----------------------------------|
| Framework      | Spring Boot 3.5.14                |
| Linguagem      | Java 21                           |
| Banco          | PostgreSQL (via Docker)           |
| ORM            | Spring Data JPA / Hibernate       |
| Migrations     | Flyway *(adicionar)*              |
| HTTP Client    | RestClient (Spring 6, nativo)     |
| Boilerplate    | Lombok                            |

---

## Dependências a Adicionar no pom.xml

Nenhuma dependência extra necessária para começar.

> `RestClient` já está disponível no `spring-boot-starter-web`. Não precisa adicionar nada.

> **Flyway (opcional):** útil em projetos com equipe ou múltiplos ambientes. Para uso pessoal/Railway, o `ddl-auto: update` do Hibernate é suficiente.

---

## Estrutura de Pacotes

```
src/main/java/com/devlil0/spendingsystem/
├── SpendingSystemApplication.java
│
├── config/
│   └── EvolutionApiConfig.java          # RestClient configurado com base-url + api-key
│
├── webhook/
│   ├── WebhookController.java           # POST /webhook/whatsapp
│   └── dto/
│       ├── EvolutionWebhookPayload.java  # payload completo da Evolution
│       ├── MessageData.java
│       └── MessageKey.java
│
├── whatsapp/
│   └── WhatsappSendMsgService.java      # envia texto via Evolution REST
│
├── spending/
│   ├── SpendingEntity.java              # entidade JPA
│   ├── SpendingRepository.java          # Spring Data
│   ├── SpendingService.java             # regras de negócio
│   └── dto/
│       └── SpendingRequest.java
│
└── parser/
    └── MessageParser.java               # transforma "Pizza 50" → SpendingRequest
```

```
src/main/resources/
├── application.yaml
└── db/migration/
    └── V1__create_spending_table.sql    # Flyway lê aqui automaticamente
```

---

## application.yaml

```yaml
spring:
  application:
    name: spending_system
  datasource:
    url: jdbc:postgresql://localhost:5432/spendingsystem
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update            # Hibernate cria/atualiza o schema automaticamente
    show-sql: true

evolution:
  base-url: https://sua-instancia.evolution.com   # URL da sua instância Cloud
  api-key: sua-api-key-aqui
  instance: nome-da-instancia
```

> No Railway, substitua os valores do `datasource` pelas variáveis de ambiente que o Railway gera automaticamente ao provisionar o PostgreSQL.

---

## Passo 1 — Entidade e Repositório

**SpendingEntity.java**
```java
@Entity
@Table(name = "spending")
@Data
@NoArgsConstructor
public class SpendingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private BigDecimal amount;

    private String category;
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

**SpendingRepository.java**
```java
public interface SpendingRepository extends JpaRepository<SpendingEntity, Long> {
    List<SpendingEntity> findByPhone(String phone);
}
```

---

## Passo 3 — Payload do Webhook da Evolution API

A Evolution envia um JSON para o seu endpoint. O formato relevante para `messages.upsert` é:

```json
{
  "event": "messages.upsert",
  "instance": "nome-da-instancia",
  "data": {
    "key": {
      "remoteJid": "5511999999999@s.whatsapp.net",
      "fromMe": false,
      "id": "ABCDEF123456"
    },
    "message": {
      "conversation": "Pizza 50"
    },
    "messageType": "conversation",
    "messageTimestamp": 1715000000
  }
}
```

**DTOs correspondentes:**

```java
// EvolutionWebhookPayload.java
@Data
public class EvolutionWebhookPayload {
    private String event;
    private String instance;
    private MessageData data;
}

// MessageData.java
@Data
public class MessageData {
    private MessageKey key;
    private MessageContent message;
    private String messageType;
}

// MessageKey.java
@Data
public class MessageKey {
    private String remoteJid;   // "5511999999999@s.whatsapp.net"
    private boolean fromMe;
}

// MessageContent.java
@Data
public class MessageContent {
    private String conversation;          // mensagem de texto simples
    private ExtendedText extendedTextMessage; // mensagem longa (tem campo text)

    public String getText() {
        if (conversation != null) return conversation;
        if (extendedTextMessage != null) return extendedTextMessage.getText();
        return null;
    }
}

// ExtendedText.java
@Data
public class ExtendedText {
    private String text;
}
```

---

## Passo 4 — WebhookController

```java
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final SpendingService spendingService;
    private final WhatsappSendMsgService whatsappSendMsgService;

    @PostMapping("/whatsapp")
    public ResponseEntity<Void> receive(@RequestBody EvolutionWebhookPayload payload) {

        // Ignorar eventos que não sejam mensagens recebidas
        if (!"messages.upsert".equals(payload.getEvent())) {
            return ResponseEntity.ok().build();
        }

        MessageData data = payload.getData();

        // Ignorar mensagens enviadas pelo próprio bot
        if (data.getKey().isFromMe()) {
            return ResponseEntity.ok().build();
        }

        String phone = extractPhone(data.getKey().getRemoteJid());
        String text = data.getMessage().getText();

        if (text == null || text.isBlank()) {
            return ResponseEntity.ok().build();
        }

        String reply = spendingService.processMessage(phone, text.trim());
        whatsappSendMsgService.sendText(phone, reply);

        return ResponseEntity.ok().build();
    }

    // "5511999999999@s.whatsapp.net" → "5511999999999"
    private String extractPhone(String remoteJid) {
        return remoteJid.split("@")[0];
    }
}
```

---

## Passo 5 — Configuração do RestClient

```java
@Configuration
public class EvolutionApiConfig {

    @Value("${evolution.base-url}")
    private String baseUrl;

    @Value("${evolution.api-key}")
    private String apiKey;

    @Bean
    public RestClient evolutionRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("apikey", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
```

---

## Passo 6 — Serviço de Envio

```java
@Service
@RequiredArgsConstructor
public class WhatsappSendMsgService {

    private final RestClient evolutionRestClient;
    
    @Value("${evolution.instance}")
    private String instance;

    public void sendText(String phone, String message) {
        var body = Map.of(
                "number", phone,
                "text", message
        );

        evolutionRestClient.post()
                .uri("/message/sendText/{instance}", instance)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
```

---

## Passo 7 — Parser de Mensagem

O usuário envia mensagens livres. Exemplos de formato que você pode adotar:

| Mensagem do usuário    | Interpretação                      |
|------------------------|------------------------------------|
| `Pizza 50`             | Alimentação · R$ 50,00             |
| `mercado 120.50`       | Alimentação · R$ 120,50            |
| `uber 25 transporte`   | Transporte · R$ 25,00              |
| `/resumo`              | Comando: resumo do mês             |

```java
    @Component
    public class MessageParser {
    
        // Formato esperado: "<descrição> <valor>" ou "<descrição> <valor> <categoria>"
        private static final Pattern SPENDING_PATTERN =
                Pattern.compile("^(.+?)\\s+(\\d+(?:[.,]\\d{1,2})?)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
    
        public Optional<SpendingRequest> parse(String text) {
            Matcher matcher = SPENDING_PATTERN.matcher(text.trim());
            if (!matcher.matches()) return Optional.empty();
    
            String description = matcher.group(1).trim();
            BigDecimal amount = new BigDecimal(matcher.group(2).replace(",", "."));
            String category = matcher.group(3) != null ? matcher.group(3).trim() : "Outros";
    
            return Optional.of(new SpendingRequest(description, amount, category));
        }
    }
```

---

## Passo 8 — SpendingService

```java
@Service
@RequiredArgsConstructor
public class SpendingService {

    private final SpendingRepository spendingRepository;
    private final MessageParser messageParser;

    public String processMessage(String phone, String text) {
        // Comando de resumo
        if (text.equalsIgnoreCase("/resumo")) {
            return buildSummary(phone);
        }

        // Tenta interpretar como gasto
        return messageParser.parse(text)
                .map(req -> saveSpending(phone, req))
                .orElse("Não entendi. Envie no formato: *Descrição Valor* (ex: Pizza 50)");
    }

    private String saveSpending(String phone, SpendingRequest req) {
        var entity = new SpendingEntity();
        entity.setPhone(phone);
        entity.setAmount(req.amount());
        entity.setCategory(req.category());
        entity.setDescription(req.description());
        spendingRepository.save(entity);

        return String.format("✅ Gasto registrado!\n📝 %s\n💰 R$ %.2f\n🏷️ %s",
                req.description(), req.amount(), req.category());
    }

    private String buildSummary(String phone) {
        var gastos = spendingRepository.findByPhone(phone);
        if (gastos.isEmpty()) return "Nenhum gasto registrado ainda.";

        BigDecimal total = gastos.stream()
                .map(SpendingEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return String.format("📊 *Resumo de gastos*\nTotal: R$ %.2f\nRegistros: %d",
                total, gastos.size());
    }
}
```

---

## Configurar o Webhook na Evolution API

No painel da Evolution (ou via API), configure o webhook da sua instância:

```
URL:    https://seu-servidor.com/webhook/whatsapp
Eventos: messages.upsert
```

Para testar localmente, use **ngrok**:
```bash
ngrok http 8080
# Copie a URL gerada (ex: https://abc123.ngrok.io) e configure na Evolution
```

---

## Ordem de Implementação Recomendada

- [ ] 1. Configurar `application.yaml` (datasource + evolution)
- [ ] 2. Criar `SpendingEntity` + `SpendingRepository`
- [ ] 3. Criar DTOs do webhook (`EvolutionWebhookPayload`, etc.)
- [ ] 4. Criar `WebhookController`
- [ ] 5. Criar `EvolutionApiConfig` + `WhatsappSendMsgService`
- [ ] 6. Criar `MessageParser` + `SpendingService`
- [ ] 7. Subir com Docker Compose e testar via ngrok

---

## docker-compose.yaml (referência)

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: spendingsystem
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
```
