package com.tianji.learning.mq;


import cn.hutool.core.collection.CollUtil;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.service.ILearningLessonService;
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
public class LessonEXListener {

    private final ILearningLessonService lessonService;

    //收消息
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.refund.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void onMsg(OrderBasicDTO dto){
        log.info("LessonEXListener 接收到消息：用户{}  删除课程{}",dto.getUserId(),dto.getCourseIds());
        //1.校验
        //工具类isEmpty判空
        if(CollUtil.isEmpty(dto.getCourseIds())
                ||dto.getUserId()==null
                ||dto.getOrderId()==null){
            //不能抛异常，否则MQ会一直重试,直到上限！！！！！
            return;
        }
        //2.调用service保存课表
        lessonService.deleteAllUserLesson(dto.getUserId(),dto.getCourseIds());

    }
}
