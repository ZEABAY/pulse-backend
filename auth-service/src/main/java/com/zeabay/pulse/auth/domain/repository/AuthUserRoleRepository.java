package com.zeabay.pulse.auth.domain.repository;

import com.zeabay.pulse.auth.domain.model.AuthUserRole;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthUserRoleRepository extends R2dbcRepository<AuthUserRole, Long> {}
