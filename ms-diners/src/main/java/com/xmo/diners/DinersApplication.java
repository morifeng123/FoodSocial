package com.xmo.diners;

import org.mapstruct.Mapping;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author 小莫同学
 * @createTime 2023/7/12 15:28
 */
@SpringBootApplication
@MapperScan("com.xmo.diners.mapper")
public class DinersApplication {
    public static void main(String[] args) {
        SpringApplication.run(DinersApplication.class, args);
    }
}
