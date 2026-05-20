# 🏗️ System Design que TODO dev Java precisa dominar

<div align="center">

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Status](https://img.shields.io/badge/Status-Em%20Progresso-yellow?style=for-the-badge)
![LinkedIn](https://img.shields.io/badge/LinkedIn-anderson--freitas21-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)

<br/>

> **"Entender System Design não é opcional para quem quer ser Senior."**
>
> Esta série foi criada para devs Java que querem ir além do CRUD e dominar  
> os conceitos que separam um Junior de um Pleno/Senior nas grandes empresas.

<br/>

[📖 Ver Série no LinkedIn](#-série-no-linkedin) • [☕ Episódios](#-episódios) • [🚀 Como Rodar](#-como-rodar) • [🎯 Para Quem é Isso](#-para-quem-é-isso)

</div>

---

## 🎯 Para quem é isso?

Esta série é para você que:

- ✅ Já sabe programar em Java mas quer evoluir para Pleno/Senior
- ✅ Quer passar em entrevistas técnicas de empresas como **Nubank, iFood, PicPay, Google, Amazon**
- ✅ Precisa entender como sistemas reais são projetados em produção
- ✅ Quer sair do "eu escrevo código que funciona" para "eu projeto sistemas que escalam"

---

## 📚 Episódios

| # | Tema | Conceitos-chave | Código | Post |
|---|------|----------------|--------|------|
| 01 | ⚡ **Escalabilidade** | Vertical vs Horizontal, Stateless, Auto-scaling | [`Ep01_Scalability.java`](./src/main/java/systemdesign/ep01/) | [LinkedIn](https://www.linkedin.com/in/anderson-freitas21) |
| 02 | ⚖️ **Load Balancing** | Round Robin, Least Connections, Health Check | [`Ep02_LoadBalancing.java`](./src/main/java/systemdesign/ep02/) | [LinkedIn](https://www.linkedin.com/in/anderson-freitas21) |
| 03 | 🗄️ **Cache** | Cache-Aside, Write-Through, Eviction (LRU/LFU) | [`Ep03_Cache.java`](./src/main/java/systemdesign/ep03/) | [LinkedIn](https://www.linkedin.com/in/anderson-freitas21) |
| 04 | 🛢️ **SQL vs NoSQL** | CAP Theorem na prática, quando usar cada um | [`Ep04_SQLvsNoSQL.java`](./src/main/java/systemdesign/ep04/) | _Em breve_ |
| 05 | 📨 **Message Queues** | Kafka, RabbitMQ, async vs sync, at-least-once | [`Ep05_MessageQueue.java`](./src/main/java/systemdesign/ep05/) | _Em breve_ |
| 06 | 🚪 **API Gateway** | Rate Limiting, Auth, Routing, Circuit Breaker | [`Ep06_ApiGateway.java`](./src/main/java/systemdesign/ep06/) | _Em breve_ |
| 07 | 🧩 **Microsserviços** | Decomposição, comunicação, falhas em cascata | [`Ep07_Microservices.java`](./src/main/java/systemdesign/ep07/) | _Em breve_ |
| 08 | 🔺 **Teorema CAP** | Consistency, Availability, Partition Tolerance | [`Ep08_CAP.java`](./srcnt/main/java/systemdesign/ep08/) | _Em breve_ |
| 09 | 🔍 **Indexação & DB** | B-Tree, índices compostos, query plan | [`Ep09_Indexing.java`](./src/main/java/systemdesign/ep09/) | _Em breve_ |
| 10 | 🏆 **Case Real** | Design de um sistema completo do zero | [`Ep10_RealCase.java`](./src/main/java/systemdesign/ep10/) | _Em breve_ |

---

## 🗂️ Estrutura do Projeto

```
system-design-java/
│
├── 📁 src/
│   └── main/
│       └── java/
│           └── systemdesign/
│               ├── ep01/        # Escalabilidade
│               ├── ep02/        # Load Balancing
│               ├── ep03/        # Cache
│               ├── ep04/        # SQL vs NoSQL
│               ├── ep05/        # Message Queues
│               ├── ep06/        # API Gateway
│               ├── ep07/        # Microsserviços
│               ├── ep08/        # Teorema CAP
│               ├── ep09/        # Indexação
│               └── ep10/        # Case Real
│
├── 📁 docs/
│   └── diagrams/                # Diagramas de arquitetura por episódio
│
├── 📄 README.md
└── 📄 pom.xml
```

---

## 🚀 Como Rodar

### Pré-requisitos

```bash
java --version   # Java 17+
mvn --version    # Maven 3.8+
```

### Clonando e rodando

```bash
# Clone o repositório
git clone https://github.com/anderson-freitas21/system-design-java.git
cd system-design-java

# Compile o projeto
mvn clean compile

# Rode um episódio específico (ex: Ep03 - Cache)
mvn exec:java -Dexec.mainClass="systemdesign.ep03.CacheSimulation"
```

> 💡 Cada classe possui um método `main()` com simulações prontas para rodar.  
> Nenhuma dependência externa necessária para os episódios básicos.

---

## 🧠 O que você vai aprender

```
┌─────────────────────────────────────────────────────┐
│                   JORNADA DO DEV                    │
├─────────────────────────────────────────────────────┤
│  Junior  →  "Meu código funciona"                   │
│  Pleno   →  "Meu código é eficiente"                │
│  Senior  →  "Meu sistema escala e sobrevive"        │
└─────────────────────────────────────────────────────┘
```

Cada episódio entrega:

- 📐 **Conceito** — teoria direta e objetiva, sem enrolação
- 🔗 **Analogia real** — comparação com situações do dia a dia
- ☕ **Implementação Java** — código funcional com Javadoc e Big O anotado
- 💼 **Contexto de entrevista** — perguntas reais cobradas por empresas top
- 🧩 **Spring Boot / Cloud** — como aplicar em projetos reais

---

## 💼 Perguntas de Entrevista Cobertas

Exemplos de perguntas abordadas ao longo da série:

> _"Como você projetaria o sistema de pagamentos do Nubank para suportar 1 milhão de transações/dia?"_

> _"Qual a diferença entre consistência eventual e forte? Quando usar cada uma?"_

> _"Por que o iFood usa Kafka e não chamadas REST entre microsserviços?"_

> _"Como implementar cache para evitar cache stampede em alta concorrência?"_

> _"Desenhe o sistema de notificações do WhatsApp para 2 bilhões de usuários."_

---

## 📊 Complexidade — Visão Geral

| Estrutura / Operação | Leitura | Escrita | Espaço |
|----------------------|---------|---------|--------|
| Cache (HashMap) | O(1) | O(1) | O(n) |
| Load Balancer Round Robin | O(1) | O(1) | O(k) |
| LRU Cache (LinkedHashMap) | O(1) | O(1) | O(n) |
| Message Queue (FIFO) | O(1) | O(1) | O(n) |
| B-Tree Index | O(log n) | O(log n) | O(n) |

---

## 🏅 Sobre o Autor

<div align="center">

**Anderson Nogueira**  
Backend Developer · Java & Kotlin · AI Tools Enthusiast

[![LinkedIn](https://img.shields.io/badge/LinkedIn-Conectar-0077B5?style=flat-square&logo=linkedin)](https://www.linkedin.com/in/anderson-freitas21)

</div>

Desenvolvedor backend na **CWi Software**, apaixonado por sistemas distribuídos e ferramentas de IA aplicadas ao desenvolvimento.

Certificações Anthropic:
- 🎓 **Claude 101** — Fundamentos da IA com Claude
- 🎓 **Claude Code in Action** *(Março/2026)* — IA aplicada ao fluxo de desenvolvimento

> _Esta série nasceu da minha própria jornada de estudo para entrevistas técnicas  
> e do desejo de compartilhar o que aprendi de forma prática e direta._

---

## 🤝 Contribuindo

Encontrou um erro? Tem sugestão de episódio? Abra uma **Issue** ou mande um **PR**!

```bash
# Fork → Clone → Branch → Commit → PR
git checkout -b feat/sugestao-episodio-11
```

---

## ⭐ Apoie a Série

Se este conteúdo está te ajudando:

- ⭐ **Dê uma estrela** no repositório
- 🔗 **Compartilhe** com outros devs Java
- 💬 **Comente** no post do LinkedIn com sua dúvida ou insight
- 👤 **Siga** [@anderson-freitas21](https://www.linkedin.com/in/anderson-freitas21) no LinkedIn

---

<div align="center">

**Série completa · 10 episódios · Do conceito ao código**

_Feito com ☕ Java e muito System Design_

</div>
