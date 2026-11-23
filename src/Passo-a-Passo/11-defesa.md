# 11 — Preparar a defesa

> ⚠️ **v2.1 — Capítulo da versão antiga:** este capítulo mostra o código da `referencia/` (nomes antigos, `com.acessoatempo`). **Os nomes dos serviços atualizaram: `garantia-acesso` (antes `incumprimento`) e `historico` (antes `indicadores`). O `historico` agora é append-only (não agrega contadores). Para a defesa, sabe explicar porquê: flexibilidade para novos indicadores sem reprocessar histórico.** O **código correto para o repositório do grupo** está em `referencia-grupo/`. Ver `referencia-grupo/LEIA-ME.md` para as diferenças.

Perguntas prováveis sobre a tua parte e a resposta curta. Para cada uma, **o código onde está a resposta**.

## 11.1 Perguntas sobre o negócio

| Pergunta | Resposta curta | Onde está |
|---|---|---|
| O que é o incumprimento? | A data-limite passou sem consulta (R4) ou a consulta foi marcada para depois dela (R5). | `prazo.ultrapassado` / `prazo.ultrapassado-previsto` |
| Quais são os 3 níveis? | (1) registar e reportar; (2) alertar o diretor, que tem 5 dias úteis; (3) propor a consulta noutra instituição (R6). | `CasosService` |
| Porque só 5 dias úteis e não corridos? | É a regra R6: não contam sábados, domingos e feriados (lista configurável). | `DiasUteisService`, `application.yml` |
| O que acontece se a outra instituição recusar? | Compensação: o episódio mantém-se na lista e o caso é sinalizado à direção clínica. | `registarResposta` |
| E se não responder? | Passado `respostaAte`, uma tarefa diária dá a proposta como recusada. | `expirarTransferencias`, `TransferenciasScheduler` |
| Para que serve o `historico`? | Dá visibilidade (tamanho da lista, cumprimento, triagem, projeção) sem atrasar as marcações. | capítulos 03 a 05 |

## 11.2 Perguntas sobre a arquitetura

| Pergunta | Resposta curta |
|---|---|
| **Porque `garantia-acesso` e `historico` são microserviços?** | `garantia-acesso` não existe no sistema atual e tem orquestração própria; `historico` tem consultas pesadas que não podem atrasar as marcações (secção 4.2). |
| **Porque comunicam por eventos e não por HTTP?** | Quem publica não precisa da resposta. Se o `historico` falhar, o `garantia-acesso` continua a funcionar (RNF02). As duas únicas chamadas síncronas do sistema são ao `utentes`. |
| **O que é consistência eventual?** | Durante instantes, dois serviços podem ver estados diferentes do mesmo episódio. É aceitável porque os prazos são de dias, não de milissegundos. |
| **O que é a orquestração e porque só na transferência?** | Um serviço coordena vários passos que podem ter de ser desfeitos (saga). O resto do sistema é coreografia: cada serviço reage a eventos. |
| **Porque uma base de dados por serviço?** | Autonomia: o serviço é dono dos seus dados e ninguém lê a BD dos outros; a única ligação é o `episodioId`. |
| **Porque os eventos não levam dados pessoais?** | RNF03: só o `utentes` tem nome e contactos. Os eventos levam só identificadores e datas. |

## 11.3 Perguntas sobre o RabbitMQ

| Pergunta | Resposta curta |
|---|---|
| Exchange, queue, binding, routing key? | A exchange `acesso.eventos` (Topic) recebe tudo; cada serviço liga a **sua fila** por **binding** às **routing keys** que lhe interessam (aula 05). |
| Porque uma fila por serviço e não por réplica? | Se houver 2 réplicas, ligam-se à **mesma fila** (*competing consumers*): cada mensagem é processada **uma só vez** por serviço. |
| O que é o ack? | Confirmação de que o evento foi processado. Só é enviada depois de o listener terminar sem erro. |
| O que é a Dead-Letter Queue? | Onde ficam as mensagens que falharam depois das tentativas: não bloqueiam a fila e ficam disponíveis para análise. |
| O que é a idempotência e como a garantiste? | O RabbitMQ entrega "pelo menos uma vez"; guardamos o `eventoId` processado e ignoramos repetições (`EventoProcessado`). |
| O que acontece se o RabbitMQ estiver em baixo ao publicar? | O evento perde-se (fica no log). **Limitação conhecida**; solução: *Transactional Outbox*. |
| Porque publicar só depois do commit? | Para não anunciar um facto que o *rollback* desfez. |

## 11.4 Perguntas sobre Spring Boot / JPA

| Pergunta | Resposta curta |
|---|---|
| Entidade vs DTO? | A entidade é a tabela; o DTO é o contrato da API. A entidade nunca sai do serviço (RNF10). |
| `@Transactional`? | Executa tudo numa só transação: ou grava tudo ou nada. |
| `ddl-auto: update`? | O Hibernate cria/atualiza as tabelas. Em produção usar-se-ia Flyway. |
| `EnumType.STRING` vs `ORDINAL`? | `STRING` grava o nome; `ORDINAL` o número da posição e partia ao reordenar o enum. |
| `@Version`? | Bloqueio otimista: evita que dois pedidos simultâneos se sobreponham. |
| 400 vs 404 vs 409 vs 202? | 400 pedido mal feito; 404 não existe; 409 estado atual não permite; 202 aceite mas continua por eventos. |
| Porque o `@ControllerAdvice`? | Trata os erros num só sítio e garante o formato comum (secção 4.4.1). |
| Porque o Mockito/`@MockitoBean` nos testes? | Para testar a lógica sem RabbitMQ e confirmar que o evento certo foi publicado. |

## 11.4b Perguntas sobre Kubernetes

As perguntas e respostas sobre Pod, Deployment, ConfigMap/Secret, sondas e `host.minikube.internal` estão no **final do [capítulo 12](12-kubernetes-minikube.md)** (secção 12.11).

## 11.5 Limitações conhecidas (diz-as tu antes que perguntem)

1. **Dual write:** gravar na BD e publicar no RabbitMQ não é atómico (falta o *Outbox*).
2. **`ddl-auto: update`** em vez de migrações versionadas.
3. **Taxa de cumprimento simplificada** (Fase 1).
4. **A tarefa agendada** não tem *lock*: só é segura com 1 réplica.
5. **`max-attempts`** com Boot 4 conta repetições (1 + 3 = 4 execuções) — documentado no capítulo 04.
6. **Autenticação fora do âmbito** (`X-Utilizador` não é validado neste serviço; a Gateway trata do 401/403).
7. **Eureka e Gateway** ainda não integrados; o Kubernetes foi testado só no Minikube local (não na VM da entrega).

## 11.6 Cola rápida: onde está cada regra

| Regra da proposta | Ficheiro |
|---|---|
| R4/R5 (incumprimento previsto/incumprido) | `CasosService.tratarEvento` → `abrirOuEscalar` |
| R6 nível 1 | `abrirOuEscalar` → `caso.aberto` |
| R6 nível 2 (5 dias úteis) | `CasosService.alertarDiretor`, `DiasUteisService` |
| R6 nível 3 (outra instituição) | `CasosService.proporTransferencia` |
| Compensação / timeout | `registarResposta`, `expirarTransferencias` |
| RNF02 (falha isolada) | Comunicação por eventos; nenhum serviço chama o outro |
| RNF07 (relógio) | `RelogioService`, evento `relogio.avancado` |
| RNF09 (Actuator) | `application.yml` → `management` |
| RNF10 (DTO) | pacote `dtos` + `ModelMapperConfig` |
| RNF11 (mensagens de erro) | `ExceptionHandlers` + exceções |
| RNF12 (paginação) | `IncumprimentosController.listar` |

## 11.7 As 12 perguntas de negócio, com resposta

São as mesmas 12 perguntas de autoavaliação do final de [`contexto.md`](contexto.md) (secção 12), aqui com a **resposta curta para dizer em voz alta** na defesa. As respostas completas, com exemplos, estão lá. **Tenta responder sem olhar.**

| # | Pergunta | Resposta para a defesa |
|---|---|---|
| 1 | Quem **calcula** a data-limite: tu ou o `gestao-acesso`? E quem **deteta** que passou? | **O `gestao-acesso`, nas duas.** Guarda as regras do TMRG, calcula a data-limite (pedido + prazo da prioridade, R2) e verifica os prazos todos os dias. O `garantia-acesso` não calcula datas: **reage** aos eventos `tmrg.*`. Assim, uma nova portaria muda **um só** serviço. |
| 2 | Diferença entre **risco**, **incumprimento previsto** e **incumprimento**? | **Risco:** 75% do prazo consumido, ainda sem falha (`prazo.em-risco`). **Previsto:** consulta marcada para **depois** da data-limite (R5; `prazo.ultrapassado-previsto`). **Incumprimento:** a data-limite passou sem consulta (R4; `prazo.ultrapassado`). Risco abre um caso `EM_RISCO` (nível 0, sem publicar); os outros dois abrem `REGISTADO` (nível 1) e publicam `caso.aberto`. |
| 3 | Porque é que o nível 3 só acontece **depois** dos 5 dias úteis do diretor? | A R6 é uma **escalada por escalões**: primeiro tenta-se resolver **dentro** do hospital (o diretor tem 5 dias úteis) e só se falhar se procura **fora**. Propor já outra instituição desrespeitava esse prazo e seria mais disruptivo para o doente. No código, `proporTransferencia` dá **409** se o prazo do diretor ainda não passou. |
| 4 | Porque "5 dias úteis" não é "7 dias"? Exemplo com feriado. | Não contam sábados, domingos nem **feriados nacionais** (lista configurável). Exemplo: segunda 16/11/2026 + 5 dias úteis = segunda **23/11**; segunda 21/12/2026 + 5 = terça **29/12**, porque 25/12 é feriado e há um fim de semana. O dia do alerta não conta. |
| 5 | O que é a **compensação** e o que acontece se a outra instituição recusar? | É **desfazer/corrigir** o efeito de um passo que falhou num processo com vários passos (*saga*). Se recusa: o **episódio mantém-se na lista**, publica-se `transferencia.recusada` e o caso fica `SINALIZADO_DIRECAO_CLINICA` (decide uma pessoa). Se aceita: `transferencia.aceite` + `caso.resolvido`. |
| 6 | O que acontece se a instituição **nunca** responder? | Cada proposta tem `respostaAte` (5 dias, configurável). Uma **tarefa agendada diária** dá como **recusadas** as propostas sem resposta: o **timeout** da saga, para nunca ficar pendurada. É o que a proposta diz: *"se recusar ou não responder no prazo, o episódio continua em espera e o caso é sinalizado"*. |
| 7 | Se o `historico` estiver em baixo, o `garantia-acesso` continua a funcionar? | **Sim.** Comunicam por **eventos** (RabbitMQ): o `garantia-acesso` publica e segue em frente; o que o `historico` não recebeu **fica na fila dele** até ele voltar. É o **RNF02**. Com HTTP síncrono, a falha propagava-se. |
| 8 | Porque é que o `historico` guarda **contadores** e não os episódios? | (1) **RNF03:** só dados agregados, que não identificam ninguém; (2) **desempenho:** somar contadores é muito mais rápido e não atrasa as marcações; (3) **autonomia:** o episódio completo é da `lista-espera`; copiá-lo duplicava dados e podia divergir. É um **modelo de leitura**. |
| 9 | Porque é que os eventos **não** levam o nome do doente? | Os dados pessoais existem **só no `utentes`** (RNF03). Se andassem nos eventos, ficariam espalhados por filas, logs e BD de vários serviços (mais risco, pior proteção de dados). Os eventos levam só identificadores e datas; as `notificacoes` pedem o contacto ao `utentes` na hora. |
| 10 | O que é a **idempotência** e que problema evita no teu código? | Processar o mesmo evento duas vezes dá o mesmo resultado que uma. O RabbitMQ entrega **"pelo menos uma vez"**, logo pode repetir. Sem proteção: dois casos para o mesmo episódio e contadores a subir a dobrar. O código guarda o `eventoId` (`EventoProcessado`) **na mesma transação** e ignora os repetidos. |
| 11 | Porque é que a **triagem lenta** faz subir os incumprimentos (R1)? | O prazo conta **desde o pedido, não desde a triagem** (R1): cada dia que o triador demora **gasta prazo**. Ex.: 30 dias, pedido a 01/03, triagem a 15/03 → só sobram 15 dias. Em casos extremos (R7) a nova data-limite já passou e entra logo em incumprimento. Por isso o `historico` mede o tempo médio de triagem (RF10). |
| 12 | Porque é a transferência a **única** orquestração do sistema? | O resto é **coreografia** (cada serviço reage a eventos). A transferência tem **vários passos**, envolve um **sistema externo** que pode recusar ou não responder e **pode ter de ser desfeita** (compensação, timeout). Alguém tem de ter a visão do conjunto: o `garantia-acesso`, o **orquestrador**. Com coreografia pura, a compensação ficaria espalhada por vários serviços. |

> 💡 **Treino:** pede a um colega para te fazer as perguntas **por ordem aleatória** e responde sem olhar. Se te enganares, volta à secção correspondente de `contexto.md`. Depois desenha no quadro o ciclo de vida do caso (`contexto.md`, secção 6.3) e diz que evento cada seta publica.
