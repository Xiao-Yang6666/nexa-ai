package com.nexa.ops.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 运营与运维横切配置属性（基础设施层，绑定 {@code ops.*} 前缀）。
 *
 * <p>把磁盘缓存目录、日志目录、Uptime-Kuma 接入等基础设施关注点收敛到 infra 层（DDD §2.3），
 * 经 {@code @ConfigurationProperties} 从 {@code application.yml} 注入，可用环境变量覆盖
 * （backend-engineer §3.4 配置外置）。目录未配置时相关运维端点降级（disk_cache disabled /
 * logs enabled:false），对齐 API-ENDPOINTS §9.3/§9.4 容错语义。</p>
 */
@Component
@ConfigurationProperties(prefix = "ops")
public class OpsProperties {

    /** 磁盘缓存配置子段。 */
    private final DiskCache diskCache = new DiskCache();
    /** 日志配置子段。 */
    private final Logs logs = new Logs();
    /** Uptime-Kuma 接入配置子段。 */
    private final Uptime uptime = new Uptime();

    /** @return 磁盘缓存配置 */
    public DiskCache getDiskCache() {
        return diskCache;
    }

    /** @return 日志配置 */
    public Logs getLogs() {
        return logs;
    }

    /** @return Uptime 配置 */
    public Uptime getUptime() {
        return uptime;
    }

    /** 磁盘缓存目录配置。 */
    public static class DiskCache {

        /** 磁盘缓存目录绝对路径；空=未启用磁盘缓存（F-4019 disk_cache_info.enabled=false）。 */
        private String dir = "";

        /** @return 缓存目录路径 */
        public String getDir() {
            return dir;
        }

        /** @param dir 缓存目录路径 */
        public void setDir(String dir) {
            this.dir = dir;
        }
    }

    /** 日志目录配置。 */
    public static class Logs {

        /** 日志目录绝对路径；空=LogDir 未配置（F-4022 enabled:false / F-4023 报错）。 */
        private String dir = "";

        /**
         * 当前活动日志文件名（清理时跳过，F-4023）；空=按前缀+最新文件推断由实现决定。
         */
        private String currentFileName = "";

        /** @return 日志目录路径 */
        public String getDir() {
            return dir;
        }

        /** @param dir 日志目录路径 */
        public void setDir(String dir) {
            this.dir = dir;
        }

        /** @return 当前活动日志文件名 */
        public String getCurrentFileName() {
            return currentFileName;
        }

        /** @param currentFileName 当前活动日志文件名 */
        public void setCurrentFileName(String currentFileName) {
            this.currentFileName = currentFileName;
        }
    }

    /** Uptime-Kuma 接入配置。 */
    public static class Uptime {

        /** Uptime-Kuma 基址（如 https://status.example.com）；空=未接入，返回空状态。 */
        private String baseUrl = "";

        /** 监控组定义（slug 列表）；空=未配置 groups，F-4026 返回空数组。 */
        private List<String> slugs = new ArrayList<>();

        /** @return Uptime-Kuma 基址 */
        public String getBaseUrl() {
            return baseUrl;
        }

        /** @param baseUrl Uptime-Kuma 基址 */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /** @return 监控组 slug 列表 */
        public List<String> getSlugs() {
            return slugs;
        }

        /** @param slugs 监控组 slug 列表 */
        public void setSlugs(List<String> slugs) {
            this.slugs = slugs;
        }
    }
}
