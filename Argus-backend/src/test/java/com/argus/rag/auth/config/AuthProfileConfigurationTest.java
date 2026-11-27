package com.argus.rag.auth.config;

import com.argus.rag.auth.service.PasswordHasher;
import com.argus.rag.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthProfileConfigurationTest {

    @Test
    void devAdminInitializerIsAbsentInProd() {
        try (AnnotationConfigApplicationContext context = context("prod")) {
            assertThat(context.getBeansOfType(DevAdminInitializer.class)).isEmpty();
        }
    }

    @Test
    void devAdminInitializerIsPresentInDev() {
        try (AnnotationConfigApplicationContext context = context("dev")) {
            assertThat(context.getBeansOfType(DevAdminInitializer.class)).hasSize(1);
        }
    }

    private AnnotationConfigApplicationContext context(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.registerBean(UserMapper.class, () -> mock(UserMapper.class));
        context.registerBean(PasswordHasher.class, () -> mock(PasswordHasher.class));
        context.register(DevAdminInitializer.class);
        context.refresh();
        return context;
    }
}
