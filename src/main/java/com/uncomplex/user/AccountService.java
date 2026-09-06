package com.uncomplex.user;

import com.uncomplex.config.DatabaseMutex;
import com.uncomplex.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account erasure. Every table referencing app_user declares ON DELETE CASCADE, so
 * removing the row also removes that user's saved roadmaps, per-node progress and
 * refresh tokens. Generated roadmaps are deliberately untouched: they are shared,
 * immutable, and carry no personal data, so one user leaving must not delete content
 * other people's libraries and share links point at.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final DatabaseMutex mutex;

    public AccountService(UserRepository users, DatabaseMutex mutex) {
        this.users = users;
        this.mutex = mutex;
    }

    @Transactional
    public void deleteAccount(Long userId) {
        // Same key the library mutations take, so an in-flight save or progress update
        // cannot land against a user row that is being removed.
        mutex.acquire("library:" + userId);
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("No account found"));
        users.delete(user);
        log.info("Deleted account {} and all data referencing it", userId);
    }
}
