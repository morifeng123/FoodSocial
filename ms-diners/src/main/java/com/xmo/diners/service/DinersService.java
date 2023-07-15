package com.xmo.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.xmo.commons.constant.ApiConstant;
import com.xmo.commons.model.domain.ResultInfo;
import com.xmo.commons.model.dto.DinersDTO;
import com.xmo.commons.model.pojo.Diners;
import com.xmo.commons.utils.AssertUtil;
import com.xmo.commons.utils.ResultInfoUtil;
import com.xmo.diners.config.OAuth2ClientConfiguration;
import com.xmo.diners.domain.OAuthDinerInfo;
import com.xmo.diners.mapper.DinersMapper;
import com.xmo.diners.vo.LoginDinerInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;

/**
 * @author 小莫同学
 * @createTime 2023/7/12 22:20
 */
@Service
public class DinersService {
    @Resource
    private RestTemplate restTemplate;

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;

    @Resource
    private OAuth2ClientConfiguration oauth2ClientConfiguration;

    @Resource
    private DinersMapper dinersMapper;

    @Resource
    private SendVerifyCodeService sendVerifyCodeService;

    /**
     * 用户注册
     *
     * @param dinersDTO
     * @param path
     * @return
     */
    public ResultInfo register(DinersDTO dinersDTO, String path) {
        //参数非空校验
        String username = dinersDTO.getUsername();
        AssertUtil.isNotEmpty(username, "请输入用户名");
        String password = dinersDTO.getPassword();
        AssertUtil.isNotEmpty(password, "请输入密码");
        String phone = dinersDTO.getPhone();
        AssertUtil.isNotEmpty(phone, "请输入手机号");
        String verifyCode = dinersDTO.getVerifyCode();
        AssertUtil.isNotEmpty(verifyCode, "请输入验证码");
        //获取验证码
        String code = sendVerifyCodeService.getCodeByPhone(phone);
        //验证码是否过期
        AssertUtil.isNotEmpty(code, "验证码已过期，请重新发送");
        //验证码是否一致
        AssertUtil.isTrue(!verifyCode.equals(code), "验证码不一致，请重新输入");
        //验证用户名是否已注册
        Diners diners = dinersMapper.selectByUsername(username.trim());
        System.out.println(diners != null);
        AssertUtil.isTrue(diners != null, "该用户名已存在，请重新输入");
        //注册
        //密码加密
        dinersDTO.setPassword(DigestUtil.md5Hex(password.trim()));
        dinersMapper.save(dinersDTO);
        //自动登录
        return signIn(username.trim(), password.trim(), path);
    }


    /**
     * 查询手机号是否已经被注册
     *
     * @param phone
     */
    public void checkPhoneIsRegistered(String phone) {
        AssertUtil.isNotEmpty(phone, "手机号不能为空");
        Diners diners = dinersMapper.selectByPhone(phone);
        AssertUtil.isTrue(diners == null, "该手机号未注册");
        AssertUtil.isTrue(diners.getIsValid() == 0, "该用户已锁定，请联系管理员解锁");
    }


    /**
     * 登录
     *
     * @param account  账号信息：用户名或手机或邮箱
     * @param password 密码
     * @param path     请求路径
     */
    public ResultInfo signIn(String account, String password, String path) {
        //参数校验
        AssertUtil.isNotEmpty(account, "请输入登录账号");
        AssertUtil.isNotEmpty(password, "请输入登录密码");
        //构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        //构建请求体(请求参数)
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("username", account);
        body.add("password", password);
        body.setAll(BeanUtil.beanToMap(oauth2ClientConfiguration));
        //合并请求头和请求体
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        //设置Authorization
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(oauth2ClientConfiguration.getClientId(),
                oauth2ClientConfiguration.getSecret()));
        //发送请求
        ResponseEntity<ResultInfo> result = restTemplate.postForEntity(oauthServerName + "oauth/token",
                entity, ResultInfo.class);        //处理请求结果
        AssertUtil.isTrue(result.getStatusCode() != HttpStatus.OK, "登录失败");
        ResultInfo resultInfo = result.getBody();
        if (resultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            //登录失败
            resultInfo.setData(resultInfo.getMessage());
            return resultInfo;
        }
        // 这里的data是一个LinkedHashMap，转成OAuthDinerInfo
        OAuthDinerInfo dinerInfo = BeanUtil.fillBeanWithMap((LinkedHashMap) resultInfo.getData(),
                new OAuthDinerInfo(), false);
        //根据业务需求返回Vo对象
        LoginDinerInfo loginDinerInfo = new LoginDinerInfo();
        loginDinerInfo.setAvatarUrl(dinerInfo.getAvatarUrl());
        loginDinerInfo.setToken(dinerInfo.getAccessToken());
        loginDinerInfo.setNickname(dinerInfo.getNickname());
        return ResultInfoUtil.buildSuccess(path, loginDinerInfo);
    }

}
