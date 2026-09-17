package com.arbor.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举 — 按模块分段，前端按 code 做分支判断
 * <p>1xxxx=用户/认证, 2xxxx=流程引擎, 3xxxx=全局参数, 4xxxx=数据源,
 * 5xxxx=脚本/接口/Redis, 6xxxx=环境/部门, 9xxxx=系统通用</p>
 *
 * @author gwl
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ====== 用户/认证 (1xxxx) ======
    USER_NOT_FOUND(10001, "用户不存在"),
    USERNAME_EXISTS(10002, "用户名已存在"),
    PASSWORD_REQUIRED(10003, "密码不能为空"),
    LOGIN_FAILED(10004, "用户名或密码错误"),
    ACCOUNT_DISABLED(10005, "账号已被停用，请联系管理员"),
    ADMIN_CANNOT_DELETE(10006, "admin 用户不可删除"),
    USERNAME_REQUIRED(10007, "用户名不能为空"),
    UNAUTHORIZED(10008, "未登录或登录已过期"),
    FORBIDDEN(10009, "无权限访问此资源"),
    OLD_PASSWORD_WRONG(10010, "旧密码不正确"),
    PASSWORD_TOO_SHORT(10011, "密码长度不能少于6位"),

    // ====== 流程引擎 (2xxxx) ======
    FLOW_NOT_FOUND(20001, "流程不存在"),
    FLOW_CLOSED(20002, "流程已关闭，无法自动执行"),
    FLOW_NO_NODES(20003, "流程没有节点，无法执行"),
    FLOW_EXEC_FAILED(20004, "流程执行失败：{0}"),
    FLOW_NO_EXEC_NODES(20005, "流程没有执行节点"),
    FLOW_NO_START_NODE(20021, "流程缺少开始节点"),
    NODE_EXEC_FAILED(20006, "节点执行失败"),
    NODE_EXEC_ERROR(20007, "节点「{0}」({1}) 执行失败: {2}"),
    NODE_EXEC_EXCEPTION(20008, "节点「{0}」({1}) 执行异常: {2}"),
    NODE_TYPE_UNSUPPORTED(20009, "不支持的节点类型: {0}"),
    NODE_RESULT_REF_FAILED(20010, "引用的节点执行失败: {0}"),
    FLOW_PARAM_NOT_SET(20011, "flow_param 未设置，请在开始节点中配置流程入参"),
    PARAM_UNRESOLVABLE(20012, "无法解析参数 $'{'{0}'}'：不是前置节点编码，也不是全局参数{1}"),
    PARAM_FIELD_NOT_FOUND(20013, "属性 \"{0}\" 不存在"),
    PARAM_ARRAY_INDEX_OOB(20014, "数组索引越界: {0}[{1}]，实际长度 {2}"),
    PARAM_ARRAY_NOT_LIST(20015, "无法对 \"{0}\" 使用数组索引，因为它不是数组"),
    PARAM_FIELD_NULL(20016, "无法访问属性 \"{0}\": 值为 null"),
    STOP_CONDITION_EVAL_FAILED(20017, "停止条件评估失败: {0}"),
    STOP_CONDITION_EXPR_FAILED(20018, "节点「{0}」({1}) 停止条件评估失败: {2}"),
    REDIS_GETSET_NO_TTL(20019, "Redis 节点「{0}」的 GET&SET 配置未设置有效过期时间，无法执行分支流程"),
    BRANCH_NODE_FAILED(20020, "分支节点「{0}」({1}) 执行失败: {2}"),
    BRANCH_NODE_EXCEPTION(20021, "分支节点「{0}」({1}) 执行异常: {2}"),
    APPLICATION_NOT_FOUND(20022, "应用不存在"),
    APPLICATION_FLOW_NOT_FOUND(20023, "关联的流程不存在"),
    SUB_FLOW_DEPTH_EXCEEDED(20024, "子流程嵌套深度超过限制({0}层)，请检查是否存在循环引用"),
    SUB_FLOW_EXEC_FAILED(20025, "子流程执行失败: {0}"),
    SCHEDULE_CRON_INVALID(20026, "Cron表达式不合法: {0}"),
    SCHEDULE_NOT_FOUND(20027, "调度配置不存在"),
    SCHEDULE_EXEC_LIMIT_REACHED(20028, "执行次数已达上限，请调整次数限制后再启用"),
    BUILTIN_VAR_NO_FIELD(20029, "内置变量 $'{'{0}'}' 是标量，不支持属性访问或数组下标"),

    // ====== 全局参数 (3xxxx) ======
    PARAM_NAME_EXISTS(30001, "参数名已存在: {0}"),
    PARAM_NOT_FOUND(30002, "参数不存在"),
    GLOBAL_PARAM_MISSING(30003, "全局参数不存在: {0}"),
    VAR_PARAM_MISSING_IN_ENV(30004, "全局参数「{0}」在当前环境未配置值，变量 {1} 无法解析{2}"),

    // ====== 数据源 (4xxxx) ======
    DATASOURCE_NOT_FOUND(40001, "数据源不存在"),
    DATASOURCE_PASSWORD_REQUIRED(40002, "密码不能为空"),

    // ====== 脚本/接口/Redis (5xxxx) ======
    DB_SCRIPT_NOT_FOUND(50001, "数据库脚本不存在"),
    DB_SCRIPT_NO_DATASOURCE(50002, "数据库脚本「{0}」未绑定数据源"),
    DB_SCRIPT_PAGE_INVALID(50003, "页码不能小于1"),
    DB_SCRIPT_PAGE_SIZE_INVALID(50004, "每页大小必须在1-200之间"),
    DB_SCRIPT_EXEC_FAILED(50005, "SQL执行失败: {0}"),
    DML_EXEC_FAILED(50006, "DML执行失败: {0}"),
    PROGRAM_SCRIPT_NOT_FOUND(50007, "程序脚本不存在: {0}"),
    REDIS_SCRIPT_NOT_FOUND(50008, "Redis脚本不存在"),
    REDIS_DATASOURCE_NOT_FOUND(50009, "Redis数据源不存在"),
    REDIS_CMD_UNSUPPORTED(50010, "不支持的Redis命令: {0}"),
    REDIS_TTL_UNIT_UNSUPPORTED(50011, "不支持的TTL单位: {0}"),
    REDIS_GETSET_NO_TTL_VAL(50012, "GET&SET 命令必须设置过期时间(ttl>0)"),
    REDIS_NO_DATASOURCE(50013, "Redis脚本未绑定数据源"),
    API_CONFIG_NOT_FOUND(50014, "接口配置不存在"),
    FLOW_VARIABLE_NOT_FOUND(50015, "变量不存在"),
    FLOW_VARIABLE_REQUIRED(50016, "全局参数、节点和数据源至少选一个"),
    GROOVY_SQL_FAILED(50019, "Groovy SQL执行失败: {0}"),
    FIELD_MAPPING_NOT_FOUND(50020, "字段映射不存在"),
    ENCRYPT_FAILED(50017, "数据加密失败"),
    DECRYPT_FAILED(50018, "数据解密失败"),
    GROOVY_SCRIPT_FAILED(50019, "Groovy脚本执行失败: {0}"),
    GROOVY_SANDBOX_VIOLATION(50022, "脚本包含禁止的操作: {0}。平台沙箱禁止执行系统命令、反射、线程操控、直接文件/网络访问等危险调用"),
    SQL_PARAM_UNSUPPORTED_POSITION(50023, "SQL 模板中 $'{'{0}'}' 所在位置无法参数化：{1}"),
    SQL_PARAM_INVALID_IDENTIFIER(50024, "SQL 模板中 $'{'{0}'}' 处于标识符位置（表名/列名/排序字段），但解析结果 \"{1}\" 不是合法标识符，无法安全内联"),
    SQL_PARAM_MARKER_MISMATCH(50025, "SQL 参数化自检失败：SQL 中占位符 {0} 个，待绑定参数 {1} 个。请检查模板中是否存在多余的 ? 字符"),
    SQL_UNTERMINATED_QUOTE(50026, "SQL 模板第 {1} 个字符处存在未闭合的{0}，请检查引号是否成对"),
    REDIS_PREFIX_NOT_FOUND(50027, "Redis业务前缀不存在"),
    REDIS_PREFIX_DUPLICATE(50028, "该数据源下已存在相同的业务前缀: {0}"),
    REDIS_PREFIX_INVALID(50029, "业务前缀格式非法: {0}"),
    REDIS_PREFIX_IN_USE(50030, "该业务前缀已被 {0} 个流程的 {1} 个节点使用，无法删除"),
    REDIS_SCRIPT_NO_PREFIX(50031, "Redis配置必须选择业务前缀"),
    REDIS_PREFIX_DS_MISMATCH(50032, "业务前缀「{0}」不属于所选数据源"),
    REDIS_PREFIX_REF_BY_SCRIPT(50033, "该业务前缀已被 {0} 个 Redis 配置引用，无法删除"),

    // ====== 环境/部门 (6xxxx) ======
    ENV_NOT_FOUND(60001, "环境不存在"),
    ENV_CODE_EXISTS(60002, "环境代码已存在: {0}"),
    ENV_DEFAULT_CODE_IMMUTABLE(60003, "默认环境的代码不可修改"),
    ENV_DEFAULT_NAME_IMMUTABLE(60004, "默认环境的名称不可修改"),
    ENV_DEFAULT_CANNOT_DELETE(60005, "默认环境不可删除"),
    DEPT_NOT_FOUND(60006, "部门不存在"),
    ENV_CONTEXT_NOT_SET(60007, "当前执行链路未设置环境，内置变量 $'{'{0}'}' 无法解析；请确认执行入口传入了环境（定时任务请检查默认环境）"),

    // ====== RBAC 权限管理 (7xxxx) ======
    ROLE_NOT_FOUND(70001, "角色不存在"),
    ROLE_CODE_EXISTS(70002, "角色编码已存在: {0}"),
    ROLE_CANNOT_DELETE(70003, "系统默认角色不可删除"),
    MENU_NOT_FOUND(70004, "菜单不存在"),
    PERMISSION_NOT_FOUND(70005, "权限不存在"),
    MENU_HAS_CHILDREN(70006, "存在子菜单，不允许删除"),
    MENU_IN_USE(70007, "菜单已被角色分配，不允许删除"),

    // ====== 应用广场 (8xxxx) ======
    MARKET_NOT_FOUND(80001, "应用广场不存在"),
    APP_INPUT_PARAMS_INVALID(80002, "应用入参 JSON 格式无效: {0}"),

    // ====== 系统通用 (9xxxx) ======
    SYSTEM_ERROR(90001, "系统繁忙，请稍后重试"),
    EXEC_LOG_NOT_FOUND(90002, "执行日志不存在"),

    // ====== 导入导出 (91xxx) ======
    IMPORT_PACKAGE_INVALID(91001, "导入包格式无效：{0}"),
    EXPORT_TARGET_NOT_FOUND(91002, "导出目标不存在或无权限"),
    IMPORT_EXEC_FAILED(91003, "导入失败：{0}");

    private final int code;
    private final String message;
}
