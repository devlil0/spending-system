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
| `BOT_ALLOWED_PHONE` | vazio | Numero autorizado a usar o bot |

Defina `BOT_ALLOWED_PHONE` para evitar que o bot responda qualquer pessoa:

```powershell
$env:BOT_ALLOWED_PHONE="5511999999999"
```

Use o numero com DDI e DDD. O codigo compara apenas os digitos, entao `+55 11 99999-9999` e `5511999999999` sao equivalentes.

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
$env:BOT_ALLOWED_PHONE="5511999999999"
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
