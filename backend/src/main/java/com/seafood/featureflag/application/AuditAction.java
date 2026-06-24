package com.seafood.featureflag.application;

/**
 * Feature flag 审计操作类型枚举。
 */
public enum AuditAction {
    ENABLE, DISABLE, PERCENTAGE_CHANGE, WHITELIST_ADD, WHITELIST_REMOVE
}
