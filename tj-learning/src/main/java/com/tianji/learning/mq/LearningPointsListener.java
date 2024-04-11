package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningPointsListener {

    private final IPointsRecordService pointsRecordService;

    /**
     * 签到增加积分
     * @param msg
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "",durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN))
    public void listenSignInListener(SignInMessage msg){
        log.debug("签到增加积分   消息来自：{}",msg);
        pointsRecordService.addPointsRecord(msg, PointsRecordType.SIGN);

    }
    // 监听新增互动问答事件
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.points.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(SignInMessage msg){
        pointsRecordService.addPointsRecord(msg, PointsRecordType.QA);
    }
}
