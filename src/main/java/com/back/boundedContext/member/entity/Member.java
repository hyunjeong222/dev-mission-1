package com.back.boundedContext.member.entity;

import com.back.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class Member extends BaseIdAndTime {
    @Column(unique = true)
    private String username;
    private String password;
    private String nickname;
    private int activityScore;

    public Member(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
    }

    // 활동 점수 증가
    public int increaseActivityScore(int amount) {
        return this.activityScore += amount;
    }
}
