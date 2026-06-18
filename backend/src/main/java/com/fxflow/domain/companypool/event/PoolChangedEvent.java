package com.fxflow.domain.companypool.event;

import org.springframework.context.ApplicationEvent;

public class PoolChangedEvent extends ApplicationEvent {

    public PoolChangedEvent(Object source) {
        super(source);
    }
}