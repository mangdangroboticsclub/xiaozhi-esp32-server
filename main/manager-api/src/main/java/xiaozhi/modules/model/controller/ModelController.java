package xiaozhi.modules.model.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.service.AgentTemplateService;
import xiaozhi.modules.config.service.ConfigService;
import xiaozhi.modules.model.dto.ModelBasicInfoDTO;
import xiaozhi.modules.model.dto.ModelConfigBodyDTO;
import xiaozhi.modules.model.dto.ModelConfigDTO;
import xiaozhi.modules.model.dto.ModelProviderDTO;
import xiaozhi.modules.model.dto.VoiceDTO;
import xiaozhi.modules.model.entity.ModelConfigEntity;
import xiaozhi.modules.model.service.ModelConfigService;
import xiaozhi.modules.model.service.ModelProviderService;
import xiaozhi.modules.timbre.service.TimbreService;

@AllArgsConstructor
@RestController
@RequestMapping("/models")
@Tag(name = "Model Configuration")
public class ModelController {

    private final ModelProviderService modelProviderService;
    private final TimbreService timbreService;
    private final ModelConfigService modelConfigService;
    private final ConfigService configService;
    private final AgentTemplateService agentTemplateService;

    @GetMapping("/names")
    @Operation(summary = "get all model name")
    @RequiresPermissions("sys:role:normal")
    public Result<List<ModelBasicInfoDTO>> getModelNames(@RequestParam String modelType,
            @RequestParam(required = false) String modelName) {
        List<ModelBasicInfoDTO> modelList = modelConfigService.getModelCodeList(modelType, modelName);
        return new Result<List<ModelBasicInfoDTO>>().ok(modelList);
    }

    @GetMapping("/{modelType}/provideTypes")
    @Operation(summary = "get model provider list")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<List<ModelProviderDTO>> getModelProviderList(@PathVariable String modelType) {
        List<ModelProviderDTO> modelProviderDTOS = modelProviderService.getListByModelType(modelType);
        return new Result<List<ModelProviderDTO>>().ok(modelProviderDTOS);
    }

    @GetMapping("/list")
    @Operation(summary = "get model config list")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<ModelConfigDTO>> getModelConfigList(
            @RequestParam(required = true) String modelType,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = true, defaultValue = "0") String page,
            @RequestParam(required = true, defaultValue = "10") String limit) {
        PageData<ModelConfigDTO> pageList = modelConfigService.getPageList(modelType, modelName, page, limit);
        return new Result<PageData<ModelConfigDTO>>().ok(pageList);
    }

    @PostMapping("/{modelType}/{provideCode}")
    @Operation(summary = "add model config")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ModelConfigDTO> addModelConfig(@PathVariable String modelType,
            @PathVariable String provideCode,
            @RequestBody ModelConfigBodyDTO modelConfigBodyDTO) {
        ModelConfigDTO modelConfigDTO = modelConfigService.add(modelType, provideCode, modelConfigBodyDTO);
        configService.getConfig(false);
        return new Result<ModelConfigDTO>().ok(modelConfigDTO);
    }

    @PutMapping("/{modelType}/{provideCode}/{id}")
    @Operation(summary = "edit model config")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ModelConfigDTO> editModelConfig(@PathVariable String modelType,
            @PathVariable String provideCode,
            @PathVariable String id,
            @RequestBody ModelConfigBodyDTO modelConfigBodyDTO) {
        ModelConfigDTO modelConfigDTO = modelConfigService.edit(modelType, provideCode, id, modelConfigBodyDTO);
        configService.getConfig(false);
        return new Result<ModelConfigDTO>().ok(modelConfigDTO);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "delete model config")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> deleteModelConfig(@PathVariable String id) {
        modelConfigService.delete(id);
        return new Result<>();
    }

    @GetMapping("/{id}")
    @Operation(summary = "get model config")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<ModelConfigDTO> getModelConfig(@PathVariable String id) {
        ModelConfigEntity item = modelConfigService.selectById(id);
        ModelConfigDTO modelConfigDTO = ConvertUtils.sourceToTarget(item, ModelConfigDTO.class);
        return new Result<ModelConfigDTO>().ok(modelConfigDTO);
    }

    @PutMapping("/enable/{id}/{status}")
    @Operation(summary = "enable/ disable model config")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> enableModelConfig(@PathVariable String id, @PathVariable Integer status) {
        ModelConfigEntity entity = modelConfigService.selectById(id);
        if (entity == null) {
            return new Result<Void>().error("model config does not exist");
        }
        entity.setIsEnabled(status);
        modelConfigService.updateById(entity);
        return new Result<Void>();
    }

    @PutMapping("/default/{id}")
    @Operation(summary = "set default model")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> setDefaultModel(@PathVariable String id) {
        ModelConfigEntity entity = modelConfigService.selectById(id);
        if (entity == null) {
            return new Result<Void>().error("model config does not exist");
        }
        // 将其他模型设置为非默认
        modelConfigService.setDefaultModel(entity.getModelType(), 0);
        entity.setIsEnabled(1);
        entity.setIsDefault(1);
        modelConfigService.updateById(entity);

        // 更新模板表中对应的模型ID
        agentTemplateService.updateDefaultTemplateModelId(entity.getModelType(), entity.getId());

        configService.getConfig(false);
        return new Result<Void>();
    }

    @GetMapping("/{modelId}/voices")
    @Operation(summary = "get model voice")
    @RequiresPermissions("sys:role:normal")
    public Result<List<VoiceDTO>> getVoiceList(@PathVariable String modelId,
            @RequestParam(required = false) String voiceName) {
        List<VoiceDTO> voiceList = timbreService.getVoiceNames(modelId, voiceName);
        return new Result<List<VoiceDTO>>().ok(voiceList);
    }
}
