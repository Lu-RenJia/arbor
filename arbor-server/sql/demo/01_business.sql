-- ============================================================
-- Arbor 演示业务库（电商订单）
-- 用法：在 arbor1 库上执行本脚本（脚本内使用全限定表名）
--   mysql -u root -p arbor1 < arbor-server/sql/demo/01_business.sql
-- 说明：
--   arbor1      —— 演示主库（dev / test 环境使用）
--   arbor_demo  —— 演示生产库（prod 环境通过数据源环境覆写使用）
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `arbor1` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS `arbor_demo` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP TABLE IF EXISTS `arbor1`.`demo_order`;
DROP TABLE IF EXISTS `arbor1`.`demo_product`;
DROP TABLE IF EXISTS `arbor1`.`demo_customer`;
DROP TABLE IF EXISTS `arbor_demo`.`demo_order`;
DROP TABLE IF EXISTS `arbor_demo`.`demo_product`;
DROP TABLE IF EXISTS `arbor_demo`.`demo_customer`;

CREATE TABLE `arbor1`.`demo_customer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_no` VARCHAR(20) NOT NULL COMMENT '客户编号',
  `name` VARCHAR(50) NOT NULL COMMENT '客户名称',
  `city` VARCHAR(50) DEFAULT NULL COMMENT '城市',
  `level` VARCHAR(20) DEFAULT NULL COMMENT '客户等级：VIP/NORMAL',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_demo_customer_no` (`customer_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示-客户表';

CREATE TABLE `arbor1`.`demo_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_no` VARCHAR(20) NOT NULL COMMENT '商品编号',
  `name` VARCHAR(100) NOT NULL COMMENT '商品名称',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '分类',
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单价',
  `stock` INT NOT NULL DEFAULT 0 COMMENT '库存',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_demo_product_no` (`product_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示-商品表';

CREATE TABLE `arbor1`.`demo_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` VARCHAR(30) NOT NULL COMMENT '订单号',
  `customer_id` BIGINT NOT NULL COMMENT '客户ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
  `amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '订单金额',
  `status` VARCHAR(20) NOT NULL COMMENT '状态：PAID/SHIPPED/COMPLETED/CANCELLED',
  `order_date` DATETIME NOT NULL COMMENT '下单时间',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_demo_order_no` (`order_no`),
  KEY `idx_demo_order_status` (`status`),
  KEY `idx_demo_order_date` (`order_date`),
  KEY `idx_demo_order_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示-订单表';

INSERT INTO `arbor1`.`demo_customer` (`id`,`customer_no`,`name`,`city`,`level`) VALUES
(1,'C001','张伟','北京','VIP'),
(2,'C002','李娜','上海','NORMAL'),
(3,'C003','王强','广州','VIP'),
(4,'C004','赵敏','深圳','NORMAL'),
(5,'C005','陈晨','杭州','NORMAL'),
(6,'C006','刘洋','成都','VIP'),
(7,'C007','孙悦','南京','NORMAL'),
(8,'C008','周杰','武汉','NORMAL');

INSERT INTO `arbor1`.`demo_product` (`id`,`product_no`,`name`,`category`,`price`,`stock`) VALUES
(1,'P001','机械键盘','外设',399.00,120),
(2,'P002','无线鼠标','外设',129.00,300),
(3,'P003','27寸显示器','显示设备',1599.00,36),
(4,'P004','USB-C 扩展坞','配件',329.00,88),
(5,'P005','降噪耳机','音频',899.00,54),
(6,'P006','固态硬盘 1TB','存储',599.00,200),
(7,'P007','笔记本支架','配件',89.00,150),
(8,'P008','桌面音箱','音频',449.00,62);

INSERT INTO `arbor1`.`demo_order` (`order_no`,`customer_id`,`product_id`,`quantity`,`amount`,`status`,`order_date`,`remark`) VALUES
('DEMO-2001',1,1,1,399.00,'PAID','2026-09-20 10:12:00','演示订单'),
('DEMO-2002',2,2,2,258.00,'SHIPPED','2026-09-21 14:20:00','演示订单'),
('DEMO-2003',3,3,1,1599.00,'COMPLETED','2026-09-22 09:05:00','演示订单'),
('DEMO-2004',4,4,1,329.00,'PAID','2026-09-23 16:42:00','演示订单'),
('DEMO-2005',5,5,1,899.00,'CANCELLED','2026-09-24 11:30:00','演示订单'),
('DEMO-2006',6,6,1,599.00,'PAID','2026-09-25 08:55:00','演示订单'),
('DEMO-2007',7,7,2,178.00,'SHIPPED','2026-09-26 19:10:00','演示订单'),
('DEMO-2008',8,8,1,449.00,'COMPLETED','2026-09-27 12:00:00','演示订单'),
('DEMO-2009',1,6,1,599.00,'PAID','2026-09-28 09:30:00','演示订单'),
('DEMO-2010',2,4,1,329.00,'COMPLETED','2026-09-29 15:15:00','演示订单'),
('DEMO-2011',3,2,3,387.00,'PAID','2026-09-30 10:45:00','演示订单'),
('DEMO-2012',4,7,1,89.00,'SHIPPED','2026-10-01 11:20:00','演示订单'),
('DEMO-2013',5,8,2,898.00,'PAID','2026-10-02 13:10:00','演示订单'),
('DEMO-2014',6,3,1,1599.00,'COMPLETED','2026-10-03 17:40:00','演示订单'),
('DEMO-2015',7,1,1,399.00,'CANCELLED','2026-10-04 09:50:00','演示订单'),
('DEMO-2016',8,5,1,899.00,'PAID','2026-10-05 14:05:00','演示订单'),
('DEMO-2017',1,2,1,129.00,'SHIPPED','2026-10-06 10:25:00','演示订单'),
('DEMO-2018',3,6,2,1198.00,'PAID','2026-10-07 16:30:00','演示订单'),
('DEMO-2019',6,4,2,658.00,'COMPLETED','2026-10-08 09:15:00','演示订单'),
('DEMO-2020',2,8,1,449.00,'PAID','2026-10-09 18:20:00','演示订单');

-- 生产演示库：结构一致，数据略有差异，用于演示数据源按环境覆写
CREATE TABLE `arbor_demo`.`demo_customer` LIKE `arbor1`.`demo_customer`;
CREATE TABLE `arbor_demo`.`demo_product` LIKE `arbor1`.`demo_product`;
CREATE TABLE `arbor_demo`.`demo_order` LIKE `arbor1`.`demo_order`;

INSERT INTO `arbor_demo`.`demo_customer` SELECT * FROM `arbor1`.`demo_customer`;
INSERT INTO `arbor_demo`.`demo_product` SELECT * FROM `arbor1`.`demo_product`;
INSERT INTO `arbor_demo`.`demo_order` SELECT * FROM `arbor1`.`demo_order` WHERE `order_no` IN ('DEMO-2001','DEMO-2002','DEMO-2003','DEMO-2004','DEMO-2005');
INSERT INTO `arbor_demo`.`demo_order` (`order_no`,`customer_id`,`product_id`,`quantity`,`amount`,`status`,`order_date`,`remark`) VALUES
('DEMO-9001',1,1,1,1.00,'PAID','2026-10-10 10:00:00','prod 环境专属订单');
