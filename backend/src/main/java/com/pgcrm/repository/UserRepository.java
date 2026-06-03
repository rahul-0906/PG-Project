package com.pgcrm.repository;

import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link User} entity.
 *
 * <p>Provides authentication and user-management query operations against the
 * {@code users} table. This repository is used by the security layer for
 * JWT-based authentication and by the owner dashboard for user account management.</p>
 *
 * <p><strong>Authentication Flow:</strong> The {@code AuthService} and
 * Spring Security {@code UserDetailsService} implementation call
 * {@link #findByEmailIgnoreCase(String)} as the primary lookup during login.
 * The custom JPQL query uses {@code LOWER()} on both sides of the comparison to
 * guarantee case-insensitive matching across all database collations, including
 * those that are case-sensitive by default (e.g., PostgreSQL with the {@code C} locale).</p>
 *
 * <p><strong>Role-Based Filtering:</strong> The {@link #findByRole(Role)} and
 * {@link #findByRoleAndBranchId(Role, String)} methods support the Owner's user
 * management dashboard, enabling filtered listing of managers by role and
 * building assignment.</p>
 *
 * @see User
 * @see Role
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Finds a user by their email address using a case-insensitive comparison.
     *
     * <p>This is the primary authentication lookup method. The JPQL {@code LOWER()}
     * function is applied to both the stored email and the input parameter to ensure
     * consistent matching regardless of how the email was originally stored or entered.
     * The database index {@code idx_user_email} on the {@code email} column optimises
     * this query.</p>
     *
     * @param email the email address to search for (any case).
     * @return an {@link Optional} containing the matching {@link User},
     *         or {@link Optional#empty()} if no account exists with that email.
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    /**
     * Checks whether a user account with the given email already exists.
     *
     * <p>Used by the {@code GuestService} and {@code UserService} as a duplicate-email
     * guard before creating new accounts. Performs a case-sensitive existence check at
     * the database level; pair with a {@code .toLowerCase()} call on the input if
     * case-insensitive duplicate detection is required.</p>
     *
     * @param email the email address to check.
     * @return {@code true} if an account with this email exists; {@code false} otherwise.
     */
    boolean existsByEmail(String email);

    /**
     * Returns all users with the specified application role.
     *
     * <p>Used by the Owner's user management dashboard to list all accounts of a given
     * role (e.g., retrieve all {@link Role#PG_MANAGER} accounts for the manager list view).</p>
     *
     * @param role the {@link Role} to filter by.
     * @return a {@link List} of {@link User} entities with the given role;
     *         empty list if none exist.
     */
    List<User> findByRole(Role role);

    /**
     * Returns all users with the specified role who are assigned to a specific branch.
     *
     * <p>Used by the Owner's manager management view to list managers assigned to a
     * particular building. The {@code branchId} field is a comma-separated string of
     * building UUIDs on the {@link User} entity; this query performs an exact string
     * match on the full {@code branchId} value. For partial (contains) matching,
     * a custom {@code LIKE} query would be required.</p>
     *
     * @param role     the {@link Role} to filter by (typically {@link Role#PG_MANAGER}).
     * @param branchId the exact branch ID string to match against {@link User#getBranchId()}.
     * @return a {@link List} of {@link User} entities matching both role and branch ID;
     *         empty list if none exist.
     */
    List<User> findByRoleAndBranchId(Role role, String branchId);
}
