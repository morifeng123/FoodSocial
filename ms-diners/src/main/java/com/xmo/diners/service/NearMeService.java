package com.xmo.diners.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.xmo.commons.constant.ApiConstant;
import com.xmo.commons.constant.RedisKeyConstant;
import com.xmo.commons.exception.ParameterException;
import com.xmo.commons.model.domain.ResultInfo;
import com.xmo.commons.model.vo.NearMeDinerVO;
import com.xmo.commons.model.vo.ShortDinerInfo;
import com.xmo.commons.model.vo.SignInDinerInfo;
import com.xmo.commons.utils.AssertUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 小莫同学
 * @createTime 2023/7/18 15:31
 */
@Service
public class NearMeService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private DinersService dinersService;

    /**
     * 更新食客坐标
     *
     * @param accessToken
     * @param lon
     * @param lat
     */
    public void updateDinerLocation(String accessToken, Float lon, Float lat) {
        // 参数校验
        AssertUtil.isTrue(lon == null, "获取经度失败");
        AssertUtil.isTrue(lat == null, "获取维度失败");
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinersInfo(accessToken);
        // 获取 key  diner:location
        String key = RedisKeyConstant.diner_location.getKey();
        // 将用户地理位置信息存入 redis
        Integer dinerId = dinerInfo.getId();
        RedisGeoCommands.GeoLocation geoLocation = new RedisGeoCommands
                .GeoLocation(dinerId, new Point(lon, lat));
        redisTemplate.opsForGeo().add(key, geoLocation);
    }

    /**
     * 获取附件的人
     *
     * @param accessToken 登录用户token
     * @param radius      半径  默认为1000
     * @param lon         经度
     * @param lat         维度
     * @return
     */
    public List<NearMeDinerVO> findNearMeDiner(String accessToken, Integer radius,
                                               Float lon, Float lat) {
        // 获取登录用户
        SignInDinerInfo dinerInfo = loadSignInDinersInfo(accessToken);
        int dinerId = dinerInfo.getId();
        if (radius == null) {
            radius = 1000;
        }
        // 获取key
        String key = RedisKeyConstant.diner_location.getKey();
        // 获取用户的经纬度
        Point point = null;
        if (lat == null || lon == null) {
            // 如果经纬度没传，则去redis中查询
            List<Point> points = redisTemplate.opsForGeo().position(key, dinerId);
            AssertUtil.isTrue(points == null || points.get(0) == null, "获取经纬度失败！");
            point = points.get(0);
        } else {
            point = new Point(lon, lat);
        }
        // 初始化距离对象，单位m
        Distance distance = new Distance(radius, RedisGeoCommands.DistanceUnit.METERS);
        // 初始化GEO 命令参数对象
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs();
        // 附近的人限制 20， 包含距离， 按由近到远排序
        args.limit(20).includeDistance().sortAscending();
        // 以用户经纬度为圆心， 范围 1000m
        Circle circle = new Circle(point, distance);
        // 获取附近的人 GeoLocation 信息
        GeoResults<RedisGeoCommands.GeoLocation<Integer>> results =
                redisTemplate.opsForGeo().radius(key, circle, args);
        // 构建有序 Map
        Map<Integer, NearMeDinerVO> nearMeDinerVOMap = Maps.newLinkedHashMap();
        results.forEach(geo -> {
            RedisGeoCommands.GeoLocation<Integer> location = geo.getContent();
            NearMeDinerVO nearMeDinerVO = new NearMeDinerVO();
            nearMeDinerVO.setId(location.getName());
            // 格式化距离
            Double dist = geo.getDistance().getValue();
            String distanceStr = NumberUtil.round(dist, 1).toString() + "m";
            nearMeDinerVO.setDistance(distanceStr);

            nearMeDinerVOMap.put(location.getName(), nearMeDinerVO);

        });
        // 获取附近的人的信息
        Integer[] dinerIds = nearMeDinerVOMap.keySet().toArray(new Integer[]{});
        List<ShortDinerInfo> shortDinerInfos = dinersService.findByIds(StrUtil.join(",", dinerIds));
        // 完善昵称头像信息
        shortDinerInfos.forEach(shortDinerInfo -> {
            NearMeDinerVO nearMeDinerVO = nearMeDinerVOMap.get(shortDinerInfo.getId());
            nearMeDinerVO.setNickname(shortDinerInfo.getNickname());
            nearMeDinerVO.setAvatarUrl(shortDinerInfo.getAvatarUrl());
        });
        //排除自己
        if (nearMeDinerVOMap.containsKey(dinerId)) {
            nearMeDinerVOMap.remove(dinerId);
        }
        return Lists.newArrayList(nearMeDinerVOMap.values());
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
