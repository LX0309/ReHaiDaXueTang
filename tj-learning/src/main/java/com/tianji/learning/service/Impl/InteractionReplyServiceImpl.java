package com.tianji.learning.service.Impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author lx
 * @since 2024-03-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;

    @Override
    public void queryReply(ReplyDTO dto) {
        //查询用户
        Long userId = UserContext.getUser();
        //写入数据  dto转po
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        this.save(reply);
        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());
        //1.判断是否是回答
        if (dto.getAnswerId() != null) {
            //2.1 是评论   累加回答评论数
            InteractionReply answerInfo = this.getById(dto.getAnswerId());
            answerInfo.setReplyTimes(answerInfo.getReplyTimes()+1);
            this.updateById(answerInfo);
        }else{
            //2.2 是回答   修改问题表最近一次回答Id   问题表回答次数++
            question.setLatestAnswerId(reply.getId());
            question.setAnswerTimes(question.getAnswerTimes()+1);
        }
        //3.如果是学生提交   累加问题表回答次数
        if(dto.getIsStudent()){
            //学生查看，修改为未查看
            question.setStatus(QuestionStatus.UN_CHECK);
        }
        questionMapper.updateById(question);
    }

    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query) {
        // 1.问题id和回答id至少要有一个，先做参数判断
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("问题或回答id不能都为空");
        }
        // 标记当前是查询问题下的回答
        boolean isQueryAnswer = questionId != null;
        // 2.分页查询reply
        Page<InteractionReply> page = lambdaQuery()
                .eq(isQueryAnswer, InteractionReply::getQuestionId, questionId)
                .eq(InteractionReply::getAnswerId, isQueryAnswer ? 0L : answerId)
                .eq(InteractionReply::getHidden, false)
                .page(query.toMpPage( // 先根据点赞数排序，点赞数相同，再按照创建时间排序
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true))
                );
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.数据处理，需要查询：提问者信息、回复目标信息、当前用户是否点赞
        Set<Long> userIds = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();
        // 3.1.获取提问者id 、回复的目标id、当前回答或评论id（统计点赞信息）
        for (InteractionReply record : records) {
            if(!record.getAnonymity()) {
                // 非匿名
                userIds.add(record.getUserId());
                userIds.add(record.getTargetUserId());
            }
            if (record.getTargetReplyId()!=null && record.getTargetReplyId()>0){
                targetReplyIds.add(record.getTargetReplyId());
            }
        }
        // 3.2.查询目标回复，如果目标回复不是匿名，则需要查询出目标回复的用户信息
        if(targetReplyIds.size() > 0) {
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity))
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            userIds.addAll(targetUserIds);
        }
        // 3.3.查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>();
        if(users!=null) {
           userMap = users.stream().collect(Collectors.toMap(UserDTO::getId,c->c));
        }
        // 4.处理VO
        List<ReplyVO> list = new ArrayList<>();
        for (InteractionReply record : records) {
            // 4.1.拷贝基础属性
            ReplyVO v = BeanUtils.copyBean(record, ReplyVO.class);
            // 4.2.回复人信息
            if(!record.getAnonymity()){
                UserDTO userDTO = userMap.get(record.getUserId());
                if (userDTO != null) {
                    v.setUserIcon(userDTO.getIcon());
                    v.setUserName(userDTO.getName());
                    v.setUserType(userDTO.getType());
                }
            }
            // 4.3.如果存在评论的目标，则需要设置目标用户信息
            UserDTO targetUser = userMap.get(record.getTargetUserId());
            if (targetUser != null) {
                v.setTargetUserName(targetUser.getName());
            }
            list.add(v);
        }
        return PageDTO.of(page,list);
    }
}
