package com.ptiters.marketplace.favorite;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/ids")
    public ResponseEntity<FavoriteIdsResponse> getFavoriteIds() {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        FavoriteIdsResponse response = favoriteService.getFavoriteIds(currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<FavoriteListResponse> listFavorites(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        FavoriteListResponse response = favoriteService.listFavorites(currentUser, limit, cursor);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Void> addFavorite(@PathVariable Long productId) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        favoriteService.addFavorite(currentUser, productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long productId) {
        User currentUser = currentUserResolver.getCurrentActiveUser();
        favoriteService.removeFavorite(currentUser, productId);
        return ResponseEntity.noContent().build();
    }
}
