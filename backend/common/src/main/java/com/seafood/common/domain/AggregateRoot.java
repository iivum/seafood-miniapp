package com.seafood.common.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.time.LocalDateTime;

@Getter
@Setter
public abstract class AggregateRoot<ID> {
    @Id
    protected ID id;

    @Version
    protected Long version;

    protected LocalDateTime createdAt;
    protected LocalDateTime updatedAt;

    public AggregateRoot() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
