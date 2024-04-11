package com.tianji.learning.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author lx
 * @since 2024-03-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        log.info("查询学习记录");
        //1.查询用户ID
        Long userId = UserContext.getUser();
        //2.查询课表信息  user_id 和 course_Id
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if(lesson==null){
            throw new BizIllegalException("该课程未加入课表");
        }
        //3.查询学习记录  lesson_id 和user_id
        List<LearningRecord> records = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        //4.封装DTO
        LearningLessonDTO lessonDTO = new LearningLessonDTO();
        lessonDTO.setId(lesson.getId());
        lessonDTO.setLatestSectionId(lesson.getLatestSectionId());
//        原始做法
//        List<LearningRecordDTO> list = new ArrayList<>();
//        for (LearningRecord record:records) {
//            LearningRecordDTO dto = new LearningRecordDTO();
//            BeanUtil.copyProperties(record,dto);
//            list.add(dto);
//        }
        //使用BeanUtils
        List<LearningRecordDTO> list = BeanUtils.copyList(records, LearningRecordDTO.class);
        lessonDTO.setRecords(list);
        return lessonDTO;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        log.info("提交学习记录");
        //1.获取用户Id
        Long userId = UserContext.getUser();
        //2.处理学习记录

        boolean isFinished = false;//代表本小节是否已经学完
        if(dto.getSectionType().equals(SectionType.VIDEO)){
            //2.1提交视频播放记录
            isFinished = handleVideoRecord(userId,dto);
        } else{
            //2.2提交考试
            isFinished = handleExamRecord(userId,dto);
        }
        if(!isFinished){
            return;
        }

        //3.处理课表数据
        handleLessonData(dto);
    }
    //处理课表数据
    private void handleLessonData(LearningRecordFormDTO dto) {
        //1.查询课表  lesson_id主键
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if(lesson==null){
            throw new BizIllegalException("课表不存在");
        }
        //2.判断是否第一次学完   isFinished是否为true
        boolean allFinished = false;

            //3.远程调用课程服务  获取总小姐数
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(dto.getLessonId(), false, false);
            if(cInfo==null){
                throw new BizIllegalException("课程不存在");
            }
            //总小节数
            Integer sectionNum = cInfo.getSectionNum();
            //4.如果isFinished为true  本小节第一次学完   判断是否学完全部小节(learned_sections==)
            //已学习小节数
            Integer learnedSections = lesson.getLearnedSections();
            allFinished = learnedSections + 1 >= sectionNum;//是否学完
        //4.更新课表数据
        lessonService.lambdaUpdate()
                .set(LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestLearnTime,dto.getCommitTime())
                .set(LearningLesson::getLatestSectionId,dto.getSectionId())
//                .set(isFinished,LearningLesson::getLearnedSections,lesson.getLearnedSections()+1)
                .setSql("learned_sections = learned_sections + 1")//原子性
                .set(lesson.getStatus()==LessonStatus.NOT_BEGIN,LearningLesson::getStatus,LessonStatus.LEARNING.getValue())
                .eq(LearningLesson::getId,lesson.getId())
                .update();

    }

    //处理视频记录
    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO dto) {
        //1.查询旧表(学习记录——小节)  learning_record  条件 userId lesson_id  section_id
        LearningRecord learningRecord = queryOldRecord(dto.getLessonId(),dto.getSectionId());
//        LearningRecord learningRecord = this.lambdaQuery()
//                .eq(LearningRecord::getUserId, userId)
//                .eq(LearningRecord::getLessonId, dto.getLessonId())
//                .eq(LearningRecord::getSectionId,dto.getSectionId())
//                .one();
        //2.判断是否存在
        if(learningRecord==null){
            //2.1 不存在则新增学习记录
            LearningRecord record = new LearningRecord();
            BeanUtil.copyProperties(dto,record);
            record.setUserId(userId);
            boolean result = this.save(record);
            if(!result){
                throw new DbException("新增学习记录失败");
            }
            return false;
        }
        //2.2 存在则更新学习记录 finished finishTime
        //判断是否为第一次学完
        boolean isFinished = !learningRecord.getFinished() && dto.getMoment()*2>=dto.getDuration();
        if(!isFinished){
            LearningRecord record = new LearningRecord();
            record.setLessonId(dto.getLessonId());
            record.setSectionId(dto.getSectionId());
            record.setMoment(dto.getMoment());
            record.setFinished(learningRecord.getFinished());
            record.setId(learningRecord.getId());
            taskHandler.addLearningRecordTask(record);
            return false;
        }


        boolean result =  this.lambdaUpdate()
                .set(LearningRecord::getMoment,dto.getMoment())
                .set(isFinished,LearningRecord::getFinished,true)
                .set(isFinished,LearningRecord::getFinishTime,dto.getCommitTime())
                .eq(LearningRecord::getId,learningRecord.getId())
                .update();
        if(!result){
            throw new DbException("更新学习记录失败");
        }

        //清理redis缓存
        taskHandler.cleanRecordCache(dto.getLessonId(),dto.getSectionId());


        return true;
    }

    /**
     * 查询旧数据
     * @param lessonId
     * @param sectionId
     * @return
     */
    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        //1.查询缓存
        LearningRecord cache = taskHandler.readRecordCache(lessonId, sectionId);
        //2.名中返回
        if(cache!= null){
            return cache;
        }
        //3.未命中查数据库
        LearningRecord dbRecord = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId,sectionId)
                .one();
        if(dbRecord==null){
            return null;
        }
        //4.放入缓存
        taskHandler.writeRecordCache(dbRecord);

        return dbRecord;
    }

    //处理考试记录
    private boolean handleExamRecord(Long userId, LearningRecordFormDTO dto) {
        //新增学习记录learning_record
        LearningRecord record = new LearningRecord();
        BeanUtil.copyProperties(dto,record);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(dto.getCommitTime());
        boolean save = this.save(record);
        if(!save){
            throw new DbException("新增考试记录失败");
        }
        //修改课表数据
        return true;
    }
}
