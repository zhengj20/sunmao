package com.reip.cost.init;

import com.reip.cost.service.CostTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时引导一份示例合同模板（幂等：已有模板则跳过；失败不影响启动）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateSampleInitializer implements ApplicationRunner {

    private final CostTemplateService templateService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            templateService.initSample();
        } catch (Exception e) {
            log.warn("示例合同模板引导失败（不影响启动）: {}", e.getMessage());
        }
    }
}
