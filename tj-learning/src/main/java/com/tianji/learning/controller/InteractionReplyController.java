package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import com.tianji.learning.service.Impl.InteractionReplyServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author lx
 * @since 2024-03-05
 */
@Api(tags = "回答评论相关接口")
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class InteractionReplyController {

    private final IInteractionReplyService  replyService;

    @ApiOperation("新增问题回答或者评论")
    @PostMapping()
    public void queryReply(@RequestBody @Validated ReplyDTO dto){
        replyService.queryReply(dto);
    }

    @ApiOperation("分页查询回答列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query){
        return replyService.queryReplyPage(query);
    }

}
