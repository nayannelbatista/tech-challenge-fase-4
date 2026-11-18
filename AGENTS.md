# Instruções do projeto

## Objetivo
Implementar a plataforma de feedback da Fase 4 em Java e AWS serverless, com foco nos requisitos obrigatórios, baixo custo e facilidade de demonstração.

## Regras de trabalho
- Java 21 e AWS SAM/CloudFormation são a base do projeto.
- Não provisionar, alterar ou remover recursos AWS, nem executar deploy, sem autorização explícita da proprietária.
- Nunca colocar credenciais, tokens, e-mails pessoais ou dados reais de feedback no Git. Use parâmetros e variáveis de ambiente.
- Validar entradas na borda, usar menor privilégio IAM e manter logs sem conteúdo sensível sempre que possível.
- Manter handlers pequenos e responsabilidades separadas; regras de domínio ficam em código testável sem AWS.
- Ao alterar código, executar os testes disponíveis e registrar com precisão o que foi e não foi verificado.
- Documentar pré-requisitos, configuração, execução local, deploy, custos esperados e ações humanas pendentes.
- Não afirmar deploy, integração AWS ou requisito concluído sem evidência observável.

## Organização esperada
- `src/main/java`: domínio e handlers Lambda.
- `src/test/java`: testes unitários.
- `template.yaml`: infraestrutura AWS SAM.
- `README.md`: arquitetura, passos operacionais, segurança e demonstração.

## Convenções
- API de feedback: `POST /avaliacao`, JSON com `descricao` (string) e `nota` (inteiro entre 0 e 10).
- Feedback crítico: nota de 0 a 4 (configurável/documentada).
- Datas e horários armazenados em UTC ISO-8601.
- Identificadores e datas devem permitir consulta e agregação semanal com custo previsível.
