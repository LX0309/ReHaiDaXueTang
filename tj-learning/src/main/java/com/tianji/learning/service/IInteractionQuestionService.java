package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author lx
 * @since 2024-03-05
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    void saveQuestion(QuestionFormDTO dto);

    void updateQuestion(Long id, QuestionFormDTO dto);

    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    void deleteQuestion(Long id);

    QuestionVO queryQuestionById(Long id);

    PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query);
}
