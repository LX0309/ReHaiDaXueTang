package com.tianji.learning.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author lx
 * @since 2024-03-11
 */
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addPointsRecord(SignInMessage msg, PointsRecordType type) {
        //0.校验
        if (msg.getUserId()==null || msg.getPoints()==null){
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        int realPoint = msg.getPoints();
        //1.判断该类型积分是否有上限
        int maxPoints = type.getMaxPoints();
        if (maxPoints>0){
            //2.有  查询是否上限  积分类型   今天 获得多少   用户id  类型
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("sum(point) as totalPoints");
            wrapper.eq("user_id",msg.getUserId())
                    .eq("type",type)
                    .between("create_time",start,end);
            Map<String, Object> map = this.getMap(wrapper);
            int currentPoints = 0;
            if (map!=null){
                BigDecimal totalPoints = (BigDecimal)map.get("totalPoints");
                currentPoints = totalPoints.intValue();
            }
            //3.判断是否超过上限
            if (currentPoints>=maxPoints){
                return;
            }
            if (currentPoints + realPoint >maxPoints){
                realPoint = maxPoints -currentPoints;
            }
        }
        //4.保存
        PointsRecord record = new PointsRecord();
        record.setUserId(msg.getUserId());
        record.setType(type);
        record.setPoints(realPoint);
        this.save(record);
        //5.累加保存总积分   当前赛季排行榜

        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        redisTemplate.opsForZSet().incrementScore(key,msg.getUserId().toString(),realPoint);


    }

    @Override
    public List<PointsStatisticsVO> queryMyTodayPoints() {
        //1.获取当前用户
        Long userId = UserContext.getUser();
        //2.查询当日积分    按type分组查询
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        wrapper.select("type","sum(points) as userId");
        wrapper.eq("user_id",userId)
                .between("create_time",start,end)
                .groupBy("type");
        List<PointsRecord> list = this.list(wrapper);
        if (CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }
        //3.封装结果
        List<PointsStatisticsVO> voList = new ArrayList<>();
        for (PointsRecord record : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(record.getType().getDesc());
            vo.setMaxPoints(record.getType().getMaxPoints());
            vo.setPoints(record.getUserId().intValue());
            voList.add(vo);
        }
        return voList;
    }
}
