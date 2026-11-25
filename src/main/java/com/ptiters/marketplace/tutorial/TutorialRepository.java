package com.ptiters.marketplace.tutorial;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository cơ bản cho Tutorial. Track tự thêm query nghiệp vụ trong package của mình.
 */
public interface TutorialRepository extends JpaRepository<Tutorial, Long> {
}
