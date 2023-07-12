package com.xmo.oauth2.server.mapper;

import com.xmo.commons.model.pojo.Diners;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

/**
 * 食客mapper
 * @author 小莫同学
 * @createTime 2023/7/12 17:20
 */
public interface DinersMapper {

    // 根据用户名 or 手机号 or 邮箱查询用户信息
    @Select("select id, username, nickname, phone, email, " +
            "password, avatar_url, roles, is_valid from t_diners where " +
            "(username = #{account} or phone = #{account} or email = #{account})")
    Diners selectByAccountInfo(@Param("account") String account);

}
