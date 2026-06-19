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

    @Column(name = "related_type", length = 50) // nullable
    private String relatedType; // 연결 대상 유형 (예: RESERVATION) — 대상 없는 알림은 null

    @Column(name = "related_id", length = 50) // nullable
    private String relatedId; // 연결 대상 식별자 — relatedType과 한 쌍(둘 다 null 또는 둘 다 set)

    @Column(name = "is_read", nullable = false)
    private boolean isRead; // 읽음 여부

    private Notification(User user, NotificationType type, String title, String message,
                         String relatedType, String relatedId) {
        // related_type / related_id 는 한 쌍 — 둘 다 있거나 둘 다 없어야 한다
        if ((relatedType == null) != (relatedId == null)) {
            throw new IllegalArgumentException("relatedType과 relatedId 는 함께 설정하거나 둘 다 null이어야 합니다");
        }
        this.user = user;
        this.type = type;
        this.title = title;
        this.message = message;
        this.relatedType = relatedType;
        this.relatedId = relatedId;
        this.isRead = false; // 생성 시 항상 미읽음 상태로 고정
    }

    // 연결 대상이 없는 일반 알림 (공지사항 등)
    public static Notification create(User user, NotificationType type, String title, String message) {
        return new Notification(user, type, title, message, null, null);
    }

    // 연결 대상(예약·거래 등)으로 딥링크되는 알림
    public static Notification create(User user, NotificationType type, String title, String message,
                                      String relatedType, String relatedId) {
        return new Notification(user, type, title, message, relatedType, relatedId);
    }

    // 읽음 처리 (setter 노출 대신 도메인 메서드로 상태 변경)
    public void markAsRead() {
        this.isRead = true;
    }
}
