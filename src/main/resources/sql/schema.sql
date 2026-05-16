-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,              -- 用户名
    password VARCHAR(200) NOT NULL,                    -- 密码（BCrypt加密）
    status TINYINT DEFAULT 1,                          -- 状态 1:正常 0:禁用
    last_login_time DATETIME,                          -- 最后登录时间
    last_login_ip VARCHAR(50),                         -- 最后登录IP
    created_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 创建时间
    updated_time DATETIME DEFAULT (datetime('now', 'localtime'))   -- 更新时间
);

-- 登录Token表
CREATE TABLE IF NOT EXISTS sys_login_token (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL,                           -- 关联用户ID
    token VARCHAR(500) NOT NULL,                       -- JWT Token
    device_id VARCHAR(100),                            -- 设备标识（User-Agent哈希）
    device_name VARCHAR(200),                          -- 设备名称
    browser_name VARCHAR(100),                         -- 浏览器名称
    os_name VARCHAR(100),                              -- 操作系统名称
    user_agent VARCHAR(500),                           -- User-Agent
    login_ip VARCHAR(50),                              -- 登录IP
    expire_time DATETIME NOT NULL,                     -- 过期时间
    last_active_time DATETIME,                         -- 最后活跃时间
    status TINYINT DEFAULT 1,                          -- 状态 1:有效 0:已退出 -1:已踢出
    created_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 创建时间
    updated_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 更新时间
    FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);
CREATE INDEX IF NOT EXISTS idx_sys_login_token_user_id ON sys_login_token(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_login_token_token ON sys_login_token(token);
CREATE INDEX IF NOT EXISTS idx_sys_login_token_expire_time ON sys_login_token(expire_time);
CREATE INDEX IF NOT EXISTS idx_sys_login_token_status ON sys_login_token(status);

-- 触发器
CREATE TRIGGER IF NOT EXISTS update_sys_user_time
AFTER UPDATE ON sys_user
BEGIN
    UPDATE sys_user SET updated_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
CREATE TRIGGER IF NOT EXISTS update_sys_login_token_time
AFTER UPDATE ON sys_login_token
BEGIN
    UPDATE sys_login_token SET updated_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
-- 闲鱼账号表
CREATE TABLE IF NOT EXISTS xianyu_account (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_note VARCHAR(100),                    -- 闲鱼账号备注
    unb VARCHAR(100),                             -- UNB标识
    device_id VARCHAR(100),                       -- 设备ID（UUID格式-用户ID，用于WebSocket连接）
    display_name VARCHAR(200),                    -- 闲鱼昵称
    avatar TEXT,                                  -- 闲鱼头像
    ip_location VARCHAR(100),                     -- IP属地
    introduction TEXT,                            -- 个人简介
    followers VARCHAR(50),                        -- 粉丝数
    following VARCHAR(50),                        -- 关注数
    sold_count INTEGER,                           -- 卖出数
    purchase_count INTEGER,                       -- 买入数
    praise_ratio VARCHAR(50),                     -- 好评率
    review_num INTEGER,                           -- 评价数
    seller_level VARCHAR(50),                     -- 卖家等级
    fish_shop_user TINYINT DEFAULT 0,             -- 是否鱼小铺
    fish_shop_level VARCHAR(50),                  -- 鱼小铺等级
    fish_shop_score INTEGER,                      -- 鱼小铺分数
    profile_updated_time DATETIME,                -- 个人资料同步时间
    profile_refresh_attempt_time DATETIME,        -- 个人资料刷新尝试时间（成功/失败都会记录，用于防重复请求）
    proxy_type VARCHAR(10),                       -- 代理类型: http/https/socks5，NULL表示不使用代理
    proxy_host VARCHAR(200),                      -- 代理主机地址
    proxy_port INTEGER,                           -- 代理端口
    proxy_username VARCHAR(200),                  -- 代理认证用户名
    proxy_password VARCHAR(200),                  -- 代理认证密码
    login_username VARCHAR(200),                  -- 闲鱼登录用户名（手机号/邮箱）
    login_password VARCHAR(500),                  -- 闲鱼登录密码
    status TINYINT DEFAULT 1,                     -- 账号状态 1:正常 -1:需要手机号验证 -2:需要人机验证
    state_reason TEXT,                            -- 状态变更原因
    state_updated_time DATETIME,                  -- 状态变更时间
    created_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 创建时间
    updated_time DATETIME DEFAULT (datetime('now', 'localtime'))   -- 更新时间
);

-- 闲鱼Cookie表
CREATE TABLE IF NOT EXISTS xianyu_cookie (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,            -- 关联的闲鱼账号ID
    cookie_text TEXT,                             -- 完整的Cookie字符串
    m_h5_tk VARCHAR(500),                         -- _m_h5_tk token（用于API签名）
    cookie_status TINYINT DEFAULT 1,              -- Cookie状态 1:有效 2:过期 3:失效
    state_reason TEXT,                            -- Cookie状态变更原因
    state_updated_time DATETIME,                  -- Cookie状态变更时间
    expire_time DATETIME,                         -- 过期时间
    websocket_token TEXT,                         -- WebSocket accessToken
    token_expire_time INTEGER,                    -- Token过期时间戳（毫秒）
    created_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 创建时间
    updated_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 更新时间
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_account_unb ON xianyu_account(unb);
CREATE INDEX IF NOT EXISTS idx_account_status ON xianyu_account(status);
CREATE INDEX IF NOT EXISTS idx_account_state_updated_time ON xianyu_account(state_updated_time);
CREATE INDEX IF NOT EXISTS idx_cookie_account_id ON xianyu_cookie(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_cookie_status ON xianyu_cookie(cookie_status);
CREATE INDEX IF NOT EXISTS idx_cookie_state_updated_time ON xianyu_cookie(state_updated_time);
CREATE INDEX IF NOT EXISTS idx_token_expire_time ON xianyu_cookie(token_expire_time);

-- 创建更新时间触发器（SQLite不支持ON UPDATE CURRENT_TIMESTAMP，需要用触发器）
-- 注意：触发器使用特殊分隔符 $$
CREATE TRIGGER IF NOT EXISTS update_xianyu_account_time 
AFTER UPDATE ON xianyu_account
BEGIN
    UPDATE xianyu_account SET updated_time = datetime('now', 'localtime') WHERE id = NEW.id;
END;
CREATE TRIGGER IF NOT EXISTS update_xianyu_cookie_time 
AFTER UPDATE ON xianyu_cookie
BEGIN
    UPDATE xianyu_cookie SET updated_time = datetime('now', 'localtime') WHERE id = NEW.id;
END;
-- 闲鱼商品信息表
CREATE TABLE IF NOT EXISTS xianyu_goods (
    id BIGINT PRIMARY KEY,                        -- 表ID（使用雪花ID）
    xy_good_id VARCHAR(100) NOT NULL,             -- 闲鱼商品ID
    xianyu_account_id BIGINT,                     -- 关联的闲鱼账号ID
    title VARCHAR(500),                           -- 商品标题
    cover_pic TEXT,                               -- 封面图片URL
    info_pic TEXT,                                -- 商品详情图片（JSON数组）
    detail_info TEXT,                             -- 商品详情信息（预留字段）
    detail_url TEXT,                              -- 商品详情页URL
    sold_price VARCHAR(50),                       -- 商品价格
    quantity INTEGER DEFAULT 1,                   -- 闲鱼库存
    exposure_count INTEGER,                       -- 曝光次数
    view_count INTEGER,                           -- 浏览次数
    want_count INTEGER,                           -- 想要人数
    status TINYINT DEFAULT 0,                     -- 商品状态 0:在售 1:已下架 2:已售出 3:闲鱼已删除
    sort_order INTEGER DEFAULT 2147483647,        -- 闲鱼客户端列表顺序，数值越小越靠前
    created_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 创建时间
    updated_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 更新时间
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建商品表索引
CREATE UNIQUE INDEX IF NOT EXISTS idx_goods_xy_good_id ON xianyu_goods(xy_good_id);
CREATE INDEX IF NOT EXISTS idx_goods_status ON xianyu_goods(status);
CREATE INDEX IF NOT EXISTS idx_goods_account_id ON xianyu_goods(xianyu_account_id);

-- 创建商品表更新时间触发器
CREATE TRIGGER IF NOT EXISTS update_xianyu_goods_time
AFTER UPDATE ON xianyu_goods
BEGIN
    UPDATE xianyu_goods SET updated_time = datetime('now', 'localtime') WHERE id = NEW.id;
END;
-- 闲鱼聊天消息表
CREATE TABLE IF NOT EXISTS xianyu_chat_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    
    -- 关联信息
    xianyu_account_id BIGINT NOT NULL,            -- 关联的闲鱼账号ID
    
    -- WebSocket消息字段
    lwp VARCHAR(50),                              -- websocket消息类型，比如："/s/para"
    pnm_id VARCHAR(100) NOT NULL,                 -- 对应的消息pnmid，比如："3813496236127.PNM"（字段1.3）
    s_id VARCHAR(100),                            -- 消息聊天框id，比如："55435931514@goofish"（字段1.2）
    
    -- 消息内容
    content_type INTEGER,                         -- 消息类别，contentType=1用户消息，32系统消息（字段1.6.3.5中的contentType）
    msg_content TEXT,                             -- 消息内容，对应1.10.reminderContent
    
    -- 发送者信息
    sender_user_name VARCHAR(200),                -- 发送者用户名称，对应1.10.reminderTitle
    sender_user_id VARCHAR(100),                  -- 发送者用户id，对应1.10.senderUserId
    sender_app_v VARCHAR(50),                     -- 发送者app版本，对应1.10._appVersion
    sender_os_type VARCHAR(20),                   -- 发送者系统版本，对应1.10._platform
    
    -- 消息链接
    reminder_url TEXT,                            -- 消息链接，对应1.10.reminderUrl
    xy_goods_id VARCHAR(100),                     -- 闲鱼商品ID，从reminder_url中的itemId参数解析
    
    -- 完整消息体
    complete_msg TEXT NOT NULL,                   -- 完整的消息体JSON
    
    -- 时间信息
    message_time BIGINT,                          -- 消息时间戳（毫秒，字段1.5）
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 创建时间
    
    -- 外键约束
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建聊天消息表索引
CREATE INDEX IF NOT EXISTS idx_chat_message_account_id ON xianyu_chat_message(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_account_time ON xianyu_chat_message(xianyu_account_id, message_time DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_pnm_id ON xianyu_chat_message(pnm_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_s_id ON xianyu_chat_message(s_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_sid_time ON xianyu_chat_message(s_id, message_time DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_sender_user_id ON xianyu_chat_message(sender_user_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_content_type ON xianyu_chat_message(content_type);
CREATE INDEX IF NOT EXISTS idx_chat_message_time ON xianyu_chat_message(message_time);
CREATE INDEX IF NOT EXISTS idx_chat_message_goods_id ON xianyu_chat_message(xy_goods_id);

-- 创建唯一索引，防止重复消息
CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_message_unique 
ON xianyu_chat_message(xianyu_account_id, pnm_id);

-- 在线会话状态表：记录对方是否已读当前账号最近发出的消息
CREATE TABLE IF NOT EXISTS xianyu_conversation_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    s_id VARCHAR(100) NOT NULL,
    read_status INTEGER,                         -- 0-未读，1-已读，NULL-未知
    read_message_id VARCHAR(100),
    read_timestamp BIGINT,
    read_receipt TEXT,
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_conversation_state_unique
ON xianyu_conversation_state(xianyu_account_id, s_id);

CREATE INDEX IF NOT EXISTS idx_conversation_state_account_id
ON xianyu_conversation_state(xianyu_account_id);

-- 商品配置表
CREATE TABLE IF NOT EXISTS xianyu_goods_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,                -- 闲鱼账号ID
    xianyu_goods_id BIGINT,                           -- 本地闲鱼商品ID
    xy_goods_id VARCHAR(100) NOT NULL,                -- 闲鱼的商品ID
    xianyu_auto_delivery_on TINYINT DEFAULT 0,        -- 自动发货开关：1-开启，0-关闭，默认关闭
    xianyu_auto_reply_on TINYINT DEFAULT 0,           -- 自动回复开关：1-开启，0-关闭，默认关闭
    xianyu_auto_reply_context_on TINYINT DEFAULT 1,   -- 携带上下文开关：1-开启，0-关闭，默认开启，跟随自动回复开关
    fixed_material TEXT,                              -- 固定资料（用于AI自动回复）
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),   -- 创建时间
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),   -- 更新时间
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建商品配置表索引
CREATE INDEX IF NOT EXISTS idx_goods_config_account_id ON xianyu_goods_config(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_goods_config_xy_goods_id ON xianyu_goods_config(xy_goods_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_goods_config_unique ON xianyu_goods_config(xianyu_account_id, xy_goods_id);

-- 创建商品配置表更新时间触发器
CREATE TRIGGER IF NOT EXISTS update_xianyu_goods_config_time
AFTER UPDATE ON xianyu_goods_config
BEGIN
    UPDATE xianyu_goods_config SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
-- 商品自动发货配置表
CREATE TABLE IF NOT EXISTS xianyu_goods_auto_delivery_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,                -- 闲鱼账号ID
    xianyu_goods_id BIGINT,                           -- 本地闲鱼商品ID
    xy_goods_id VARCHAR(100) NOT NULL,                -- 闲鱼的商品ID
    delivery_mode TINYINT DEFAULT 1,                  -- 发货模式：1-文本发货，2-卡密发货，3-自定义发货，4-API发货
    rule_name VARCHAR(100),                           -- 规则名称
    match_keyword VARCHAR(500),                       -- 本地匹配关键词，多个用逗号分隔
    match_type TINYINT DEFAULT 1,                     -- 匹配方式：1-任意关键词，2-全部关键词
    priority INTEGER DEFAULT 100,                     -- 规则优先级，数字越小越靠前
    enabled TINYINT DEFAULT 1,                        -- 规则启用状态：0-关闭，1-开启
    stock INTEGER DEFAULT -1,                         -- 本地库存：-1表示不限库存
    stock_warn_threshold INTEGER DEFAULT 0,           -- 库存预警阈值：0表示不预警
    total_delivered INTEGER DEFAULT 0,                -- 累计成功发货次数
    today_delivered INTEGER DEFAULT 0,                -- 今日成功发货次数
    last_delivery_time DATETIME,                      -- 最近一次成功发货时间
    auto_delivery_content TEXT,                       -- 自动发货的文本内容
    kami_config_ids TEXT,                             -- 卡密发货：绑定的卡密配置ID列表（逗号分隔）
    kami_delivery_template TEXT,                      -- 卡密发货文案模板，使用{kmKey}占位符替换卡密内容
    multi_kami_mode TINYINT DEFAULT 1,                -- 多卡密发送模式：1-合并为一条消息，2-分多条消息发送
    auto_delivery_image_url TEXT,                     -- 自动发货图片URL
    post_delivery_text TEXT,                          -- 发货内容发送成功后追加发送的文本
    auto_confirm_shipment TINYINT DEFAULT 0,          -- 自动确认发货开关：0-关闭，1-开启
    delivery_delay_seconds INTEGER DEFAULT 0,         -- 延时发货秒数：0表示立即发货
    trigger_payment_enabled TINYINT DEFAULT 1,        -- 付款消息触发：0-关闭，1-开启
    trigger_bargain_enabled TINYINT DEFAULT 0,        -- 小刀/讲价卡片触发：0-关闭，1-开启
    api_allocate_url TEXT,                            -- API发货：分配/占用接口URL
    api_confirm_url TEXT,                             -- API发货：确认交付接口URL（可选）
    api_return_url TEXT,                              -- API发货：回库接口URL（可选）
    api_header_name VARCHAR(100),                     -- API发货：密钥请求头名称
    api_header_value TEXT,                            -- API发货：密钥请求头值
    api_request_extras TEXT,                          -- API发货：附加自定义请求JSON
    api_delivery_template TEXT,                       -- API发货：最终发送文案模板，支持 {apiContent}
    api_content_path VARCHAR(200) DEFAULT 'data.content',       -- API发货：发货内容响应字段路径
    api_allocation_id_path VARCHAR(200) DEFAULT 'data.allocationId', -- API发货：占用ID响应字段路径
    rag_delay_seconds INTEGER DEFAULT 15,             -- 自动回复延时秒数（RAG回复延时）
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),   -- 创建时间
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),   -- 更新时间
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建自动发货配置表索引
CREATE INDEX IF NOT EXISTS idx_auto_delivery_config_account_id ON xianyu_goods_auto_delivery_config(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_auto_delivery_config_xy_goods_id ON xianyu_goods_auto_delivery_config(xy_goods_id);
CREATE INDEX IF NOT EXISTS idx_auto_delivery_config_account_goods ON xianyu_goods_auto_delivery_config(xianyu_account_id, xy_goods_id);

-- 创建自动发货配置表更新时间触发器
CREATE TRIGGER IF NOT EXISTS update_xianyu_goods_auto_delivery_config_time
AFTER UPDATE ON xianyu_goods_auto_delivery_config
BEGIN
    UPDATE xianyu_goods_auto_delivery_config SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
-- 商品订单表
CREATE TABLE IF NOT EXISTS xianyu_goods_order (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,                -- 闲鱼账号ID
    xianyu_goods_id BIGINT,                           -- 本地闲鱼商品ID
    xy_goods_id VARCHAR(100) NOT NULL,                -- 闲鱼的商品ID
    pnm_id VARCHAR(100) NOT NULL,                     -- 消息pnmid，用于防止重复
    order_id VARCHAR(100),                            -- 订单ID
    buyer_user_id VARCHAR(100),                       -- 买家用户ID
    buyer_user_name VARCHAR(256),                     -- 买家用户名
    sid VARCHAR(200),                                 -- 会话ID（用于WebSocket发送消息的cid）
    content TEXT,                                     -- 发货消息内容
    delivery_mode TINYINT,                            -- 发货模式快照
    rule_name VARCHAR(100),                           -- 发货规则名称快照
    delivery_snapshot TEXT,                           -- 发货配置快照
    external_allocation_id VARCHAR(200),              -- 外部API发货占用ID
    external_confirm_state TINYINT DEFAULT 0,         -- 外部API确认状态：0-未确认，1-已确认，-1-确认失败
    external_return_state TINYINT DEFAULT 0,          -- 外部API回库状态：0-未回库，1-已回库，-1-回库失败
    external_return_reason VARCHAR(500),              -- 外部API回库原因/失败原因
    trigger_source VARCHAR(50),                       -- 触发来源：payment/bargain/manual
    trigger_content TEXT,                             -- 触发消息文案
    state TINYINT DEFAULT 0,                          -- 发货是否成功: 1-成功, 0-待发货, -1-失败
    fail_reason VARCHAR(500),                         -- 失败理由
    confirm_state TINYINT DEFAULT 0,                  -- 确认发货状态: 0-未确认, 1-已确认
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 创建时间(本地时间)
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建订单表索引
CREATE INDEX IF NOT EXISTS idx_goods_order_account_id ON xianyu_goods_order(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_goods_order_xy_goods_id ON xianyu_goods_order(xy_goods_id);
CREATE INDEX IF NOT EXISTS idx_goods_order_state ON xianyu_goods_order(state);
CREATE INDEX IF NOT EXISTS idx_goods_order_create_time ON xianyu_goods_order(create_time);
CREATE INDEX IF NOT EXISTS idx_goods_order_pnm_id ON xianyu_goods_order(pnm_id);
CREATE INDEX IF NOT EXISTS idx_goods_order_order_id ON xianyu_goods_order(order_id);

-- 创建唯一索引，防止同一消息重复
CREATE UNIQUE INDEX IF NOT EXISTS idx_goods_order_unique 
ON xianyu_goods_order(xianyu_account_id, pnm_id);

-- 通知日志表（本地记录，不主动请求闲鱼）
CREATE TABLE IF NOT EXISTS xianyu_notification_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel VARCHAR(50),                              -- 通知渠道：webhook/email/local
    event_type VARCHAR(80),                           -- 事件类型
    title VARCHAR(200),                               -- 标题
    content TEXT,                                     -- 内容
    status TINYINT DEFAULT 0,                         -- 状态：1-成功，0-跳过，-1-失败
    error_message TEXT,                               -- 错误信息
    create_time DATETIME DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX IF NOT EXISTS idx_notification_log_event_type ON xianyu_notification_log(event_type);
CREATE INDEX IF NOT EXISTS idx_notification_log_create_time ON xianyu_notification_log(create_time);

-- 商品擦亮定时任务表
CREATE TABLE IF NOT EXISTS xianyu_item_polish_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,                -- 闲鱼账号ID
    enabled TINYINT DEFAULT 1,                        -- 是否启用：0-关闭，1-开启
    run_hour INTEGER DEFAULT 8,                       -- 每日执行小时：0-23
    random_delay_max_minutes INTEGER DEFAULT 10,      -- 最大随机延迟分钟
    next_run_time DATETIME,                           -- 下次执行时间
    last_run_time DATETIME,                           -- 上次执行时间
    last_result TEXT,                                 -- 上次执行摘要JSON
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_item_polish_task_account_id ON xianyu_item_polish_task(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_item_polish_task_next_run ON xianyu_item_polish_task(enabled, next_run_time);

CREATE TRIGGER IF NOT EXISTS update_xianyu_item_polish_task_time
AFTER UPDATE ON xianyu_item_polish_task
BEGIN
    UPDATE xianyu_item_polish_task SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- 商品自动回复记录表
CREATE TABLE IF NOT EXISTS xianyu_goods_auto_reply_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,                -- 闲鱼账号ID
    xianyu_goods_id BIGINT,                           -- 本地闲鱼商品ID
    xy_goods_id VARCHAR(100) NOT NULL,                -- 闲鱼的商品ID
    s_id VARCHAR(100),                                -- 会话ID（用于延时任务去重）
    pnm_id VARCHAR(100),                              -- 触发回复的消息pnmId
    buyer_user_id VARCHAR(100),                       -- 买家用户ID
    buyer_user_name VARCHAR(200),                     -- 买家用户名
    buyer_message TEXT,                               -- 买家消息内容
    reply_content TEXT,                               -- 回复消息内容
    reply_type TINYINT DEFAULT 1,                     -- 回复类型：1-关键词匹配，2-RAG智能回复
    matched_keyword VARCHAR(200),                     -- 匹配的关键词
    trigger_context TEXT,                             -- 触发上下文JSON（包含触发消息列表和RAG命中资料列表）
    state TINYINT DEFAULT 0,                          -- 状态：0-待回复，1-成功，-1-失败
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),   -- 创建时间
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建自动回复记录表索引
CREATE INDEX IF NOT EXISTS idx_auto_reply_record_account_id ON xianyu_goods_auto_reply_record(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_auto_reply_record_xy_goods_id ON xianyu_goods_auto_reply_record(xy_goods_id);
CREATE INDEX IF NOT EXISTS idx_auto_reply_record_state ON xianyu_goods_auto_reply_record(state);
CREATE INDEX IF NOT EXISTS idx_auto_reply_record_create_time ON xianyu_goods_auto_reply_record(create_time);
CREATE INDEX IF NOT EXISTS idx_auto_reply_record_s_id ON xianyu_goods_auto_reply_record(s_id);
CREATE INDEX IF NOT EXISTS idx_auto_reply_record_pnm_id ON xianyu_goods_auto_reply_record(pnm_id);
-- 创建唯一索引，防止同一会话重复回复（用于延时任务去重）
CREATE UNIQUE INDEX IF NOT EXISTS idx_auto_reply_record_unique 
ON xianyu_goods_auto_reply_record(xianyu_account_id, s_id, pnm_id);

-- 自动回复关键词规则表
CREATE TABLE IF NOT EXISTS xianyu_auto_reply_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,                -- 闲鱼账号ID
    xy_goods_id VARCHAR(100),                         -- 闲鱼商品ID，空表示通用规则
    rule_name VARCHAR(100),                           -- 规则名称
    keywords TEXT,                                    -- 关键词，多个用换行或逗号分隔
    match_type TINYINT DEFAULT 1,                     -- 匹配方式：1-任意关键词，2-全部关键词
    reply_type TINYINT DEFAULT 1,                     -- 回复方式：1-文字，2-图片，3-文字+图片
    reply_content TEXT,                               -- 文字回复内容
    image_urls TEXT,                                  -- 图片URL，多个用换行或逗号分隔
    priority INTEGER DEFAULT 100,                     -- 优先级，数字越小越靠前
    enabled TINYINT DEFAULT 1,                        -- 启用状态：0-关闭，1-开启
    is_default TINYINT DEFAULT 0,                     -- 是否默认回复：0-否，1-是
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX IF NOT EXISTS idx_auto_reply_rule_account_id ON xianyu_auto_reply_rule(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_auto_reply_rule_xy_goods_id ON xianyu_auto_reply_rule(xy_goods_id);
CREATE INDEX IF NOT EXISTS idx_auto_reply_rule_enabled ON xianyu_auto_reply_rule(enabled);
CREATE INDEX IF NOT EXISTS idx_auto_reply_rule_priority ON xianyu_auto_reply_rule(priority);

CREATE TRIGGER IF NOT EXISTS update_xianyu_auto_reply_rule_time
AFTER UPDATE ON xianyu_auto_reply_rule
BEGIN
    UPDATE xianyu_auto_reply_rule SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;


-- 操作日志表
CREATE TABLE IF NOT EXISTS xianyu_operation_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT,                        -- 账号ID
    operation_type VARCHAR(50),                      -- 操作类型
    operation_module VARCHAR(100),                   -- 操作模块
    operation_desc VARCHAR(500),                     -- 操作描述
    operation_status TINYINT,                        -- 操作状态：1-成功，0-失败，2-部分成功
    target_type VARCHAR(50),                         -- 目标类型
    target_id VARCHAR(100),                          -- 目标ID
    request_params TEXT,                             -- 请求参数（JSON格式）
    response_result TEXT,                            -- 响应结果（JSON格式）
    error_message TEXT,                              -- 错误信息
    ip_address VARCHAR(50),                          -- IP地址
    user_agent VARCHAR(500),                         -- 浏览器UA
    duration_ms INTEGER,                             -- 操作耗时（毫秒）
    create_time BIGINT,                              -- 创建时间（时间戳，毫秒）
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建操作日志表索引
CREATE INDEX IF NOT EXISTS idx_operation_log_account_id ON xianyu_operation_log(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_operation_log_type ON xianyu_operation_log(operation_type);
CREATE INDEX IF NOT EXISTS idx_operation_log_status ON xianyu_operation_log(operation_status);
CREATE INDEX IF NOT EXISTS idx_operation_log_create_time ON xianyu_operation_log(create_time);

-- 系统配置表
CREATE TABLE IF NOT EXISTS xianyu_sys_setting (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    setting_key VARCHAR(100) NOT NULL UNIQUE,              -- 配置键
    setting_value TEXT,                                     -- 配置值
    setting_desc VARCHAR(500),                              -- 配置描述
    created_time DATETIME DEFAULT (datetime('now', 'localtime')),  -- 创建时间
    updated_time DATETIME DEFAULT (datetime('now', 'localtime'))   -- 更新时间
);

-- 创建系统配置表索引
CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_setting_key ON xianyu_sys_setting(setting_key);

-- 创建系统配置表更新时间触发器
CREATE TRIGGER IF NOT EXISTS update_xianyu_sys_setting_time
AFTER UPDATE ON xianyu_sys_setting
BEGIN
    UPDATE xianyu_sys_setting SET updated_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
-- 初始化系统配置数据
INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('sys_prompt', '你是一个闲鱼卖家，你叫肥极喵，不要回复的像AI，简短回答
参考相关信息回答,不要乱回答,不知道就换不同语气回复提示用户详细点询问', 'AI智能回复的系统提示词');

-- AI API Key配置（初始为空，用户在前端设置页面配置后生效）
INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('ai_api_key', '', 'AI服务的API Key（配置后立即生效，无需重启）');

-- AI API Base URL配置
INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('ai_base_url', 'https://dashscope.aliyuncs.com/compatible-mode', 'AI服务的API Base URL');

-- AI 模型配置
INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('ai_model', 'deepseek-v3', 'AI对话模型名称');

-- 邮件通知开关配置
INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('email_notify_ws_disconnect_enabled', '0', 'WebSocket断连邮件通知开关：0-关闭，1-开启');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('email_notify_cookie_expire_enabled', '0', 'Cookie过期邮件通知开关：0-关闭，1-开启');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_webhook_enabled', '0', 'Webhook通知开关：0-关闭，1-开启');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_webhook_url', '', 'Webhook通知地址');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_webhook_type', 'generic', 'Webhook通知类型：generic/feishu/dingtalk/wecom');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_channel', 'generic', '当前通知方式');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_dispatch_mode', 'single', '通知发送模式：single-只推送当前方式，all-推送全部已配置方式');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_auto_delivery_success', '0', '自动发货成功通知开关：0-关闭，1-开启');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_auto_delivery_fail', '1', '自动发货失败通知开关：0-关闭，1-开启');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_stock_warning', '1', '库存预警通知开关：0-关闭，1-开启');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_hourly_report_enabled', '0', '自动发货整点本地报表通知开关：0-关闭，1-开启');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('notify_captcha_success_enabled', '0', '人机验证恢复成功通知开关：0-关闭，1-开启');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('goofish_im_embed_enabled', '0', '官方闲鱼IM辅助入口开关：0-关闭，1-开启');

-- 卡密配置表
CREATE TABLE IF NOT EXISTS xianyu_kami_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,                -- 闲鱼账号ID
    alias_name VARCHAR(200),                          -- 别名
    alert_enabled TINYINT DEFAULT 0,                  -- 预警开关：0-关闭，1-开启
    alert_threshold_type TINYINT DEFAULT 1,           -- 预警阈值类型：1-数量，2-百分比
    alert_threshold_value INTEGER DEFAULT 10,         -- 预警阈值数值
    alert_email VARCHAR(200),                         -- 预警接收邮箱
    total_count INTEGER DEFAULT 0,                    -- 卡密总数（冗余计数，方便查询）
    used_count INTEGER DEFAULT 0,                     -- 已使用数量（冗余计数）
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX IF NOT EXISTS idx_kami_config_account_id ON xianyu_kami_config(xianyu_account_id);

CREATE TRIGGER IF NOT EXISTS update_xianyu_kami_config_time
AFTER UPDATE ON xianyu_kami_config
BEGIN
    UPDATE xianyu_kami_config SET update_time = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- 卡密明细表
CREATE TABLE IF NOT EXISTS xianyu_kami_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kami_config_id BIGINT NOT NULL,                   -- 关联卡密配置ID
    kami_content TEXT NOT NULL,                        -- 卡密内容
    status TINYINT DEFAULT 0,                         -- 状态：0-未使用，1-已使用
    order_id VARCHAR(100),                            -- 使用该卡密的订单ID
    used_time DATETIME,                               -- 使用时间
    sort_order INTEGER DEFAULT 0,                     -- 排序号（顺序发货时使用）
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (kami_config_id) REFERENCES xianyu_kami_config(id)
);

CREATE INDEX IF NOT EXISTS idx_kami_item_config_id ON xianyu_kami_item(kami_config_id);
CREATE INDEX IF NOT EXISTS idx_kami_item_status ON xianyu_kami_item(status);
CREATE INDEX IF NOT EXISTS idx_kami_item_config_status ON xianyu_kami_item(kami_config_id, status);

-- 卡密使用记录表
CREATE TABLE IF NOT EXISTS xianyu_kami_usage_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kami_config_id BIGINT NOT NULL,                   -- 关联卡密配置ID
    kami_item_id BIGINT NOT NULL,                     -- 关联卡密明细ID
    xianyu_account_id BIGINT NOT NULL,                -- 闲鱼账号ID
    xy_goods_id VARCHAR(100),                         -- 闲鱼商品ID
    order_id VARCHAR(100),                            -- 订单ID
    buyer_user_id VARCHAR(100),                       -- 买家用户ID
    buyer_user_name VARCHAR(256),                     -- 买家用户名
    kami_content TEXT NOT NULL,                        -- 发出的卡密内容
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (kami_config_id) REFERENCES xianyu_kami_config(id),
    FOREIGN KEY (kami_item_id) REFERENCES xianyu_kami_item(id)
);

CREATE INDEX IF NOT EXISTS idx_kami_usage_config_id ON xianyu_kami_usage_record(kami_config_id);
CREATE INDEX IF NOT EXISTS idx_kami_usage_order_id ON xianyu_kami_usage_record(order_id);
CREATE INDEX IF NOT EXISTS idx_kami_usage_account_id ON xianyu_kami_usage_record(xianyu_account_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_kami_usage_unique ON xianyu_kami_usage_record(kami_item_id, order_id);

-- 媒体库图片素材表
CREATE TABLE IF NOT EXISTS xianyu_media_library (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id INTEGER NOT NULL,                -- 闲鱼账号ID
    file_name VARCHAR(255),                            -- 文件名
    media_url TEXT NOT NULL,                           -- 上传到闲鱼CDN后的图片地址
    file_size INTEGER,                                 -- 原始文件大小
    created_time VARCHAR(30) DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours'))
);

CREATE INDEX IF NOT EXISTS idx_media_library_account_id ON xianyu_media_library(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_media_library_created_time ON xianyu_media_library(created_time);

-- 发布地图点位缓存表
CREATE TABLE IF NOT EXISTS xianyu_poi_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id INTEGER NOT NULL,                -- 闲鱼账号ID
    division_id INTEGER NOT NULL,                      -- 行政区编码
    prov VARCHAR(100) NOT NULL,                        -- 省份
    city VARCHAR(100) NOT NULL,                        -- 城市
    area VARCHAR(100),                                 -- 区县
    poi_id VARCHAR(100) NOT NULL,                      -- 地图POI ID
    poi_name VARCHAR(255) NOT NULL,                    -- 地图点位名称
    gps VARCHAR(100) NOT NULL,                         -- 纬度,经度
    latitude VARCHAR(50),                              -- 纬度
    longitude VARCHAR(50),                             -- 经度
    address TEXT,                                      -- 详细地址
    source VARCHAR(50) DEFAULT 'official_poi',          -- 来源
    is_default TINYINT DEFAULT 0,                      -- 是否账号默认点位
    created_time VARCHAR(30) DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours')),
    updated_time VARCHAR(30) DEFAULT (strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_poi_cache_account_division ON xianyu_poi_cache(xianyu_account_id, division_id);
CREATE INDEX IF NOT EXISTS idx_poi_cache_account_id ON xianyu_poi_cache(xianyu_account_id);

-- 发布商品定时任务表
CREATE TABLE IF NOT EXISTS xianyu_publish_schedule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,
    title VARCHAR(100),
    payload_json TEXT NOT NULL,
    status TINYINT DEFAULT 0,                       -- 0:待发布 1:发布成功 -1:发布失败
    scheduled_time DATETIME NOT NULL,
    executed_time DATETIME,
    item_id VARCHAR(100),
    fail_reason TEXT,
    created_time DATETIME DEFAULT (datetime('now', 'localtime')),
    updated_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

CREATE INDEX IF NOT EXISTS idx_publish_schedule_status_time ON xianyu_publish_schedule(status, scheduled_time);
CREATE INDEX IF NOT EXISTS idx_publish_schedule_account_id ON xianyu_publish_schedule(xianyu_account_id);

CREATE TRIGGER IF NOT EXISTS update_xianyu_publish_schedule_time
AFTER UPDATE ON xianyu_publish_schedule
BEGIN
    UPDATE xianyu_publish_schedule SET updated_time = datetime('now', 'localtime') WHERE id = NEW.id;
END;

-- 闲鱼订单详情表（用于订单管理和数据统计）
CREATE TABLE IF NOT EXISTS xianyu_order (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xianyu_account_id BIGINT NOT NULL,                -- 关联的闲鱼账号ID
    order_id VARCHAR(100) NOT NULL,                   -- 订单ID
    xy_goods_id VARCHAR(100),                         -- 闲鱼商品ID
    goods_title VARCHAR(500),                         -- 商品标题
    buyer_user_id VARCHAR(100),                       -- 买家用户ID
    buyer_user_name VARCHAR(256),                     -- 买家用户名
    seller_user_id VARCHAR(100),                      -- 卖家用户ID
    seller_user_name VARCHAR(256),                    -- 卖家用户名
    order_status INTEGER,                             -- 订单状态：1-待付款，2-待发货，3-已发货，4-已完成，5-已取消
    order_status_text VARCHAR(100),                   -- 订单状态文本
    order_amount BIGINT,                              -- 订单金额（单位：分）
    order_amount_text VARCHAR(50),                    -- 订单金额文本
    pnm_id VARCHAR(100),                              -- 关联的消息pnmid
    s_id VARCHAR(200),                                -- 关联的会话ID
    reminder_url TEXT,                                -- 消息链接
    order_create_time BIGINT,                         -- 订单创建时间戳（毫秒）
    order_pay_time BIGINT,                            -- 订单支付时间戳（毫秒）
    order_delivery_time BIGINT,                       -- 订单发货时间戳（毫秒）
    order_complete_time BIGINT,                       -- 订单完成时间戳（毫秒）
    receiver_name VARCHAR(100),                       -- 收货人姓名
    receiver_phone VARCHAR(50),                       -- 收货人电话
    receiver_address TEXT,                            -- 收货人完整地址
    receiver_city VARCHAR(100),                       -- 收货城市（用于地区统计）
    complete_msg TEXT,                                -- 完整的消息体JSON
    buy_quantity INTEGER DEFAULT 1,                    -- 购买数量
    create_time DATETIME DEFAULT (datetime('now', 'localtime')),
    update_time DATETIME DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY (xianyu_account_id) REFERENCES xianyu_account(id)
);

-- 创建订单详情表索引
CREATE INDEX IF NOT EXISTS idx_xianyu_order_account_id ON xianyu_order(xianyu_account_id);
CREATE INDEX IF NOT EXISTS idx_xianyu_order_order_id ON xianyu_order(order_id);
CREATE INDEX IF NOT EXISTS idx_xianyu_order_xy_goods_id ON xianyu_order(xy_goods_id);
CREATE INDEX IF NOT EXISTS idx_xianyu_order_status ON xianyu_order(order_status);
CREATE INDEX IF NOT EXISTS idx_xianyu_order_create_time ON xianyu_order(order_create_time);
CREATE INDEX IF NOT EXISTS idx_xianyu_order_city ON xianyu_order(receiver_city);

-- 创建唯一索引，防止同一订单重复
CREATE UNIQUE INDEX IF NOT EXISTS idx_xianyu_order_unique
ON xianyu_order(xianyu_account_id, order_id);

-- 创建订单详情表更新时间触发器
CREATE TRIGGER IF NOT EXISTS update_xianyu_order_time
AFTER UPDATE ON xianyu_order
BEGIN
    UPDATE xianyu_order SET update_time = datetime('now', 'localtime') WHERE id = NEW.id;
END;

-- AI 提供商配置表（支持 N 个自定义 OpenAI 兼容提供商）
CREATE TABLE IF NOT EXISTS xianyu_ai_provider (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,                        -- 提供商显示名称
    api_key TEXT NOT NULL DEFAULT '',                  -- API Key
    base_url VARCHAR(500) NOT NULL DEFAULT '',         -- API Base URL
    model VARCHAR(200) NOT NULL DEFAULT '',            -- 默认模型名称
    is_active TINYINT DEFAULT 0,                       -- 是否为当前激活的对话提供商（全局唯一）
    enabled TINYINT DEFAULT 1,                         -- 是否启用
    sort_order INTEGER DEFAULT 100,                    -- 排序（越小越靠前）
    created_time DATETIME DEFAULT (datetime('now', 'localtime')),
    updated_time DATETIME DEFAULT (datetime('now', 'localtime'))
);

CREATE INDEX IF NOT EXISTS idx_ai_provider_active ON xianyu_ai_provider(is_active);
CREATE INDEX IF NOT EXISTS idx_ai_provider_sort ON xianyu_ai_provider(sort_order, id);

CREATE TRIGGER IF NOT EXISTS update_xianyu_ai_provider_time
AFTER UPDATE ON xianyu_ai_provider
BEGIN
    UPDATE xianyu_ai_provider SET updated_time = datetime('now', 'localtime') WHERE id = NEW.id;
END;

-- 补全缺失的系统配置初始化数据
INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('ai_reply_provider', '', 'AI回复使用的服务提供商（已迁移至 xianyu_ai_provider 表）');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('openai_compatible_api_key', '', '通用OpenAI兼容服务的API Key');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('openai_compatible_base_url', '', '通用OpenAI兼容服务的API Base URL');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('openai_compatible_model', '', '通用OpenAI兼容服务的模型名称');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('ai_embedding_api_key', '', 'Embedding模型API Key（留空则使用激活提供商的API Key）');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('ai_embedding_base_url', '', 'Embedding模型API Base URL（留空则使用激活提供商的Base URL）');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('ai_embedding_model', 'text-embedding-v3', 'Embedding模型名称');

INSERT OR IGNORE INTO xianyu_sys_setting (setting_key, setting_value, setting_desc)
VALUES ('delivery_multi_quantity_send_mode', 'merge', '多件订单自动发货发送方式：merge=合并一条，separate=逐条发送');
