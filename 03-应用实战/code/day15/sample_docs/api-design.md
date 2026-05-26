# API 设计原则

## RESTful API 基础

### HTTP 方法

| 方法 | 用途 | 幂等 |
|------|------|------|
| GET | 获取资源 | 是 |
| POST | 创建资源 | 否 |
| PUT | 更新资源（全量） | 是 |
| PATCH | 更新资源（部分） | 是 |
| DELETE | 删除资源 | 是 |

### URL 设计规范

- 使用名词复数：`/api/users`
- 层级用斜杠：`/api/users/123/orders`
- 查询参数过滤：`/api/users?role=admin&page=1`
- 版本控制：`/api/v1/users`

### 状态码

```
2xx: 成功
  200 OK
  201 Created
  204 No Content

3xx: 重定向
  301 Moved Permanently
  304 Not Modified

4xx: 客户端错误
  400 Bad Request
  401 Unauthorized
  403 Forbidden
  404 Not Found
  429 Too Many Requests

5xx: 服务端错误
  500 Internal Server Error
  502 Bad Gateway
  503 Service Unavailable
```

## 请求与响应格式

### 统一响应结构

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "id": 123,
        "name": "Alice"
    }
}
```

### 分页响应

```json
{
    "code": 0,
    "data": {
        "items": [...],
        "total": 100,
        "page": 1,
        "page_size": 20
    }
}
```

## 认证方式

- **API Key**: 简单，适合服务端通信
- **JWT**: 无状态，适合分布式系统
- **OAuth 2.0**: 适合第三方授权

## 错误处理最佳实践

1. 永远不要暴露内部实现细节
2. 提供有意义的错误消息
3. 使用统一的错误格式
4. 记录完整的错误栈

## 速率限制

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 85
X-RateLimit-Reset: 1640995200
```
