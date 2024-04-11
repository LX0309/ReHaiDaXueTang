package com.tianji.learning.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author lx
 * @since 2024-03-11
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardList(PointsBoardQuery query) {
        //1.查询当前用户Id
        Long userId = UserContext.getUser();
        //2.判断查当前还是历史  query.season
        Boolean isCurrent = query.getSeason()==null || query.getSeason()==0;
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Long season = query.getSeason();
        //3.查询我的积分排名   判断   redis  dh
        PointsBoard board = isCurrent ? queryMyCurrentBoard(key) : queryMyHistoryBoard(season);
        //4.查询赛季列表 判断   redis  dh
        List<PointsBoard> list = isCurrent ? queryCurrentBoard(key,query.getPageNo(),query.getPageSize()) : queryHistoryBoard(query);
        //远程调用用户服务  查询用户名字
        Set<Long> uids = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        if (CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, String> collect = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c.getName()));


        //5.封装vo返回
        PointsBoardVO vo = new PointsBoardVO();
        vo.setRank(board.getRank());
        vo.setPoints(board.getPoints());
        List<PointsBoardItemVO> voList = new ArrayList<>();
        for (PointsBoard pointsBoard : list) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            String s = collect.get(pointsBoard.getUserId());
            itemVO.setName(s);
            itemVO.setPoints(pointsBoard.getPoints());
            itemVO.setRank(pointsBoard.getRank());
            voList.add(itemVO);
        }
        vo.setBoardList(voList);


        return vo;
    }


    //查询当前 分页
    public List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize) {
        //1.计算 start end
        int start = (pageNo-1) * pageSize;
        int end = start + pageSize -1;
        //2.查询
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (CollUtils.isEmpty(typedTuples)){
            return CollUtils.emptyList();
        }
        List<PointsBoard> list = new ArrayList<>();
        int rank = start + 1;
        //3.封装
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String userId = typedTuple.getValue();
            Double score = typedTuple.getScore();
            if (StringUtils.isBlank(userId) || score==null){
                continue;
            }
            PointsBoard board = new PointsBoard();
            board.setUserId(Long.valueOf(userId));
            board.setPoints(score.intValue());
            board.setRank(rank++);
            list.add(board);
        }
        return list;
    }

    //查询历史  所有
    private List<PointsBoard> queryHistoryBoard(PointsBoardQuery query) {
        return null;
    }

    //查询历史   mysql
    private PointsBoard queryMyHistoryBoard(Long season) {

        return null;
    }
    //查询当前  redis
    private PointsBoard queryMyCurrentBoard(String key) {
        Long userId = UserContext.getUser();
        //获取积分
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        //获取排名
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());

        //封装
        PointsBoard board = new PointsBoard();
        board.setRank(rank==null ? 0 : rank.intValue()+1);
        board.setPoints(score==null ? 0 : score.intValue());
        return board;
    }
}
