# rc_yupeng

这是一个内部 HTTP 通知服务的 MVP。业务系统把外部供应商 API 的调用请求提交给本服务，本服务负责持久化、异步投递、失败重试和状态查询。设计目标不是覆盖所有生产场景，而是用尽量小的系统边界说明可靠投递的关键工程判断。

## 1. 问题理解

多个内部业务系统会在关键事件发生后通知外部系统，例如广告平台注册成功回传、CRM 状态更新、库存变更等。不同供应商的 URL、Header、Body 都不同，业务系统不关心外部 API 的响应内容，只希望通知请求能稳定、可靠地送达。

因此这个服务解决的问题是：

- 接收业务系统提交的 HTTP 通知请求。
- 将请求持久化，避免服务重启或 MQ 暂时不可用导致任务丢失。
- 异步调用外部 HTTP API，避免业务系统被外部供应商延迟拖慢。
- 对可恢复失败进行重试，并保留最终失败记录。
- 提供状态查询，方便排查和人工处理。

明确不解决的问题：

- 不做供应商模板管理平台。MVP 中业务方直接提交完整 URL、Header、Body。
- 不解析供应商业务响应。题目说明业务系统不关心外部返回值，MVP 只判断 HTTP 状态码。
- 不保证 exactly-once。外部 HTTP 调用无法真正保证只发生一次，因此本系统选择 at-least-once，并提供 `idempotencyKey` 支持外部幂等。
- 不做权限、多租户、监控 UI、复杂限流和熔断，这些是后续演进内容。

## 2. 架构设计

```text
Business System
      |
      | POST /api/notifications
      v
Notification API
      |
      | transaction
      v
PostgreSQL: notifications + notification_outbox
      |
      | scheduled outbox publisher
      v
RabbitMQ
      |
      | consumer
      v
External Vendor HTTP API
```

核心组件：

- Spring Boot API：接收入站通知请求，返回 `202 Accepted`。
- PostgreSQL `notifications` 表：保存通知请求、状态、尝试次数、错误信息和下一次重试时间。
- PostgreSQL `notification_outbox` 表：保存待发布 MQ 的任务，避免“通知已入库但 MQ 发布失败”造成任务丢失。
- RabbitMQ：作为异步投递队列，解耦 API 写入和外部 HTTP 调用。
- Consumer worker：执行外部 HTTP 调用，并根据结果更新状态。
- Retry scheduler：扫描到期的 `RETRY_SCHEDULED` 任务，重新放入 outbox。

## 3. 投递语义与失败处理

投递语义：**at-least-once**。

原因：

- API 写入数据库后，即使服务在发布 MQ 前宕机，outbox 轮询也能恢复发布。
- RabbitMQ consumer 可能在调用外部 API 成功后、更新本地状态前失败，这时后续可能重复调用外部 API。
- 对 HTTP 外部系统无法可靠实现 exactly-once，所以更现实的做法是 at-least-once + 幂等键。

HTTP 结果处理：

- `2xx`：视为投递成功，状态变为 `SUCCEEDED`。
- `429`、`5xx`、网络错误、超时：视为可重试失败。
- 普通 `4xx`：视为不可重试失败，状态变为 `FAILED`。
- 超过最大重试次数：状态变为 `DEAD`，保留最后错误。

默认重试策略：

- 最大尝试次数：5 次。
- 退避间隔：`1m, 5m, 15m, 1h, 6h`。
- 请求超时：5 秒。

## 4. API

### 创建通知

```bash
curl -i -X POST http://localhost:8080/api/notifications \
  -H 'Content-Type: application/json' \
  -d '{
    "targetUrl": "https://httpbin.org/status/200",
    "method": "POST",
    "headers": {
      "Content-Type": "application/json"
    },
    "body": "{\"event\":\"user_registered\",\"userId\":\"u_123\"}",
    "idempotencyKey": "registration-u_123"
  }'
```

响应：

```json
{
  "notificationId": "3b51e6c6-65a3-41ef-b1e8-a3172690c6fb"
}
```

### 查询通知

```bash
curl http://localhost:8080/api/notifications/{notificationId}
```

响应示例：

```json
{
  "id": "3b51e6c6-65a3-41ef-b1e8-a3172690c6fb",
  "targetUrl": "https://httpbin.org/status/200",
  "method": "POST",
  "status": "SUCCEEDED",
  "attemptCount": 1,
  "nextAttemptAt": null,
  "lastError": null,
  "createdAt": "2026-05-19T11:00:00+08:00",
  "updatedAt": "2026-05-19T11:00:03+08:00"
}
```

## 5. 本地运行

前置条件：

- Java 21
- Maven 3.9+
- Docker Desktop

启动依赖：

```bash
docker compose up -d
```

启动服务：

```bash
mvn spring-boot:run
```

运行测试：

```bash
mvn test
```

RabbitMQ Management UI：

- URL: http://localhost:15672
- Username: `notification`
- Password: `notification`

PostgreSQL：

- JDBC URL: `jdbc:postgresql://localhost:5432/notifications`
- Username: `notification`
- Password: `notification`

## 6. 系统边界

该系统主要解决的问题只有一个：如何可靠调用外部API。

该系统不关注一下问题：

- 该系统不关注外部返回值。因为作业中明确提出无需关注返回值的具体内容，所以我们只需接收状态码即可

- 不保证 exactly-once。外部 HTTP API 无法真正保证只调用一次。网络超时时，供应商可能已经处理了请求，但我们不知道。所以系统选择“至少一次投递”，并通过 idempotencyKey 支持外部系统去重。

- 无需监控后台。这些是生产系统可能需要的，但第一版不是重点。


## 7. 可靠性与失败处理

- 通知投递语义：至少一次
- 在外部系统失败或者长期不可用的情况下，我的处理策略：

外部系统短暂失败时，系统通过指数退避进行有限重试；长期不可用时，任务最终进入 DEAD 状态并保留错误信息，交给人工排查或后续补偿。系统不会无限重试，也不会阻塞业务系统主流程。

但如果我们已经知道是外部供应商的问题而导致我们的API请求长期不可用，则需要采取进一步策略来保护我们的供应商，例如采取熔断措施，更改状态标记为PAUSED等。我们还可以用弹窗的形式来提醒用户例如“当前 CRM 同步服务暂时不可用，我们会稍后自动重试”。但要具体问题具体分析，如果用户注册成功后广告回传失败，就不需要弹窗提示。只有当用户执行立即同步到第三方平台这种显示操作的时候，我们才可以提示“第三方服务调用不可用”。


## 8. 关键取舍

AI选择用Kafka来做消息队列（因为本机配置Kafka）
但我选择 RabbitMQ，而不是 Kafka：

- 该场景是可靠任务投递、ACK、重试和 worker 消费，不是事件流分析。
- RabbitMQ 的队列模型更容易表达“有任务要投递给外部 HTTP API”。
- Kafka 在高吞吐事件日志场景很强，但用它实现 per-message 重试和业务状态查询会增加 MVP 复杂度。


AI选择一开始就引入复杂熔断、限流和动态配置中心
但我选择放弃这些配置的引入：

熔断和限流是未来演进方向，尤其当某个供应商长期不可用时很重要。
而且第一版已经有了很多防治系统崩溃的特性例如：
- 有限重试
- 指数退避
- DEAD 状态
- lastError

我觉得这些特性可以覆盖基础失败处理。完整熔断系统可以放到后续版本。

AI追求exactly-once投递，但我认为如果要实现此功能需HTTP API配合，但这并不是该作业关注的点。因此放弃该方案，选择at-least-once + idempotencyKey

除此之外，AI还建议作出完整的供应链接入模版，以规范所有供应商接入要求，我觉得都是冗余。如果我们之后的业务供应商数量非常多，可以考虑设计接入模版以便于供应商接入。但这是第一版MVP，我更偏向调用系统而非供应商。所以MVP让业务直接提交：
- targetUrl
- method
- headers
- body
先把链路跑通再说

## 7. 未来演进

如果流量或复杂度上升，可以按以下方向演进：

- 首先queue肯定要拆分的，如果供应商很多但还共用一个queue，当某个供应商发生故障的时候，容易造成阻塞。我们可以根据vendor来拆分。

- 加入供应商级限流和熔断
不同供应商承载能力不同。未来可以维护 vendor 配置：
 maxConcurrency
 rateLimitPerMinute
 circuitBreakerStatus
 retryPolicy
假如某个供应商持续失败，则可以进入熔断状态

- 增加监控、告警和可观测性
这是我认为最应该，也是最重要的一个特性。我们要引入Prometheus和Grafana等插件，增加metrics指标，再在代码出容易产生exception且后果严重的区域，增加日志记录功能，错误发生时直接上报Prometheus，便于人工排查，保证整个系统的可靠性与稳定性。

## 8. 项目结构

```text
src/main/java/com/rightcapital/notification
  config/       RabbitMQ, HTTP client, properties
  controller/   REST controllers and exception handling
  dto/          REST request/response DTOs
  domain/       JPA entities and enums
  repository/   Spring Data repositories
  service/      command service and message DTO
  worker/       outbox publisher, consumer, retry scheduler
src/main/resources/db/migration
  V1__create_notification_tables.sql
```
