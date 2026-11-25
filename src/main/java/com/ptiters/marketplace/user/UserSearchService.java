package com.ptiters.marketplace.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tìm kiếm người dùng công khai cho mời nhóm (Python {@code ConversationService.search_users}):
 * chỉ trả user_id, public_id, full_name, avatar_url của user ACTIVE, loại chính mình và (tuỳ chọn) thành viên nhóm.
 * {@code UserService.searchUsers} gọi service này (đã nối ở bước gộp Wave 2).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSearchService {

    private final AdminUserQueryRepository repo;

    public List<UserSearchResponse> search(Long currentUserId, String query, int limit, Long excludeConversationId) {
        return repo.searchActiveUsers(query, limit, currentUserId, excludeConversationId).stream()
                .map(u -> new UserSearchResponse(u.getUserId(), u.getPublicId(), u.getFullName(), u.getAvatarUrl()))
                .toList();
    }
}
