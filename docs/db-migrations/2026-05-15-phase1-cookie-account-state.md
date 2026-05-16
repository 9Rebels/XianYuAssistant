# Phase 1 Cookie / Account State Migration

本阶段只做加字段灰度，不改旧字段语义。应用代码仍读写 `xianyu_account.status` 和 `xianyu_cookie.cookie_status`，并在状态收敛服务中同步写入 `state_reason` / `state_updated_time`。

代码支持先于 DDL 发布：实体中的灰度字段不参与默认查询；写入时如果旧库还没有新增列，会降级只写旧状态字段。执行本迁移后，新字段开始持久化。

## 执行前检查

```sql
SELECT status, COUNT(*) FROM xianyu_account GROUP BY status;
SELECT cookie_status, COUNT(*) FROM xianyu_cookie GROUP BY cookie_status;
SELECT id, xianyu_account_id, cookie_status FROM xianyu_cookie WHERE cookie_status NOT IN (1, 2, 3) OR cookie_status IS NULL;
SELECT id, status FROM xianyu_account WHERE status NOT IN (-2, -1, 1) OR status IS NULL;
```

`status = -1` 是兼容历史数据的手机号验证状态，本阶段不新增写入路径。

## 灰度步骤

1. 备份 `dbdata/xianyu_assistant.db`。
2. 停应用或确认无写流量后执行 `2026-05-15-phase1-cookie-account-state.sql`。
3. 启动应用，确认账号列表、连接详情、自动发货流程正常。
4. 观察 `xianyu_account.state_updated_time` 与 `xianyu_cookie.state_updated_time` 是否随状态变更更新。
5. 观察 `xianyu_operation_log` 中 Cookie / Account 状态相关记录。

## 回滚

优先恢复执行前备份。SQLite 新版本支持 `DROP COLUMN`，但生产回滚不建议依赖在线删列。
