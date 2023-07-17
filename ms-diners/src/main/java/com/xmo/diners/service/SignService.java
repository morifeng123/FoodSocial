package com.xmo.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.xmo.commons.constant.ApiConstant;
import com.xmo.commons.exception.ParameterException;
import com.xmo.commons.model.domain.ResultInfo;
import com.xmo.commons.model.vo.SignInDinerInfo;
import com.xmo.commons.utils.AssertUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 签到业务逻辑层
 *
 * @author 小莫同学
 * @createTime 2023/7/17 16:03
 */
@Service
public class SignService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate redisTemplate;

    public Map<String, Boolean> getSignInfo(String accessToken, String dateStr) {
        // 获取登录用户
        SignInDinerInfo dinerInfo = loadSignInDinersInfo(accessToken);
        // 获取日期
        Date date = getDate(dateStr);
        // 构建Key
        String signKey = buildSignKey(dinerInfo.getId(), date);
        // 构建一个自动排序的map
        Map<String, Boolean> signInfo = new TreeMap<>();
        // 获取某月的总天数 (考虑闰年)
        int dayOfMonth = DateUtil.lengthOfMonth(DateUtil.month(date) + 1,
                DateUtil.isLeapYear(DateUtil.dayOfYear(date)));
        // bitfield user:sign:5:202307 u30 0
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return signInfo;
        }
        long v = list.get(0) == null ? 0 : list.get(0);
        // 由低位到高位进行遍历，为 0 表示未签，为 1 表示已签
        for (int i = dayOfMonth; i > 0; i--) {
            // 获取日期时间，比如 i = 31，最终拿到 yyyyMM31
            LocalDateTime dateTime = LocalDateTimeUtil.of(date).withDayOfMonth(i);
            boolean flag = v >> 1 << 1 != v;
            signInfo.put(DateUtil.format(dateTime, "yyyy-MM-dd"), flag);
            v >>= 1;
        }
        return signInfo;
    }

    /**
     * 获取用户签到次数
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public long getSignCount(String accessToken, String dateStr) {
        // 获取登录用户
        SignInDinerInfo dinerInfo = loadSignInDinersInfo(accessToken);
        // 获取日期
        Date date = getDate(dateStr);
        // 构建Key
        String signKey = buildSignKey(dinerInfo.getId(), date);
        // e.g. BITCOUNT user:sign:5:202307
        return (Long) redisTemplate.execute(
                (RedisCallback<Long>) con -> con.bitCount(signKey.getBytes())
        );
    }

    /**
     * 用户签到
     *
     * @param accessToken
     * @param dateStr
     * @return
     */
    public int doSign(String accessToken, String dateStr) {
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinersInfo(accessToken);
        // 获取日期
        Date date = getDate(dateStr);
        // 获取日期对应的天数， 多少号
        int offset = DateUtil.dayOfMonth(date) - 1;
        //构建Key
        String signKey = buildSignKey(dinerInfo.getId(), date);
        // 查看是否已签到
        Boolean isSign = redisTemplate.opsForValue().getBit(signKey, offset);
        AssertUtil.isTrue(isSign, "当前日期已签到，无需再签");
        // 签到
        redisTemplate.opsForValue().setBit(signKey, offset, true);
        // 统计连续签到的次数
        int count = getContinuousSignCount(dinerInfo.getId(), date);
        return count;
    }

    /**
     * 统计连续签到的次数
     *
     * @param dinerId
     * @param date
     * @return
     */
    private int getContinuousSignCount(Integer dinerId, Date date) {
        // 获取日期对应的天数， 多少号
        int dayOfMonth = DateUtil.dayOfMonth(date);
        // 构建key
        String signKey = buildSignKey(dinerId, date);
        BitFieldSubCommands bitFieldSubCommands = BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                .valueAt(0);
        List<Long> list = redisTemplate.opsForValue().bitField(signKey, bitFieldSubCommands);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        long v = list.get(0) == null ? 0 : list.get(0);
        int signCount = 0;    //连续签到的次数
        for (int i = dayOfMonth; i > 0; i--) {
            // 右移再左移，如果等于自己说明最低位是 0，表示未签到
            if (v >> 1 << 1 == v) {
                // 低位 0 且非当天说明连续签到中断了
                if (i != dayOfMonth) break;
            } else {
                // 右移一位并重新赋值，相当于把最低位丢弃一位
                signCount++;
            }
            v >>= 1;
        }
        return signCount;
    }

    /**
     * 构建Key
     *
     * @param dinerId
     * @param date
     * @return
     */
    private String buildSignKey(Integer dinerId, Date date) {
        return String.format("user:sign:%d:%s", dinerId,
                DateUtil.format(date, "yyyyMM"));
    }

    /**
     * 获取日期
     *
     * @param dateStr
     * @return
     */
    private Date getDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) {
            return new Date();
        }
        try {
            return DateUtil.parseDate(dateStr);
        } catch (Exception e) {
            throw new ParameterException("请传入yyyy-MM-dd的日期格式");
        }
    }


    /**
     * 获取登录用户信息
     *
     * @param accessToken
     * @return
     */
    private SignInDinerInfo loadSignInDinersInfo(String accessToken) {
        //必须登录
        AssertUtil.mustLogin(accessToken);
        String url = oauthServerName + "user/me?access_token=" + accessToken;
        ResultInfo resultInfo = restTemplate.getForObject(url, ResultInfo.class);
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(resultInfo.getMessage());
        }
        // 这里的data是一个LinkedHashMap，SignInDinerInfo
        SignInDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new SignInDinerInfo(), false);

        if (dinerInfo == null) {
            throw new ParameterException(ApiConstant.NO_LOGIN_CODE, ApiConstant.NO_LOGIN_MESSAGE);
        }

        return dinerInfo;
    }


}
