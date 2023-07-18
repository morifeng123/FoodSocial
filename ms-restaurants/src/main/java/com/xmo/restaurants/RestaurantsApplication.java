package com.xmo.restaurants;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author 小莫同学
 * @createTime 2023/7/18 16:51
 */
@SpringBootApplication
@MapperScan("com.xmo.restaurants.mapper")
public class RestaurantsApplication {
    public static void main(String[] args) {
        SpringApplication.run(RestaurantsApplication.class, args);
    }
}
