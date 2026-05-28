# MCP 协议详解与实践指南

> Day 45 · 前沿拓展 · 2026-05-28

---

## 什么是 MCP？

**MCP（Model Context Protocol）** 是 Anthropic 在 2024 年底发布的开放式协议，目标是成为 **AI 世界的 USB-C 接口**。

> 就像 USB-C 让任何设备都能用同一个接口连接显示器、键盘、U 盘，  
> MCP 让任何 LLM 都能用同一个协议连接数据库、文件系统、API 等外部工具。

### 核心思想

```
传统方式（N 个集成）             MCP 方式（1 个协议）
┌──────────┐                  ┌──────────┐
│  LLM A   │──→ API 1️⃣         │  LLM A   │──→ MCP 协议 ──→ 工具 1
│  LLM B   │──→ API 2️⃣         │  LLM B   │──→ MCP 协议 ──→ 工具 2
│  LLM C   │──→ API 3️⃣         │  LLM C   │──→ MCP 协议 ──→ 工具 3
└──────────┘                  └──────────┘
```

---

## MCP 架构

```
┌─────────────────────────────────────┐
│            Host（宿主应用）           │
│  Hermes Agent / Claude Desktop / IDE │
└──────────────┬──────────────────────┘
               │ MCP Protocol (JSON-RPC)
               ▼
┌─────────────────────────────────────┐
│           MCP Server                │
│  ┌─────────┐  ┌─────────┐          │
│  │  Files  │  │ GitHub  │  ...      │
│  └─────────┘  └─────────┘          │
└─────────────────────────────────────┘
```

| 角色 | 说明 | 举例子 |
|:-----|:-----|:-------|
| **Host** | 运行 LLM 的应用 | Hermes Agent、Claude Desktop、VS Code |
| **Server** | 提供工具的资源适配器 | `mcp-server-time`、`mcp-server-filesystem` |
| **Client** | Host 和 Server 之间的连接 | Hermes 内置 MCP Client |

### 传输方式

| 方式 | 说明 | 适用场景 |
|:-----|:-----|:---------|
| **stdio** | 通过 stdin/stdout 通信 | 本地工具，最常用 |
| **Streamable HTTP** | 通过 HTTP 请求通信 | 远程服务，分布式部署 |

---

## 实践 1：Hermes Agent 配置 MCP

Hermes Agent 自带原生 MCP 客户端，配置在 `~/.hermes/config.yaml` 中：

```yaml
mcp_servers:
  time:                          # 服务器名称（自定义）
    command: "uvx"               # 启动命令
    args: ["mcp-server-time"]    # 参数
```

### 支持的 MCP 服务器示例

```yaml
mcp_servers:
  # 时间服务器
  time:
    command: "uvx"
    args: ["mcp-server-time"]

  # 文件系统服务器（访问指定目录）
  filesystem:
    command: "npx"
    args: ["-y", "@modelcontextprotocol/server-filesystem", "/home/zuoshangs/projects"]

  # GitHub 集成
  github:
    command: "npx"
    args: ["-y", "@modelcontextprotocol/server-github"]
    env:
      GITHUB_PERSONAL_ACCESS_TOKEN: "ghp_xxx..."

  # 数据库（PostgreSQL）
  postgres:
    command: "npx"
    args: ["-y", "@anthropic/mcp-server-pg", "postgresql://user:pass@localhost/db"]

  # 远程 HTTP 服务
  company_api:
    url: "https://mcp.mycompany.com/v1/mcp"
    headers:
      Authorization: "Bearer sk-xxx..."
```

### 工作原理

```
Hermes 启动
    │
    ▼
读取 config.yaml → mcp_servers
    │
    ▼
对每个服务器：spawn 子进程 / HTTP 连接
    │
    ▼
调用 list_tools() 发现所有工具
    │
    ▼
注册为 mcp_{server}_{tool} 格式的工具
    │
    ▼
在对话中直接可用
```

**工具命名：** 前缀自动加入，例如 `mcp_filesystem_read_file`、`mcp_time_get_current_time`

---

## 实践 2：Python 实现 MCP Server

### 安装

```bash
pip install mcp
```

### 创建一个简单的计算器 MCP Server

```python
# calc_server.py
from mcp.server import Server, NotificationOptions
from mcp.server.models import InitializationOptions
import mcp.server.stdio
import mcp.types as types


async def main():
    server = Server("calc-server")

    @server.list_tools()
    async def handle_list_tools() -> list[types.Tool]:
        return [
            types.Tool(
                name="add",
                description="两个数相加",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "a": {"type": "number", "description": "加数"},
                        "b": {"type": "number", "description": "被加数"},
                    },
                    "required": ["a", "b"],
                },
            ),
            types.Tool(
                name="multiply",
                description="两个数相乘",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "a": {"type": "number"},
                        "b": {"type": "number"},
                    },
                    "required": ["a", "b"],
                },
            ),
        ]

    @server.call_tool()
    async def handle_call_tool(
        name: str, arguments: dict
    ) -> list[types.TextContent]:
        if name == "add":
            result = arguments["a"] + arguments["b"]
        elif name == "multiply":
            result = arguments["a"] * arguments["b"]
        else:
            raise ValueError(f"Unknown tool: {name}")

        return [types.TextContent(type="text", text=str(result))]

    async with mcp.server.stdio.stdio_server() as (read_stream, write_stream):
        await server.run(
            read_stream,
            write_stream,
            InitializationOptions(
                server_name="calc-server",
                server_version="1.0.0",
            ),
        )


if __name__ == "__main__":
    import asyncio
    asyncio.run(main())
```

**用法：** 在 Hermes config.yaml 中配置：

```yaml
mcp_servers:
  calc:
    command: "python"
    args: ["/path/to/calc_server.py"]
```

### 使用 MCP Client SDK 调用

```python
# client_demo.py — 直接通过 MCP 协议调用
import asyncio
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


async def main():
    # 连接到 MCP Server
    server_params = StdioServerParameters(
        command="python",
        args=["calc_server.py"],
    )

    async with stdio_client(server_params) as (read, write):
        async with ClientSession(read, write) as session:
            # 初始化
            await session.initialize()

            # 列出可用工具
            tools = await session.list_tools()
            print("可用工具：")
            for tool in tools.tools:
                print(f"  - {tool.name}: {tool.description}")

            # 调用工具
            result = await session.call_tool("add", {"a": 3, "b": 5})
            print(f"\n3 + 5 = {result.content[0].text}")

            result = await session.call_tool("multiply", {"a": 4, "b": 7})
            print(f"4 * 7 = {result.content[0].text}")


asyncio.run(main())
```

---

## MCP 在 Java 中的使用

Spring AI 1.0.0-M6 尚未原生集成 MCP，但可以通过 **ToolCallback** 机制间接使用：

```java
// Spring AI 中包装 MCP 工具
@Component
public class McpToolWrapper {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Bean
    public ToolCallback mcpTimeTool() {
        return ToolCallbacks.from("get_current_time", "获取当前时间",
            ToolCallbacks.fromToolSpecification(
                ToolSpecification.builder("get_current_time")
                    .description("获取指定时区的当前时间")
                    .addParameter("timezone", Type.STRING, "时区，如 Asia/Shanghai")
                    .build()
            ),
            request -> {
                String tz = (String) request.getValue("timezone");
                // 这里可以调用本地 MCP Client 或直接调用
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));
                return now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        );
    }
}
```

> 💡 **社区动态：** Spring AI 官方计划在 1.1 版本集成 MCP，届时可直接通过配置声明式接入。

---

## MCP 协议的优势

| 维度 | 传统方式 | MCP 方式 |
|:-----|:---------|:---------|
| **集成数量** | N 个 LLM × M 个工具 | N + M |
| **接口标准** | 每个工具不同的 API | 统一 JSON-RPC 2.0 |
| **热加载** | 需要重启应用 | 动态发现工具列表 |
| **安全** | 每集成单独处理 | 统一权限/环境隔离 |
| **社区生态** | 碎片化 | 快速增长的 MCP 服务器生态 |

---

## 热门 MCP 服务器生态

| 领域 | MCP Server | 安装方式 |
|:-----|:-----------|:---------|
| 🕐 时间 | `mcp-server-time` | `uvx mcp-server-time` |
| 📁 文件系统 | `@modelcontextprotocol/server-filesystem` | `npx -y` |
| 🐙 GitHub | `@modelcontextprotocol/server-github` | `npx -y` |
| 🐘 PostgreSQL | `@anthropic/mcp-server-pg` | `npx -y` |
| 📊 SQLite | `@anthropic/mcp-server-sqlite` | `npx -y` |
| 🌐 Web 抓取 | `@anthropic/mcp-server-web-fetch` | `npx -y` |
| 🔍 搜索 | `@modelcontextprotocol/server-brave-search` | `npx -y` |
| 📝 笔记 | `@modelcontextprotocol/server-obsidian` | `npx -y` |
| 📈 股票 | `@anthropic/mcp-server-stock-price` | `npx -y` |
| 🐍 Python | `@modelcontextprotocol/server-python-interpreter` | `npx -y` |

---

> **一句话总结 MCP：**  
> 让 AI 应用像插 USB 一样接入外部工具，Host 和 Server 各司其职，协议统一、生态共享。
