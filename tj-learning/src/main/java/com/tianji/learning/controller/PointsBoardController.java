package com.tianji.learning.controller;


import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.service.IPointsBoardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学霸天梯榜 前端控制器
 * </p>
 *
 * @author lx
 * @since 2024-03-11
 */
@Api(tags = "排行榜相关接口")
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class PointsBoardController {

    private final IPointsBoardService pointsBoardService;

    @ApiOperation("查询当前赛季--查询历史赛季排行榜")
    @GetMapping
    public PointsBoardVO queryPointsBoardList(PointsBoardQuery query){
        return pointsBoardService.queryPointsBoardList(query);
    }

}
