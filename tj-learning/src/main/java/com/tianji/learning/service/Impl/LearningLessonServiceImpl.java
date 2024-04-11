package com.tianji.learning.service.Impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.ILearningRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author lx
 * @since 2024-02-27
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper learningRecordMapper;

    @Override
    public void addUserLesson(Long userId, List<Long> courseIds) {
        //1.通过feign远程调用课程服务，获取课程信息
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        //2.封装po实体类，填充过期时间
        List<LearningLesson> lessons = new ArrayList<>();

        for (CourseSimpleInfoDTO cinfo: cinfos) {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(userId);
            lesson.setCourseId(cinfo.getId());
            Integer integer = cinfo.getValidDuration();//课程有效期 单位：月
            //过期时间 = 当前时间 + 过期时间
            if(integer!=null){
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(integer));
            }
            lessons.add(lesson);
        }
        //3.批量保存
        this.saveBatch(lessons);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        //1.获取当前登录人
        Long user = UserContext.getUser();

        //2.分页查询我的课表
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtil.isEmpty(records)){
            return PageDTO.empty(page);
        }

        List<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        //3.远程调用课程服务
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(courseIds);
        if(CollUtil.isEmpty(cInfos)){
            throw new BizIllegalException("课程不存在");
        }

//        将集合转为Map对象，避免双循环浪费资源
        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cInfos.stream()
                .collect(Collectors
                        .toMap(CourseSimpleInfoDTO::getId, c -> c));
        //4.将po的数据转为vo
        List<LearningLessonVO> voList = new ArrayList<>();
        for (LearningLesson record:records) {
            LearningLessonVO vo = new LearningLessonVO();
            BeanUtils.copyProperties(record,vo);
            //根据ID获取课信息
            CourseSimpleInfoDTO infoDTO = infoDTOMap.get(record.getCourseId());
            if(infoDTO!=null){
                vo.setCourseName(infoDTO.getName());
                vo.setCourseCoverUrl(infoDTO.getCoverUrl());
                vo.setSections(infoDTO.getSectionNum());
            }
            voList.add(vo);
        }




        //5.返回
        return PageDTO.of(page,voList);
    }

    @Override
    public LearningLessonVO queryMyCurrentLessons() {
        //1.获取当前用户ID
        Long userId = UserContext.getUser();
        //2.查询最近学习课程 按Latest_learn_time 降序取第一 正在学习中的 status=1
        // Select * from xx where user_id == {user} AND status == 1 order by latest_learn_time limit 1
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        //3.封装为vo
        LearningLessonVO vo = new LearningLessonVO();
        BeanUtils.copyProperties(lesson,vo);


        //4.远程调用查询课程信息
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if(cInfo==null){
            throw new BadRequestException("课程不存在");
        }

        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setSections(cInfo.getSectionNum());

        //5.统计课表总课程数
        //Select count(1) from xx where userId == {userId}
        Integer count = Math.toIntExact(lambdaQuery().eq(LearningLesson::getUserId, userId).count());
        vo.setCourseAmount(count);
        // 6.查询小节信息
        List<CataSimpleInfoDTO> cataInfos =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataInfos)) {
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        //5.返回
        return vo;
    }

    @Override
    public Long isLessonValid(Long courseId) {
        //1.获取用户ID
        Long UserId = UserContext.getUser();
        //2.查询课程状态
        //select * where status == 0,1,2;
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, UserId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson==null){
            return null;
        }
        //3.报名了返回LessonId，否则为空
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if(expireTime!=null && now.isAfter(expireTime)){
            return null;
        }
        return lesson.getId();
    }

    @Override
    public LearningLessonVO isLessonHave(Long courseId) {
        //1.查询用户ID
        Long userId = UserContext.getUser();
        //2.查询是否拥有课程
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getCourseId,courseId).one();
        //3.为拥有返回null
        if(lesson==null){
            return null;
        }
        LearningLessonVO vo = new LearningLessonVO();
        BeanUtils.copyProperties(lesson,vo);
        return vo;
    }

    @Override
    public void deleteAllUserLesson(Long userId, List<Long> courseIds) {
        //构件VO集合
        List<LearningLesson> lessons = new ArrayList<>();
        for (Long courseId:courseIds) {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(userId);
            lesson.setCourseId(courseId);
            lessons.add(lesson);
        }
        this.removeBatchByIds(lessons);
    }

    @Override
    public void deleteUserLesson(Long courseId) {
        //查询当前用户
        Long userId = UserContext.getUser();
        //删除用户对应课程
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("course_id", courseId);
        this.remove(wrapper);
    }

    @Override
    public void createLearningPlans(Long courseId, Integer freq) {
        //获取当前登录用户
        Long userId = UserContext.getUser();
        //查询课程信息
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson==null){
            throw new BizIllegalException("课程不存在");
        }
        //修改数据
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if(lesson.getPlanStatus() == PlanStatus.NO_PLAN) {
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        //1.获取用户 Id
        Long userId = UserContext.getUser();
        //3.查询本周学习计划 --select SUM(week_freq) from learning_lesson where user_id = {userId} and start in(0,1) and plan_start = 1;

        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal")
                .eq("user_id",userId)
                .in("status",LessonStatus.LEARNING,LessonStatus.NOT_BEGIN)
                .eq("plan_status",PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = this.getMap(wrapper); //{plansTotal : 7}
        Integer plansTotal = 0;
        if(map!=null && map.get("plansTotal")!=null){
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }
        //4.查询本周 已学习的计划总数据  select count(*) from learning_record where user_id = {userId} and finished_time in()
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);


        Integer weekFinishedNum = Math.toIntExact(learningRecordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime)));
        //5.查询课表信息
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.LEARNING, LessonStatus.NOT_BEGIN)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(new ArrayList<>());
            return vo;
        }
        //6.远程查询课程信息
        Set<Long> CourseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());

        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(CourseIds);
        Map<Long, CourseSimpleInfoDTO> cInfosMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        //7.查询学习记录表
        QueryWrapper<LearningRecord> rWrapper = new QueryWrapper<>();
        rWrapper.select("lesson_id as lessId","count(*) as userId")
                        .eq("user_id",userId);
        rWrapper.eq("finished",true);
        rWrapper.between("finish_time",weekBeginTime,weekEndTime);
        rWrapper.groupBy("lesson_id");
        List<LearningRecord> learningRecords = learningRecordMapper.selectList(rWrapper);
        Map<Long, Long> longMap = learningRecords.stream().collect(Collectors.toMap(LearningRecord::getLessonId, c -> c.getUserId()));

        //8.封装vo
        LearningPlanPageVO vo = new LearningPlanPageVO();
        List<LearningPlanVO> planVOS = new ArrayList<>();
        vo.setWeekTotalPlan(plansTotal);
        vo.setWeekFinished(weekFinishedNum);
        for (LearningLesson record:records) {
            LearningPlanVO planVO = new LearningPlanVO();
            BeanUtils.copyProperties(record,planVO);
            CourseSimpleInfoDTO infoDTO = cInfosMap.get(record.getCourseId());
            if(infoDTO!=null){
                planVO.setCourseName(infoDTO.getName());
                planVO.setSections(infoDTO.getSectionNum());
            }
            Long aLong = longMap.get(record.getId());
            if(aLong!=null){
                planVO.setWeekLearnedSections(Math.toIntExact(aLong));
            }else {
                planVO.setWeekLearnedSections(0);
            }
            planVOS.add(planVO);
        }
        vo.setList(planVOS);
        vo.setTotal(page.getTotal());
        vo.setPages(page.getPages());
        return vo;
    }
}
