# Day 29：Dify 工作流平台 — Java ↔ Dify 双向集成

> **目标**：部署开源的 Dify 工作流平台，实现 Java 服务与 Dify 的可视化 AI 工作流双向集成
> **日期**：2026-05-27
> **前置**：Docker Compose, Spring Boot 3.4.4, Java 21

---

## 1. 什么是 Dify

**Dify** 是一个开源的低代码 AI 应用开发平台，可以理解为 AI 工作流的可视化 IDE。

| 特性 | 说明 |
|------|------|
| **可视化编排** | 拖拽式搭建 LLM + 知识库 + 工具的工作流 |
| **内置 LLM 支持** | 接入 DeepSeek / OpenAI / 本地模型 |
| **知识库** | 内置文档上传 + 切分 + 向量化 + 检索 |
| **自定义工具** | 通过 OpenAPI/Swagger 接入外部 HTTP API |
| **工作流 API** | 发布后的工作流可通过 REST API 调用 |
| **私有化部署** | Docker Compose，数据不出内网 |

### 架构设计

```
┌──────────────────────────────────────┐
│          Dify Web UI (:3000)          │
│  可视化拖拽编排 RAG 工作流 + 工具      │
└──────────┬───────────────────────────┘
           │ HTTP
┌──────────▼───────────────────────────┐
│       Dify API / Worker (:5001)       │
│   LLM 调用 · 知识库检索 · 工具调度    │
└──────────────────┬───────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
┌──────────────┐    ┌────────────────────┐
│ OpenAPI 工具  │    │  Java 调用 Dify    │
│ (Java 暴露)   │    │  (Spring Boot)    │
│ POST /api/   │    │  POST /api/dify/  │
│ tools/weather│    │  workflow?query=  │
└──────────────┘    └────────────────────┘
```

---

## 2. Dify 部署（Docker Compose）

### 2.1 环境准备

```bash
# 确认 Docker 和 Compose 已安装
docker --version
docker compose version
```

### 2.2 自定义 docker-compose.yml

创建最小化部署配置（`code/day29/dify/docker-compose.yml`），仅包含必需的 5 个服务：

| 服务 | 镜像 | 端口 | 作用 |
|------|------|------|------|
| `postgres` | postgres:15-alpine | 5433 | 主数据库（替换 Dify 默认 5432 避免冲突） |
| `redis` | redis:6-alpine | 6380 | 缓存/消息队列 |
| `api` | langgenius/dify-api:1.14.2 | 5001 | Dify API 服务 |
| `worker` | langgenius/dify-api:1.14.2 | — | 异步任务执行器 |
| `web` | langgenius/dify-web:1.14.2 | 3000 | Web 管理界面 |
| `sandbox` | langgenius/dify-sandbox:0.2.15 | — | 安全代码执行环境 |

> **注意**：由于我们在中国大陆，使用 `m.daocloud.io` 作为 Docker Hub 镜像加速拉取。

### 2.3 启动 Dify

```bash
cd code/day29/dify
# 先拉取镜像（网络慢，耐心等待）
docker pull m.daocloud.io/docker.io/library/postgres:15-alpine
docker tag m.daocloud.io/docker.io/library/postgres:15-alpine postgres:15-alpine

docker pull m.daocloud.io/docker.io/library/redis:6-alpine
docker tag m.daocloud.io/docker.io/library/redis:6-alpine redis:6-alpine

docker pull m.daocloud.io/docker.io/langgenius/dify-api:1.14.2
docker pull m.daocloud.io/docker.io/langgenius/dify-web:1.14.2

# 启动所有服务
docker compose up -d

# 查看启动日志
docker compose logs -f

# 确认服务就绪
curl http://localhost:5001/health   # Dify API
curl http://localhost:3000          # Dify Web
```

### 2.4 首次登录

1. 浏览器打开 `http://localhost:3000`
2. 设置管理员账号密码（默认密码配置在环境变量 INIT_PASSWORD 中）
3. 在 Settings → Model Provider 中添加 DeepSeek API Key

---

## 3. Java 工具服务（Dify 的自定义工具）

### 3.1 项目结构

```
java-dify/
├── pom.xml
├── src/main/resources/application.yml
└── src/main/java/com/ai/learning/dify/
    ├── DifyApplication.java          # 启动类
    ├── config/DifyConfig.java        # Dify API 配置
    ├── model/
    │   ├── ToolRequest.java          # 工具请求模型
    │   └── ToolResponse.java         # 工具响应模型
    ├── controller/
    │   ├── ToolController.java       # 工具 API（被 Dify 调用）
    │   └── DifyController.java       # Dify 工作流调用（调 Dify）
    └── service/
        ├── WeatherService.java       # 天气查询
        ├── CalculatorService.java    # 数学计算
        ├── DateTimeService.java      # 日期时间
        ├── MemoryNoteService.java    # 内存笔记
        └── DifyClient.java           # Dify API 客户端
```

### 3.2 工具 API（Dify → Java）

每个工具都是一个 REST POST 端点，接收 JSON `{"query": "...", "params": "..."}`：

```bash
# 查询北京天气
curl -X POST http://localhost:8080/api/tools/weather \
  -H "Content-Type: application/json" \
  -d '{"query":"北京"}'

# 数学计算
curl -X POST http://localhost:8080/api/tools/calculator \
  -H "Content-Type: application/json" \
  -d '{"query":"25*40"}'

# 日期时间
curl -X POST http://localhost:8080/api/tools/datetime \
  -H "Content-Type: application/json" \
  -d '{"query":"现在几点"}'

# 笔记管理
curl -X POST http://localhost:8080/api/tools/note \
  -H "Content-Type: application/json" \
  -d '{"query":"save","params":"memo:Hello Dify"}'
```

### 3.3 OpenAPI 规范用于自动发现

Dify 支持通过 OpenAPI 3.0 规范自动导入工具。访问 `GET /api/tools/openapi.json` 获取：

```json
{
  "openapi": "3.0.0",
  "info": { "title": "Java Tools API", "version": "1.0.0" },
  "paths": {
    "/api/tools/weather": {
      "post": {
        "summary": "查询天气",
        "requestBody": {
          "content": {
            "application/json": {
              "schema": {
                "type": "object",
                "properties": {
                  "query": {"type": "string", "description": "城市名"}
                }
              }
            }
          }
        }
      }
    }
    // ... 更多工具
  }
}
```

### 3.4 启动 Java 服务

```bash
cd code/day29/java-dify
mvn clean compile
mvn spring-boot:run
# 服务运行在 http://localhost:8080
```

---

## 4. Dify 工作流配置（可视化）

### 4.1 创建应用

1. 登录 Dify Web（http://localhost:3000）
2. 点击「创建应用」→ 选择「Chatflow」或「Workflow」
3. 命名应用（如 "Java 编排器"）

### 4.2 添加自定义工具

1. 进入工作流编辑器
2. 点击「工具」→「创建自定义工具」
3. 选择「导入 OpenAPI 规范」
4. 填入 URL：`http://host.docker.internal:8080/api/tools/openapi.json`
   > Docker Linux 下，使用宿主机 IP 而非 `localhost`
5. 保存后即可在工作流中使用天气、计算、日期等工具

### 4.3 编排 RAG 工作流

示例工作流：

```
[开始] → [LLM 节点] → [知识库检索] → [工具调用] → [LLM 汇总] → [结束]
                          ↓                ↓
                     向量检索 Java       Java 天气/计算 API
```

1. **开始节点**：接收用户输入
2. **LLM 节点**：分析用户意图（需要天气？计算？还是知识问答？）
3. **条件分支**：
   - 天气类 → 调用 Java 天气工具
   - 计算类 → 调用 Java 计算工具
   - 知识类 → 检索知识库
4. **LLM 汇总节点**：整合结果返回用户

### 4.4 发布工作流

1. 点击右上角「发布」
2. 在「API 访问」中复制 API Key（`app-xxx` 格式）

---

## 5. Java 调用 Dify（Java → Dify）

### 5.1 更新配置

在 `application.yml` 中填入 Dify API Key：

```yaml
dify:
  api:
    base-url: http://localhost:5001
    api-key: app-xxxxxxxxxxxx   # 从 Dify 复制
```

### 5.2 调用工作流 API

```bash
# 通过 Java 服务调用 Dify 工作流
curl -X POST "http://localhost:8080/api/dify/workflow?query=北京天气怎么样"

# 发送聊天消息
curl -X POST "http://localhost:8080/api/dify/chat?query=今天有什么新闻"
```

### 5.3 DifyClient (Java) 实现核心

```java
@Service
public class DifyClient {
    public Mono<String> runWorkflow(String query, String userId) {
        return webClient.post()
                .uri("/v1/workflows/run")
                .bodyValue(Map.of(
                        "inputs", Map.of("query", query),
                        "response_mode", "blocking",
                        "user", userId
                ))
                .retrieve()
                .bodyToMono(String.class);
    }
}
```

---

## 6. Python 对照版

`code/day29/python/dify_demo.py` 提供与 Java 版对应的 Python 实现：
- `DifyClient` — Python 版 Dify API 客户端
- `JavaToolSimulator` — 模拟工具端点（不依赖 Spring Boot）
- `generate_openapi_spec()` — 生成 OpenAPI 规范

```bash
cd code/day29
python python/dify_demo.py --test tools
```

---

## 7. 关键知识点

### Dify 工作流组件

| 组件 | 说明 |
|------|------|
| **LLM 节点** | 调用大模型，可指定 prompt 模板 |
| **知识库检索** | 从向量库检索相关文档片段 |
| **工具节点** | 调用外部 HTTP API |
| **代码节点** | 执行 Python/JavaScript 代码 |
| **条件分支** | 根据变量值路由到不同分支 |
| **变量聚合** | 合并多个上游的结果 |
| **迭代节点** | 对列表逐项处理 |

### Docker 网络通信

```
Dify (容器内) ──→ host.docker.internal:8080 ──→ Java (宿主机)
Java (宿主机)  ──→ localhost:5001 ──────────→ Dify (容器内)
```

- 容器内访问宿主机：Linux Docker 用 `172.17.0.1`（默认网桥网关）
- 或使用 `--add-host host.docker.internal:host-gateway`

---

## 8. 常见问题

### 8.1 Docker Hub 拉取慢（中国大陆）

```bash
# 使用 DaoCloud 镜像
docker pull m.daocloud.io/docker.io/langgenius/dify-api:1.14.2

# 完成后重新 tag
docker tag m.daocloud.io/docker.io/langgenius/dify-api:1.14.2 langgenius/dify-api:1.14.2
```

### 8.2 Dify 无法访问 Java 服务

Docker 容器内 `localhost:8080` 指向容器自身，不是宿主机：
- Linux：使用 `172.17.0.1:8080`（Docker 网桥网关）
- 或者在 `docker-compose.yml` 中添加 `extra_hosts: - "host.docker.internal:host-gateway"`

### 8.3 Dify 工作流发布后无 API 响应

检查：
1. Dify API Key 是否正确复制到 Java 配置
2. DeepSeek API Key 是否在 Dify Settings 中配置
3. 工作流是否已发布（状态显示绿色）

---

## 9. 本次产出

| 项目 | 文件 | 状态 |
|------|------|------|
| Docker 部署 | `dify/docker-compose.yml` | ✅ |
| Java 工具 API | `java-dify/` (13 文件) | ✅ 编译通过 |
| Dify 客户端 | `DifyClient.java` | ✅ |
| Python 对照 | `python/dify_demo.py` | ✅ |
| 教程文档 | 本文 | ✅ |

**与 Day 27-28 关联**：
- Day 27 的 ReAct 循环 → Dify 工作流可视化替代
- Day 28 的多工具编排 → Dify 工具节点 + 条件分支
- Day 28 的 Agent 记忆 → Dify 内置对话记忆

---

**下节预告**：Day 30 — 多 Agent 协作系统（Java 版 Orchestrator-Worker）
