# 数据库结构说明

## 文件说明

### schema.sql
完整的数据库表结构定义文件，包含所有表的最新结构。

**重要表结构：**

#### 1. xianyu_goods_auto_delivery_config（自动发货配置表）
```sql
- id: 主键ID
- xianyu_account_id: 闲鱼账号ID
- xianyu_goods_id: 本地闲鱼商品ID
- xy_goods_id: 闲鱼的商品ID
- delivery_mode: 发货类型（1-文本，2-卡密，3-自定义，4-API）
- auto_delivery_content: 自动发货的文本内容
- auto_delivery_image_url: 发货图片
- api_allocate_url / api_confirm_url / api_return_url: API发货接口
- api_header_value: API发货密钥
- api_request_extras: API附加请求JSON
- api_delivery_template: API发送文案模板，支持 {apiContent}
- auto_confirm_shipment: 自动确认发货开关（0-关闭，1-开启）✨新增
- create_time: 创建时间
- update_time: 更新时间
```

#### 2. xianyu_goods_auto_delivery_record（自动发货记录表）
```sql
- id: 主键ID
- xianyu_account_id: 闲鱼账号ID
- xianyu_goods_id: 本地闲鱼商品ID
- xy_goods_id: 闲鱼的商品ID
- pnm_id: 消息pnmid（防重复）
- buyer_user_id: 买家用户ID
- buyer_user_name: 买家用户名称
- content: 发货消息内容
- state: 自动发货状态（1-成功，0-失败）
- order_id: 订单ID ✨新增
- order_state: 确认发货状态（0-未确认发货，1-已确认发货）✨新增
- create_time: 创建时间
```

### 升级脚本

#### upgrade_add_order_fields_to_delivery_record.sql
为自动发货记录表添加订单相关字段：
- `order_id`: 订单ID
- `order_state`: 确认发货状态

**执行时间：** 2025-01-15

#### upgrade_add_auto_confirm_shipment.sql
为自动发货配置表添加自动确认发货开关：
- `auto_confirm_shipment`: 自动确认发货开关（0-关闭，1-开启）

**执行时间：** 2025-12-23

## 使用说明

### 新数据库初始化
直接执行 `schema.sql` 即可创建完整的数据库结构。

### 现有数据库升级
按照升级脚本的执行时间顺序依次执行：
1. `upgrade_add_order_fields_to_delivery_record.sql`
2. `upgrade_add_auto_confirm_shipment.sql`

## 功能说明

### 自动确认发货功能
当 `auto_confirm_shipment = 1` 时：
1. 买家下单触发自动发货
2. 自动发货成功后，系统自动调用确认收货接口
3. 确认收货成功后，更新 `order_state = 1`

### 字段关系
- `state`: 记录自动发货是否成功
- `order_state`: 记录是否已确认发货
- `order_id`: 关联订单ID，用于确认收货操作
