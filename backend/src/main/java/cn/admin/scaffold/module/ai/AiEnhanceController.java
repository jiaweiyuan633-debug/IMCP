package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.ai.dto.AiChatRequest;
import cn.admin.scaffold.module.ai.dto.KnowledgeBaseSaveRequest;
import cn.admin.scaffold.module.ai.dto.KnowledgeDocSaveRequest;
import cn.admin.scaffold.module.ai.dto.KnowledgeQuery;
import cn.admin.scaffold.module.ai.dto.PromptQuery;
import cn.admin.scaffold.module.ai.dto.PromptSaveRequest;
import cn.admin.scaffold.module.ai.vo.AiChatVo;
import cn.admin.scaffold.module.ai.vo.KnowledgeBaseVo;
import cn.admin.scaffold.module.ai.vo.KnowledgeDocVo;
import cn.admin.scaffold.module.ai.vo.PromptVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 增强：模型网关对话 + Prompt 模板 + RAG 知识库（与 AiController 的异步任务派发并存）。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiEnhanceController {

    private final ModelGateway modelGateway;
    private final PromptTemplateService promptTemplateService;
    private final KnowledgeService knowledgeService;

    // ---------- 对话 ----------

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('ai:chat')")
    @OperLog(module = "AI 管理", action = "AI 对话")
    public Result<AiChatVo> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(modelGateway.chat(request));
    }

    // ---------- Prompt 模板 ----------

    @GetMapping("/prompt")
    @PreAuthorize("hasAuthority('ai:prompt:list')")
    public Result<PageResult<PromptVo>> pagePrompt(PromptQuery query) {
        return Result.success(promptTemplateService.page(query));
    }

    @PostMapping("/prompt")
    @PreAuthorize("hasAuthority('ai:prompt:add')")
    @OperLog(module = "AI 管理", action = "新增 Prompt 模板")
    public Result<Long> createPrompt(@Valid @RequestBody PromptSaveRequest request) {
        return Result.success(promptTemplateService.create(request));
    }

    @PutMapping("/prompt/{id}")
    @PreAuthorize("hasAuthority('ai:prompt:edit')")
    @OperLog(module = "AI 管理", action = "编辑 Prompt 模板")
    public Result<Void> updatePrompt(@PathVariable Long id, @Valid @RequestBody PromptSaveRequest request) {
        request.setId(id);
        promptTemplateService.update(request);
        return Result.success();
    }

    @DeleteMapping("/prompt/{id}")
    @PreAuthorize("hasAuthority('ai:prompt:delete')")
    @OperLog(module = "AI 管理", action = "删除 Prompt 模板")
    public Result<Void> deletePrompt(@PathVariable Long id) {
        promptTemplateService.delete(id);
        return Result.success();
    }

    // ---------- 知识库 ----------

    @GetMapping("/knowledge")
    @PreAuthorize("hasAuthority('ai:knowledge:list')")
    public Result<PageResult<KnowledgeBaseVo>> pageKnowledge(KnowledgeQuery query) {
        return Result.success(knowledgeService.pageBase(query));
    }

    @PostMapping("/knowledge")
    @PreAuthorize("hasAuthority('ai:knowledge:add')")
    @OperLog(module = "AI 管理", action = "新增知识库")
    public Result<Long> createKnowledge(@Valid @RequestBody KnowledgeBaseSaveRequest request) {
        return Result.success(knowledgeService.createBase(request));
    }

    @PutMapping("/knowledge/{id}")
    @PreAuthorize("hasAuthority('ai:knowledge:edit')")
    @OperLog(module = "AI 管理", action = "编辑知识库")
    public Result<Void> updateKnowledge(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseSaveRequest request) {
        request.setId(id);
        knowledgeService.updateBase(request);
        return Result.success();
    }

    @DeleteMapping("/knowledge/{id}")
    @PreAuthorize("hasAuthority('ai:knowledge:delete')")
    @OperLog(module = "AI 管理", action = "删除知识库")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        knowledgeService.deleteBase(id);
        return Result.success();
    }

    // ---------- 知识库文档 ----------

    @GetMapping("/knowledge-doc")
    @PreAuthorize("hasAuthority('ai:knowledge:list')")
    public Result<PageResult<KnowledgeDocVo>> pageDoc(KnowledgeQuery query) {
        return Result.success(knowledgeService.pageDoc(query));
    }

    @PostMapping("/knowledge-doc")
    @PreAuthorize("hasAuthority('ai:knowledge:doc:add')")
    @OperLog(module = "AI 管理", action = "新增知识库文档")
    public Result<Long> createDoc(@Valid @RequestBody KnowledgeDocSaveRequest request) {
        return Result.success(knowledgeService.createDoc(request));
    }

    @PutMapping("/knowledge-doc/{id}")
    @PreAuthorize("hasAuthority('ai:knowledge:edit')")
    @OperLog(module = "AI 管理", action = "编辑知识库文档")
    public Result<Void> updateDoc(@PathVariable Long id, @Valid @RequestBody KnowledgeDocSaveRequest request) {
        request.setId(id);
        knowledgeService.updateDoc(request);
        return Result.success();
    }

    @DeleteMapping("/knowledge-doc/{id}")
    @PreAuthorize("hasAuthority('ai:knowledge:doc:delete')")
    @OperLog(module = "AI 管理", action = "删除知识库文档")
    public Result<Void> deleteDoc(@PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return Result.success();
    }

    /** 供对话页下拉选择知识库。 */
    @GetMapping("/knowledge/options")
    @PreAuthorize("hasAuthority('ai:chat')")
    public Result<List<KnowledgeBaseVo>> knowledgeOptions(KnowledgeQuery query) {
        return Result.success(knowledgeService.pageBase(query).getRecords());
    }
}
