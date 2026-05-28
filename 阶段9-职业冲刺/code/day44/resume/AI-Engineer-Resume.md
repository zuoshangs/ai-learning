# Resume — AI Engineering

> **Target Role:** Java AI Engineer / AI Application Developer / LLM Engineering  
> **Updated:** 2026-05-28

---

## Summary

Java engineer with hands-on experience building production-grade AI applications. Completed a 42-day intensive AI engineering bootcamp, delivering a full-stack **AI Customer Service Platform** with RAG, Agent orchestration, and LLMOps governance — all in Java + Spring AI.

---

## Technical Skills

| Category | Technologies |
|:---------|:-------------|
| **Languages** | Java 21 (primary), Python 3.x (secondary) |
| **Frameworks** | Spring Boot 3.4.4, Spring AI 1.0.0-M6, LangChain |
| **AI Models** | DeepSeek Chat/Reasoner, OpenAI GPT, Ollama qwen2.5 |
| **Vector DB** | PgVector (HNSW index) |
| **Databases** | PostgreSQL, Redis |
| **Infrastructure** | Docker, Docker Compose, GitHub Actions CI/CD |
| **Monitoring** | Prometheus, Grafana, Actuator |
| **Tools** | Maven, Git, JUnit 5, Mockito |

---

## Key Project

### 🏆 AI Customer Service Platform

**Built from scratch over 42 days**  
**Stack:** Spring AI 1.0.0-M6 · Spring Boot 3.4.4 · Java 21 · PgVector · Redis · Docker  
**Scale:** 316 Java files + 71 Python counterparts · 85,815 lines of code · 46/46 tests passing  
**Source:** [github.com/zuoshangs/ai-learning](https://github.com/zuoshangs/ai-learning)

#### Three Metrics-Driven Achievements

| # | Achievement | Metrics | Technical Depth |
|:-:|:------------|:--------|:----------------|
| ① | **Full RAG Pipeline** | 5 retrieval modes, < 200ms latency | Hybrid Search (Vector + BM25) + RRF fusion + BGE Reranker |
| ② | **LLMOps Governance** | Token bucket rate limit @5req/s, semantic cache >60% hit rate, per-call cost tracking | Response normalization + real-time dashboard + Prometheus monitoring |
| ③ | **Ticket State Machine** | 6-state auto-transition (new→assign→process→review→close→reopen) with smart routing | State machine pattern + permission checks + audit trail |

#### Architecture

```
┌─────────┐   ┌─────────┐   ┌──────────┐   ┌──────────┐
│   UI     │   │  Chat   │   │  Ticket  │   │Dashboard │
│ HTML/JS  │──▶│ Service │──▶│  System  │──▶│  & Ops   │
└─────────┘   └────┬────┘   └────┬─────┘   └──────────┘
                   │              │
                   ▼              ▼
            ┌────────────┐ ┌────────────┐
            │ RAG Engine  │ │ LLMOps     │
            │ PgVector    │ │ RateLimit  │
            │ HybridSearch│ │ Cache/Cost │
            └────────────┘ └────────────┘
```

#### Technical Highlights

| Feature | Implementation |
|:--------|:---------------|
| **Streaming Chat** | SSE + multi-turn memory via Spring AI ChatClient + WebFlux |
| **RAG Pipeline** | Query Rewrite → Hybrid Retrieval → Rerank → Context Injection |
| **Agent Tools** | Weather / Calculator / Search / KB via `@Tool` + auto-orchestration |
| **Rate Limiting** | Custom token bucket algorithm (20 capacity, 5/s refill) |
| **Semantic Cache** | SHA-256 normalization + embedding similarity dual cache |
| **Cost Tracking** | Per-call Prompt/Completion token stats with real-time FX rate |
| **CI/CD** | GitHub Actions: test → build → publish to ghcr.io, multi-stage Docker |

---

## Learning Journey

```
Phase 1-3: AI Fundamentals    — Python, Prompt Engineering, API
  ↓
Phase 4:   Java AI Landing    — Spring AI, ChatClient, Stream, @Tool
  ↓
Phase 5:   RAG Engineering    — Doc Loading, Embedding, HyDE, Hybrid Search, Rerank
  ↓
Phase 6:   Agent Workflows    — ReAct, Multi-tool, Memory, Multi-Agent
  ↓
Phase 7:   LLMOps             — Rate Limit, Cache, Cost, Monitoring
  ↓
Phase 8:   Capstone Project   — AI Customer Service Platform (Full stack)
```

**Deliverables:** 43 tutorial notes · Dual-language code (Java + Python) · Public GitHub repo

---

## Interview Readiness

- Compiled **40+ interview questions** covering RAG / Agent / LLMOps / Spring AI / Architecture
- Wrote in-depth **Spring AI vs LangChain4j comparison report** based on real project experience

---

## Why Me

> **I'm a Java engineer, not an AI researcher.**  
> I don't train models or replicate papers.  
> What I do: **use AI to solve real business problems** — RAG for better customer service, Agents for workflow automation, LLMOps for cost control.  
> If you need someone to integrate LLMs into a Spring Boot application, I'm your person.

---

> 💡 **Resume tips:**
> - Tailor the achievement highlights to your target company (finance/insurance → emphasize ticket state machine & security; SaaS → emphasize LLMOps cost control)
> - Prepare STAR stories — one per achievement
> - The open-source repo `ai-learning` is your best portfolio piece — link to it everywhere
