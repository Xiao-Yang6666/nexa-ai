package com.nexa.application.model;

import com.nexa.domain.model.exception.InvalidModelParameterException;
import com.nexa.domain.model.exception.VendorNotFoundException;
import com.nexa.domain.model.model.Vendor;
import com.nexa.domain.model.repository.VendorRepository;
import com.nexa.domain.model.vo.Pagination;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 供应商元数据 CRUD 用例（应用层，F-3018）。
 *
 * <p>承载供应商列表/搜索/创建/更新/删除全部用例（单一供应商子能力较薄，合并为一个用例类，
 * 避免过度拆分；与渠道域多用例类风格略异但同属 application 编排层）。领域不变量（name 非空/长度）
 * 在 {@link Vendor} 聚合守护，跨聚合唯一性（name 唯一）在本用例校验（backend-engineer §2.2）。</p>
 */
@Service
public class ManageVendorUseCase {

    private final VendorRepository vendorRepository;

    /** @param vendorRepository 供应商仓储 */
    public ManageVendorUseCase(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    /**
     * 分页列表（F-3018）。
     *
     * @param pagination 分页参数
     * @return 当前页供应商
     */
    @Transactional(readOnly = true)
    public List<Vendor> list(Pagination pagination) {
        return vendorRepository.findPage(pagination);
    }

    /** @return 供应商总数（F-3018 列表 total） */
    @Transactional(readOnly = true)
    public long count() {
        return vendorRepository.count();
    }

    /**
     * 关键词搜索（F-3018）。
     *
     * @param keyword 关键词（可空白 → 全量）
     * @return 命中供应商
     */
    @Transactional(readOnly = true)
    public List<Vendor> search(String keyword) {
        return vendorRepository.search(keyword);
    }

    /**
     * 创建供应商（F-3018）。
     *
     * @param name   名称（必填非空白）
     * @param icon   图标（可空）
     * @param status 状态码（可空 → 启用）
     * @return 创建后的供应商
     * @throws InvalidModelParameterException 名称为空（领域）或重名（本用例）
     */
    @Transactional
    public Vendor create(String name, String icon, Integer status) {
        Vendor vendor = Vendor.create(name, icon, status);
        // 跨聚合不变量：name 全局唯一（幂等键）。重名 → 「供应商名称已存在」（F-3018）。
        vendorRepository.findByName(vendor.name()).ifPresent(existing -> {
            throw new InvalidModelParameterException("供应商名称已存在");
        });
        return vendorRepository.save(vendor);
    }

    /**
     * 更新供应商（F-3018）。
     *
     * @param id     供应商 id（必填）
     * @param name   新名称（必填非空白）
     * @param icon   新图标（可空）
     * @param status 新状态码（可空 → 不改）
     * @return 更新后的供应商
     * @throws InvalidModelParameterException 缺 id / 名称非法 / 重名
     * @throws VendorNotFoundException        供应商不存在
     */
    @Transactional
    public Vendor update(Long id, String name, String icon, Integer status) {
        if (id == null || id <= 0) {
            // 领域规则来源：F-3018 BACKLOG T-120「更新缺 Id 返回缺少供应商 ID」。
            throw new InvalidModelParameterException("缺少供应商 ID");
        }
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new VendorNotFoundException(id));

        if (name != null && !name.isBlank()) {
            Optional<Vendor> sameName = vendorRepository.findByName(name.trim());
            if (sameName.isPresent() && !sameName.get().id().equals(vendor.id())) {
                throw new InvalidModelParameterException("供应商名称已存在");
            }
        }
        vendor.updateMeta(name, icon, status);
        return vendorRepository.save(vendor);
    }

    /**
     * 软删除供应商（F-3018）。
     *
     * @param id 供应商 id
     * @throws VendorNotFoundException 供应商不存在
     */
    @Transactional
    public void delete(long id) {
        if (vendorRepository.findById(id).isEmpty()) {
            throw new VendorNotFoundException(id);
        }
        vendorRepository.deleteById(id);
    }
}
