package com.ptiters.marketplace.favorite;

import java.time.LocalDateTime;
import java.util.List;

public interface FavoriteRepositoryCustom {

    record PaginatedResult<T>(
            List<T> items,
            boolean hasMore,
            String nextCursor
    ) {}

    PaginatedResult<Favorite> listFavorites(
            Long userId,
            int limit,
            LocalDateTime cursorTime,
            Long cursorPid
    );
}
