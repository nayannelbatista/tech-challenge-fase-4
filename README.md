# Plataforma de Feedback

Projeto desenvolvido para o Tech Challenge da FIAP, Fase 4. A aplicação recebe avaliações por uma API HTTP, armazena os registros e envia notificações administrativas para avaliações críticas. Uma rotina semanal consolida os dados e encaminha um relatório por e-mail.

O código-fonte está publicado no [GitHub](https://github.com/nayannelbatista/tech-challenge-fase-4). A stack `fiap-tech-challenge-fase4` está implantada na região `us-east-2`, com a rota `/avaliacao` ativa.

## Arquitetura

```text
Cliente → API Gateway HTTP API → FeedbackFunction → DynamoDB
                                       └─ avaliação crítica → EventBridge
                                                                └→ CriticalNotificationFunction → SNS

EventBridge (agenda semanal) → WeeklyReportFunction → DynamoDB e SNS
```

- **FeedbackFunction** recebe e valida avaliações, grava cada registro no DynamoDB e publica um evento EventBridge quando a nota é crítica.
- **CriticalNotificationFunction** consome eventos de avaliações críticas e publica uma notificação no tópico SNS.
- **WeeklyReportFunction** consulta os sete dias completos anteriores, calcula total, média e contagens por dia e urgência, e envia o relatório pelo SNS.
- **FeedbackTable** usa `dia` como chave de partição e `id` como chave de ordenação. O faturamento sob demanda acompanha o volume de leitura e escrita.

O endpoint está configurado como público, com limite de 10 requisições por segundo e burst de 20. A regra semanal é executada às segundas-feiras, às 09:00 UTC.

## Tecnologias

- Java 21 e Maven
- AWS Lambda e AWS Serverless Application Model (SAM)
- API Gateway HTTP API
- Amazon DynamoDB
- Amazon EventBridge
- Amazon SNS
- JUnit 5

## Funcionamento

### API

`POST /avaliacao` recebe JSON com `descricao` e `nota`:

```http
Content-Type: application/json
```

```json
{
  "descricao": "O atendimento foi rápido e atencioso.",
  "nota": 9
}
```

A nota deve ser um número inteiro entre 0 e 10. A descrição é obrigatória, não pode conter apenas espaços e deve ter até 4.000 caracteres. Espaços nas extremidades são removidos. Notas de 0 a 4 são classificadas como `CRITICA`; de 5 a 10, como `NORMAL`.

Em caso de sucesso, a API responde com status `201`:

```json
{
  "id": "identificador-gerado",
  "urgencia": "NORMAL",
  "data": "2026-11-17T12:00:00Z"
}
```

Entradas inválidas retornam status `400` e um objeto JSON com o campo `erro`. Falhas inesperadas retornam status `500`.

### Persistência e notificações

Os registros incluem identificador UUID, descrição, nota, urgência e instante em UTC no formato ISO-8601. A chave de partição guarda a data UTC (`dia`), permitindo consultar cada dia do período semanal sem varredura da tabela. As consultas paginam pelos resultados usando `LastEvaluatedKey`.

Avaliações críticas são publicadas no barramento EventBridge e encaminhadas ao SNS. O relatório semanal inclui os dados consultados, contagem por dia, contagem por urgência e média das notas. A função consulta cada um dos sete dias do período; cada consulta pagina os resultados do DynamoDB usando `LastEvaluatedKey`.

## Execução local

Requisitos: JDK 21, Maven, AWS SAM CLI e Docker para executar a API local com SAM. Os testes unitários do domínio podem ser executados sem recursos AWS:

```bash
mvn test
```

Para compilar e empacotar:

```bash
mvn package
```

Para iniciar a API localmente:

```bash
sam build
sam local start-api
```

A execução local das funções que acessam DynamoDB, EventBridge ou SNS depende desses serviços disponíveis ou de uma implementação local compatível, além das variáveis de ambiente configuradas no template.

## Implantação

A infraestrutura é definida em `template.yaml`. O template declara a API, as três funções Lambda, a tabela DynamoDB, o barramento e regra EventBridge, o tópico e inscrição SNS e a agenda semanal.

A stack `fiap-tech-challenge-fase4` está implantada na região `us-east-2`, com status `UPDATE_COMPLETE`. O endpoint publicado é `https://gptomgt3v8.execute-api.us-east-2.amazonaws.com/prod/avaliacao`. Para atualizar a stack existente ou realizar uma nova implantação em outra conta/região, são necessários AWS CLI e SAM CLI configurados para o destino. O modo guiado é:

```bash
sam deploy --guided
```

A inscrição SNS configurada pelo parâmetro `AdminEmail` foi confirmada. Chamadas de API e Lambda, operações DynamoDB, eventos, armazenamento de logs e mensagens SNS podem gerar custos conforme região e volume; consulte os preços vigentes da AWS. A entrega por e-mail depende da inscrição SNS permanecer confirmada.

### Implantação automatizada

O workflow `.github/workflows/deploy.yml` está configurado para execução manual (`workflow_dispatch`) e exige a confirmação `confirm_aws_deploy`. Ele executa os testes Maven, `sam validate --lint`, `sam build` e atualiza a stack `fiap-tech-challenge-fase4` com SAM. A configuração do GitHub Actions requer o ambiente `aws-deploy-approval`, a role OIDC informada em `AWS_DEPLOY_ROLE_ARN`, a variável `AWS_REGION` e o segredo `ADMIN_EMAIL`. Proteções e revisores desse ambiente devem ser configurados nas definições do GitHub, se desejados. O workflow está configurado, mas não há confirmação de que tenha sido executado; a implantação e a atualização registradas nesta documentação foram realizadas pela CLI SAM.

## Segurança

- O endpoint de envio é público e não exige autenticação. O template aplica throttling na API. `FeedbackFunction` recebe permissão de escrita na tabela e de publicação no barramento EventBridge; `CriticalNotificationFunction` recebe permissão de publicação no tópico SNS, e a regra EventBridge pode invocá-la; `WeeklyReportFunction` recebe permissão de leitura na tabela e publicação no mesmo tópico. As políticas geradas pelo SAM são vinculadas aos recursos declarados.
- DynamoDB tem criptografia em repouso habilitada; a recuperação point-in-time está desativada no template. O tópico SNS usa a chave gerenciada pela AWS `alias/aws/sns`. As funções têm tracing ativo pelo AWS X-Ray e logs em formato JSON.
- A descrição é armazenada no DynamoDB e incluída nas notificações e relatórios SNS. O código de tratamento de erros evita registrar o conteúdo do feedback nos logs.
- Não inclua credenciais, tokens ou dados reais de feedback no repositório.
- O relatório pagina as consultas DynamoDB e divide o conteúdo em mensagens SNS sequenciais, limitadas a 240 KiB em bytes UTF-8 por mensagem.

## Monitoramento

- As funções Lambda enviam logs em formato JSON ao CloudWatch Logs. Consulte os grupos `/aws/lambda/<nome-da-função>` para investigar invocações e erros.
- O tracing AWS X-Ray está ativo nas funções e permite inspecionar traces e dependências da aplicação.
- O template não configura retenção dos logs, alarmes, dashboards ou access logs do API Gateway.

## Demonstração

Os testes Maven, `sam validate --lint` e `sam build` foram concluídos com sucesso. Na AWS, `POST /avaliacao` retornou `201` para feedback normal e crítico, ambos persistidos no DynamoDB. A avaliação crítica acionou a regra EventBridge e a Lambda de notificação; o e-mail de alerta foi recebido pela assinatura SNS confirmada. A função do relatório semanal foi invocada manualmente, executou sem erro e enviou o relatório por e-mail. O relatório cobriu o período de 10/11 a 16/11 e apresentou indicadores zerados, pois não havia avaliações nesse intervalo. A regra EventBridge semanal está provisionada e habilitada para executar às segundas-feiras, às 09:00 UTC; a validação manual da função não representa uma execução disparada por essa agenda.
