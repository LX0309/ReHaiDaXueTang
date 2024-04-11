package com.tianji.api.client.remark.fallback;

import com.tianji.api.client.remark.remarkClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


/*
*  remarkClient降级处理
* */
@Slf4j
public class RemarkClientFallback implements FallbackFactory<remarkClient> {
    /*
    如果remark服务超时，走降级方案，防止雪崩
     */
    @Override
    public remarkClient create(Throwable cause) {
        log.error("remark服务出错，降级处理：",cause);
        return new remarkClient() {
            @Override
            public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
                return new HashSet<>();
            }
        };
    }
}
