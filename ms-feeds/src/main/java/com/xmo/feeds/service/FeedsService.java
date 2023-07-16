package com.xmo.feeds.service;

import cn.hutool.core.bean.BeanUtil;
import com.xmo.commons.constant.ApiConstant;
import com.xmo.commons.constant.RedisKeyConstant;
import com.xmo.commons.exception.ParameterException;
import com.xmo.commons.model.domain.ResultInfo;
import com.xmo.commons.model.pojo.Feeds;
import com.xmo.commons.model.vo.SignInDinerInfo;
import com.xmo.commons.utils.AssertUtil;
import com.xmo.feeds.mapper.FeedsMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 小莫同学
 * @createTime 2023/7/16 20:37
 */
@Service
public class FeedsService {

    @Value("${service.name.ms-oauth-server}")
    private String oauthServerName;
    @Value("${service.name.ms-follow-server}")
    private String followServerName;
    @Value("${service.name.ms-diners-server}")
    private String dinersServerName;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private FeedsMapper feedsMapper;
    @Resource
    private RedisTemplate redisTemplate;

    /**
     * 变更Feed
     *
     * @param followingDinerId 关注好友的Id
     * @param accessToken      登录用户的token
     * @param type             1 关注  0 取关
     */
    @Transactional(rollbackFor = Exception.class)
    public void addFollowingFeeds(Integer followingDinerId, String accessToken, int type) {
        // 请选择关注的好友
        AssertUtil.isTrue(followingDinerId == null || followingDinerId < 1,
                "请选择关注的好友");
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinersInfo(accessToken);
        // 获取关注/取关的食客的所有 Feed
        List<Feeds> feedsList = feedsMapper.findByDinerId(followingDinerId);
        if (feedsList == null || feedsList.isEmpty()) {
            return;
        }
        // 我关注的好友的 FeedsKey
        String key = RedisKeyConstant.following_feeds.getKey() + dinerInfo.getId();
        // 取关
        if (type == 0) {
            List<Integer> feedIds = feedsList.stream()
                    .map(feed -> feed.getId())
                    .collect(Collectors.toList());
            redisTemplate.opsForZSet().remove(key, feedIds.toArray(new Integer[]{}));
        } else {
            //关注
            Set<ZSetOperations.TypedTuple> typedTuples = feedsList.stream()
                    .map(feed -> new DefaultTypedTuple<>(feed.getId(), (double) feed.getUpdateDate().getTime()))
                    .collect(Collectors.toSet());
            redisTemplate.opsForZSet().add(key, typedTuples);
        }


    }


    /**
     * 删除Feed
     *
     * @param id
     * @param accessToken
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id, String accessToken) {
        // 请选择要删除的 Feed
        AssertUtil.isTrue(id == null || id < 1,
                "请选择要删除Feed");
        // 获取登录的用户
        SignInDinerInfo dinerInfo = loadSignInDinersInfo(accessToken);
        // 获取 Feed的内容
        Feeds feeds = feedsMapper.findById(id);
        // 判断 Feed 是否已被删除，且只能删除自己的 Feed
        AssertUtil.isTrue(feeds == null, "该Feed已被删除");
        AssertUtil.isTrue(!feeds.getFkDinerId().equals(dinerInfo.getId()),
                "只能删除自己的Feed");
        // 删除 Feed (逻辑删除)
        int delete = feedsMapper.delete(id);
        if (delete == 0) {
            return;
        }
        // 将内容从粉丝的集合中删除
        List<Integer> followers = findFollowers(dinerInfo.getId());
        // 先获取我的粉丝
        followers.forEach(follower -> {
            String key = RedisKeyConstant.following_feeds.getKey() + follower;
            redisTemplate.opsForZSet().remove(key, feeds.getId());
        });
    }

    /**
     * 添加Feed
     *
     * @param feeds
     * @param accessToken
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(Feeds feeds, String accessToken) {
        // 非空校验
        AssertUtil.isNotEmpty(feeds.getContent(), "请输入内容");
        AssertUtil.isTrue(feeds.getContent().length() > 255, "输入内容太多");
        // 获取登录用户信息
        SignInDinerInfo dinerInfo = loadSignInDinersInfo(accessToken);
        // Feed 关联用户信息
        feeds.setFkDinerId(dinerInfo.getId());
        // 添加Feed
        int save = feedsMapper.save(feeds);
        AssertUtil.isTrue(save == 0, "添加失败");
        // 推送到粉丝列表中
        // 先获取粉丝 id 集合
        List<Integer> followers = findFollowers(dinerInfo.getId());
        // 推送 Feed
        long now = System.currentTimeMillis();
        followers.forEach(follower -> {
            String key = RedisKeyConstant.following_feeds.getKey() + follower;
            redisTemplate.opsForZSet().add(key, feeds.getId(), now);
        });
    }

    /**
     * 获取粉丝列表
     * @param dinerId
     * @return
     */
    private List<Integer> findFollowers(Integer dinerId) {
        String url = followServerName + "followers/" + dinerId;
        ResultInfo followersResultInfo = restTemplate.getForObject(url, ResultInfo.class);
        if (followersResultInfo.getCode() != ApiConstant.SUCCESS_CODE) {
            throw new ParameterException(followersResultInfo.getCode(), followersResultInfo.getMessage());
        }

        List<Integer> followers = (List<Integer>) followersResultInfo.getData();
        return followers;
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

        return dinerInfo;
    }

}
