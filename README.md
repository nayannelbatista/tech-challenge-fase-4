# Plataforma de Feedback

Projeto desenvolvido para o Tech Challenge da FIAP, Fase 4. A aplicação recebe avaliações por uma API HTTP, armazena os registros e envia notificações administrativas para avaliações críticas. Uma rotina semanal consolida os dados e encaminha um relatório por e-mail.

O código-fonte está publicado no [GitHub](https://github.com/nayannelbatista/tech-challenge-fase-4). A implantação na AWS e os testes de integração em ambiente cloud estão pendentes.

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

`POST /avaliação` recebe JSON com `descricao` e `nota`. Em URLs, o caractere `ç` é codificado como `%C3%A7` (o template publica a rota como `/avalia%C3%A7%C3%A3o`):

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

Os registros incluem identificador UUID, descrição, nota, urgência e instante em UTC no formato ISO-8601. A chave de partição guarda a data UTC (`dia`), permitindo consultar cada dia do período semanal sem varredura da tabela.

Avaliações críticas são publicadas no barramento EventBridge e encaminhadas ao SNS. O relatório semanal inclui os dados consultados, contagem por dia, contagem por urgência e média das notas. A função consulta uma vez por dia e não pagina os resultados do DynamoDB; se uma consulta diária exceder o limite de uma resposta, registros adicionais não entram no relatório.

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

A implantação na AWS ainda está pendente. Quando realizada, requer AWS CLI e SAM CLI configurados para a conta e região de destino. O comando padrão de implantação guiada é:

```bash
sam deploy --guided
```

A implantação cria recursos sujeitos a cobrança, incluindo chamadas de API e Lambda, leituras e gravações no DynamoDB, eventos, armazenamento de logs e mensagens SNS. O custo depende da região, do volume e do período de retenção dos logs; consulte os preços vigentes da AWS antes da implantação. O endereço definido pelo parâmetro `AdminEmail` precisa confirmar a inscrição SNS recebida por e-mail. Até a confirmação, as notificações e os relatórios não serão entregues a esse endereço.

## Segurança

- O endpoint de envio é público e não exige autenticação. O template aplica throttling na API. `FeedbackFunction` recebe permissão de escrita na tabela e de publicação no barramento EventBridge; `CriticalNotificationFunction` recebe permissão de publicação no tópico SNS, e a regra EventBridge pode invocá-la; `WeeklyReportFunction` recebe permissão de leitura na tabela e publicação no mesmo tópico. As políticas geradas pelo SAM são vinculadas aos recursos declarados.
- DynamoDB tem criptografia em repouso habilitada; a recuperação point-in-time está desativada no template. O tópico SNS usa a chave gerenciada pela AWS `alias/aws/sns`. As funções têm tracing ativo pelo AWS X-Ray e logs em formato JSON.
- A descrição é armazenada no DynamoDB e incluída nas notificações e relatórios SNS. O código de tratamento de erros evita registrar o conteúdo do feedback nos logs.
- Não inclua credenciais, tokens ou dados reais de feedback no repositório.
- O relatório envia as avaliações consultadas dos sete dias em uma única mensagem SNS. A consulta não pagina resultados do DynamoDB e mensagens SNS têm limite de tamanho; volumes elevados podem exigir paginação das consultas e uma estratégia de divisão do relatório ou armazenamento externo.

## Demonstração

A demonstração funcional em ambiente AWS depende da implantação e dos testes de integração cloud, ainda pendentes. Após essa etapa, o fluxo pode ser apresentado com uma submissão normal, uma submissão crítica e o relatório semanal, verificando as mensagens SNS, os registros no DynamoDB e as execuções das funções Lambda.
