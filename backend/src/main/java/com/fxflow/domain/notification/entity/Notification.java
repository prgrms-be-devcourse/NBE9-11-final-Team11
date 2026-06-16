package com.fxflow.domain.notification.entity;

import com.fxflow.domain.notification.enums.NotificationType;
import com.fxflow.domain.user.entity.User;
import com.fxflow.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 알림 수신 유저

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30, nullable = false)
    private NotificationType type; // 알림 유형

    @Column(name = "title", length = 200, nullable = false)
    private String title; // 알림 제목

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message; // 알림 내용

    @Column(name = "is_read", nullable = false)
    private boolean isRead; // 읽음 여부

    private Notification(User user, NotificationType type, String title, String message) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.message = message;
        this.isRead = false; // 생성 시 항상 미읽음 상태로 고정
    }

    // 필수값 누락 및 잘못된 상태의 객체 생성을 막기 위해 정적 팩토리 메서드 사용
    public static Notification create(User user, NotificationType type, String title, String message) {
        return new Notification(user, type, title, message);
    }

    // 읽음 처리 (setter 노출 대신 도메인 메서드로 상태 변경)
    public void markAsRead() {
        this.isRead = true;
    }
}
