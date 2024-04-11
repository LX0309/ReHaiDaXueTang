package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author lx
 * @since 2024-02-27
 */
@Api(tags = "我的课程相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @ApiOperation("分页查询我的课程")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        return lessonService.queryMyLessons(query);
    }

    @ApiOperation("查询我的最近学习课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLessons(){
        return lessonService.queryMyCurrentLessons();
    }

    /**
     * 校验当前用户是否可以学习当前课程
     * @param courseId 课程id
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    @ApiOperation("校验当前用户是否可以学习当前课程")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId){
        return lessonService.isLessonValid(courseId);
    }

    /**
     * 校验当前用户是否拥有该课程
     * @param courseId 课程id
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    @ApiOperation("校验当前用户是否拥有该课程")
    @GetMapping("/{courseId}")
    public LearningLessonVO isLessonHave(@PathVariable("courseId") Long courseId){
        return lessonService.isLessonHave(courseId);
    }

    /**
     * 根据用户和课程删除课程表课程信息
     * @param courseId 课程id
     */
    @ApiOperation("根据用户和课程删除课程表课程信息")
    @DeleteMapping("/{courseId}")
    public void deleteLesson(@PathVariable("courseId") Long courseId){
        lessonService.deleteUserLesson(courseId);
    }

    /**
     * 创建学习计划
     * @param dto 前端数据
     */
    @ApiOperation("创建学习计划")
    @PostMapping("/plans")
    public void createLearningPlans(@Validated @RequestBody LearningPlanDTO dto){
        lessonService.createLearningPlans(dto.getCourseId(),dto.getFreq());
    }
    /**
     * 查询学习计划
     * @param query 前端数据
     */
    @ApiOperation("查询学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return lessonService.queryMyPlans(query);
    }
}
