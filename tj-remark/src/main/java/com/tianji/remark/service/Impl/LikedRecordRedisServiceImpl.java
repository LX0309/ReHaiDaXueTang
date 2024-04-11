package com.tianji.remark.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author lx
 * @since 2024-03-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void LikeRecordFormDTO(LikeRecordFormDTO dto) {
        //1.获取登录用户
        Long userId = UserContext.getUser();
        //2.判断是否点赞
        /*Boolean flag = true;
        if(dto.getLiked()){
            //2.1点赞逻辑
            flag = liked(dto);
        }else {
            //2.2取消点赞逻辑
            flag = unliked(dto);
        }*/
        Boolean flag = dto.getLiked() ? liked(dto,userId) : unliked(dto,userId);
        if (!flag){
            //点赞嚯取消点赞失败
            return;
        }
        //3.统计业务id下的总点赞数
//        Integer TotalLikedNum = Math.toIntExact(this.lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .count());
        //基于redis统计业务id下的总点赞数
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long TotalLikedNum = redisTemplate.opsForSet().size(key);
        if(TotalLikedNum==null){
            return;
        }
        //采用redis  zSet缓存
        String bizTypeTotalLikedKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet().add(bizTypeTotalLikedKey,dto.getBizId().toString(),TotalLikedNum);



        //发送消息到MQ
//        String RoutingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//        LikedTimesDTO msg = LikedTimesDTO.builder()
//                .bizId(dto.getBizId())
//                .likedTimes(TotalLikedNum)
//                .build();
//        rabbitMqHelper.send(MqConstants.Exchange.LIKE_RECORD_EXCHANGE,RoutingKey,msg);
    }

    @Override
    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {

        // 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.查询点赞状态
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 3.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(bizIds::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集
        //普通查询
//        //redis实现
//        //1.获取用户
//        Long userId = UserContext.getUser();
//        if (CollUtils.isEmpty(bizIds)){
//            return CollUtils.emptySet();
//        }
//
//        Set<Long> retSet = new HashSet<>();
//        //2.循环bizIDs
//        for (Long bizId : bizIds) {
//            //3.判断当前业务  当前用户是否点赞
//            Boolean member = redisTemplate.opsForSet().isMember(RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId, userId.toString());
//            if (member){
//                retSet.add(bizId);
//            }
//        }
//        //4.如果有则返回
//        return retSet;


        /*  DB版本
        //1.获取用户ID
        Long userId = UserContext.getUser();
        //2.查询点赞记录表    in bizIds   userID
        List<LikedRecord> list = this.lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .in(LikedRecord::getBizId, bizIds)
                .list();
        //3.将查询的bizIds转集合返回
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());*/
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        //1. 拼接key
        String bizTypeTotalLikeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + bizType;


        //从Redis 的zSet  按分数排序   maxBizSize 的   业务点赞信息
        List<LikedTimesDTO> list = new ArrayList<>();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().popMin(bizTypeTotalLikeKey, maxBizSize);
        for (ZSetOperations.TypedTuple<String> typedTuple:typedTuples) {
            String bizId = typedTuple.getValue();
            Double score = typedTuple.getScore();
            if(StringUtils.isBlank(bizId) || score==null){
                continue;
            }
            //封装消息体  msg
            LikedTimesDTO msg = LikedTimesDTO.builder().bizId(Long.valueOf(bizId)).likedTimes(score.intValue()).build();
            list.add(msg);
        }
        if (CollUtils.isNotEmpty(list)){
            log.info("发送MQ消息：{}",list);
            String RoutingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType);
            rabbitMqHelper.send(
                    MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                    RoutingKey,
                    list);
        }
    }

    //取消点赞
    private Boolean unliked(LikeRecordFormDTO dto,Long userId) {
        //基于redis实现
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        return result!=null && result>0;
        /*LikedRecord record = this.lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, dto.getBizId())
                .one();
        if (record==null){
            //说明没点过赞
            return false;
        }
        return this.removeById(record.getId());*/

    }
    //点赞
    private Boolean liked(LikeRecordFormDTO dto,Long userId) {
        //基于Redis实现
        //拼接key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
//        redisTemplate.boundSetOps(key).add(userId.toString());
        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        return result!=null && result>0;
        /*LikedRecord record = this.lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, dto.getBizId())
                .one();
        if (record!=null){
            //说明点过赞
            return false;
        }
        LikedRecord likedRecord = new LikedRecord();
        likedRecord.setUserId(userId);
        likedRecord.setBizId(dto.getBizId());
        likedRecord.setBizType(dto.getBizType());
        return this.save(likedRecord);*/
    }
}
