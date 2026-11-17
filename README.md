# Plataforma de Feedback — FIAP Tech Challenge Fase 4

Implementação inicial em Java 21 e AWS Serverless Application Model (SAM). Esta versão contém o código, testes unitários e template de infraestrutura; **nenhum recurso foi criado na AWS**.

## Arquitetura

```text
POST /avaliação → API Gateway HTTP API → FeedbackFunction → DynamoDB
                                               └─ crítico → EventBridge → CriticalNotificationFunction → SNS/e-mail
EventBridge Scheduler (segunda, 09:00 UTC) → WeeklyReportFunction → DynamoDB + SNS/e-mail
```

- `FeedbackFunction`: valida `descricao` e `nota`, persiste o registro e publica evento apenas para nota crítica.
- `CriticalNotificationFunction`: recebe eventos críticos e publica descrição, urgência e data no tópico SNS.
- `WeeklyReportFunction`: consulta os sete dias completos anteriores e envia descrições, datas, urgência, contagem por dia, contagem por urgência e média.
- `FeedbackTable`: DynamoDB sob demanda, chave `dia` + `id` e criptografia em repouso.
- Observabilidade: logs JSON e AWS X-Ray ativo nas funções. IAM criado pelo SAM concede somente escrita/leitura da tabela, publicação no EventBridge e publicação no tópico.
- Proteção básica do endpoint público: throttling de estágio configurado para 10 requisições/segundo com burst de 20.

**Regra inicial de criticidade:** notas de 0 a 4. Confirmar se o enunciado/avaliação da disciplina define outro limite antes de publicar.

## Contrato

`POST /avaliação` com `Content-Type: application/json`:

```json
{"descricao":"Atendimento demorou","nota":3}
```

Resposta de sucesso `201` contém `id`, `urgencia` e `data`. Descrições vazias, maiores que 4.000 caracteres e notas fora do intervalo inteiro de 0 a 10 retornam `400`.

## Estado do ambiente local

Verificação inicial: OpenJDK/Javac 21 e Git disponíveis. Maven, AWS CLI e AWS SAM CLI não estavam instalados. O diretório inicial não era um repositório Git.

Verificação posterior: Maven 3.10.1, AWS CLI 2.40.3 e SAM CLI 1.172.1 instalados localmente; `mvn test` passou com 4 testes e `sam validate --lint` marcou o template como válido. `sam build` compilou e empacotou as três funções com sucesso. Nada foi implantado na AWS.

## Construção e testes

Com Maven 3.9+:

```bash
mvn test
mvn package
```

O pacote sombreado é utilizado pelas funções Java Lambda. Para execução local do endpoint, depois de instalar AWS SAM CLI e Docker:

```bash
sam build
sam local start-api
```

O teste local de handlers que acessam AWS requer uma forma local de fornecer os serviços AWS (por exemplo, LocalStack) ou credenciais e recursos existentes; os testes unitários do domínio não dependem disso.

## Deploy (não executado)

Pré-requisitos: AWS CLI configurada com perfil de menor privilégio, SAM CLI, bucket de artefatos (ou `sam deploy --guided`) e e-mail administrativo capaz de confirmar uma inscrição SNS. Antes do primeiro deploy, revisar região, custos e quotas. O primeiro deploy cria API Gateway HTTP API, três funções Lambda, tabela DynamoDB sob demanda, tópico/inscrição SNS, EventBridge customizado e regras; pode haver cobranças por chamadas, armazenamento, logs, eventos e notificações. Não executar até autorização explícita da responsável.

Depois da autorização e instalação das ferramentas:

```bash
sam deploy --guided
```

Há workflows preparados em `.github/workflows`: CI executa testes, validação SAM e build em alterações; deploy é exclusivamente manual (`workflow_dispatch`) e só inicia se a pessoa marcar `confirm_aws_deploy`. O deploy também usa o Environment `aws-deploy-approval`. Nas configurações do GitHub, fora do YAML, configure esse Environment com reviewer obrigatório, secret `AWS_DEPLOY_ROLE_ARN`, secret `ADMIN_EMAIL` e variável `AWS_REGION`. A role deve confiar no GitHub via OIDC e ter permissões limitadas ao stack e recursos da aplicação. O workflow utiliza `--resolve-s3`, que cria ou reutiliza o bucket gerenciado de artefatos da SAM, e faz deploy quando acionado; obter autorização da responsável antes de iniciar essa execução.

O endereço informado em `AdminEmail` receberá uma mensagem da AWS para confirmar a inscrição. Sem confirmação, alertas e relatórios não chegam por e-mail. A região e valores de custo devem ser registrados na documentação da apresentação.

## Segurança, governança e limitações a fechar

- A API de submissão é pública para permitir envio sem conta. A proteção inclui validação no handler, throttling e IAM de menor privilégio para os recursos internos. Antes de uso real, avaliar WAF e definir retenção dos dados.
- SNS usa a chave gerenciada AWS `alias/aws/sns`; DynamoDB tem criptografia padrão habilitada.
- Logs não registram descrição de feedback; o conteúdo é enviado apenas ao SNS para os administradores e fica armazenado na tabela.
- Não há dados sensíveis de exemplo no repositório. Não inserir credenciais ou feedback real em issues, commits ou gravações.
- A rota Unicode deve ser confirmada com `sam validate` e teste após deploy; se API Gateway ou clientes não preservarem Unicode, documentar o caminho percent-encoded equivalente antes da demonstração.
- Relatórios SNS têm limite de tamanho de mensagem; avaliar paginação/arquivo em S3 caso o volume de feedback cresça.

## Entrega e demonstração

1. Executar testes e empacotamento local (feito: quatro testes passaram).
2. Validar template (`sam validate --lint`) e revisar permissões (validação feita; revisão final pendente).
3. Com autorização explícita, provisionar em região definida e testar submissão normal/crítica e relatório.
4. O repositório público foi criado em https://github.com/nayannelbatista/tech-challenge-fase-4; enviar o primeiro commit depois da revisão final.
5. Gravar vídeo mostrando arquitetura, chamadas da API, três funções, tabela, regra semanal, logs e configuração de IAM/SNS, ocultando dados pessoais.

O repositório público existe, mas o código ainda não foi publicado. O vídeo ainda não foi gravado.
