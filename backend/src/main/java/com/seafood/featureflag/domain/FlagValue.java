package com.seafood.featureflag.domain;

/**
 * Feature flag 求值结果值对象，携带结论与原因便于调试日志。
 */
public record FlagValue(boolean enabled, EvalReason reason) {
    public enum EvalReason { WHITELIST, ROLLOUT, DISABLED, EXPIRED }
}
