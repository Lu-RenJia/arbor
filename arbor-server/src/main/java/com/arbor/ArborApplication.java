package com.arbor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Arbor - 启动类
 *
 * @author gwl
 */
@SpringBootApplication
@MapperScan("com.arbor.mapper")
@EnableScheduling
public class ArborApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArborApplication.class, args);
    }
}
