-- ============================================================
-- Arbor 演示账号 / 角色 / 菜单 / 权限
-- 前置：已执行 02_platform.sql（创建演示广场）
-- 执行：mysql -u root -p arbor1 < arbor-server/sql/demo/03_permissions.sql
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;
REPLACE INTO `permission` (`id`,`code`,`name`,`remark`,`create_time`,`update_time`) VALUES (145,'file:list','文件管理列表','查看文件管理','2026-10-25 21:23:05','2026-10-25 21:23:05');
REPLACE INTO `permission` (`id`,`code`,`name`,`remark`,`create_time`,`update_time`) VALUES (146,'audit:list','审计日志列表','查看操作审计日志','2026-10-25 21:23:05','2026-10-25 21:23:05');
REPLACE INTO `menu` (`id`,`parent_id`,`name`,`path`,`component`,`query`,`is_frame`,`route_name`,`menu_type`,`is_cache`,`status`,`del_flag`,`icon`,`permission_code`,`sort_order`,`visible`,`create_time`,`update_time`) VALUES (45,20,'文件管理','/service/file',NULL,NULL,'0',NULL,'C','0','0','0','FolderOutlined','file:list',29,1,'2026-10-25 21:23:05','2026-10-25 21:23:05');
REPLACE INTO `menu` (`id`,`parent_id`,`name`,`path`,`component`,`query`,`is_frame`,`route_name`,`menu_type`,`is_cache`,`status`,`del_flag`,`icon`,`permission_code`,`sort_order`,`visible`,`create_time`,`update_time`) VALUES (46,30,'操作审计','/service/audit-log',NULL,NULL,'0',NULL,'C','0','0','0','FileSearchOutlined','audit:list',35,1,'2026-10-25 21:23:05','2026-10-25 21:23:05');
REPLACE INTO `role` (`id`,`code`,`role_key`,`name`,`remark`,`role_sort`,`data_scope`,`status`,`del_flag`,`create_time`,`update_time`) VALUES (3,'DEMO_ANALYST','DEMO_ANALYST','演示分析员','演示用角色：只读流程/日志，可查看文件与审计，可执行共享应用',3,'1','0','0','2026-10-25 21:24:15','2026-10-25 21:24:15');
REPLACE INTO `department` (`id`,`name`,`parent_id`,`ancestors`,`remark`,`leader`,`phone`,`email`,`status`,`del_flag`,`sort_order`,`create_time`,`update_time`) VALUES (2,'数据集成部',0,'0','演示部门',NULL,NULL,NULL,'0','0',10,'2026-10-25 21:24:15','2026-10-25 21:24:15');
REPLACE INTO `user` (`id`,`username`,`password`,`nickname`,`email`,`phone`,`sex`,`avatar`,`create_time`,`update_time`,`role`,`department_id`,`status`,`login_ip`,`login_date`,`pwd_update_date`,`del_flag`,`create_by`,`update_by`) VALUES (5,'demo','$2a$10$cBHR0ac/cuOO7oP.9yBhNOIRI8.XZl8ETgOtI2f3tQMqHtRfL7xjq','演示用户',NULL,NULL,NULL,NULL,'2026-10-25 21:24:15','2026-10-25 21:24:15','DEMO_ANALYST',2,'0',NULL,NULL,NULL,'0',NULL,NULL);
REPLACE INTO `environment` (`id`,`name`,`code`,`remark`,`is_default`,`is_deleted`,`deleted_time`,`sort_order`,`user_id`,`create_time`,`update_time`) VALUES (5,'默认环境','default',NULL,1,0,NULL,0,5,'2026-10-25 21:24:15','2026-10-25 21:24:15');

INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (1,145);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (1,146);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (2,145);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (3,1);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (3,10);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (3,20);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (3,120);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (3,124);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (3,144);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (3,145);
INSERT IGNORE INTO `role_permission` (`role_id`,`permission_id`) VALUES (3,146);

INSERT IGNORE INTO `user_role` (`user_id`,`role_id`) VALUES (5,3);
INSERT IGNORE INTO `app_market_user` (`market_id`,`user_id`) VALUES (1,4),(1,5);
SET FOREIGN_KEY_CHECKS=1;