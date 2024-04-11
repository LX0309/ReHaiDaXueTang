package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author lx
 * @since 2024-03-05
 */
@Api(tags = "互动提问相关接口")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@RequestBody @Validated QuestionFormDTO dto){
        questionService.saveQuestion(dto);
    }

    @ApiOperation("修改互动问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable Long id,
                               @RequestBody QuestionFormDTO dto ){
        questionService.updateQuestion(id,dto);
    }

    @ApiOperation("删除互动问题")
    @DeleteMapping("/{id}")
    public void deleteQuestion(@PathVariable Long id){
        questionService.deleteQuestion(id);
    }

    @ApiOperation("分页查询互动问题--客户端")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query){
        return questionService.queryQuestionPage(query);
    }

    @ApiOperation("查询问题详细内容--用户端")
    @GetMapping("/{id}")
    public QuestionVO queryQuestionById(@PathVariable Long id){
        return questionService.queryQuestionById(id);
    }



}
