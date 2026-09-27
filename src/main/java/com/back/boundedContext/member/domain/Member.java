package com.back.boundedContext.member.domain;

import com.back.shared.member.domain.SourceMember;
import com.back.shared.member.dto.MemberDto;
import com.back.shared.member.event.MemberModifiedEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "MEMBER_MEMBER")
@NoArgsConstructor
@Getter
public class Member extends SourceMember {

    public Member(String username, String password, String nickname) {
        super(username, password, nickname);
    }

    // 활동 점수 증가
    public int increaseActivityScore(int amount) {
        // 변경량이 0이면 이벤트를 발행 X
        if (amount == 0) return getActivityScore();

        setActivityScore(getActivityScore() + amount);

        publishEvent(
                new MemberModifiedEvent(new MemberDto(this))
        );

        return getActivityScore();
    }
}
