//package com.tianji.remark.service.Impl;
//
//import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
//import com.tianji.common.constants.MqConstants;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.remark.domain.dto.LikeRecordFormDTO;
//import com.tianji.remark.domain.dto.LikedTimesDTO;
//import com.tianji.remark.domain.po.LikedRecord;
//import com.tianji.remark.mapper.LikedRecordMapper;
//import com.tianji.remark.service.ILikedRecordService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
///**
// * <p>
// * 点赞记录表 服务实现类
// * </p>
// *
// * @author lx
// * @since 2024-03-08
// */
///**/
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//    private final RabbitMqHelper rabbitMqHelper;
//
//    @Override
//    public void LikeRecordFormDTO(LikeRecordFormDTO dto) {
//        //1.获取登录用户
//        Long userId = UserContext.getUser();
//        //2.判断是否点赞
//        /*Boolean flag = true;
//        if(dto.getLiked()){
//            //2.1点赞逻辑
//            flag = liked(dto);
//        }else {
//            //2.2取消点赞逻辑
//            flag = unliked(dto);
//        }*/
//        Boolean flag = dto.getLiked() ? liked(dto,userId) : unliked(dto,userId);
//        if (!flag){
//            //点赞嚯取消点赞失败
//            return;
//        }
//        //3.统计业务id下的总点赞数
//        Integer TotalLikedNum = Math.toIntExact(this.lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .count());
//        //发送消息到MQ
//        String RoutingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//        LikedTimesDTO msg = LikedTimesDTO.builder()
//                .bizId(dto.getBizId())
//                .likedTimes(TotalLikedNum)
//                .build();
//        rabbitMqHelper.send(MqConstants.Exchange.LIKE_RECORD_EXCHANGE,RoutingKey,msg);
//    }
//
//    @Override
//    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
//        //1.获取用户ID
//        Long userId = UserContext.getUser();
//        //2.查询点赞记录表    in bizIds   userID
//        List<LikedRecord> list = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .in(LikedRecord::getBizId, bizIds)
//                .list();
//        //3.将查询的bizIds转集合返回
//        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
//    }
//
//    //取消点赞
//    private Boolean unliked(LikeRecordFormDTO dto,Long userId) {
//        LikedRecord record = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if (record==null){
//            //说明没点过赞
//            return false;
//        }
//        return this.removeById(record.getId());
//    }
//    //点赞
//    private Boolean liked(LikeRecordFormDTO dto,Long userId) {
//        LikedRecord record = this.lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if (record!=null){
//            //说明点过赞
//            return false;
//        }
//        LikedRecord likedRecord = new LikedRecord();
//        likedRecord.setUserId(userId);
//        likedRecord.setBizId(dto.getBizId());
//        likedRecord.setBizType(dto.getBizType());
//        return this.save(likedRecord);
//    }
//}
