package com.xmo.diners.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 小莫同学
 * @createTime 2023/7/12 15:29
 */
@RestController
@RequestMapping("/hello")
public class HelloController {

    @GetMapping
    public String hello(String name) {
        return "hello "+name;
    }

}
