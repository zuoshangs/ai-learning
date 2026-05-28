# Day 40 — 工单系统

## 今日任务

| 项目 | 内容 |
|:-----|:------|
| **工单 CRUD** | 创建、查看、分配、解决、关闭 |
| **状态工作流** | PENDING → IN_PROGRESS → RESOLVED → CLOSED |
| **工单统计** | 按状态/优先级/分类汇总 + 平均解决时长 |
| **管理页面** | 工单列表 + 创建 + 详情 + 操作 |
| **产出** | ✅ 完整的工单管理系统 |

## 1. 工单工作流

```
                    ┌──────────┐
                    │ PENDING  │  ← 用户创建/系统自动创建
                    │  待处理   │
                    └────┬─────┘
                    ┌────┴───┐
              ┌─────┤ 分配   ├──────┐
              │     └────────┘      │
              ▼                     ▼
        ┌──────────┐          ┌──────────┐
        │IN_PROGRESS│          │  CLOSED  │  ← 可直接关闭
        │  处理中   │          │  已关闭  │
        └────┬─────┘          └──────────┘
             │
             ▼
        ┌──────────┐
        │ RESOLVED │  ← 需要填写解决方案
        │  已解决  │
        └────┬─────┘
             │
             ▼
        ┌──────────┐
        │  CLOSED  │
        └──────────┘
```

### 状态转换规则

| 当前状态 | 可转换到 |
|:--------|:---------|
| PENDING | IN_PROGRESS, CLOSED |
| IN_PROGRESS | RESOLVED, PENDING |
| RESOLVED | CLOSED, IN_PROGRESS |
| CLOSED | —（不可变更） |

## 2. 核心实现

### 2.1 工单模型（Ticket.java）

```java
public class Ticket {
    private String id;                  // UUID
    private String title;               // 标题
    private String description;         // 描述
    private String category;            // 售后/物流/会员/账号/其他
    private Priority priority;          // LOW / MEDIUM / HIGH / URGENT
    private Status status;              // PENDING / IN_PROGRESS / RESOLVED / CLOSED
    private String creatorName;         // 创建人
    private String assignee;            // 负责人
    private String resolution;          // 解决方案
    private Long resolvedAt;            // 解决时间戳
    // + createdAt, updatedAt
}
```

### 2.2 状态流转（TicketService.java）

```java
private boolean isValidTransition(Status from, Status to) {
    return switch (from) {
        case PENDING -> to == IN_PROGRESS || to == CLOSED;
        case IN_PROGRESS -> to == RESOLVED || to == PENDING;
        case RESOLVED -> to == CLOSED || to == IN_PROGRESS;
        case CLOSED -> false; // 不可变更
    };
}
```

### 2.3 分配自动推进

分配工单时，如果当前是 PENDING 状态自动推进到 IN_PROGRESS：

```java
public Ticket assignTicket(String id, String assignee) {
    Ticket ticket = getTicketOrThrow(id);
    ticket.setAssignee(assignee);
    if (ticket.getStatus() == Status.PENDING) {
        ticket.setStatus(Status.IN_PROGRESS);  // 自动推进
    }
    return ticket;
}
```

### 2.4 统计指标

```java
public class TicketStats {
    public int total, pending, inProgress, resolved, closed;
    public double avgResolutionHours;
    public Map<String, Integer> byPriority;   // 按优先级分布
    public Map<String, Integer> byCategory;   // 按分类分布
}
```

## 3. API 端点

| 端点 | 方法 | 功能 |
|:-----|:----:|:-----|
| `/api/tickets` | GET | 工单列表（支持 `?status=&priority=&category=`） |
| `/api/tickets` | POST | 创建工单 |
| `/api/tickets/{id}` | GET | 工单详情 |
| `/api/tickets/{id}/assign` | PUT | 分配负责人 |
| `/api/tickets/{id}/status` | PUT | 变更状态 |
| `/api/tickets/{id}` | DELETE | 删除工单 |
| `/api/tickets/stats` | GET | 统计概览 |
| `/api/tickets/categories` | GET | 分类 + 枚举值 |
| `/api/tickets/from-chat` | POST | 从客服对话创建工单 |
| `/tickets` | GET | 工单管理页面 |

## 4. 管理界面

工单管理页面功能：

| 功能 | 实现 |
|:-----|:------|
| 统计卡片 | 总计 / 待处理 / 处理中 / 已解决 / 平均解决时长 |
| 筛选栏 | 按状态、优先级、分类 |
| 工单卡片 | 优先级左侧色条 + 状态徽标 + 信息预览 |
| 点击详情 | 模态框展示完整信息 + 操作按钮 |
| 操作按钮 | 开始处理 / 分配 / 标记解决 / 重新打开 / 关闭 / 删除 |
| 创建页面 | 表单填写标题/描述/分类/优先级/创建人 |

## 5. 测试结果

### Java 后端

```
=== Web UI (tickets page) === HTTP 200 ✅

=== Stats ===
Total: 4 | Pending: 2 | InProgress: 1 | Resolved: 1
Avg resolution: 3.0h
ByPriority: 紧急1 / 高1 / 低1 / 中1
ByCategory: 售后1 / 账号1 / 会员1 / 物流1

=== All Tickets ===
  RESOLVED 退货退款申请 (售后, URGENT)
  IN_PROGRESS 无法登录账号 (账号, HIGH)
  PENDING 会员积分问题 (会员, LOW)
  PENDING 物流延迟查询 (物流, MEDIUM)

=== Create & Assign & Resolve ===
  Created: ✅
  Assigned to 管理员, auto → IN_PROGRESS ✅
  Status: RESOLVED, resolution set ✅

=== Final Stats ===
  Total: 5 | Pending: 3 | InProgress: 0 | Resolved: 2
```

### Python Demo

```
Test 1: Create Ticket              ✅
Test 2: Status Workflow            ✅ (4 transitions, CLOSED rejected)
Test 3: Assign Ticket              ✅ (auto IN_PROGRESS)
Test 4: Statistics                 ✅
Test 5: Filters                    ✅
Test 6: Full Workflow              ✅
```

## 6. 文件变更

### 新增

```
day40/
├── cs-platform/src/main/java/com/ai/cs/ticket/
│   ├── Ticket.java                ← 工单模型（enum Status + Priority）
│   ├── TicketService.java         ← 业务逻辑（状态机+统计）
│   └── TicketController.java      ← REST 端点
├── cs-platform/src/main/resources/templates/tickets.html ← 工单管理页
└── python/ticket_demo.py           ← Python 演示
```

### 修改

```
cs-platform/src/main/java/com/ai/cs/admin/WebController.java ← 新增 /tickets 路由
```

## 7. 总结

Day 40 让智能客服平台拥有了工单管理能力：

| 功能 | 状态 |
|:-----|:----:|
| 工单创建/查看 | ✅ |
| 状态工作流（4 状态 + 转换规则） | ✅ |
| 负责人分配 | ✅ |
| 解决方案记录 + 解决时长 | ✅ |
| 统计概览（按状态/分类/优先级） | ✅ |
| 管理页面（浏览+筛选+操作） | ✅ |
| 非法状态转换拒绝 | ✅ |

### 明日 Day 41：管理后台 + LLMOps 集成 📊

- 客服对话、知识库、工单聚合仪表盘
- LLMOps 集成（缓存、限流、监控）
- Docker 化准备
