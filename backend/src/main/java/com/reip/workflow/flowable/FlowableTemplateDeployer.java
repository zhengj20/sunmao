package com.reip.workflow.flowable;

import com.reip.common.exception.BizException;
import com.reip.workflow.entity.WflTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 按 bizType 把模板 JSON 转 BPMN 并部署到 Flowable：
 *  - 部署名 = wfl_<bizType>_<definitionHash>，同定义不重复部署（幂等）；
 *  - definition 变化 → 新部署（新版本），在途实例沿用旧版本（在途走旧版）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowableTemplateDeployer {

    private final RepositoryService repositoryService;

    /** 确保该模板已部署，返回可用的 processDefinitionKey（= bizType）。 */
    public String ensureDeployed(WflTemplate tpl) {
        String bizType = tpl.getBizType();
        String xml = BpmnConverter.convert(tpl);
        String name = deploymentName(bizType, xml);
        long existing = repositoryService.createDeploymentQuery()
                .deploymentName(name).processDefinitionKey(bizType).count();
        if (existing > 0) return bizType;

        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .addString(bizType + ".bpmn", xml)
                .deploy();
        log.info("[workflow] 部署流程 bizType={} deployment={} xmlHash={}", bizType, deployment.getId(),
                hash(xml));

        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(bizType).latestVersion().singleResult();
        if (pd == null) throw new BizException("流程部署成功但无法解析定义:" + bizType);
        return bizType;
    }

    private String deploymentName(String bizType, String bpmnXml) {
        return "wfl_" + bizType + "_" + hash(bpmnXml);
    }

    private String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) hex.append(String.format("%02x", d[i]));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString((s == null ? "" : s).hashCode());
        }
    }
}
