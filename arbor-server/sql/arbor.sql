
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for api_config
-- ----------------------------
DROP TABLE IF EXISTS `api_config`;
CREATE TABLE `api_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `api_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '接口ID，系统自动生成',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '接口名称',
  `method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '请求方式',
  `url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '接口地址，支持${}全局参数',
  `timeout` int NULL DEFAULT 30 COMMENT '超时时间（秒）',
  `headers` json NULL COMMENT '请求头JSON',
  `params` json NULL COMMENT '请求参数JSON',
  `body_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'none' COMMENT 'Body类型',
  `body_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Body内容，支持${}全局参数',
  `response_example` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '响应示例',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `flow_id` bigint NULL DEFAULT NULL COMMENT '所属流程ID（NULL=全局资源）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `api_id`(`api_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_api_config_uid`(`uid` ASC) USING BTREE,
  INDEX `idx_api_id`(`api_id` ASC) USING BTREE,
  INDEX `idx_api_config_flow_id`(`flow_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '接口配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of api_config
-- ----------------------------

-- ----------------------------
-- Table structure for app_market
-- ----------------------------
DROP TABLE IF EXISTS `app_market`;
CREATE TABLE `app_market`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '广场名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_by` bigint NOT NULL COMMENT '创建者 user_id',
  `update_by` bigint NULL DEFAULT NULL COMMENT '更新者 user_id',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标记',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '应用广场' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_market
-- ----------------------------

-- ----------------------------
-- Table structure for app_market_app
-- ----------------------------
DROP TABLE IF EXISTS `app_market_app`;
CREATE TABLE `app_market_app`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `market_id` bigint NOT NULL COMMENT '广场ID',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `env_id` bigint NULL DEFAULT NULL COMMENT '所有者指定的环境ID',
  `shared_by` bigint NOT NULL COMMENT '共享者 user_id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_market_app_env`(`market_id` ASC, `app_id` ASC, `env_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '广场共享应用' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_market_app
-- ----------------------------

-- ----------------------------
-- Table structure for app_market_user
-- ----------------------------
DROP TABLE IF EXISTS `app_market_user`;
CREATE TABLE `app_market_user`  (
  `market_id` bigint NOT NULL COMMENT '广场ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  PRIMARY KEY (`market_id`, `user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '广场人员' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of app_market_user
-- ----------------------------

-- ----------------------------
-- Table structure for application
-- ----------------------------
DROP TABLE IF EXISTS `application`;
CREATE TABLE `application`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用名称',
  `flow_id` bigint NOT NULL COMMENT '关联的流程ID',
  `env_id` bigint NULL DEFAULT NULL COMMENT '执行环境ID，NULL=默认环境',
  `input_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '入参JSON',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_application_uid`(`uid` ASC) USING BTREE,
  INDEX `idx_flow_id`(`flow_id` ASC) USING BTREE,
  INDEX `idx_user_id_app`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '应用管理表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of application
-- ----------------------------

-- ----------------------------
-- Table structure for audit_log
-- ----------------------------
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NULL DEFAULT NULL COMMENT '操作人ID（登录场景可能为空）',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作人用户名',
  `module_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务模块，如 数据源/用户/角色',
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作：CREATE/UPDATE/DELETE/LOGIN/LOGIN_FAILED/LOGOUT/IMPORT/EXPORT',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作描述',
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '请求参数快照（敏感字段已脱敏）',
  `method` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'HTTP 方法',
  `uri` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '请求路径',
  `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源IP',
  `success` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否成功',
  `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_audit_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_audit_username`(`username` ASC) USING BTREE,
  INDEX `idx_audit_module_name`(`module_name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作审计日志' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of audit_log
-- ----------------------------
INSERT INTO `audit_log` VALUES (1, NULL, 'admin', '认证', 'LOGIN', '登录成功', NULL, 'POST', '/api/auth/login', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (2, NULL, 'admin', '全局参数', 'CREATE', '全局参数CREATE', '{\"arg1\":{\"paramName\":\"test_param1\",\"paramValue\":\"\",\"remark\":\"\"}}', 'POST', '/api/global-param', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (3, NULL, 'admin', '环境', 'CREATE', '环境CREATE', '{\"arg1\":{\"name\":\"开发环境\",\"code\":\"dev\",\"remark\":\"\",\"sortOrder\":0}}', 'POST', '/api/environment', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (4, NULL, 'admin', '环境', 'CREATE', '环境CREATE', '{\"arg1\":{\"name\":\"测试环境\",\"code\":\"test\",\"remark\":\"\",\"sortOrder\":0}}', 'POST', '/api/environment', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (5, NULL, 'admin', '环境', 'CREATE', '环境CREATE', '{\"arg1\":{\"name\":\"生产环境\",\"code\":\"prod\",\"remark\":\"\",\"sortOrder\":0}}', 'POST', '/api/environment', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (6, NULL, 'admin', '全局参数', 'UPDATE', '保存环境参数值', '{\"arg1\":1,\"arg2\":2,\"arg3\":{\"paramValue\":\"我是开发环境的值\"}}', 'PUT', '/api/global-param/values/1/2', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (7, NULL, 'admin', '全局参数', 'UPDATE', '保存环境参数值', '{\"arg1\":1,\"arg2\":3,\"arg3\":{\"paramValue\":\"我是测试环境的值\"}}', 'PUT', '/api/global-param/values/1/3', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (8, NULL, 'admin', '全局参数', 'UPDATE', '保存环境参数值', '{\"arg1\":1,\"arg2\":4,\"arg3\":{\"paramValue\":\"我是【生产环境】的值\"}}', 'PUT', '/api/global-param/values/1/4', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (9, NULL, 'admin', '全局参数', 'UPDATE', '保存环境参数值', '{\"arg1\":1,\"arg2\":3,\"arg3\":{\"paramValue\":\"我是【测试环境】的值\"}}', 'PUT', '/api/global-param/values/1/3', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (10, NULL, 'admin', '全局参数', 'UPDATE', '保存环境参数值', '{\"arg1\":1,\"arg2\":2,\"arg3\":{\"paramValue\":\"我是【开发环境】的值\"}}', 'PUT', '/api/global-param/values/1/2', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');
INSERT INTO `audit_log` VALUES (11, NULL, 'admin', '全局参数', 'UPDATE', '保存环境参数值', '{\"arg1\":1,\"arg2\":3,\"arg3\":{\"paramValue\":\"我是【测试环境】的值2\"}}', 'PUT', '/api/global-param/values/1/3', '0:0:0:0:0:0:0:1', 1, NULL, '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for datasource
-- ----------------------------
DROP TABLE IF EXISTS `datasource`;
CREATE TABLE `datasource`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据源名称',
  `db_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据源类型：MYSQL/ORACLE',
  `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据源地址，支持${}全局参数',
  `port` int NOT NULL COMMENT '端口',
  `database_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '验证数据库名，支持${}全局参数',
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名，支持${}全局参数',
  `password` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码（加密），支持${}全局参数',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `pool_min_idle` int NULL DEFAULT NULL COMMENT '连接池最小空闲连接',
  `pool_max_size` int NULL DEFAULT NULL COMMENT '连接池最大连接数',
  `pool_idle_timeout_sec` int NULL DEFAULT NULL COMMENT '空闲超时(秒)',
  `pool_max_lifetime_sec` int NULL DEFAULT NULL COMMENT '连接最大存活时间(秒)',
  `pool_conn_timeout_sec` int NULL DEFAULT NULL COMMENT '获取连接超时(秒)',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_datasource_uid`(`uid` ASC) USING BTREE,
  INDEX `idx_user_id_ds`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据源配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of datasource
-- ----------------------------

-- ----------------------------
-- Table structure for datasource_env_config
-- ----------------------------
DROP TABLE IF EXISTS `datasource_env_config`;
CREATE TABLE `datasource_env_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `datasource_id` bigint NOT NULL COMMENT 'datasource.id',
  `env_id` bigint NOT NULL COMMENT 'environment.id',
  `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '该环境下的主机地址',
  `port` int NULL DEFAULT NULL COMMENT '该环境下的端口',
  `database_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '该环境下的数据库名',
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '该环境下的用户名',
  `password` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '该环境下的密码(AES加密)',
  `pool_min_idle` int NULL DEFAULT NULL COMMENT '连接池最小空闲连接',
  `pool_max_size` int NULL DEFAULT NULL COMMENT '连接池最大连接数',
  `pool_idle_timeout_sec` int NULL DEFAULT NULL COMMENT '空闲超时(秒)',
  `pool_max_lifetime_sec` int NULL DEFAULT NULL COMMENT '连接最大存活时间(秒)',
  `pool_conn_timeout_sec` int NULL DEFAULT NULL COMMENT '获取连接超时(秒)',
  `user_id` bigint NOT NULL DEFAULT 1 COMMENT '用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_ds_env`(`datasource_id` ASC, `env_id` ASC) USING BTREE,
  INDEX `idx_env_id`(`env_id` ASC) USING BTREE,
  INDEX `idx_datasource_id`(`datasource_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据源-环境配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of datasource_env_config
-- ----------------------------

-- ----------------------------
-- Table structure for db_script
-- ----------------------------
DROP TABLE IF EXISTS `db_script`;
CREATE TABLE `db_script`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '脚本名称',
  `datasource_id` bigint NULL DEFAULT NULL COMMENT '关联的数据源ID',
  `sql_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'SQL语句，支持${}全局参数',
  `return_limit` int NOT NULL DEFAULT 10 COMMENT '返回条数，默认10，范围1-200',
  `query_timeout` int NULL DEFAULT NULL COMMENT '查询超时（秒）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `flow_id` bigint NULL DEFAULT NULL COMMENT '所属流程ID（NULL=全局资源）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_db_script_uid`(`uid` ASC) USING BTREE,
  INDEX `idx_db_script_flow_id`(`flow_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据库脚本配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of db_script
-- ----------------------------

-- ----------------------------
-- Table structure for department
-- ----------------------------
DROP TABLE IF EXISTS `department`;
CREATE TABLE `department`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '部门名称',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '上级部门ID，0=顶级',
  `ancestors` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '祖级列表(逗号分隔)',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `leader` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '负责人',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标记(0正常 2删除)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '部门表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of department
-- ----------------------------
INSERT INTO `department` VALUES (1, '开发', 0, '0', '', NULL, NULL, NULL, '0', '0', 0, '2026-07-05 20:20:20', '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for environment
-- ----------------------------
DROP TABLE IF EXISTS `environment`;
CREATE TABLE `environment`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '环境名称',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '环境代码',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认环境',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除标志',
  `deleted_time` datetime NULL DEFAULT NULL COMMENT '删除时间',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `user_id` bigint NOT NULL DEFAULT 1 COMMENT '用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_code`(`user_id` ASC, `code` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '环境配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of environment
-- ----------------------------
INSERT INTO `environment` VALUES (1, '默认环境', 'default', NULL, 1, 0, NULL, 0, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `environment` VALUES (2, '开发环境', 'dev', '', 0, 0, NULL, 0, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `environment` VALUES (3, '测试环境', 'test', '', 0, 0, NULL, 0, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `environment` VALUES (4, '生产环境', 'prod', '', 0, 0, NULL, 0, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for execution_log
-- ----------------------------
DROP TABLE IF EXISTS `execution_log`;
CREATE TABLE `execution_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '流程ID',
  `env_id` bigint NULL DEFAULT NULL COMMENT '执行时的环境ID',
  `flow_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程名称',
  `execute_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '执行方式：AUTO/MANUAL',
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '日志来源: FLOW_TEST/APP/FLOW_MANUAL/FLOW_AUTO',
  `owner_id` bigint NULL DEFAULT NULL COMMENT '所属人(流程/应用创建者)',
  `executor_id` bigint NULL DEFAULT NULL COMMENT '执行人(实际触发者)',
  `status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态：SUCCESS/FAIL',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `duration` bigint NULL DEFAULT NULL COMMENT '耗时（毫秒）',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  `stop_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '停止类型：NORMAL=正常停止, ABNORMAL=异常停止',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  `app_id` bigint NULL DEFAULT NULL COMMENT '关联应用ID',
  `flow_snapshot_id` bigint NULL DEFAULT NULL COMMENT '流程快照ID',
  `input_params` json NULL COMMENT '调用入参',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_id`(`flow_id` ASC) USING BTREE,
  INDEX `idx_start_time`(`start_time` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_source`(`source` ASC) USING BTREE,
  INDEX `idx_owner_id`(`owner_id` ASC) USING BTREE,
  INDEX `idx_executor_id`(`executor_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '流程执行日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of execution_log
-- ----------------------------
INSERT INTO `execution_log` VALUES (1, 1, NULL, '新流程', 'MANUAL', 'FLOW_TEST', 1, 1, 'SUCCESS', '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1136, NULL, NULL, '2026-07-05 20:20:20', 1, NULL, NULL, NULL);
INSERT INTO `execution_log` VALUES (2, 1, NULL, '新流程', 'MANUAL', 'FLOW_TEST', 1, 1, 'SUCCESS', '2026-07-05 20:20:20', '2026-07-05 20:20:20', 7, NULL, NULL, '2026-07-05 20:20:20', 1, NULL, NULL, NULL);
INSERT INTO `execution_log` VALUES (3, 1, NULL, '新流程', 'MANUAL', 'FLOW_TEST', 1, 1, 'SUCCESS', '2026-07-05 20:20:20', '2026-07-05 20:20:20', 28, NULL, NULL, '2026-07-05 20:20:20', 1, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for execution_node_log
-- ----------------------------
DROP TABLE IF EXISTS `execution_node_log`;
CREATE TABLE `execution_node_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `execution_log_id` bigint NOT NULL COMMENT '所属执行日志ID',
  `node_id` bigint NULL DEFAULT NULL COMMENT '节点定义ID',
  `node_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点编码',
  `node_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点名称',
  `node_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点类型',
  `sort_order` int NOT NULL COMMENT '执行顺序',
  `status` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态：SUCCESS/FAIL/SKIP',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `duration` bigint NULL DEFAULT NULL COMMENT '耗时（毫秒）',
  `input_params` json NULL COMMENT '输入参数',
  `output_result` json NULL COMMENT '输出结果',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_execution_log_id`(`execution_log_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '节点执行日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of execution_node_log
-- ----------------------------
INSERT INTO `execution_node_log` VALUES (1, 1, NULL, 'node1', '程序节点', 'REF_PROGRAM_SCRIPT', 1, 'SUCCESS', NULL, NULL, 1136, NULL, NULL, NULL, '2026-07-05 20:20:20', 0);
INSERT INTO `execution_node_log` VALUES (2, 2, NULL, 'node1', '程序节点', 'REF_PROGRAM_SCRIPT', 1, 'SUCCESS', NULL, NULL, 8, NULL, NULL, NULL, '2026-07-05 20:20:20', 0);
INSERT INTO `execution_node_log` VALUES (3, 3, NULL, 'node1', '程序节点', 'REF_PROGRAM_SCRIPT', 1, 'SUCCESS', NULL, NULL, 27, NULL, NULL, NULL, '2026-07-05 20:20:20', 0);

-- ----------------------------
-- Table structure for execution_script_log
-- ----------------------------
DROP TABLE IF EXISTS `execution_script_log`;
CREATE TABLE `execution_script_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `execution_log_id` bigint NOT NULL COMMENT '所属执行日志ID',
  `node_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节点编码',
  `node_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '节点名称',
  `log_level` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '级别：DEBUG/INFO/WARN/ERROR',
  `logger` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'logger 名（= 节点名称）',
  `log_time` datetime(3) NOT NULL COMMENT '打印时间（毫秒精度）',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '日志内容（占位符已替换，单条上限 1000 字符）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '本次执行内的顺序（1-based）',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID（保留列，不参与租户过滤）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_execution_log_id`(`execution_log_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '脚本执行日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of execution_script_log
-- ----------------------------

-- ----------------------------
-- Table structure for field_mapping
-- ----------------------------
DROP TABLE IF EXISTS `field_mapping`;
CREATE TABLE `field_mapping`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '关联流程ID',
  `field_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '英文字段名（JSON key）',
  `field_label` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '中文字段名（展示列头）',
  `mapping_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '字典值映射 JSON，如 {\"1\":\"苹果\",\"2\":\"橘子\"}',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `is_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用 1=是 0=否',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_id`(`flow_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字段映射表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of field_mapping
-- ----------------------------

-- ----------------------------
-- Table structure for file_resource
-- ----------------------------
DROP TABLE IF EXISTS `file_resource`;
CREATE TABLE `file_resource`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '所属流程ID',
  `file_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '相对存储路径',
  `file_size` bigint NOT NULL COMMENT '文件字节数',
  `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'MIME类型',
  `file_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'EXAMPLE' COMMENT '文件类型：EXAMPLE=示例文件',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `user_id` bigint NULL DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_id`(`flow_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文件资源表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of file_resource
-- ----------------------------

-- ----------------------------
-- Table structure for flow
-- ----------------------------
DROP TABLE IF EXISTS `flow`;
CREATE TABLE `flow`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程描述',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0关闭 1启用',
  `node_count` int NULL DEFAULT 0 COMMENT '节点数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_uid`(`uid` ASC) USING BTREE,
  INDEX `idx_user_id_flow`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '流程定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of flow
-- ----------------------------
INSERT INTO `flow` VALUES (1, '新流程', '', 0, 2, '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1, '22ddc4b3-f092-4f67-b249-f2f6c2c76909');

-- ----------------------------
-- Table structure for flow_node
-- ----------------------------
DROP TABLE IF EXISTS `flow_node`;
CREATE TABLE `flow_node`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '所属流程ID',
  `node_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点编码，流程内唯一',
  `node_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点名称',
  `node_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点类型：DB_QUERY/GROOVY_SCRIPT/API_SEND/REF_DB_SCRIPT/REF_PROGRAM_SCRIPT/REF_API',
  `sort_order` int NOT NULL COMMENT '执行顺序',
  `config` json NOT NULL COMMENT '节点配置JSON',
  `example_output` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '示例输出结果，JSON格式',
  `branch_of` bigint NULL DEFAULT NULL COMMENT '分支父节点ID(flow_node.id)，NULL=主线',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_node_code`(`flow_id` ASC, `node_code` ASC) USING BTREE,
  INDEX `idx_flow_id`(`flow_id` ASC) USING BTREE,
  INDEX `idx_branch_of`(`branch_of` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '流程节点定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of flow_node
-- ----------------------------
INSERT INTO `flow_node` VALUES (2, 1, 'start', '开始', 'START', 1, '{\"outputType\": \"JSON\"}', NULL, NULL, '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1);
INSERT INTO `flow_node` VALUES (3, 1, 'node1', '程序节点', 'REF_PROGRAM_SCRIPT', 2, '{\"resourceMode\": \"inline\", \"programScriptId\": \"PSB70FC59FFC43\"}', NULL, NULL, '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1);

-- ----------------------------
-- Table structure for flow_node_param
-- ----------------------------
DROP TABLE IF EXISTS `flow_node_param`;
CREATE TABLE `flow_node_param`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_node_id` bigint NOT NULL COMMENT '所属流程节点ID',
  `param_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '参数名',
  `param_source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源类型：GLOBAL/NODE',
  `source_node_id` bigint NULL DEFAULT NULL COMMENT '来源节点ID',
  `param_expression` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '参数表达式',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_node_id`(`flow_node_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '节点参数引用表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of flow_node_param
-- ----------------------------

-- ----------------------------
-- Table structure for flow_node_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `flow_node_snapshot`;
CREATE TABLE `flow_node_snapshot`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `snapshot_id` bigint NOT NULL COMMENT 'FK flow_snapshot.id',
  `node_id` bigint NOT NULL COMMENT '原 flow_node.id',
  `flow_id` bigint NOT NULL COMMENT '所属流程ID',
  `node_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点编码',
  `node_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点名称',
  `node_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '节点类型',
  `sort_order` int NOT NULL COMMENT '执行顺序',
  `config` json NOT NULL COMMENT '节点配置JSON',
  `branch_of` bigint NULL DEFAULT NULL COMMENT '分支父节点ID',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_snapshot_id`(`snapshot_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '节点快照' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of flow_node_snapshot
-- ----------------------------
INSERT INTO `flow_node_snapshot` VALUES (1, 1, 2, 1, 'start', '开始', 'START', 1, '{\"outputType\": \"JSON\"}', NULL);
INSERT INTO `flow_node_snapshot` VALUES (2, 1, 3, 1, 'node1', '程序节点', 'REF_PROGRAM_SCRIPT', 2, '{\"resourceMode\": \"inline\", \"programScriptId\": \"PSB70FC59FFC43\"}', NULL);

-- ----------------------------
-- Table structure for flow_schedule
-- ----------------------------
DROP TABLE IF EXISTS `flow_schedule`;
CREATE TABLE `flow_schedule`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_id` bigint NOT NULL COMMENT '关联流程ID',
  `cron_expr` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_520_ci NOT NULL COMMENT 'Cron表达式（Spring 6位：秒 分 时 日 月 周）',
  `enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否启用：0-停用 1-启用',
  `max_exec_count` int NULL DEFAULT NULL COMMENT '执行次数上限，NULL=不限',
  `exec_count` int NOT NULL DEFAULT 0 COMMENT '已执行次数',
  `last_exec_time` datetime NULL DEFAULT NULL COMMENT '上次执行时间',
  `next_exec_time` datetime NULL DEFAULT NULL COMMENT '下次执行时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_flow_id`(`flow_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_520_ci COMMENT = '流程定时调度配置' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of flow_schedule
-- ----------------------------

-- ----------------------------
-- Table structure for flow_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `flow_snapshot`;
CREATE TABLE `flow_snapshot`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '原流程ID',
  `version` int NOT NULL DEFAULT 1 COMMENT '版本号',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '流程名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '流程描述',
  `status` tinyint NULL DEFAULT NULL COMMENT '状态(0关闭 1启用)',
  `node_count` int NULL DEFAULT NULL COMMENT '节点数量',
  `snapshot_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_flow_id`(`flow_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '流程快照' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of flow_snapshot
-- ----------------------------
INSERT INTO `flow_snapshot` VALUES (1, 1, 1, '新流程', '', 0, 1, '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for flow_variable
-- ----------------------------
DROP TABLE IF EXISTS `flow_variable`;
CREATE TABLE `flow_variable`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `flow_id` bigint NOT NULL COMMENT '所属流程ID',
  `var_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '变量名',
  `param_id` bigint NULL DEFAULT NULL COMMENT '关联全局参数ID',
  `node_id` bigint NULL DEFAULT NULL COMMENT '关联节点ID',
  `node_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联节点编码（稳定标识）',
  `user_id` bigint NOT NULL DEFAULT 1 COMMENT '用户ID',
  `datasource_id` bigint NULL DEFAULT NULL COMMENT '关联数据源ID（与param_id/node_id三选一）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_var`(`flow_id` ASC, `var_name` ASC) USING BTREE,
  INDEX `idx_flow`(`flow_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '流程变量表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of flow_variable
-- ----------------------------
INSERT INTO `flow_variable` VALUES (1, 1, 'preParam', 1, NULL, NULL, 1, NULL, '2026-07-05 20:20:20', '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for global_param
-- ----------------------------
DROP TABLE IF EXISTS `global_param`;
CREATE TABLE `global_param`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `param_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '参数名，如 global_tunnelAreaIds',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_param`(`user_id` ASC, `param_name` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '全局参数表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of global_param
-- ----------------------------
INSERT INTO `global_param` VALUES (1, 'global_test_param1', '', '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1);

-- ----------------------------
-- Table structure for global_param_value
-- ----------------------------
DROP TABLE IF EXISTS `global_param_value`;
CREATE TABLE `global_param_value`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `param_id` bigint NOT NULL COMMENT 'global_param.id',
  `env_id` bigint NOT NULL COMMENT 'environment.id',
  `param_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '该环境下的参数值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_param_env`(`param_id` ASC, `env_id` ASC) USING BTREE,
  INDEX `idx_env_id`(`env_id` ASC) USING BTREE,
  INDEX `idx_param_id`(`param_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '全局参数-环境值表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of global_param_value
-- ----------------------------
INSERT INTO `global_param_value` VALUES (1, 1, 2, '我是【开发环境】的值', '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1);
INSERT INTO `global_param_value` VALUES (2, 1, 3, '我是【测试环境】的值2', '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1);
INSERT INTO `global_param_value` VALUES (3, 1, 4, '我是【生产环境】的值', '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1);

-- ----------------------------
-- Table structure for menu
-- ----------------------------
DROP TABLE IF EXISTS `menu`;
CREATE TABLE `menu`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父菜单ID，0=顶级',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '菜单名称',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由路径，目录可为空',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由参数',
  `is_frame` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '是否外链(0否 1是)',
  `route_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '路由名称(Vue3去重)',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '菜单类型(M目录 C菜单 F按钮)',
  `is_cache` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '是否缓存(0否 1是)',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标记(0正常 2删除)',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
  `permission_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联权限码，NULL=纯目录',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `visible` tinyint NOT NULL DEFAULT 1 COMMENT '是否可见',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 45 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of menu
-- ----------------------------
INSERT INTO `menu` VALUES (1, 0, '首页看板', '/dashboard', NULL, NULL, '0', NULL, 'C', '0', '0', '0', 'HomeOutlined', 'dashboard:view', 0, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (10, 0, '流程管理', NULL, NULL, NULL, '0', NULL, 'M', '0', '0', '0', 'ApartmentOutlined', NULL, 10, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (11, 10, '流程列表', '/flow/list', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'flow:list', 11, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (12, 10, '执行日志', '/flow/log', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'log:view', 12, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (20, 0, '数据管理', NULL, NULL, NULL, '0', NULL, 'M', '0', '0', '0', 'DatabaseOutlined', NULL, 20, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (21, 20, '全局参数', '/service/param', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'param:list', 21, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (22, 20, '数据源配置', '/service/datasource', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'datasource:list', 22, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (23, 20, '数据库脚本', '/service/db-script', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'db-script:list', 23, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (24, 20, '程序脚本', '/service/program-script', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'program-script:list', 24, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (25, 20, '接口配置', '/service/api-config', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'api-config:list', 25, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (26, 20, 'Redis数据源', '/service/redis-datasource', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'redis:list', 26, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (27, 20, 'Redis配置', '/service/redis-script', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'redis:list', 27, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (28, 20, '环境管理', '/service/environment', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'environment:list', 28, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (30, 0, '系统管理', NULL, NULL, NULL, '0', NULL, 'M', '0', '0', '0', 'SettingOutlined', NULL, 30, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (31, 30, '用户管理', '/service/user', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'user:list', 31, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (32, 30, '部门管理', '/service/department', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'department:list', 32, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (33, 30, '角色管理', '/service/role', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'role:list', 33, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (34, 30, '菜单管理', '/service/menu', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'menu:list', 34, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (40, 0, '应用管理', NULL, NULL, NULL, '0', NULL, 'M', '0', '0', '0', 'AppstoreOutlined', NULL, 40, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (41, 40, '我的应用', '/app/my', NULL, NULL, '0', NULL, 'C', '0', '0', '0', NULL, 'app:my:list', 41, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (42, 40, '应用广场', '/app/market', '', '', '0', '', 'C', '0', '0', '0', '', 'app:market:list', 42, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `menu` VALUES (44, 20, '工具类', '/service/tool-class', NULL, NULL, '0', NULL, 'C', '0', '0', '0', 'CodeOutlined', 'program-script:list', 25, 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for permission
-- ----------------------------
DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限码',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 145 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '权限表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of permission
-- ----------------------------
INSERT INTO `permission` VALUES (1, 'dashboard:view', '查看仪表盘', '首页看板', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (10, 'flow:list', '流程列表', '查看流程列表', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (11, 'flow:create', '创建流程', '新建流程', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (12, 'flow:update', '修改流程', '编辑流程配置', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (13, 'flow:delete', '删除流程', '删除流程', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (14, 'flow:execute', '执行流程', '手动执行流程', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (20, 'log:view', '查看执行日志', '查看数据执行日志', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (30, 'param:list', '参数列表', '查看全局参数', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (31, 'param:create', '创建参数', '新建全局参数', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (32, 'param:update', '修改参数', '编辑全局参数', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (33, 'param:delete', '删除参数', '删除全局参数', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (40, 'datasource:list', '数据源列表', '查看数据源配置', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (41, 'datasource:create', '创建数据源', '新建数据源', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (42, 'datasource:update', '修改数据源', '编辑数据源', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (43, 'datasource:delete', '删除数据源', '删除数据源', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (50, 'db-script:list', 'DB脚本列表', '查看数据库脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (51, 'db-script:create', '创建DB脚本', '新建数据库脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (52, 'db-script:update', '修改DB脚本', '编辑数据库脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (53, 'db-script:delete', '删除DB脚本', '删除数据库脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (54, 'db-script:execute', '执行DB脚本', '执行数据库脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (60, 'program-script:list', '程序脚本列表', '查看程序脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (61, 'program-script:create', '创建程序脚本', '新建程序脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (62, 'program-script:update', '修改程序脚本', '编辑程序脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (63, 'program-script:delete', '删除程序脚本', '删除程序脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (64, 'program-script:execute', '执行程序脚本', '执行程序脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (70, 'api-config:list', '接口配置列表', '查看接口配置', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (71, 'api-config:create', '创建接口配置', '新建接口配置', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (72, 'api-config:update', '修改接口配置', '编辑接口配置', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (73, 'api-config:delete', '删除接口配置', '删除接口配置', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (80, 'redis:list', 'Redis列表', '查看Redis数据源和脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (81, 'redis:create', '创建Redis', '新建Redis数据源/脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (82, 'redis:update', '修改Redis', '编辑Redis数据源/脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (83, 'redis:delete', '删除Redis', '删除Redis数据源/脚本', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (90, 'environment:list', '环境列表', '查看环境配置', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (91, 'environment:create', '创建环境', '新建环境', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (92, 'environment:update', '修改环境', '编辑环境', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (93, 'environment:delete', '删除环境', '删除环境', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (100, 'department:list', '部门列表', '查看部门', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (101, 'department:create', '创建部门', '新建部门', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (102, 'department:update', '修改部门', '编辑部门', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (103, 'department:delete', '删除部门', '删除部门', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (110, 'user:list', '用户列表', '查看用户', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (111, 'user:create', '创建用户', '新建用户', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (112, 'user:update', '修改用户', '编辑用户信息及角色', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (113, 'user:delete', '删除用户', '删除用户', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (114, 'user:reset-password', '重置密码', '重置用户密码', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (120, 'app:my:list', '我的应用列表', '查看我的应用', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (121, 'app:create', '创建应用', '新建应用', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (122, 'app:update', '修改应用', '编辑应用', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (123, 'app:delete', '删除应用', '删除应用', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (124, 'app:execute', '执行应用', '执行应用', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (130, 'role:list', '角色列表', '查看角色及权限', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (131, 'role:create', '创建角色', '新建角色', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (132, 'role:update', '修改角色', '编辑角色', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (133, 'role:delete', '删除角色', '删除角色', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (140, 'menu:list', '菜单列表', '查看菜单配置', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (141, 'menu:create', '创建菜单', '新建菜单项', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (142, 'menu:update', '修改菜单', '编辑菜单项', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (143, 'menu:delete', '删除菜单', '删除菜单项', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `permission` VALUES (144, 'app:market:list', '应用广场列表', '应用广场查看权限', '2026-07-05 20:20:20', '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for program_script
-- ----------------------------
DROP TABLE IF EXISTS `program_script`;
CREATE TABLE `program_script`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `script_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '脚本ID，系统自动生成',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '脚本名称',
  `script_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Groovy脚本，支持${}全局参数',
  `query_timeout` int NULL DEFAULT NULL COMMENT '查询超时（秒）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `flow_id` bigint NULL DEFAULT NULL COMMENT '所属流程ID（NULL=全局资源）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '所属用户ID',
  `is_tool` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为工具类: 0=普通脚本, 1=工具类',
  `tool_class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工具类类名（is_tool=1时有效）',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `script_id`(`script_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_program_script_uid`(`uid` ASC) USING BTREE,
  UNIQUE INDEX `uk_tool_class_name`(`tool_class_name` ASC) USING BTREE,
  INDEX `idx_script_id`(`script_id` ASC) USING BTREE,
  INDEX `idx_program_script_flow_id`(`flow_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '程序脚本配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of program_script
-- ----------------------------
INSERT INTO `program_script` VALUES (1, 'PSB70FC59FFC43', '测试脚本', 'import cn.hutool.core.util.StrUtil\nimport com.arbor.groovy.util.JsonUtil\nimport com.arbor.groovy.util.DateUtil\nimport com.arbor.groovy.util.DataTransformUtil\n\n/**\n * 脚本入口方法（固定签名，框架自动调用）\n * @param params 全局参数 Map<String, String>\n * @return 执行结果，任意类型\n */\ndef execute(params) {\n    // 获取全局参数示例：params.get(\"global_xxx\") 或直接使用变量名 global_xxx\n    // 可用工具类：dateUtil / jsonUtil / dataUtil / hutool / log\n    // 打日志：log.info(\"处理{}条\", n) / log.debug / log.warn / log.error(\"出错了\", e)\n    //   日志会展示在设计器「日志」页签与执行历史详情，请勿打印密码/密钥等敏感信息\n\n    def result = [\n        status: \"success\",\n        timestamp: dateUtil.now(),\n        message: \"Hello Arbor\",\n        preParam: preParam,\n        paramCount: params?.size() ?: 0\n    ]\n    return result\n}', NULL, '', 1, '2026-07-05 20:20:20', '2026-07-05 20:20:20', 1, 0, NULL, '5d0d064f-5ddd-4aba-98f9-fb723cef7cf6');

-- ----------------------------
-- Table structure for redis_datasource
-- ----------------------------
DROP TABLE IF EXISTS `redis_datasource`;
CREATE TABLE `redis_datasource`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据源名称',
  `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Redis主机地址',
  `port` int NOT NULL DEFAULT 6379 COMMENT 'Redis端口',
  `password` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Redis密码(AES加密)',
  `db` int NULL DEFAULT 0 COMMENT 'Redis数据库编号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_redis_datasource_uid`(`uid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Redis数据源配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of redis_datasource
-- ----------------------------

-- ----------------------------
-- Table structure for redis_key_prefix
-- ----------------------------
DROP TABLE IF EXISTS `redis_key_prefix`;
CREATE TABLE `redis_key_prefix`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `datasource_id` bigint NOT NULL COMMENT '所属Redis数据源ID',
  `prefix` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务前缀（静态，不支持${}，可含冒号）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '前缀名称（便于识别，如 订单业务）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_redis_prefix_ds`(`datasource_id` ASC, `prefix` ASC) USING BTREE,
  UNIQUE INDEX `uk_redis_key_prefix_uid`(`uid` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Redis业务前缀表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of redis_key_prefix
-- ----------------------------

-- ----------------------------
-- Table structure for redis_script
-- ----------------------------
DROP TABLE IF EXISTS `redis_script`;
CREATE TABLE `redis_script`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '脚本名称',
  `flow_id` bigint NULL DEFAULT NULL COMMENT '所属流程ID（NULL=全局资源，非空=内联资源）',
  `datasource_id` bigint NULL DEFAULT NULL COMMENT '关联Redis数据源ID',
  `prefix_id` bigint NOT NULL COMMENT '关联业务前缀ID（redis_key_prefix.id）',
  `command` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'GET' COMMENT 'Redis命令(GET/SET/DEL等)',
  `redis_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Redis Key',
  `redis_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'Redis Value',
  `ttl` int NULL DEFAULT NULL COMMENT '过期时间',
  `ttl_unit` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SECONDS' COMMENT 'TTL单位: SECONDS/MINUTES/HOURS/DAYS',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `uid` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '全局唯一标识（导入导出匹配用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_redis_script_uid`(`uid` ASC) USING BTREE,
  INDEX `idx_redis_script_flow_id`(`flow_id` ASC) USING BTREE,
  INDEX `idx_redis_script_prefix_id`(`prefix_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Redis脚本配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of redis_script
-- ----------------------------

-- ----------------------------
-- Table structure for role
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色编码',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '角色权限标识(如admin)',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `role_sort` int NULL DEFAULT 0 COMMENT '排序',
  `data_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '1' COMMENT '数据范围(1全部 2自定义 3本部门 4本部门及以下 5本人)',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标记(0正常 2删除)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of role
-- ----------------------------
INSERT INTO `role` VALUES (1, 'ADMIN', 'ADMIN', '管理员', '系统管理员，拥有全部权限', 1, '1', '0', '0', '2026-07-05 20:20:20', '2026-07-05 20:20:20');
INSERT INTO `role` VALUES (2, 'USER', 'USER', '普通用户', '普通用户，拥有基本查看和执行权限', 2, '1', '0', '0', '2026-07-05 20:20:20', '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for role_permission
-- ----------------------------
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_perm`(`role_id` ASC, `permission_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 94 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-权限关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of role_permission
-- ----------------------------
INSERT INTO `role_permission` VALUES (1, 1, 71, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (2, 1, 73, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (3, 1, 70, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (4, 1, 72, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (5, 1, 121, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (6, 1, 123, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (7, 1, 124, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (8, 1, 120, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (9, 1, 122, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (10, 1, 1, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (11, 1, 41, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (12, 1, 43, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (13, 1, 40, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (14, 1, 42, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (15, 1, 51, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (16, 1, 53, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (17, 1, 54, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (18, 1, 50, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (19, 1, 52, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (20, 1, 101, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (21, 1, 103, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (22, 1, 100, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (23, 1, 102, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (24, 1, 91, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (25, 1, 93, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (26, 1, 90, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (27, 1, 92, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (28, 1, 11, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (29, 1, 13, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (30, 1, 14, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (31, 1, 10, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (32, 1, 12, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (33, 1, 20, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (34, 1, 141, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (35, 1, 143, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (36, 1, 140, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (37, 1, 142, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (38, 1, 31, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (39, 1, 33, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (40, 1, 30, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (41, 1, 32, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (42, 1, 61, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (43, 1, 63, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (44, 1, 64, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (45, 1, 60, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (46, 1, 62, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (47, 1, 81, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (48, 1, 83, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (49, 1, 80, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (50, 1, 82, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (51, 1, 131, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (52, 1, 133, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (53, 1, 130, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (54, 1, 132, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (55, 1, 111, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (56, 1, 113, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (57, 1, 110, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (58, 1, 114, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (59, 1, 112, '2026-07-05 20:20:20');
INSERT INTO `role_permission` VALUES (93, 1, 144, '2026-07-05 20:20:20');

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码(BCrypt加密)',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '手机号',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '2' COMMENT '性别(0男 1女 2未知)',
  `avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '头像地址',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'USER' COMMENT '角色',
  `department_id` bigint NULL DEFAULT NULL COMMENT '部门ID',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '状态(0正常 1停用)',
  `login_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '最后登录IP',
  `login_date` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime NULL DEFAULT NULL COMMENT '密码最后修改时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标记(0正常 2删除)',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建者',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新者',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'admin', '$2a$10$Rlk7Jx3IX2k7lCw.CjCepeovXhchxlQ3m64.VM9Q/M3mraCnAhAAm', '管理员', NULL, NULL, '2', NULL, '2026-07-05 20:20:20', '2026-07-05 20:20:20', 'ADMIN', NULL, '0', NULL, NULL, '2026-07-05 20:20:20', '0', NULL, NULL);
INSERT INTO `user` VALUES (4, 'test', '$2a$10$Wy2u0LH35TfB/fvqD.mkaO7MJzVKTPR08IuHyDOXb.Rie3Eiov.Oy', 'test', NULL, NULL, '2', NULL, '2026-07-05 20:20:20', '2026-07-05 20:20:20', 'USER', 1, '0', NULL, NULL, NULL, '0', NULL, NULL);

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户-角色关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user_role
-- ----------------------------
INSERT INTO `user_role` VALUES (1, 1);
INSERT INTO `user_role` VALUES (4, 2);

SET FOREIGN_KEY_CHECKS = 1;
