# Spending System

Bot de controle de gastos via WhatsApp. O projeto recebe mensagens pela Evolution API, registra os gastos em PostgreSQL e responde pelo WhatsApp com confirmacoes, resumo ou remocao de itens.

## Tecnologias

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- PostgreSQL
- Evolution API
- Maven

## Como funciona

Fluxo principal:

```text
WhatsApp -> Evolution API -> Webhook Spring Boot -> PostgreSQL
                               |
                               v
                         Resposta via Evolution API
```

O webhook recebe eventos `messages.upsert` em:

```text
POST /webhook/whatsapp
POST /webhook/whatsapp/messages-upsert
```

## Configuracao

As configuracoes podem ser definidas por variaveis de ambiente:

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `PORT` | `8080` | Porta HTTP da aplicacao |
| `PGHOST` | `localhost` | Host do PostgreSQL |
| `PGPORT` | `5432` | Porta do PostgreSQL |
| `PGDATABASE` | `spendingsystem` | Nome do banco |
| `PGUSER` | `postgres` | Usuario do banco |
| `PGPASSWORD` | `postgres` | Senha do banco |
| `EVOLUTION_BASE_URL` | `http://localhost:8081` | URL base da Evolution API |
| `EVOLUTION_API_KEY` | vazio | Chave da Evolution API |
| `EVOLUTION_INSTANCE` | vazio | Nome da instancia na Evolution API |
| `BOT_ALLOWED_JIDS` | vazio | JIDs autorizados a acionar o bot, separados por virgula |

Defina `BOT_ALLOWED_JIDS` para evitar que o bot responda conversas fora da lista:

```powershell
$env:BOT_ALLOWED_JIDS="120363123456789012@g.us,120363987654321098@g.us"
```

Use o JID completo da conversa. Para grupos, o JID termina com `@g.us`; para conversa direta, normalmente termina com `@s.whatsapp.net`. Em grupos autorizados, qualquer participante pode enviar mensagens e o bot responde no grupo.

## Rodando localmente

Suba o PostgreSQL:

```powershell
docker compose up -d
```

Configure as variaveis da Evolution API:

```powershell
$env:EVOLUTION_BASE_URL="https://sua-evolution-api.com"
$env:EVOLUTION_API_KEY="sua-chave"
$env:EVOLUTION_INSTANCE="sua-instancia"
$env:BOT_ALLOWED_JIDS="120363123456789012@g.us,120363987654321098@g.us"
```

Rode a aplicacao:

```powershell
mvn spring-boot:run
```

Se o `JAVA_HOME` nao estiver configurado, aponte para uma JDK 21 antes de rodar:

```powershell
$env:JAVA_HOME="C:\Users\Admin\.jdks\ms-21.0.11"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## Webhook na Evolution API

Configure a Evolution API para enviar eventos `messages.upsert` para:

```text
https://seu-dominio.com/webhook/whatsapp
```

Em ambiente local, exponha a porta com uma ferramenta como ngrok ou Cloudflare Tunnel e use a URL publica gerada.

## Mensagens aceitas

Registrar gasto:

```text
Pizza 50
Uber 22,90 Transporte
Mercado 120,50 Alimentacao
```

Ver resumo:

```text
/resumo
```

Remover gasto por descricao:

```text
remover "Pizza"
remover Pizza
```

Remover todos os gastos do seu numero:

```text
remover todos
```

## Testes

Execute:

```powershell
mvn test
```

Os testes usam perfil `test` e banco H2 em memoria.
