package com.ptiters.marketplace.communitymoment;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho CommunityMoment. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface CommunityMomentRepository extends JpaRepository<CommunityMoment, Long> {
}
