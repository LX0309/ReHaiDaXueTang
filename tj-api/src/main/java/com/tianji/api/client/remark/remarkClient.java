package com.tianji.api.client.remark;


import com.tianji.api.client.remark.fallback.RemarkClientFallback;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

@FeignClient(value = "remark-service",fallbackFactory = RemarkClientFallback.class)
public interface remarkClient {

    @ApiOperation("批量查询点赞状态")
    @GetMapping("/likes/list")
    public Set<Long> getLikesStatusByBizIds(@RequestParam("bizIds") List<Long> bizIds);


}
