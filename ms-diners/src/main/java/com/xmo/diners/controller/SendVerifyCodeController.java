package com.xmo.diners.controller;

import com.xmo.commons.model.domain.ResultInfo;
import com.xmo.commons.utils.ResultInfoUtil;
import com.xmo.diners.service.SendVerifyCodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 发送验证码控制层
 * @author 小莫同学
 * @createTime 2023/7/15 11:46
 */
@RestController
public class SendVerifyCodeController {

    @Resource
    private SendVerifyCodeService sendVerifyCodeService;
    @Resource
    private HttpServletRequest request;

    /**
     * 发送验证码
     * @param phone
     * @return
     */
    @GetMapping("send")
    public ResultInfo send(String phone){
        sendVerifyCodeService.send(phone);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),"发送成功");
    }

}
