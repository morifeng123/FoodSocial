package com.xmo.diners.controller;

import com.xmo.commons.model.domain.ResultInfo;
import com.xmo.commons.utils.ResultInfoUtil;
import com.xmo.diners.service.SignService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author 小莫同学
 * @createTime 2023/7/17 16:47
 */
@RestController
@RequestMapping("/sign")
public class SignController {

    @Resource
    private SignService signService;
    @Resource
    private HttpServletRequest request;

    /**
     * 获取用户签到情况 默认为当月
     * @param access_token
     * @param dateStr
     * @return
     */
    @GetMapping
    public ResultInfo getSignInfo(String access_token, String dateStr) {
        Map<String, Boolean> signInfo = signService.getSignInfo(access_token, dateStr);
        return ResultInfoUtil.buildSuccess(request.getServletPath(),signInfo);
    }

    /**
     * 签到，可以补签
     *
     * @param access_token
     * @param date         某个日期 yyyy-MM-dd 默认当天
     * @return
     */
    @PostMapping
    public ResultInfo<Integer> sign(String access_token,
                                    @RequestParam(required = false) String date) {

        int count = signService.doSign(access_token, date);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), count);
    }

    /**
     * 获取签到次数 默认当月
     *
     * @param access_token
     * @param date         某个日期 yyyy-MM-dd
     * @return
     */
    @GetMapping("count")
    public ResultInfo<Long> getSignCount(String access_token, String date) {
        Long count = signService.getSignCount(access_token, date);
        return ResultInfoUtil.buildSuccess(request.getServletPath(), count);
    }

}
