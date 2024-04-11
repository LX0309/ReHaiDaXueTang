package com.lx;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.LearningApplication;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@SpringBootTest(classes = LearningApplication.class)
public class MPTest {

    @Autowired
    ILearningLessonService lessonService;
    @Autowired
    IPointsBoardService pointsBoardService;

    /*
    MP的普通分页查询
     */
    @Test
    public void Test(){

        Page<LearningLesson> page = new Page<>(1,2); //第一页，两条数据
        LambdaQueryWrapper<LearningLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningLesson::getUserId,2);
        wrapper.orderByDesc(LearningLesson::getLatestLearnTime);
        lessonService.page(page,wrapper);
        System.out.println(page.getTotal());
        System.out.println(page.getPages());
        List<LearningLesson> records = page.getRecords();
        for (LearningLesson record : records) {
            System.out.println(record);
        }
    }
    /*
    MP的进阶分页查询
     */
    @Test
    public void Test1(){

        PageQuery query = new PageQuery();
        query.setPageNo(1);
        query.setPageSize(2);
        query.setSortBy("latest_learn_time");
        query.setIsAsc(false);
        Page<LearningLesson> page = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, 2)
                .page(query.toMpPage("latest_learn_time", false));
        System.out.println(page.getTotal());
        System.out.println(page.getPages());
        List<LearningLesson> records = page.getRecords();
        for (LearningLesson record : records) {
            System.out.println(record);
        }
    }

    @Test
    public void test2(){
        List<LearningLesson> list = new ArrayList<>();

        LearningLesson lesson1 = new LearningLesson();
        lesson1.setCourseId(1L);
        lesson1.setId(1L);

        LearningLesson lesson2 = new LearningLesson();
        lesson2.setCourseId(2L);
        lesson2.setId(2L);

        list.add(lesson1);
        list.add(lesson2);

        //从list获取id值
        
        //foreach遍历
        List<Long> ids = new ArrayList<>();
        for (LearningLesson lesson:list) {
            ids.add(lesson.getId());
        }

        //使用stream流
        List<Long> ids2 = list.stream().map(LearningLesson::getId).collect(Collectors.toList());

        //使用stream流转为map对象
        Map<Long, LearningLesson> map = list.stream().collect(Collectors.toMap(LearningLesson::getId, c -> c));

        System.out.println(ids);
        System.out.println(ids2);
        System.out.println(map);
    }
    @Test
    public void test3(){
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal")
                .eq("user_id",2)
                .in("status", LessonStatus.LEARNING,LessonStatus.NOT_BEGIN)
                .eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = lessonService.getMap(wrapper); //{plansTotal : 7}
        System.out.println(map);
    }

    @Test
    public void test4(){
        TableInfoContext.setInfo("points_board_13");
        System.out.println(TableInfoContext.getInfo());
        PointsBoard board = new PointsBoard();
        board.setId(1L);
        board.setUserId(2L);
        board.setPoints(19);
        System.out.println(board);
        pointsBoardService.save(board);
    }
}
