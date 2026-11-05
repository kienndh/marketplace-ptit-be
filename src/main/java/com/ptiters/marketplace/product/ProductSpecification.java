
package com.ptiters.marketplace.product;

import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class ProductSpecification {

    public static Specification<Product> buildSpec(
            ProductFilterForm form,
            LocalDateTime cursorTime,
            Long cursorId
    ) {
        return new Specification<Product>() {

            @Override
            public @Nullable Predicate toPredicate(
                    Root<Product> root,
                    CriteriaQuery<?> query,
                    CriteriaBuilder builder
            ) {
                var predicates = new ArrayList<Predicate>();


                Root<User> userRoot = query.from(User.class);

                predicates.add(
                        builder.equal(
                                root.get("sellerId"),
                                userRoot.get("userId")
                        )
                );

                predicates.add(
                        builder.equal(
                                userRoot.get("status"),
                                UserStatus.ACTIVE
                        )
                );


                predicates.add(
                        builder.isFalse(root.get("deleted"))
                );

                predicates.add(
                        builder.equal(root.get("status"), "AVAILABLE")
                );

                predicates.add(
                        builder.equal(root.get("moderationStatus"), "CLEAR")
                );

                if (form != null) {

                    if (form.getCategoryId() != null) {
                        predicates.add(
                                builder.equal(
                                        root.get("categoryId"),
                                        form.getCategoryId()
                                )
                        );
                    }

                    if (form.getMinPrice() != null) {
                        predicates.add(
                                builder.greaterThanOrEqualTo(
                                        root.get("price"),
                                        form.getMinPrice()
                                )
                        );
                    }

                    if (form.getMaxPrice() != null) {
                        predicates.add(
                                builder.lessThanOrEqualTo(
                                        root.get("price"),
                                        form.getMaxPrice()
                                )
                        );
                    }

                    if (form.getListingType() != null) {
                        predicates.add(
                                builder.equal(
                                        root.get("listingType"),
                                        form.getListingType()
                                )
                        );
                    }
                }

                if (cursorTime != null && cursorId != null) {

                    Predicate olderTime = builder.lessThan(
                            root.get("createdAt"),
                            cursorTime
                    );

                    Predicate sameTimeSmallerId = builder.and(
                            builder.equal(
                                    root.get("createdAt"),
                                    cursorTime
                            ),
                            builder.lessThan(
                                    root.get("productId"),
                                    cursorId
                            )
                    );

                    predicates.add(
                            builder.or(olderTime, sameTimeSmallerId)
                    );
                }

                return builder.and(
                        predicates.toArray(new Predicate[0])
                );
            }
        };
    }
}
