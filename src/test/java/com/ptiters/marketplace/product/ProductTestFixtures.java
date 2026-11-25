package com.ptiters.marketplace.product;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ptiters.marketplace.category.Category;
import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.category.CategoryTestFixtures;
import com.ptiters.marketplace.configuration.JwtConfig;
import com.ptiters.marketplace.user.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class ProductTestFixtures {

    public static final String TEST_PROJECT_ID = "test-marketplace-ptit";
    public static final String TEST_ISSUER = "https://securetoken.google.com/" + TEST_PROJECT_ID;
    public static final String TEST_KID = "test-product-key-001";

    public static final KeyPair KEY_PAIR;
    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KEY_PAIR = kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JwtDecoder createTestJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
        decoder.setJwtValidator(JwtConfig.createFirebaseJwtValidator(TEST_PROJECT_ID));
        return decoder;
    }

    public static String createToken(String uid, String email) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(TEST_ISSUER)
                    .audience(TEST_PROJECT_ID)
                    .subject(uid)
                    .claim("user_id", uid)
                    .claim("email", email)
                    .claim("email_verified", true)
                    .claim("auth_time", Date.from(now.minusSeconds(10)))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(TEST_KID)
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(KEY_PAIR.getPrivate()));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static User createUser(UserRepository repo, String email, String name, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(name);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPublicId(UUID.randomUUID());
        return repo.save(user);
    }

    public static UserAuthIdentity createAuthIdentity(UserAuthIdentityRepository repo, User user, String providerUid) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(UserAuthIdentity.PROVIDER_FIREBASE);
        identity.setProviderUid(providerUid);
        return repo.save(identity);
    }

    public static Category createCategory(CategoryRepository repo, String namePrefix) {
        Category c = CategoryTestFixtures.createSyntheticCategory(namePrefix);
        return repo.save(c);
    }

    public static Product createProduct(
            ProductRepository repo,
            User seller,
            Category category,
            String title,
            BigDecimal price,
            ProductStatus status,
            ProductApprovalStatus approvalStatus
    ) {
        Product p = Product.builder()
                .seller(seller)
                .category(category)
                .title(title)
                .description("Mô tả sản phẩm kiểm thử " + title)
                .price(price)
                .listingType(ListingType.FIXED_PRICE)
                .stock(10)
                .reservedStock(0)
                .status(status)
                .approvalStatus(approvalStatus)
                .moderationStatus(ProductModerationStatus.CLEAR)
                .viewCount(0)
                .isDeleted(false)
                .build();
        return repo.save(p);
    }
}
