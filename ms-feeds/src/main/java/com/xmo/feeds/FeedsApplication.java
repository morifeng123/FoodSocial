package com.xmo.feeds;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author 小莫同学
 * @createTime 2023/7/16 21:05
 */
@SpringBootApplication
@MapperScan("com.xmo.feeds.mapper")
public class FeedsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeedsApplication.class, args);
    }
}
