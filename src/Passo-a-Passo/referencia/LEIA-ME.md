# `referencia/`: código e análises de apoio

| Ficheiro / pasta | O que é |
|---|---|
| [`acesso-a-tempo/`](acesso-a-tempo/) | **Versão antiga** completa dos 2 serviços (Spring Boot 4.1, `com.acessoatempo`), com Docker, Kubernetes e o teste de ponta a ponta. 52 testes; testada com PostgreSQL, RabbitMQ e Minikube reais. **Só a uso para o `Dockerfile`, o `k8s/` e o capítulo 10** |
| [`../referencia-grupo/`](../referencia-grupo/LEIA-ME.md) | **O código que escrevo**: os mesmos 2 serviços no estilo do repositório do grupo (Boot 3.5, `pt.estg.mtsds.acesso`). 53 testes com o `verify` do grupo |
| [`grupo-explicado.md`](grupo-explicado.md) | O que faz o resto do repositório do grupo (ferramentas e CI, Docker Compose, Eureka, Gateway, `servico-modelo`, `referenciacao`) e quais módulos existem |
| [`tmrg-explicado.md`](tmrg-explicado.md) | O que faz o código do `tmrg` do Avelino |
| [`dependencia.md`](dependencia.md) | O `tmrg` bate certo comigo? Pontos para o PR e perguntas ao Avelino |

**Onde está o resto:** o que é meu e o contrato de eventos em [`../minha-parte.md`](../minha-parte.md); as tarefas, branches e commits em [`../plano-de-trabalho.md`](../plano-de-trabalho.md).
