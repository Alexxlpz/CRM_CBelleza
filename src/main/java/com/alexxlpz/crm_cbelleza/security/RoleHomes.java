package com.alexxlpz.crm_cbelleza.security;

import com.alexxlpz.crm_cbelleza.entities.Role;

/** Página de inicio de cada rol tras iniciar sesión o al intentar acceder a una zona ajena. */
public final class RoleHomes {

    public static final String CLIENT_HOME = "/centers";
    public static final String WORKER_HOME = "/worker/dashboard";
    public static final String PUBLIC_HOME = "/home";

    private RoleHomes() {
    }

    public static String homeFor(Role role) {
        if (role == null) {
            return PUBLIC_HOME;
        }
        return switch (role) {
            case CLIENT -> CLIENT_HOME;
            case WORKER -> WORKER_HOME;
        };
    }

    public static String homeFor(AppUserDetails user) {
        if (user == null) {
            return PUBLIC_HOME;
        }
        if (user.isWorker() && user.getCenterId() == null) {
            return PUBLIC_HOME;
        }
        return homeFor(user.getRole());
    }
}
