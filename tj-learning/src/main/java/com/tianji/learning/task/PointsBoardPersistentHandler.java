package com.tianji.learning.task;


import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;
    private final IPointsBoardService pointsBoardService;
    private final RedisTemplate redisTemplate;

    /**
     * 每个月一号凌晨三点创建新表（上赛季）
     */
//    @Scheduled(cron = "30 2 11 12 3 ?")//单机版  不支持集群
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        log.debug("创建上赛季表任务执行");
        //1.获取上月
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        //2.查询是否存在
        PointsBoardSeason one = pointsBoardSeasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        log.debug("上赛季信息   {}",one);
        if(one==null){
            return;
        }
        //3.创建表
        pointsBoardSeasonService.createPointsBoardTableBySeason(one.getId());
    }

    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        log.debug(("持久化数据到DB"));
        //1.获取上月
        LocalDateTime time = LocalDateTime.now().minusMonths(1);

        //2.查询赛季表  获取上个赛季信息
        PointsBoardSeason one = pointsBoardSeasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        log.debug("上赛季信息    {}",one);
        if (one==null){
            return;
        }
        Integer id = one.getId();
        //3.计算动态表 存入threadlocal
        String name = LearningConstants.POINTS_BOARD_TABLE_PREFIX + id;
        TableInfoContext.setInfo(name);
        //4.分页获取redis上赛季积分榜数据
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateTimeFormatter.ofPattern("yyyyMM"));

        int shardIndex = XxlJobHelper.getShardIndex(); //当前分片索引
        int shardTotal = XxlJobHelper.getShardTotal(); //总分片数


        int pageNo =  shardIndex + 1;
        int pageSize = 5;
        log.debug("key{}",key);
        while (true) {
            log.debug("处理第 {} 页数据",pageNo);
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoard(key, pageNo, pageSize);
            log.debug("{}",boardList);
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            // 4.持久化到数据库
            // 4.1.把排名信息写入id
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            // 4.2.持久化
            pointsBoardService.saveBatch(boardList);
            // 5.翻页
            pageNo += shardTotal;
        }
        //6.清空表名
        TableInfoContext.remove();
    }
    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 3.删除
        redisTemplate.unlink(key);
    }
}
