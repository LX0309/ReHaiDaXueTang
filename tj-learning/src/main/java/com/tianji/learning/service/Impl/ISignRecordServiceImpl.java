package com.tianji.learning.service.Impl;

import cn.hutool.core.collection.CollUtil;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ISignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;
    @Override
    public SignResultVO addSignRecords() {
        //1.获取用户ID
        Long userId = UserContext.getUser();
        //2.拼接Key
        LocalDate date = LocalDate.now();
        String format = date.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        //3.用bitMap保存签到记录     判断返回值    ture表示已经签到  false为未签到
        int offset = date.getDayOfMonth() - 1; //获取日期字段（偏移量）

        Boolean setBit = redisTemplate.opsForValue().setBit(key, offset, true);
        if (setBit){
            //已经签到过
            throw new BizIllegalException("不能重复签到");
        }
        //4.计算连续签到天数
        int days = countSignDays(key,date.getDayOfMonth());

        //5.计算连续签到的奖励积分   （7 - 10）（14 - 20） （28  - ）
        int RewardPoints = 0;
        switch (days){
            case 7:
                RewardPoints  = 10;
                break;
            case 14:
                RewardPoints  = 20;
                break;
            case 28:
                RewardPoints  = 40;
                break;
        }
        //6. 保存积分  发送消息到MQ
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId,RewardPoints+1));

        //7.封装VO返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(days);
        vo.setRewardPoints(RewardPoints);
        return vo;
    }

    @Override
    public Byte[] querySignRecords() {
        //1.查询登录用户
        Long userId = UserContext.getUser();
        //2.拼接Key
        LocalDate date = LocalDate.now();
        String format = date.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId.toString() + format;
        //查询签到数据
        int dayOfMonth = date.getDayOfMonth();
        List<Long> longs = redisTemplate.opsForValue()
                .bitField(key,
                        BitFieldSubCommands.create()
                                .get(BitFieldSubCommands.BitFieldType.
                                        unsigned(dayOfMonth)).valueAt(0));
        if(CollUtil.isEmpty(longs)){
            return new Byte[0];
        }
        //转数组
        Long num = longs.get(0);
        int offset = dayOfMonth -1;
        Byte[] arr = new Byte[dayOfMonth];
        while (offset>=0){
            arr[offset] = (byte)(num & 1);
            offset--;
            num = num >>>1;
        }
        return arr;
    }

    /**
     * 统计连续签到天数
     * @param key 缓存的key
     * @param dayOfMonth  本月第一天到今天的天数
     * @return 连续签到天数
     */
    private int countSignDays(String key, int dayOfMonth) {
        //1.查询数据
        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtil.isEmpty(bitField)){
            return 0;
        }
        Long num = bitField.get(0);
        log.debug("num : {}",num);
        //2. 十进制转二进制     右移   与运算   计算 1
        int count = 0;//计数器
        while ((num & 1) == 1){
            count++;
            num = num>>>1;
        }
        return count;
    }
}
