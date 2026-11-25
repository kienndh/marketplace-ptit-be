package com.ptiters.marketplace.dailyneed;

import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DailyNeedService {

    private static final int MAX_FEED_LIMIT = 10;
    private static final int MAX_CONTENT_CODEPOINTS = 120;

    private final DailyNeedQueryRepository queryRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public DailyNeedListResponse getRecentDailyNeeds(int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, MAX_FEED_LIMIT));
        List<DailyNeedResponse> items = queryRepository.findRecentNotes(clampedLimit).stream()
                .map(d -> toResponse(d, false))
                .toList();
        return new DailyNeedListResponse(items);
    }

    @Transactional(readOnly = true)
    public DailyNeedListResponse getTodayDailyNeeds() {
        LocalDate today = DailyNeedQueryRepository.getVietnamToday();
        List<DailyNeedResponse> items = queryRepository.findTodayNotes(today, MAX_FEED_LIMIT).stream()
                .map(d -> toResponse(d, false))
                .toList();
        return new DailyNeedListResponse(items);
    }

    @Transactional(readOnly = true)
    public DailyNeedResponse getMyTodayDailyNeed(User currentUser) {
        LocalDate today = DailyNeedQueryRepository.getVietnamToday();
        return queryRepository.findUserNoteForDate(currentUser.getUserId(), today)
                .map(d -> toResponse(d, false))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public DailyNeedSummaryResponse getMyDailyNeedSummary(User currentUser) {
        Long userId = currentUser.getUserId();
        LocalDate today = DailyNeedQueryRepository.getVietnamToday();

        DailyNeedResponse todayJournal = queryRepository.findUserNoteForDate(userId, today)
                .map(d -> toResponse(d, false))
                .orElse(null);
        boolean hasJournalToday = todayJournal != null;
        boolean hasCheckedInToday = queryRepository.hasUserCheckedIn(userId, today);

        int streakCount;
        if (hasCheckedInToday) {
            streakCount = queryRepository.calculateUserStreak(userId, today);
        } else {
            LocalDate yesterday = today.minusDays(1);
            if (queryRepository.hasUserCheckedIn(userId, yesterday)) {
                streakCount = queryRepository.calculateUserStreak(userId, yesterday);
            } else {
                streakCount = 0;
            }
        }

        return new DailyNeedSummaryResponse(
                todayJournal,
                hasJournalToday,
                hasCheckedInToday,
                hasCheckedInToday,
                streakCount
        );
    }

    @Transactional(readOnly = true)
    public DailyNeedPublicResponse getUserTodayDailyNeed(UUID publicId) {
        if (publicId == null) {
            throw ApiException.notFound("Không tìm thấy người dùng.");
        }
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.notFound("Không tìm thấy người dùng.");
        }

        LocalDate today = DailyNeedQueryRepository.getVietnamToday();
        DailyNeed note = queryRepository.findUserNoteForDate(user.getUserId(), today).orElse(null);
        if (note == null) {
            return null;
        }

        return new DailyNeedPublicResponse(
                note.getDailyNeedId(),
                note.getContent(),
                note.getNoteDate(),
                note.getUpdatedAt(),
                toAuthor(user)
        );
    }

    public DailyNeedResponse upsertMyTodayDailyNeed(User currentUser, DailyNeedCreateOrUpdateRequest request) {
        String normalizedContent = normalizeAndValidateContent(request != null ? request.content() : null);
        LocalDate today = DailyNeedQueryRepository.getVietnamToday();

        DataIntegrityViolationException lastConflict = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                DailyNeedResponse result = transactionTemplate.execute(status -> {
                    queryRepository.upsertUserNote(currentUser.getUserId(), today, normalizedContent);
                    boolean checkinCreated = queryRepository.recordCheckin(currentUser.getUserId(), today);
                    DailyNeed saved = queryRepository.findUserNoteForDate(currentUser.getUserId(), today)
                            .orElseThrow(() -> new ApiException(
                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Không thể lưu nhật ký hôm nay."
                            ));
                    return toResponse(saved, checkinCreated);
                });
                if (result != null) {
                    return result;
                }
            } catch (DataIntegrityViolationException e) {
                lastConflict = e;
            }
        }
        throw ApiException.conflict("Xung đột cập nhật nhật ký hôm nay, vui lòng thử lại: "
                + (lastConflict != null ? lastConflict.getMostSpecificCause().getMessage() : ""));
    }

    @Transactional
    public void deleteMyTodayDailyNeed(User currentUser) {
        LocalDate today = DailyNeedQueryRepository.getVietnamToday();
        queryRepository.deleteUserNoteForDate(currentUser.getUserId(), today);
    }

    public static String normalizeAndValidateContent(String rawContent) {
        if (rawContent == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung nhật ký không được để trống.");
        }
        String trimmed = rawContent.strip();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung nhật ký không được để trống.");
        }
        String normalized = String.join(" ", trimmed.split("\\s+"));
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung nhật ký không được để trống.");
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_CONTENT_CODEPOINTS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung nhật ký tối đa 120 ký tự.");
        }
        return normalized;
    }

    private static DailyNeedAuthor toAuthor(User user) {
        return new DailyNeedAuthor(
                user.getPublicId(),
                user.getFullName(),
                user.getAvatarUrl()
        );
    }

    private static DailyNeedResponse toResponse(DailyNeed d, boolean checkinCreated) {
        return new DailyNeedResponse(
                d.getDailyNeedId(),
                d.getContent(),
                d.getNoteDate(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                toAuthor(d.getUser()),
                checkinCreated
        );
    }
}
