package com.bigbear.ai.platform.trigger.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.bigbear.ai.platform.domain.bo.AgentBo;
import com.bigbear.ai.platform.domain.entity.AgentEntity;
import com.bigbear.ai.platform.domain.vo.AgentVo;
import com.bigbear.ai.platform.infra.manager.AgentManager;
import com.mybatisflex.core.query.QueryWrapper;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能体管理接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/agent")
@Tag(name = "智能体管理", description = "智能体配置管理相关接口")
public class AgentController extends BaseController {

    private final AgentManager agentManager;

    private final Converter converter;

    /**
     * 分页查询智能体列表
     */
    @Operation(summary = "分页查询智能体列表")
    @SaCheckPermission("ai:agent:list")
    @GetMapping("/list")
    public TableDataInfo<AgentVo> list(AgentBo bo, PageQuery pageQuery) {
        // 使用   的分页方法
        return agentManager.selectVoPage(pageQuery, buildQueryWrapper(bo));
    }

    /**
     * 获取智能体详情
     */
    @Operation(summary = "获取智能体详情")
    @SaCheckPermission("ai:agent:query")
    @GetMapping("/{id}")
    public R<AgentVo> getInfo(@PathVariable String id) {
        return R.ok(agentManager.selectVoById(id));
    }

    /**
     * 根据编码获取智能体
     */
    @Operation(summary = "根据编码获取智能体")
    @SaCheckPermission("ai:agent:query")
    @GetMapping("/code/{agentCode}")
    public R<AgentVo> getByCode(@PathVariable String agentCode) {
        AgentEntity entity = agentManager.getByAgentCode(agentCode);
        return R.ok(converter.convert(entity, AgentVo.class));
    }

    /**
     * 新增智能体
     */
    @Operation(summary = "新增智能体")
    @SaCheckPermission("ai:agent:add")
    @Log(title = "智能体管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping()
    public R<Void> add(@Validated(AddGroup.class) @RequestBody AgentBo bo) {
        // 检查编码是否已存在
        if (agentManager.isAgentCodeExists(bo.getAgentCode())) {
            return R.fail("智能体编码已存在");
        }
        AgentEntity entity = converter.convert(bo, AgentEntity.class);
        return toAjax(agentManager.save(entity));
    }

    /**
     * 修改智能体
     */
    @Operation(summary = "修改智能体")
    @SaCheckPermission("ai:agent:edit")
    @Log(title = "智能体管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping()
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody AgentBo bo) {
        AgentEntity entity = converter.convert(bo, AgentEntity.class);
        return toAjax(agentManager.updateById(entity));
    }

    /**
     * 删除智能体（软删除）
     */
    @Operation(summary = "删除智能体")
    @SaCheckPermission("ai:agent:remove")
    @Log(title = "智能体管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable List<String> ids) {
        for (String id : ids) {
            agentManager.softDelete(id);
        }
        return R.ok();
    }

    /**
     * 根据用户ID查询智能体列表
     */
    @Operation(summary = "根据用户ID查询智能体列表")
    @SaCheckPermission("ai:agent:list")
    @GetMapping("/user/{userId}")
    public R<List<AgentVo>> listByUserId(@PathVariable Long userId) {
        List<AgentEntity> list = agentManager.listByUserId(userId);
        return R.ok(converter.convert(list, AgentVo.class));
    }

    private QueryWrapper buildQueryWrapper(AgentBo bo) {
        QueryWrapper qw = QueryWrapper.create().from(AgentEntity.class);
        if (bo.getUserId() != null) {
            qw.and(AgentEntity::getUserId).eq(bo.getUserId());
        }
        if (StringUtils.isNotBlank(bo.getAgentCode())) {
            qw.and(AgentEntity::getAgentCode).like(bo.getAgentCode());
        }
        if (StringUtils.isNotBlank(bo.getAgentName())) {
            qw.and(AgentEntity::getAgentName).like(bo.getAgentName());
        }
        qw.and(AgentEntity::getDelFlag).eq(0);
        qw.orderBy(AgentEntity::getSort, true);
        return qw;
    }
}
