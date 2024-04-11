package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikedRecordListener {

    private final IInteractionReplyService replyService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.QA_LIKED_TIMES_KEY
    ))
    public void listenReplyLikedTimesChange(List<LikedTimesDTO> list){
        log.debug("监听到回答或评论{}的点赞数变更:",list);
        //转 po
        List<InteractionReply> interactionReplies = new ArrayList<>();
        for (LikedTimesDTO dto : list) {
            InteractionReply reply = new InteractionReply();
            reply.setId(dto.getBizId());
            reply.setLikedTimes(dto.getLikedTimes());
            interactionReplies.add(reply);
        }
        replyService.updateBatchById(interactionReplies);
    }
//    public void listenReplyLikedTimesChange(LikedTimesDTO dto){
//        log.debug("监听到回答或评论{}的点赞数变更:{}", dto.getBizId(), dto.getLikedTimes());
//        InteractionReply r = new InteractionReply();
//        r.setId(dto.getBizId());
//        r.setLikedTimes(dto.getLikedTimes());
//        replyService.updateById(r);
//    }
}
