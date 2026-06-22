package com.nexa.modelgroup.application;

import com.nexa.modelgroup.domain.exception.InvalidModelGroupParameterException;
import com.nexa.modelgroup.domain.exception.ModelGroupNotFoundException;
import com.nexa.modelgroup.domain.model.ModelGroupAccess;
import com.nexa.modelgroup.domain.repository.ModelGroupAccessRepository;
import com.nexa.modelgroup.domain.repository.ModelGroupRepository;
import com.nexa.modelgroup.domain.vo.AccessSubjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 模型组访问授权用例（管理端，私有模型组的 grant/revoke/list）。
 *
 * <p>编排授权关系：授权前校验目标模型组存在（不存在 → 404）；重复授权幂等（已存在直接返回既有动作语义，
 * 不抛错）；撤销按授权记录 id 物理删除。授权清单查询为只读。</p>
 */
@Service
public class ManageModelGroupAccessUseCase {

    private final ModelGroupRepository modelGroupRepository;
    private final ModelGroupAccessRepository accessRepository;

    /**
     * @param modelGroupRepository 模型组仓储（校验目标组存在）
     * @param accessRepository     授权仓储
     */
    public ManageModelGroupAccessUseCase(ModelGroupRepository modelGroupRepository,
                                         ModelGroupAccessRepository accessRepository) {
        this.modelGroupRepository = modelGroupRepository;
        this.accessRepository = accessRepository;
    }

    /**
     * 授权一个主体访问私有模型组。
     *
     * @param modelGroupId  模型组主键
     * @param subjectType   主体类型字面量（USER/TOKEN）
     * @param subjectId     主体主键
     * @return 授权记录（已存在则返回既有记录，幂等）
     * @throws ModelGroupNotFoundException 模型组不存在（→404）
     * @throws InvalidModelGroupParameterException 字段非法（→400）
     */
    @Transactional
    public ModelGroupAccess grant(long modelGroupId, String subjectType, long subjectId) {
        if (modelGroupRepository.findById(modelGroupId).isEmpty()) {
            throw new ModelGroupNotFoundException(modelGroupId);
        }
        AccessSubjectType type = AccessSubjectType.fromWire(subjectType);

        // 幂等：重复授权不再插入（DB 唯一约束兜底；此处先查避免抛唯一冲突）。
        if (accessRepository.exists(modelGroupId, type, subjectId)) {
            return accessRepository.findByModelGroupId(modelGroupId).stream()
                    .filter(a -> a.subjectType() == type && a.subjectId() == subjectId)
                    .findFirst()
                    .orElseThrow(() -> new InvalidModelGroupParameterException(
                            "access record vanished concurrently"));
        }

        ModelGroupAccess access = ModelGroupAccess.grant(
                modelGroupId, type, subjectId, Instant.now().getEpochSecond());
        return accessRepository.save(access);
    }

    /**
     * 撤销授权（按授权记录 id 物理删除）。
     *
     * @param accessId 授权记录主键
     * @throws ModelGroupNotFoundException 授权记录不存在（→404，复用 NotFound 语义）
     */
    @Transactional
    public void revoke(long accessId) {
        boolean deleted = accessRepository.deleteById(accessId);
        if (!deleted) {
            throw new ModelGroupNotFoundException(accessId);
        }
    }

    /**
     * 列出某模型组的授权清单（只读）。
     *
     * @param modelGroupId 模型组主键
     * @return 授权记录列表
     */
    @Transactional(readOnly = true)
    public List<ModelGroupAccess> listAccess(long modelGroupId) {
        return accessRepository.findByModelGroupId(modelGroupId);
    }
}
