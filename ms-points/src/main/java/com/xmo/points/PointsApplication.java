package com.xmo.points;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author 小莫同学
 * @createTime 2023/7/17 21:38
 */
@SpringBootApplication
@MapperScan("com.xmo.points.mapper")
public class PointsApplication {
    public static void main(String[] args) {
        SpringApplication.run(PointsApplication.class, args);
    }
}
