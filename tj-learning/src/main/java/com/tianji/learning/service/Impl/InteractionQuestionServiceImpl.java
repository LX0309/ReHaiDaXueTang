package com.tianji.learning.service.Impl;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.jaxb.SpringDataJaxb;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author lx
 * @since 2024-03-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;
    private final UserClient userClient;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        //1.获取当前用户ID
        Long userId = UserContext.getUser();
        //2.dto转po
        InteractionQuestion question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        question.setUserId(userId);
        //3.保存
        this.save(question);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        //校验dto
        if(StringUtils.isBlank(dto.getDescription()) || StringUtils.isBlank(dto.getTitle()) || dto.getAnonymity()==null){
            throw new BadRequestException("非法参数");
        }
        //校验ID
        InteractionQuestion question = this.getById(id);
        if(question==null){
            throw new BadRequestException("非法参数");
        }
        //校验用户
        Long userId = UserContext.getUser();
        if(!userId.equals(question.getUserId())){
            throw new BadRequestException("只能修改自己的问题");
        }

        //dto转po
        question.setAnonymity(dto.getAnonymity());
        question.setDescription(dto.getDescription());
        question.setTitle(dto.getTitle());
        //更新数据
        this.updateById(question);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        //数据校验  courseID
        if (query.getCourseId()==null){
            throw new BadRequestException("课程id不能为空");
        }
        //1.查询用户ID
        Long userId = UserContext.getUser();
        //2.分页查询 interaction_questions  条件:courseID  onlyMine为true才加userId   小节id不为空加  hidden 为false  分页查询  时间倒叙
        Page<InteractionQuestion> page = this.lambdaQuery()
//                .select(InteractionQuestion.class, tableFieldInfo -> {
//                    //排除问题描述  提高性能
//                    return tableFieldInfo.getProperty().equals("description");
//                })
                .select(InteractionQuestion.class,tableFieldInfo -> !tableFieldInfo.getProperty().equals("description"))
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .eq(InteractionQuestion::getHidden, false)
//                .page(query.toMpPage("create_time",false))
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
//        空集合返回
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //获取最新回答的id集合
        Set<Long> latestAnswerIds = new HashSet<>();
        Set<Long> UserIds = new HashSet<>();//用户Id集合
        for (InteractionQuestion record:records) {
            if (!record.getAnonymity()){
                UserIds.add(record.getUserId());
            }
            if (record.getLatestAnswerId()!=null){
                latestAnswerIds.add(record.getLatestAnswerId());
            }
        }
        //3.根据ID 批量查最新回答信息
        Map<Long,InteractionReply> replyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(latestAnswerIds)){
//            List<InteractionReply> replyList = replyService.listByIds(latestAnswerIds);//没有排除被隐藏的
            List<InteractionReply> replyList = replyService.list(Wrappers.<InteractionReply>lambdaQuery()
                    .in(InteractionReply::getId, latestAnswerIds)
                    .eq(InteractionReply::getHidden, false));
            for (InteractionReply reply:replyList) {
                if(!reply.getAnonymity()){
                    UserIds.add(reply.getUserId());
                }
                replyMap.put(reply.getId(),reply);
            }
        }
        //4.远程查询用户 批量
        List<UserDTO> userDTOS = userClient.queryUserByIds(UserIds);
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));

        //5.封装vo
        List<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion record:records) {
            QuestionVO vo = BeanUtils.copyBean(record,QuestionVO.class);
            if(!vo.getAnonymity()){
                //不匿名则要展示名称和头像
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO!=null){
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }
            InteractionReply reply = replyMap.get(record.getId());
            if(reply!=null){
                if(!reply.getAnonymity()){//非匿名
                    UserDTO userDTO = userDTOMap.get(record.getUserId());
                    if (userDTO!=null){
                        vo.setLatestReplyUser(userDTO.getName());//最新回答人的昵称
                    }
                }
                vo.setLatestReplyContent(reply.getContent());//最新回答的内容
            }
            voList.add(vo);
        }
        return PageDTO.of(page,voList);
    }

    @Override
    public void deleteQuestion(Long id) {
        //1.查询当前用户
        Long userId = UserContext.getUser();
        //2.查询问题是否存在
        InteractionQuestion question = this.lambdaQuery()
                .eq(InteractionQuestion::getUserId, userId)
                .eq(InteractionQuestion::getId, id)
                .one();
        if(question==null){
            throw new BadRequestException("问题不存在");
        }
        //3.判断是否为当前用户的问题
        if(!question.getUserId().equals(userId)){
            throw new BadRequestException("不能删除其他用户问题");
        }
        //4.删除问题下的回答以及评论   question_id
        replyService.remove(Wrappers.<InteractionReply>lambdaQuery().eq(InteractionReply::getQuestionId,question.getId()));
        this.removeById(question);

    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        //1.校验
        if (id==null){
             throw new BadRequestException("非法参数!");
        }
        //2.查询问题表   根据主键查询
        InteractionQuestion question = this.getById(id);
        if (question==null){
            throw new BadRequestException("问题不存在!");
        }
        //3.隐藏   返回空
        if (question.getHidden()){
            return null;
        }
        QuestionVO questionVO = BeanUtils.copyBean(question,QuestionVO.class);

        //4.匿名  用户昵称和头像不用查询
        if (!question.getAnonymity()){
            //查询用户数据
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO!=null){
                questionVO.setUserName(userDTO.getName());
                questionVO.setUserIcon(userDTO.getIcon());
            }
        }
        //5.封装vo返回

        return questionVO;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        //根据课程名称查询Id
        List<Long> ids = null;
        if(StringUtils.isNotBlank(query.getCourseName())){
            ids = searchClient.queryCoursesIdByName(query.getCourseName());
            if(CollUtils.isEmpty(ids)){
                return PageDTO.empty(0L,0L);
            }
        }

        //1.查询互动问题表    条件前端传条件就添加条件   分页  按提问时间倒叙
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(ids),InteractionQuestion::getCourseId, ids)
                .eq(query.getStatus()!=null,InteractionQuestion::getStatus, query.getStatus())
                .between(query.getBeginTime() != null && query.getEndTime() != null,
                        InteractionQuestion::getCreateTime, query.getBeginTime(), query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(0L,0L);
        }
        Set<Long> uids = new HashSet<>();//用户id集合
        Set<Long>  courseids = new HashSet<>();//课程id集合
        Set<Long> sectionAndChapterids = new HashSet<>();//章节集合
        for (InteractionQuestion record :records) {
            uids.add(record.getUserId());//用户id
            courseids.add(record.getCourseId());//课程id
            sectionAndChapterids.add(record.getSectionId());//节id
            sectionAndChapterids.add(record.getChapterId());//章
        }


        //2.远程调用用户服务  获取用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        if(CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //3.远程调用课程服务    获取课程信息
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(courseids);
        if(CollUtils.isEmpty(cInfos)){
            throw new BizIllegalException("课程不存在");
        }
        Map<Long, CourseSimpleInfoDTO> cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        //4.远程调用课程服务    获取章节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(sectionAndChapterids);
        if(CollUtils.isEmpty(cataSimpleInfoDTOS)){
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cataSimpleInfoMap = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));

        //5.获取分类信息

        //6.封装vo返回
        List<QuestionAdminVO> voList = new ArrayList<>();
        for (InteractionQuestion record :records) {
            QuestionAdminVO AdminVO = BeanUtils.copyBean(record,QuestionAdminVO.class);
            UserDTO userDTO = userDTOMap.get(record.getUserId());
            if(userDTO!=null){
                AdminVO.setUserName(userDTO.getName());//用户名
            }
            CourseSimpleInfoDTO cInfoDTO = cInfoMap.get(record.getCourseId());
            if(cInfoDTO!=null){
                AdminVO.setCourseName(cInfoDTO.getName());//课程名
                //获取123级 分类ID
                List<Long> categoryIds = cInfoDTO.getCategoryIds();
                String categoryNames = categoryCache.getCategoryNames(categoryIds);
                AdminVO.setCategoryName(categoryNames);//三级名称拼接
            }
            AdminVO.setChapterName(cataSimpleInfoMap.get(record.getChapterId()));//章
            AdminVO.setSectionName(cataSimpleInfoMap.get(record.getSectionId()));//节
            voList.add(AdminVO);
        }
        return PageDTO.of(page,voList);
    }

}
