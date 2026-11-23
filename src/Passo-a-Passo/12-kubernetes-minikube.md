# 12 — Kubernetes e Minikube: pôr o `incumprimento` e o `indicadores` no cluster

**Objetivo:** correr os teus dois serviços no **Minikube**, com **Deployment**, **ConfigMap**, **Secret** e **sondas de saúde**, ligados ao PostgreSQL e ao RabbitMQ que correm **fora** do cluster.
**Baseado em:** aula 06 (*Docker*, *Kubernetes*: Pod, Service, ConfigMap/Secret, Deployment, `kubectl`, Minikube, demonstração `bookstore-inventory` 1/22 a 22/22) e na secção 5.1 da proposta.

> ✅ **Verificado:** todos os ficheiros e comandos deste capítulo foram executados num cluster Minikube real (os 2 serviços ficaram `Ready`, ligaram-se ao PostgreSQL e ao RabbitMQ, consumiram eventos, recuperaram de um Pod apagado e escalaram para 2 réplicas).
> ⚠️ **Não verificado:** a **VM do Vagrant** da entrega e o **Eureka/Gateway** dos colegas (ver 12.9).

## 12.1 Os conceitos da aula 06 e o que usamos

| Conceito (aula 06) | O que é | Usamos? |
|---|---|---|
| **Container / imagem Docker** | O serviço empacotado com as dependências | ✅ (capítulo 10) |
| **Pod** | A unidade mínima: 1 contentor a correr. **Temporário** (pode ser recriado) | ✅ (criado pelo Deployment) |
| **Deployment** | O "blueprint" do estado pretendido: imagem, **réplicas**, configuração, sondas | ✅ **um por serviço** |
| **ReplicaSet** | Garante o nº de réplicas; **gerido pelo Deployment** (não o escreves) | ✅ (automático) |
| **ConfigMap** | Configuração **não sensível**, injetada como variáveis de ambiente | ✅ |
| **Secret** | Dados **sensíveis** (passwords), guardados em base64 | ✅ |
| **Service** | IP/nome fixo + balanceamento para um conjunto de Pods | ⛔ **Não para os teus serviços** (ver abaixo) |
| **Ingress** | Entrada de tráfego externo no cluster | ⛔ (a Gateway é o ponto de entrada) |
| **Volume / StatefulSet** | Dados persistentes / aplicações com estado | ⛔ (PostgreSQL e RabbitMQ ficam **fora**: *"para não termos de gerir estado dentro do Kubernetes"*, secção 5.1) |

### O desenho da proposta (secção 5.1) aplicado a ti

```text
        ┌────────────────────── Minikube (cluster) ──────────────────────┐
        │   Gateway (Service NodePort)  ◄── único ponto acessível de fora │
        │   Eureka  (Service ClusterIP)                                   │
        │                                                                 │
        │   incumprimento-deployment ─► Pod (1 réplica)   ┐               │
        │   indicadores-deployment   ─► Pod (1 réplica)   ├─ SEM Service  │
        │   (outros serviços...)                          ┘  (descobrem-se pelo Eureka)
        └───────────────┬───────────────────────────────┬─────────────────┘
                        │ host.minikube.internal        │
                 PostgreSQL :5432                  RabbitMQ :5672        <- fora do cluster (Docker Compose)
```

- **Cada microserviço tem um Deployment**, a configuração num **ConfigMap** e as passwords num **Secret**.
- **Só o Eureka (ClusterIP) e a Gateway (NodePort) têm `Service`** (*"para haver um só mecanismo de descoberta"*): os serviços encontram-se pelo **Eureka**, não pelos Services do Kubernetes. Por isso **não** crias Services para os teus dois serviços.
- **Réplicas:** só o `agendamento` e a `referenciacao` correm com 2. **Os teus ficam com 1** (o `incumprimento` tem uma tarefa agendada que não é segura com várias réplicas; capítulo 08).
- **Saúde:** o Kubernetes usa o **Actuator** (`/actuator/health`) para **recriar** os serviços que falharem (RNF06).

## 12.2 Pré-requisitos

| | Comando de verificação |
|---|---|
| Docker Desktop **aberto** | `docker info` |
| Minikube | `minikube version` |
| kubectl | `kubectl version --client` |
| Os JAR gerados e as imagens (capítulo 10) | `mvn -pl incumprimento,indicadores -am package -DskipTests` |
| PostgreSQL e RabbitMQ a correr | `docker compose ps` na pasta `infra` (capítulo 01) |

Arranca o Minikube (aula 06, *Setup 2/2*):

```bash
minikube start                       # 1.ª vez: demora alguns minutos
minikube status                      # host, kubelet e apiserver "Running"
kubectl get nodes                    # 1 nó (o Minikube tem um só)
```

> ℹ️ **Tens de ter cuidado com os perfis.** O Minikube pode ter vários clusters ("perfis") e o `kubectl` aponta para **um** de cada vez. Confirma com `minikube profile list` e `kubectl config current-context` **antes** de aplicares nada, para não mexeres no cluster errado (de outra disciplina, por exemplo).
>
> Dica da aula (slide *TIPS*): se o arranque der *"Unable to resolve the current Docker CLI context"*, corre `docker context use default`; se der erro de *container runtime*, `minikube status` e repete `minikube start`.

## 12.3 Passo 1 — Imagens Docker dentro do Minikube

O cluster do Minikube tem o **seu próprio** Docker interno: não vê as imagens do teu Docker local. Na aula, a solução é publicar no **Docker Hub** e deixar o Minikube descarregar (secção *Demonstração 5/22 a 9/22*). Para a entrega (VM, possivelmente sem internet) é mais simples **carregar a imagem local diretamente**:

```bash
# na raiz do projeto, depois do mvn package
docker build -t incumprimento:1.0 incumprimento
docker build -t indicadores:1.0   indicadores

minikube image load incumprimento:1.0
minikube image load indicadores:1.0
minikube image ls | grep -E 'incumprimento|indicadores'      # confirmar
```

| Opção | Como | Quando |
|---|---|---|
| **A) `minikube image load`** (usada aqui) | `docker build` + `minikube image load` | Sem registry; funciona offline (VM da entrega) |
| **B) Docker Hub** (aula 06) | `docker login` → `docker tag incumprimento:1.0 UTILIZADOR/incumprimento:1.0` → `docker push UTILIZADOR/incumprimento:1.0` e usar essa imagem no `image:` do Deployment | Se os colegas usarem um registry comum |

> ⚠️ **A tag importa.** Com `image: nome:latest` o Kubernetes tenta **sempre** descarregar a imagem e falha (`ErrImagePull`) se só existir localmente. Usa uma **versão explícita** (`1.0`) e `imagePullPolicy: IfNotPresent`, como nos manifests abaixo. Ao mudares o código, **sobe a versão** (`1.1`) para o Kubernetes perceber que mudou.

## 12.4 Passo 2 — Os ficheiros YAML (manifests)

Os 3 passos da aula (*Modo de utilização genérica*): containerização → **ficheiros YAML** → `kubectl apply`. Cria a pasta **`k8s/`** na raiz do projeto, com **3 ficheiros por serviço** (o `incumprimento` aqui; o `indicadores` é igual, mudando o nome, a porta `8086` e a BD).

### 12.4.1 ConfigMap — a configuração não sensível

📄 **`k8s/incumprimento-configmap.yaml`**

```yaml
# ConfigMap: configuração NÃO sensível do serviço incumprimento (aula 06: ConfigMap).
# Cada chave vira uma VARIÁVEL DE AMBIENTE no contentor; o application.yml lê-as com ${NOME:valor}.
apiVersion: v1
kind: ConfigMap
metadata:
  name: incumprimento-config
  labels:
    app: incumprimento
data:
  # PostgreSQL e RabbitMQ correm FORA do cluster (Docker Compose no mesmo computador/VM).
  # "host.minikube.internal" é o nome que o Minikube dá à máquina que o aloja.
  DB_HOST: "host.minikube.internal"
  DB_PORT: "5432"
  DB_NAME: "incumprimento_db"
  RABBIT_HOST: "host.minikube.internal"
  RABBIT_PORT: "5672"
  # Eureka Server do grupo (Service ClusterIP). CONFIRMAR o nome do Service com quem o fizer.
  EUREKA_URL: "http://eureka:8761/eureka/"
```

- Cada chave (`DB_HOST`, `DB_NAME`...) torna-se uma **variável de ambiente** no contentor. O `application.yml` já as lê com `${DB_HOST:localhost}` (capítulo 02): **o mesmo JAR funciona no computador e no cluster, sem recompilar**.
- **`host.minikube.internal`** é o nome que o Minikube dá à **máquina que o aloja**. Dentro de um Pod, **`localhost` é o próprio Pod**, não o teu computador! É por isto que o PostgreSQL e o RabbitMQ (fora do cluster) se acedem por este nome.
- `EUREKA_URL` aponta para o Service `eureka` (🤝 confirmar o nome com quem fizer o Eureka).

### 12.4.2 Secret — as passwords

📄 **`k8s/incumprimento-secret.yaml`**

```yaml
# Secret: dados SENSÍVEIS do serviço incumprimento (aula 06: Secret).
# stringData aceita texto simples; o Kubernetes guarda-o em base64 (NÃO é encriptação!).
# Valores de DESENVOLVIMENTO: numa entrega real não se faz commit de passwords reais.
apiVersion: v1
kind: Secret
metadata:
  name: incumprimento-secret
  labels:
    app: incumprimento
type: Opaque
stringData:
  DB_USER: "incumprimento_user"
  DB_PASSWORD: "incumprimento_pass"
  RABBIT_USER: "user"
  RABBIT_PASSWORD: "123"
```

- **`stringData`** deixa escrever em texto simples; o Kubernetes guarda em **base64**. A aula avisa: *"não se trata de encriptação"*. Quem tiver acesso ao cluster lê-as.
- São valores **de desenvolvimento**. Numa entrega real, não se faz commit de passwords reais. Alternativa sem ficheiro: `kubectl create secret generic incumprimento-secret --from-literal=DB_PASSWORD=...`.

### 12.4.3 Deployment — o estado pretendido

📄 **`k8s/incumprimento-deployment.yaml`**

```yaml
# Deployment do serviço incumprimento (aula 06: Deployment). Descreve o ESTADO PRETENDIDO:
# "quero 1 réplica desta imagem, com esta configuração, e verifica a saúde dela".
apiVersion: apps/v1
kind: Deployment
metadata:
  name: incumprimento-deployment
  labels:
    app: incumprimento
spec:
  replicas: 1                       # nº de Pods (réplicas). Mudar com: kubectl scale deployment ... --replicas=2
  selector:
    matchLabels:
      app: incumprimento                       # quais os Pods geridos por este Deployment
  template:                         # molde dos Pods
    metadata:
      labels:
        app: incumprimento
    spec:
      containers:
        - name: incumprimento
          image: incumprimento:1.0           # imagem local carregada com "minikube image load"
          imagePullPolicy: IfNotPresent   # não tentar descarregar de um registry se já existir localmente
          ports:
            - containerPort: 8085
          envFrom:                  # injeta TODAS as chaves como variáveis de ambiente
            - configMapRef:
                name: incumprimento-config
            - secretRef:
                name: incumprimento-secret
          env:
            - name: JAVA_TOOL_OPTIONS   # a JVM usa no máximo 70% da memória do contentor
              value: "-XX:MaxRAMPercentage=70"
          resources:
            requests:               # o mínimo que o Pod precisa (usado para o escalonamento)
              cpu: "100m"
              memory: "384Mi"
            limits:                 # o máximo permitido; acima da memória o Pod é terminado
              memory: "768Mi"
          # startupProbe: dá tempo à aplicação para arrancar (até 30 x 5 s = 150 s) antes das outras sondas.
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8085
            periodSeconds: 5
            failureThreshold: 30
          # livenessProbe: "o processo está vivo?" Se falhar, o Kubernetes REINICIA o Pod.
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8085
            periodSeconds: 10
            failureThreshold: 3
          # readinessProbe: "está pronto a trabalhar?" Se falhar, o Pod fica fora de serviço (sem ser reiniciado).
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8085
            periodSeconds: 10
            failureThreshold: 3
```

| Campo | O que faz | Para a defesa |
|---|---|---|
| `replicas: 1` | Nº de Pods | `kubectl scale` altera sem editar o ficheiro |
| `selector.matchLabels` + `template.metadata.labels` | Ligam o Deployment aos seus Pods pela *label* `app` | Têm de **coincidir** |
| `image` + `imagePullPolicy: IfNotPresent` | Que imagem usar e não a descarregar se já existir | Ver a armadilha da tag `latest` |
| `envFrom` (`configMapRef`, `secretRef`) | Injeta **todas** as chaves como variáveis de ambiente | Config separada do código (aula: portabilidade entre ambientes) |
| `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70` | A JVM usa até 70% da memória do contentor para o *heap* | Por omissão só usaria 25% (desperdício); acima de ~80% arrisca-se a passar o limite e o Pod ser morto (`OOMKilled`) |
| `resources.requests/limits` | O mínimo que o Pod precisa e o máximo permitido | Ultrapassar o limite de memória → o Pod é terminado (`OOMKilled`) |
| **`startupProbe`** | Espera pelo arranque (até 30 × 5 s) antes das outras sondas | O arranque demora ~30 s em máquinas lentas; sem isto o `liveness` mataria o Pod a meio |
| **`livenessProbe`** | "Está vivo?" Se falhar **3 vezes**, o Kubernetes **reinicia** o Pod | É o "recria os serviços que falharam" da proposta |
| **`readinessProbe`** | "Está pronto?" Se falhar, **deixa de receber tráfego** (sem reiniciar) | Distinção importante |

**Porque `/actuator/health/liveness` e `/readiness` e não só `/actuator/health`?** São **parte** do `/actuator/health` (o Spring Boot cria estes dois grupos). O *liveness* só diz se a aplicação está viva, **sem** depender da base de dados: se a BD cair, **não** queremos que o Kubernetes reinicie todos os Pods em ciclo (não resolve nada e piora). Isto cumpre a proposta (usar o Actuator) com mais cuidado.

## 12.5 Passo 3 — Aplicar e acompanhar

```bash
kubectl apply -f k8s/          # aplica TODOS os ficheiros da pasta (aula 06: kubectl apply -f)
```

Resultado esperado:

```text
configmap/incumprimento-config created
deployment.apps/incumprimento-deployment created
secret/incumprimento-secret created
... (o mesmo para o indicadores)
```

Acompanha a criação (aula 06, *Demonstração 15/22 e 16/22*):

```bash
kubectl get deployments                          # READY 0/1 -> 1/1
kubectl get pods                                 # STATUS: ContainerCreating -> Running
kubectl wait --for=condition=Ready pod -l app=incumprimento --timeout=150s
kubectl describe pod -l app=incumprimento        # eventos, sondas, imagem, variáveis
kubectl logs deploy/incumprimento-deployment     # o log do Spring Boot (banner, "Started ...")
```

O que **vi** no teste: com 2 CPUs, `Started Incumprimento in ~33 seconds` e os Pods ficaram `1/1 Running`, `RESTARTS 0`. Durante o arranque aparece, no `describe`, **`Startup probe failed ... connection refused`**: é **normal** (a aplicação ainda não tinha arrancado; o Kubernetes tenta de novo).

> **E o Eureka?** No log aparece `UnknownHostException: eureka` se o Eureka ainda não existir no cluster. **É um aviso, não impede o serviço de ficar `Ready`** (testado): quando o Eureka existir, o serviço regista-se sozinho.

## 12.6 Passo 4 — Testar

Como **não há `Service`** (proposta), usa o **`port-forward`** da aula 06 (*kubectl port-forward*): liga uma porta do teu computador ao Pod.

```bash
kubectl port-forward deploy/incumprimento-deployment 18085:8085 &
kubectl port-forward deploy/indicadores-deployment   18086:8086 &

curl localhost:18085/incumprimento/landing
curl localhost:18086/indicadores/landing
curl localhost:18085/actuator/health/readiness      # {"status":"UP"}
```

Publica um evento no RabbitMQ (interface `http://localhost:15672`, exchange `acesso.eventos`, `content_type` = `application/json`) como no capítulo 09 e consulta:

```bash
curl 'localhost:18085/incumprimentos?esp=CARDIOLOGIA'      # o caso aparece
curl 'localhost:18086/indicadores/lista?esp=CARDIOLOGIA'   # os contadores mudam
```

Isto prova a cadeia completa: **Pod → host.minikube.internal → RabbitMQ/PostgreSQL**. Para parar os `port-forward`: `pkill -f port-forward`.

> 💡 Para testes pontuais podes expor um Pod sem criar `Service`. Se o grupo quiser um `Service` temporário, a aula mostra o `type: NodePort`/`LoadBalancer` e o `minikube service <nome>` (*Demonstração 17/22 a 20/22*); mas **não o deixes no repositório** (a proposta só prevê Services no Eureka e na Gateway).

## 12.7 Experiências para a defesa (todas testadas)

### 1) Auto-recuperação: apaga um Pod

```bash
kubectl delete pod -l app=indicadores        # "mata" o Pod
kubectl get pods -l app=indicadores          # o ReplicaSet cria logo outro (ContainerCreating)
```

O Pod antigo fica `Terminating` e **um novo nasce em segundos**: o Deployment mantém o estado pretendido (1 réplica). Em ~16 s o novo Pod estava `1/1 Running`. É o **RNF06** em ação.

### 2) Escalar: várias réplicas e *competing consumers*

```bash
kubectl scale deployment indicadores-deployment --replicas=2
kubectl rollout status deployment/indicadores-deployment
kubectl get pods -l app=indicadores          # 2 Pods
```

Na interface do RabbitMQ, a fila `indicadores.eventos` passa a ter **2 consumers**: as duas réplicas ligam-se à **mesma fila** e cada mensagem é processada **por uma só** (*competing consumers*). Por isso a fila é **por serviço e não por réplica** (capítulo 04), e a **idempotência** (`EventoProcessado`) protege contra repetições. Volta atrás com `--replicas=1`.

> ⚠️ **No `incumprimento`, não escales:** a tarefa `@Scheduled` correria em todas as réplicas (capítulo 08).

### 3) Atualização sem paragem (*rolling update*)

```bash
# depois de construir e carregar a imagem 1.1 (docker build -t indicadores:1.1 ... + minikube image load)
kubectl set image deployment/indicadores-deployment indicadores=indicadores:1.1
kubectl rollout status deployment/indicadores-deployment
kubectl rollout history deployment/indicadores-deployment     # revisões
kubectl rollout undo deployment/indicadores-deployment        # voltar atrás (rollback)
```

Testei `kubectl rollout restart deployment/indicadores-deployment`: o Kubernetes **cria o Pod novo e só termina o antigo quando o novo está `Ready`** (*zero-downtime*, aula 06). O histórico passou a ter 2 revisões.

## 12.8 Comandos úteis (da aula 06)

| Comando | Para quê |
|---|---|
| `minikube start` / `stop` / `status` / `delete` | Ligar, parar, ver estado, apagar o cluster |
| `minikube profile list` | Ver os clusters (perfis) existentes |
| `minikube ip` | IP do Minikube |
| `minikube dashboard` | Interface gráfica |
| `kubectl get all` | Tudo o que está a correr |
| `kubectl get pods -o wide` / `--show-labels` | Pods com mais detalhe |
| `kubectl get pods -l app=incumprimento` | Filtrar por *label* |
| `kubectl describe pod <nome>` | Eventos, sondas, imagem, variáveis |
| `kubectl logs <pod>` / `deploy/<nome>` | Ver o log |
| `kubectl exec -it <pod> -- sh` | Entrar no contentor |
| `kubectl scale deployment <nome> --replicas=N` | Mudar o nº de réplicas |
| `kubectl rollout restart deployment/<nome>` | Reiniciar os Pods |
| `kubectl get events` | Eventos recentes do cluster |
| `kubectl delete -f k8s/` | Apagar o que foi aplicado |
| `kubectl get configmaps` / `secrets` | Ver a configuração |

## 12.9 O que depende dos colegas / da entrega

| Ponto | Estado | Nota |
|---|---|---|
| **Eureka** no cluster | 🤝 Dos colegas | Combinar o **nome do Service** (`eureka`?) e a porta; ajustar `EUREKA_URL` no ConfigMap |
| **Gateway** (NodePort) | 🤝 Dos colegas | As rotas dos teus serviços estão no capítulo 10 |
| **VM (Vagrant + VirtualBox, `.ova`)** | ⚠️ Não testada | Dentro da VM os comandos são os mesmos; confirmar que `host.minikube.internal` chega ao PostgreSQL/RabbitMQ (Docker Compose na VM) |
| **Nome do namespace** | A combinar | Usei o `default` (como a aula). Se o grupo usar um namespace próprio, acrescenta `namespace:` em cada ficheiro (ou `kubectl apply -n ...`) |
| **Imagens** | A combinar | `minikube image load` (offline) ou Docker Hub comum |
| **Passwords** | A combinar | Os Secrets têm valores de desenvolvimento |

## 12.10 Armadilhas encontradas (e como evitar)

| Problema | Causa | Solução |
|---|---|---|
| `Connection refused` à BD/RabbitMQ | Dentro do Pod, `localhost` é o próprio Pod | Usar `host.minikube.internal` (ConfigMap) |
| `ErrImagePull` / `ImagePullBackOff` | Imagem só existe no Docker local, ou tag `latest` | `minikube image load` + tag explícita + `IfNotPresent` |
| O Pod reinicia em ciclo no arranque | `livenessProbe` dispara antes de a app arrancar | `startupProbe` (já incluída) |
| Pod `OOMKilled` | Limite de memória baixo ou `MaxRAMPercentage` demasiado alto | `limits.memory` ≥ 768Mi e `MaxRAMPercentage` ≈ 70 |
| Aplicaste no cluster errado | O `kubectl` aponta para outro perfil | `kubectl config current-context` antes de aplicar |
| Mudaste o código e nada mudou | A imagem tem a mesma tag | Subir a tag (`1.1`) e atualizar o `image:` |
| `kubectl top` dá "Metrics API not available" | O Minikube não traz o *metrics-server* | `minikube addons enable metrics-server` (opcional; não testado) |
| O `init-db.sql` não criou as BD | O volume do PostgreSQL já existia | `docker compose down -v` (apaga os dados desse volume) |

## 12.11 Perguntas para a defesa, com resposta

Responde **primeiro sem olhar**; depois confirma. As respostas marcadas com ✅ foram **confirmadas no teste** em Minikube.

### 1. O que é um Pod? E um Deployment? E um ReplicaSet?

- **Pod:** a unidade mais pequena do Kubernetes; normalmente **um contentor** a correr. É **temporário** por natureza: pode ser apagado e recriado a qualquer momento e, de cada vez, **muda de IP**.
- **Deployment:** o *blueprint* do **estado pretendido** de um serviço: que imagem, **quantas réplicas**, que configuração e que sondas de saúde. Tu não crias Pods à mão; descreves o Deployment e o Kubernetes mantém esse estado.
- **ReplicaSet:** o recurso que **garante o número de réplicas** (se um Pod morre, cria outro). É **gerido pelo Deployment**: nunca o escreves diretamente. Em cada atualização o Deployment cria um ReplicaSet novo e vai trocando os Pods.

### 2. ConfigMap vs Secret? Os Secrets são seguros?

- **ConfigMap:** configuração **não sensível** (`DB_HOST`, `DB_NAME`, `RABBIT_HOST`...).
- **Secret:** dados **sensíveis** (`DB_PASSWORD`, `RABBIT_PASSWORD`).
- Os dois chegam ao contentor como **variáveis de ambiente** (`envFrom`), e o `application.yml` lê-as com `${DB_HOST:localhost}`. O mesmo JAR funciona no computador e no cluster **sem recompilar**.
- **Os Secrets não são encriptados:** são guardados em **base64** (que se descodifica trivialmente). Protegem pouco; quem tiver acesso ao cluster lê-os. A aula 06 diz-o expressamente. Por isso não se faz commit de passwords reais.

### 3. Porque não há `Service` nos teus serviços?

A proposta (secção 5.1) só prevê Services no **Eureka** (ClusterIP) e na **Gateway** (NodePort), *"para haver um só mecanismo de descoberta"*: os serviços encontram-se **pelo Eureka** e a Gateway distribui os pedidos pelas instâncias (Spring Cloud LoadBalancer). Se também usássemos os Services do Kubernetes, haveria **dois** mecanismos de descoberta em paralelo.
Para testar sem Service usa-se `kubectl port-forward`.

### 4. Porque é que o PostgreSQL e o RabbitMQ estão **fora** do cluster?

Para **não gerir estado dentro do Kubernetes**. Bases de dados e brokers precisam de **armazenamento persistente** (Volumes, StatefulSet), de ordem de arranque e de cuidados de cópia de segurança, e a aula 06 diz que isso é *"mais complicado de gerir"*, razão pela qual muitos programadores **o deixam fora**. Os Pods são **descartáveis** (*stateless*); os dados não. Por isso correm em Docker Compose, com volumes (secção 5.1 da proposta).

### 5. Como é que um Pod chega ao PostgreSQL que está fora do cluster?

Pelo nome **`host.minikube.internal`**, que o Minikube resolve para **a máquina que o aloja**. ✅ Foi o que usei no ConfigMap e funcionou.
**`localhost` não serve:** dentro de um Pod, `localhost` é **o próprio Pod**, não o teu computador. É o erro mais comum (`Connection refused`).

### 6. Liveness vs readiness vs startup? O que acontece se a base de dados cair?

| Sonda | Pergunta | Se falhar |
|---|---|---|
| **startupProbe** | "Já acabou de arrancar?" | O Kubernetes espera (até 30 × 5 s) e **só depois** ativa as outras sondas |
| **livenessProbe** | "Está vivo?" | O Pod é **reiniciado** |
| **readinessProbe** | "Está pronto a receber trabalho?" | O Pod fica **sem tráfego**, **sem ser reiniciado** |

A `startupProbe` existe porque o arranque demorou cerca de **33 s** ✅: sem ela, a `livenessProbe` podia matar o Pod **a meio do arranque**, em ciclo.
Usam-se `/actuator/health/liveness` e `/actuator/health/readiness` (partes do `/actuator/health`). O *liveness* **não depende da base de dados**: se a BD cair, **não queremos** que o Kubernetes reinicie todos os Pods (não resolve nada e piora). A aplicação continua viva e retoma quando a BD voltar.

### 7. O que acontece se apagares um Pod?

O **ReplicaSet** vê que há menos Pods do que o pretendido e **cria outro imediatamente**. ✅ No teste, o Pod antigo ficou `Terminating` e o novo estava `1/1 Running` em cerca de **16 segundos**. É a auto-recuperação (RNF06): o Kubernetes mantém o **estado pretendido** do Deployment, não o Pod concreto.

### 8. O que acontece quando escalas o `indicadores` para 2 réplicas?

✅ Aparecem **2 Pods** e a fila `indicadores.eventos` passa a ter **2 consumers**. As duas réplicas ligam-se à **mesma fila** (*competing consumers*): cada mensagem é processada **por uma só**, o que reparte a carga. É por isso que a fila é **por serviço e não por réplica**, e a **idempotência** (`EventoProcessado`) protege contra repetições.

### 9. Porque é que o `incumprimento` fica com **1 réplica**?

Tem uma **tarefa agendada** (`@Scheduled`, que expira as transferências sem resposta) **sem lock**. Com várias réplicas, a tarefa correria **em todas**, ao mesmo tempo, com o risco de tratar a mesma proposta duas vezes. A proposta só dá 2 réplicas ao `agendamento` e à `referenciacao` (os mais carregados). Se fosse preciso escalar, seria necessário um lock (por exemplo ShedLock).

### 10. O que é um *rolling update* e como se volta atrás?

Ao mudar a imagem, o Kubernetes **cria os Pods novos e só termina os antigos quando os novos estão `Ready`**, por isso **não há interrupção** (*zero-downtime*, aula 06). ✅ Testei `kubectl rollout restart`: o Pod novo ficou `Running` com o antigo ainda a terminar, e o histórico passou a 2 revisões.
Para voltar atrás: `kubectl rollout undo deployment/<nome>`. O Kubernetes guarda o histórico de revisões.

### 11. Porque é que a imagem tem a tag `1.0` e `imagePullPolicy: IfNotPresent`?

A imagem foi carregada **localmente** no Minikube (`minikube image load`) e **não existe num registry**. Com a tag `latest` o Kubernetes tenta **sempre descarregar** e falha (`ErrImagePull`); com uma **versão explícita** e `IfNotPresent`, usa a imagem que já está no nó. Ao mudares o código, **sobe a versão** (`1.1`) para o Kubernetes perceber que mudou.

### 12. O que fazem `requests`, `limits` e `MaxRAMPercentage`?

- **`requests`:** o mínimo que o Pod precisa; o Kubernetes usa-o para **decidir em que nó** o colocar.
- **`limits`:** o máximo permitido; se a **memória** passar o limite, o Pod é terminado (`OOMKilled`).
- **`-XX:MaxRAMPercentage=70`:** diz à JVM para usar até 70% da memória do contentor para o *heap*. Por omissão usaria só **25%** (desperdício); perto de 100% arriscava passar o limite com a memória fora do heap (*metaspace*, *threads*).

### 13. Como mudas a configuração (por exemplo a BD) sem recompilar?

Edita o **ConfigMap** (ou o Secret) e faz `kubectl apply -f k8s/`; depois **reinicia os Pods** (`kubectl rollout restart deployment/<nome>`) para lerem os novos valores (as variáveis de ambiente só se leem no arranque). Não é preciso recompilar nem reconstruir a imagem: é a **separação entre configuração e código** da aula 06 (portabilidade entre ambientes).

### 14. Como funciona a entrega com Minikube e quais são as limitações?

O Minikube é um cluster Kubernetes de **um só nó**, para **desenvolvimento e testes**, não para produção (aula 06). A entrega é uma VM com o Minikube lá dentro (secção 5.1). Limitações a assumir: os Secrets têm valores de desenvolvimento, só há 1 nó (sem alta disponibilidade real), o PostgreSQL e o RabbitMQ não estão no cluster e o Eureka/Gateway dependem do trabalho dos colegas (12.9).

---

## ✅ Checklist do capítulo 12

- [ ] `minikube status` a funcionar e `kubectl config current-context` no cluster certo
- [ ] Imagens `incumprimento:1.0` e `indicadores:1.0` carregadas (`minikube image ls`)
- [ ] `kubectl apply -f k8s/` sem erros e os 2 Pods `1/1 Running`
- [ ] `landing` e um evento de teste a funcionar via `port-forward`
- [ ] Testaste: apagar um Pod, escalar o `indicadores` para 2 e ver 2 consumers no RabbitMQ
- [ ] Sabes explicar cada campo do Deployment e porque não há `Service`
