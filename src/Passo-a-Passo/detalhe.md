# Detalhe: trabalhar em equipa com o GitHub e encaixar a tua parte no repositório do grupo

> Responde a duas perguntas:
> **A)** Como trabalhar com o GitHub (branch, commit, push e Pull Request) e em que **ordem**.
> **B)** Se o repositório já tem um POM, **preciso de outro `acesso-a-tempo` com dois módulos** como na pasta `referencia/`?
>
> O que **eu** vou fazer, com que branches e mensagens de commit, está em [`plano-de-trabalho.md`](plano-de-trabalho.md). As dependências dos colegas estão em [`minha-parte.md`](minha-parte.md), secção 9.

## Respostas rápidas

| Pergunta | Resposta curta |
|---|---|
| Crio o Pull Request **antes** de escrever o código? | **Não.** O PR só existe depois de teres **commits na tua branch e do `push`**. Ordem: *branch → código → commit → push → **depois** Pull Request*. |
| Posso fazer o PR e continuar a trabalhar? | **Sim.** Qualquer `push` novo para a mesma branch **atualiza o PR** automaticamente. (Se quiseres abri-lo cedo, usa um **PR em rascunho**, *Draft*.) |
| Crio outro POM `acesso-a-tempo` com os meus 2 módulos? | **Não.** O POM pai é **o do repositório do grupo**. Os teus 2 módulos ficam **ao lado** dos dos colegas e apontam para esse POM pai. A pasta `referencia/` só existe para trabalhares **sozinho** enquanto não tens o POM do grupo. |

---

# Parte A — GitHub: a forma correta de trabalhar

## A.1 Os conceitos em 1 minuto

| Conceito | O que é |
|---|---|
| **Repositório** | O projeto e o seu histórico. Há uma cópia no **GitHub** (`origin`) e uma **no teu computador** |
| **`main`** | A branch "oficial", a que tem o trabalho já aprovado de toda a equipa |
| **Branch** | Uma "linha de trabalho" tua, isolada. Podes mexer à vontade sem estragar a `main` |
| **Commit** | Uma "fotografia" gravada do teu código, com uma mensagem. Fica **só no teu computador** |
| **Push** | Envia os teus commits para o GitHub |
| **Pull Request (PR)** | Um **pedido no GitHub** para juntar a tua branch à `main`. É onde se **revê** o código e se discute |
| **Review** | Um colega lê o teu PR e aprova ou pede alterações (a proposta pede que os PR sejam revistos por **outro elemento**, secção 5.2) |
| **Merge** | A tua branch é incorporada na `main` |

```text
          teu computador                                       GitHub (origin)
   ┌───────────────────────────┐                      ┌──────────────────────────────┐
   │ main  ──●──●──●           │  <── git pull ────   │ main  ──●──●──●              │
   │          \                │                      │          \                   │
   │ feature   ●──●──●         │  ── git push ─────>  │ feature   ●──●──●  ══► PULL REQUEST ══► (revisão) ══► merge na main
   └───────────────────────────┘                      └──────────────────────────────┘
```

## A.2 A ordem certa (resposta à tua pergunta)

Pensavas: *"crio o PR, depois escrevo o código, faço commit e push"*. A ordem real é esta:

```text
1) Atualizar a main            git switch main  &&  git pull
2) Criar a TUA branch          git switch -c feature/historico-esqueleto
3) Escrever o código           (um capítulo do guia de cada vez)
4) Testar                      mvn test
5) COMMIT                      git add ...  &&  git commit -m "..."     (podes fazer vários)
6) PUSH                        git push -u origin feature/historico-esqueleto
7) CRIAR O PULL REQUEST        (no GitHub)  ◄── só aqui!
8) Revisão e correções         novos commits + push  (o PR atualiza-se sozinho)
9) MERGE                       depois de aprovado
10) Limpar                     voltar à main, git pull, apagar a branch
```

**Porque o PR não pode vir antes?** Um PR compara a tua branch com a `main`. Se a tua branch não tiver commits novos, o GitHub diz *"There isn't anything to compare"* e **não deixa criar** o PR.

**Exceção útil — PR em rascunho (Draft):** depois do **primeiro push** podes abrir um *Draft Pull Request*. Serve para a equipa ver que estás a trabalhar naquilo. Continuas a fazer commit e push e, quando acabares, carregas em **"Ready for review"**.

## A.3 Preparação (só uma vez)

```bash
# 1. Clonar o repositório do grupo (o endereço está na secção 5.1 da proposta; confirma com a equipa)
git clone https://github.com/mtsds-grupo5/acesso-a-tempo.git
cd acesso-a-tempo

# 2. Identificar-te (o teu nome aparece no histórico: na defesa individual isto conta!)
git config user.name  "O teu nome"
git config user.email "o_email_da_tua_conta_GitHub@exemplo.pt"

# 3. Ver as ligações e a branch
git remote -v          # origin  https://github.com/...
git branch -a          # main e as branches remotas
```

> 💡 **Precisas de permissão** de escrita no repositório: pede ao dono para te adicionar como *collaborator* (*Settings → Collaborators*). Se o `git push` der *"Permission denied"* é isto.
>
> 💡 Usa o **mesmo e-mail da tua conta GitHub** nos commits; caso contrário o GitHub **não associa** os commits ao teu perfil e o teu contributo parece não existir.

> 🔒 **O repositório do grupo tem um *hook* que bloqueia o `push` direto para a `main`** (`.githooks/pre-push`). Ativa-o **uma vez por clone**: `git config core.hooksPath .githooks`. A mensagem pede-te uma branch `feature/<servico>-<tema>` e um PR. O CI (`.github/workflows/build.yml`) corre `./mvnw verify` com o Spotless e **recusa código mal formatado**; o PR template pede revisão por **um elemento de outra equipa**. Detalhes em `referencia/grupo-explicado.md`.

**Antes do primeiro commit: o `.gitignore`.** Confirma que o repositório ignora o que **não** é código:

```gitignore
# Maven
target/
# IntelliJ
.idea/
*.iml
out/
# Ficheiros do sistema e logs
.DS_Store
*.log
# Segredos (nunca no repositório)
.env
```

Se o `.gitignore` não existir ou estiver incompleto, **acrescenta-o num commit à parte** e avisa o grupo. **Nunca** faças commit da pasta `target/` nem da `.idea/`: enchem o repositório e causam conflitos permanentes.

## A.4 O ciclo de trabalho, passo a passo

### Passo 1 — Atualizar a `main`

```bash
git switch main
git pull                      # traz o trabalho que os colegas já integraram
```

Faz isto **sempre antes de criar uma branch nova**, para partires do código mais recente.

### Passo 2 — Criar a tua branch

```bash
git switch -c feature/historico-esqueleto
```

`-c` = *create*. Podes confirmar onde estás com `git branch` (a atual tem um `*`).

**Nomes de branch** (combina a convenção com o grupo):

| Padrão | Exemplo |
|---|---|
| `feature/<serviço>-<o-quê>` | `feature/historico-esqueleto`, `feature/garantia-acesso-regras` |
| `fix/<serviço>-<o-quê>` | `fix/garantia-acesso-dias-uteis` |
| `docs/<o-quê>` | `docs/api-garantia-acesso` |

Letras minúsculas, **sem espaços**, hífens. **Uma branch por tarefa**, não uma única branch para o projeto todo.

### Passo 3 e 4 — Programar e testar

Segue um capítulo do guia de cada vez. **Antes de fazeres commit, corre os testes** (`mvn test`, ou o botão do IntelliJ). Nunca envies código que não compila.

### Passo 5 — Commit

```bash
git status                    # o que mudou? (vermelho = ainda não preparado, verde = preparado)
git diff                      # ver as alterações linha a linha
git add historico/          # prepara os ficheiros (evita "git add ." às cegas)
git status                    # confirma que não há target/, .idea/, ...
git commit -m "feat(historico): esqueleto do serviço com landing e actuator"
```

**Boas práticas de commit:**

- **Commits pequenos e frequentes**: um por capítulo (ou por ficheiro importante) é um bom ritmo. Facilita a revisão e o "desfazer".
- **Mensagens claras, no imperativo**, com o formato `tipo(serviço): descrição`:

| Tipo | Quando | Exemplo |
|---|---|---|
| `feat` | Funcionalidade nova | `feat(garantia-acesso): regras dos 3 níveis` |
| `fix` | Correção de erro | `fix(historico): semana ISO no fim do ano` |
| `test` | Testes | `test(garantia-acesso): testes dos dias úteis` |
| `docs` | Documentação | `docs: contexto de negócio da parte 3` |
| `refactor` | Reorganização sem mudar comportamento | `refactor(garantia-acesso): extrai ModelMapperConfig` |
| `chore` | Configuração, build | `chore: acrescenta Dockerfile` |

- **Nunca** `git commit -m "alterações"` ou `"asdf"`: o histórico é lido por outros (e pelos professores).
- **Não** juntes coisas sem relação no mesmo commit (um commit = uma ideia).

### Passo 6 — Push

```bash
git push -u origin feature/historico-esqueleto     # 1.ª vez: -u liga a branch local à remota
git push                                              # nas vezes seguintes basta isto
```

Depois do push, a tua branch já existe no GitHub.

### Passo 7 — Criar o Pull Request

**Pelo site:**

1. Abre o repositório no GitHub. Aparece uma faixa amarela **"Compare & pull request"** para a tua branch; carrega.
   (Ou: separador **Pull requests → New pull request**.)
2. Confirma os dois campos: **base: `main`** ← **compare: `feature/historico-esqueleto`**.
3. **Título** curto e claro (`feat(historico): esqueleto do serviço`) e **descrição** (ver modelo abaixo).
4. À direita: **Reviewers** → escolhe **um colega**; **Assignees** → tu; **Labels** se existirem.
5. **Create pull request** (ou *Create draft pull request* se ainda não está pronto).

**Pelo IntelliJ:** *Git → GitHub → Create Pull Request…* (precisas da conta GitHub ligada em *Settings → Version Control → GitHub*). **Pela linha de comandos** (com o `gh` instalado): `gh pr create --base main --title "..." --body "..."`.

**Modelo de descrição do PR:**

```markdown
## O que muda
- Cria o módulo `historico` (pom, application.yml, landing, actuator)

## Como testar
1. `docker compose up -d` (pasta infra)
2. Arrancar `Indicadores`
3. `GET http://localhost:8086/historico/landing` → 200

## Checklist
- [ ] Compila e os testes passam (`mvn test`)
- [ ] Sem `target/`, `.idea/` nem passwords reais
- [ ] Documentação (Javadoc) escrita
- [ ] Atualizei a minha branch com a `main` (sem conflitos)

## Notas para quem revê
- Escolhi X porque Y
```

### Passo 8 — Revisão e correções

O colega comenta. Para corrigir, **não crias outro PR**: voltas à **mesma branch**, alteras, fazes commit e push:

```bash
# (ainda na mesma branch)
git add ...
git commit -m "fix(historico): corrige o formato da semana"
git push                      # o PR atualiza-se sozinho
```

Responde aos comentários no GitHub e marca-os como *resolved* depois de tratados.

### Passo 9 — Merge

Só depois de **aprovado** (e com os testes a passar). No GitHub: **Merge pull request**. Sugestão ao grupo: usar **Squash and merge** (junta os teus commits num só na `main`; histórico mais limpo) **ou** *Merge commit* (mantém todos os commits individuais). Combinem **uma** forma.

> 💡 Como a defesa é **individual**, convém que os teus commits **continuem visíveis** no histórico. Se usarem *Squash and merge*, o teu nome continua como autor do commit resultante, mas os commits pequenos desaparecem da `main`. Combinem isto com a equipa.

### Passo 10 — Limpar e recomeçar

```bash
git switch main
git pull                                   # traz o teu próprio merge e o dos colegas
git branch -d feature/historico-esqueleto    # apaga a branch local
```

No GitHub aparece o botão **Delete branch** depois do merge; usa-o. A seguir, **novo ciclo** (volta ao Passo 1) para a tarefa seguinte.

## A.5 Quantos Pull Requests faço?

PR **pequenos** são revistos mais depressa e causam menos conflitos: **uma tarefa = uma branch = um PR**, com 1 a 3 commits. A lista das tarefas, os nomes das branches (`feature/<servico>-<tema>`) e as mensagens de commit estão em [`plano-de-trabalho.md`](plano-de-trabalho.md), secções 4 e 6.

## A.6 Manter a tua branch atualizada e resolver conflitos

Enquanto trabalhas, os colegas fazem merge na `main`. Para não divergir demasiado, **de vez em quando** (e **sempre antes de abrir o PR**):

```bash
git fetch origin                 # vê o que mudou no GitHub (não altera nada teu)
git merge origin/main            # traz as novidades da main para a tua branch
```

Se houver **conflito** (duas pessoas mudaram a mesma linha), o Git pára e marca o ficheiro. **O conflito mais provável é no `pom.xml` do repositório**, porque todos acrescentam o seu módulo ao `<modules>`:

```xml
<modules>
<<<<<<< HEAD
    <module>historico</module>
    <module>garantia-acesso</module>
=======
    <module>referenciacao</module>
    <module>agendamento</module>
>>>>>>> origin/main
</modules>
```

**Como resolver:** apaga as linhas `<<<<<<<`, `=======` e `>>>>>>>` e **mantém as linhas de todos** (neste caso, as quatro):

```xml
<modules>
    <module>historico</module>
    <module>garantia-acesso</module>
    <module>referenciacao</module>
    <module>agendamento</module>
</modules>
```

Depois:

```bash
git add pom.xml
git commit                      # o Git já sugere a mensagem de merge
git push
```

> O IntelliJ tem um **editor de conflitos** (*Git → Resolve Conflicts*) com 3 painéis (o teu, o dos outros, o resultado) que ajuda muito. **Em caso de dúvida, pergunta ao colega** antes de apagar o trabalho dele.

## A.7 Regras de ouro

| ✅ Faz | ❌ Não faças |
|---|---|
| Trabalhar **sempre numa branch tua** | Fazer commit **diretamente na `main`** |
| `git pull` na `main` **antes** de criar a branch | Começar uma branch a partir de uma `main` desatualizada |
| Commits pequenos, com mensagem clara | Um commit gigante no fim com "tudo" |
| Testar (`mvn test`) **antes** do commit | Enviar código que não compila |
| Rever o `git status` antes do commit | `git add .` sem olhar (apanha `target/`, `.idea/`) |
| Pedir revisão a **outro** elemento | Aprovar e fazer merge do teu próprio PR sem revisão |
| Manter o PR **pequeno** e focado | PR com 3000 linhas de assuntos diferentes |
| Passwords reais **só** em variáveis/Secrets | Commits com passwords ou chaves (`.env`) |
| `git push --force` **nunca** em branches partilhadas | Reescrever o histórico da `main` |

> 🔒 **Segredos:** as passwords do `application.yml` do guia (`garantia_acesso_pass`, `123`...) são valores **de desenvolvimento**. Se alguma vez commitares uma password **real** por engano, **trata-a como comprometida** (muda-a): apagar o ficheiro depois **não** remove o histórico.

## A.8 Se algo correr mal (cola rápida)

| Situação | Solução |
|---|---|
| **Fiz commits na `main` por engano** (e ainda não fiz push) | `git switch -c feature/o-que-fiz` (cria a branch **com** esses commits) → `git switch main` → `git reset --hard origin/main` (⚠️ **só** depois de confirmares que a nova branch tem o teu trabalho) |
| **Quero desfazer o último commit mas manter as alterações** | `git reset --soft HEAD~1` |
| **Quero deitar fora alterações de um ficheiro** | `git restore caminho/Ficheiro.java` (⚠️ perde-se o que não foi guardado em commit) |
| **Esqueci um ficheiro no último commit** (ainda sem push) | `git add Ficheiro` → `git commit --amend --no-edit` |
| **Adicionei `target/` por engano** | `git rm -r --cached target` → acrescenta `target/` ao `.gitignore` → commit |
| **`git push` rejeitado** ("non-fast-forward") | Alguém mexeu na mesma branch: `git pull` e resolve conflitos |
| **Não sei em que branch estou** | `git branch` (a atual tem `*`) e `git status` |
| **Ver o histórico** | `git log --oneline --graph --decorate -15` |
| **Guardar trabalho a meio para mudar de branch** | `git stash` … depois `git stash pop` |

---

# Parte B — O POM: preciso de outro `acesso-a-tempo`?

## B.1 A resposta

**Não.** Se o repositório do grupo **já tem um POM pai**, esse é **o único POM pai**. **Não cries outro** `acesso-a-tempo` com os teus dois módulos lá dentro: ficaria um projeto Maven **dentro de outro projeto Maven**, com versões possivelmente diferentes das dos colegas.

A pasta `referencia/acesso-a-tempo/` tem um POM pai **só porque o exemplo teve de funcionar sozinho** (para o poder compilar e testar). No repositório real, esse POM **é substituído pelo do grupo**.

### O que tens (referência) vs o que vais ter (repositório do grupo)

```text
   referencia/  (projeto SOZINHO, só para estudo)          Repositório do grupo (REAL)
   ───────────────────────────────────────────────         ─────────────────────────────────────────────
   acesso-a-tempo/                                          acesso-a-tempo/                  <- raiz do repositório
   ├── pom.xml          <- pai "de brincar"                 ├── pom.xml            <- POM PAI DO GRUPO (um só)
   ├── infra/                                               ├── infra/ (ou o que o grupo definir)
   ├── garantia-acesso/ <- TEU                              ├── referenciacao/     <- Parte 1
   │   └── pom.xml                                          ├── agendamento/       <- Parte 1
   └── historico/       <- TEU                              ├── lista-espera/      <- Parte 2
       └── pom.xml                                          ├── gestao-acesso/     <- Parte 2
                                                            ├── garantia-acesso/   <- TEU  (copiado da referencia)
                                                            ├── historico/         <- TEU  (copiado da referencia)
                                                            ├── utentes/           <- Parte 4
                                                            ├── notificacoes/      <- Parte 4
                                                            ├── api-gateway/  eureka-server/   <- plataforma
                                                            └── (README, .gitignore, ...)
```

**O que levas da `referencia/` para o repositório:** apenas as **pastas `garantia-acesso/` e `historico/`**. **Não** levas o `pom.xml` da raiz da referência (já há o do grupo).

## B.2 Como funciona a ligação ao POM pai

Cada módulo diz quem é o seu pai no bloco `<parent>`:

```xml
<!-- historico/pom.xml -->
<parent>
    <groupId>com.acessoatempo</groupId>        <!-- ◄ têm de ser os do POM PAI DO GRUPO -->
    <artifactId>acesso-a-tempo</artifactId>    <!-- ◄ -->
    <version>1.0-SNAPSHOT</version>            <!-- ◄ -->
</parent>
<artifactId>historico</artifactId>
```

E o POM pai lista os filhos no `<modules>`:

```xml
<!-- pom.xml da raiz -->
<modules>
    <module>historico</module>     <!-- nome da PASTA do módulo -->
    <module>garantia-acesso</module>
    ...
</modules>
```

Duas regras:

1. O `<parent>` do módulo **tem de ter exatamente o `groupId`, `artifactId` e `version`** do POM pai do grupo. Se forem diferentes dos do guia (`com.acessoatempo` / `acesso-a-tempo` / `1.0-SNAPSHOT`), **corrige nos teus dois `pom.xml`**.
2. O texto de `<module>` é o **nome da pasta**. Se a pasta se chamar `garantia-acesso`, o `<module>` é `garantia-acesso`.

## B.3 Passo a passo para encaixar a tua parte

### 1. Clonar e **olhar** para o POM do grupo

Abre o `pom.xml` da raiz e **anota**:

| O que procurar | Onde |
|---|---|
| `groupId`, `artifactId`, `version` | Topo do ficheiro (vão para o `<parent>` dos teus módulos) |
| Versão do **Spring Boot**, **Spring Cloud** e **Java** | `<properties>` |
| Os **BOM** estão importados? | `<dependencyManagement>` (Boot **e** Cloud) |
| Que dependências **já recebem todos** os módulos | `<dependencies>` |
| Configuração de **plugins** | `<build>` → `<pluginManagement>` |

### 2. Confrontar com o que a tua parte precisa (checklist)

O código do guia foi testado com um POM pai que tem as seguintes coisas. **Confirma que o do grupo também as tem** (a coluna "Se faltar" diz o que fazer):

| # | O que o teu código precisa | Como saber | Se faltar |
|---|---|---|---|
| 1 | **BOM do Spring Boot 3.5.x** importado (`spring-boot-dependencies`) | `<dependencyManagement>` | Pedir que acrescentem ao pai (ideal) **ou** copiar o bloco para o `pom.xml` do módulo |
| 2 | **BOM do Spring Cloud 2025.0.x** importado | idem | idem (precisas dele para o Eureka client) |
| 3 | **Java 17** (`maven.compiler.release`) | `<properties>` | Acrescentar no pai |
| 4 | **`<parameters>true</parameters>`** no `maven-compiler-plugin` | `<pluginManagement>` | O guia já escreve `@PathVariable("id")`, `@RequestParam("esp")`: funciona **mesmo sem isto** |
| 5 | **Processador do Lombok** (`annotationProcessorPaths`) | idem | Em JDK 17 a 21 funciona sem isto; em JDK 23+ é obrigatório |
| 6 | **`repackage`** no `spring-boot-maven-plugin` | idem | **Sem isto `java -jar` falha** ("no main manifest attribute"): acrescentar (ver abaixo) |
| 7 | `lombok`, `spring-boot-starter-webmvc`, `modelmapper` e os starters de **teste** nas `<dependencies>` do pai | `<dependencies>` | Declará-los no `pom.xml` do módulo (ver abaixo) |

**Fallback no `pom.xml` do módulo (se o pai não tiver):**

```xml
<!-- a) BOM do Spring Boot e do Spring Cloud (se o pai não os importar) -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.5.16</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- b) repackage: JAR executável (no <build><plugins> do módulo) -->
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>3.5.16</version>
    <executions>
        <execution>
            <goals><goal>repackage</goal></goals>
        </execution>
    </executions>
</plugin>
```

> 💡 **O melhor é corrigir no pai** (um só sítio, todos beneficiam). Propõe-no ao grupo (ou a quem fez o POM) num **Pull Request** pequeno só para o `pom.xml` da raiz, **antes** de integrares os módulos. Mais fácil de rever e de reverter.

### 3. Se o grupo usar versões **diferentes** das do guia

O código está escrito para **Spring Boot 3.5 / Spring Cloud 2025.1**. Se o grupo ficar em **Spring Boot 3.x**, estas linhas mudam (a tabela inversa de `00-LEIA-ME.md`):

| Neste guia (Boot 4) | Em Boot 3.x |
|---|---|
| `spring-boot-starter-webmvc` | `spring-boot-starter-web` |
| `spring-boot-starter-webmvc-test` | (não existe; vem no `starter-test`) |
| `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` |
| `JacksonJsonMessageConverter` | `Jackson2JsonMessageConverter` |
| `@MockitoBean` | `@MockBean` ou `@MockitoBean` (a partir da 3.4) |

Decidam **a versão uma só vez**, em grupo, e escrevam-na no POM pai. Todos os serviços **têm de usar a mesma**.

### 4. Copiar os teus módulos para o repositório

```bash
# (na tua branch, na raiz do repositório clonado)
cp -R /caminho/da/tua/pasta/garantia-acesso  ./garantia-acesso
cp -R /caminho/da/tua/pasta/historico    ./historico
```

(ou, melhor ainda, **cria os módulos diretamente dentro do repositório clonado** e vai escrevendo o código lá, assim não há cópias.)

No **IntelliJ**: abre a **raiz do repositório** como projeto (*File → Open* na pasta com o `pom.xml` do grupo). Para criar um módulo: botão direito na raiz → *New → Module* → **Maven**, **Parent** = o projeto do grupo. O IntelliJ **acrescenta o `<module>` ao POM pai** sozinho.

### 5. Ajustar o `<parent>` e o `<modules>`

- Nos teus 2 `pom.xml`: `<parent>` com o `groupId/artifactId/version` **do grupo** (B.2).
- No `pom.xml` da raiz: confirma que tem
  ```xml
  <module>garantia-acesso</module>
  <module>historico</module>
  ```
  (só as **tuas** duas linhas; as dos outros já estão ou virão nos PR deles).

### 6. Compilar e testar **a partir da raiz**

```bash
mvn -pl garantia-acesso,historico -am test           # só os teus 2 módulos (e o que eles precisarem)
mvn -pl garantia-acesso,historico -am package -DskipTests   # gera os JAR
```

`-pl` = *project list* (só estes módulos); `-am` = *also make* (e o pai). Assim **não precisas** de compilar os serviços dos colegas, que podem estar a meio.

### 7. O que **não** levas / **não** duplicas

| Ficheiro | Fazer |
|---|---|
| `referencia/.../pom.xml` (pai da referência) | **Não levar.** Já existe o do grupo |
| `infra/docker-compose.yml` e `init-db.sql` | **Não** criar um segundo se já existir um no repositório. Se existir um só do grupo, **acrescenta lá** só o que faltar. Combinem onde fica e quem o mantém (a proposta diz que a "base comum" é da Parte 4) |
| `Dockerfile`s dos teus módulos | **Levar** (ficam dentro de cada módulo) |
| `.gitignore` | Usar o do repositório; completar se faltar |

## B.4 Resumo visual do que muda

```text
 ANTES (sozinho, "referencia")              DEPOIS (no repositório do grupo)
 ─────────────────────────────              ────────────────────────────────
 pom pai  (o teu, de teste)        ───►     pom pai do GRUPO (o único)
   ├── garantia-acesso                        ├── garantia-acesso    ◄── levas esta pasta
   └── historico                              ├── historico          ◄── levas esta pasta
                                              ├── referenciacao (colega)
                                              └── ... (colegas)

  <parent> aponta para o teu pai            <parent> aponta para o pai do GRUPO
```

## B.5 Lista de verificação antes de abrir o 1.º Pull Request

- [ ] Fiz `git pull` na `main` e criei uma **branch nova**
- [ ] O `<parent>` dos meus 2 `pom.xml` aponta para o POM pai **do grupo**
- [ ] `<modules>` da raiz tem as minhas 2 linhas (e as dos outros que já existiam)
- [ ] Confirmei os 7 pontos da tabela B.3 (BOM, Java, repackage, dependências comuns...)
- [ ] `mvn -pl garantia-acesso,historico -am test` passa na raiz do repositório
- [ ] `git status` **sem** `target/` nem `.idea/`
- [ ] Mensagens de commit claras e **o meu nome/e-mail** corretos
- [ ] Descrição do PR preenchida e **um colega** indicado para rever

---
