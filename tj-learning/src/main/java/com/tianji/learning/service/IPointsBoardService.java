package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author lx
 * @since 2024-03-11
 */
public interface IPointsBoardService extends IService<PointsBoard> {


    PointsBoardVO queryPointsBoardList(PointsBoardQuery query);
    List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize);
}
