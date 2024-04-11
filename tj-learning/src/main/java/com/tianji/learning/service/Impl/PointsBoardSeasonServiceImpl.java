package com.tianji.learning.service.Impl;

import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lx
 * @since 2024-03-11
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    @Override
    public void createPointsBoardTableBySeason(Integer id) {
        getBaseMapper().createTableBySeason(LearningConstants.POINTS_BOARD_TABLE_PREFIX + id);
    }
}
