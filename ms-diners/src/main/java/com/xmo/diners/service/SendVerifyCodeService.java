package com.xmo.diners.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.xmo.commons.constant.RedisKeyConstant;
import com.xmo.commons.utils.AssertUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 发送验证码业务
 *
 * @author 小莫同学
 * @createTime 2023/7/15 11:35
 */
@Service
public class SendVerifyCodeService {

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 根据手机号获取验证码
     * @param phone
     * @return
     */
    public String getCodeByPhone(String phone) {
        String key = RedisKeyConstant.verify_code.getKey() + phone;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 发送验证码
     * @param phone
     */
    public void send(String phone) {
        // 检测非空
        AssertUtil.isNotEmpty(phone, "手机号不能为空");
        // 根据手机号查询是否已生成验证码， 已生成直接返回
        if (checkCodeIsExpired(phone)) {
            return;
        }
        // 生成 6 位随机验证码
        String code = RandomUtil.randomNumbers(6);
        // 调用短信服务发送短信
        // 发送成功， 将 code 保存到 Redis，失效时间为 60s
        String key = RedisKeyConstant.verify_code.getKey() + phone;
        redisTemplate.opsForValue().set(key, code,60, TimeUnit.SECONDS);
    }

    /**
     * 根据手机号查询是否已生成验证码
     *
     * @param phone
     * @return
     */
    private boolean checkCodeIsExpired(String phone) {
        String key = RedisKeyConstant.verify_code.getKey() + phone;
        String code = redisTemplate.opsForValue().get(key);
        //没有生成过
        if (StrUtil.isBlank(code)){
            return false;
        }
        return true;
    }
}
