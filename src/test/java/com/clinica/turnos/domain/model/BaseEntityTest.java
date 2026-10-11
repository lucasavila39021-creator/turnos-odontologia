package com.clinica.turnos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    @Test
    void baseEntityUsaLocalDateTime() {
        // Verifica que BaseEntity usa LocalDateTime no Instant (RN-GL-02, DATETIME)
        assertThat(BaseEntity.class.getDeclaredFields())
                .anySatisfy(f -> {
                    if (f.getName().equals("createdAt")) {
                        assertThat(f.getType()).isEqualTo(LocalDateTime.class);
                    }
                });
    }

    @Test
    void prePersistYPreUpdateCallbacksExisten() throws Exception {
        Method onCreate = BaseEntity.class.getDeclaredMethod("onCreate");
        Method onUpdate = BaseEntity.class.getDeclaredMethod("onUpdate");
        assertThat(onCreate.isAnnotationPresent(PrePersist.class)).isTrue();
        assertThat(onUpdate.isAnnotationPresent(PreUpdate.class)).isTrue();
    }

    @Test
    void onCreateSeteaTimestamps() {
        Usuario u = new Usuario("test", "$2a$12$hash", "a@b.com", Rol.PACIENTE, true);
        // Simula PrePersist via reflection
        try {
            var m = BaseEntity.class.getDeclaredMethod("onCreate");
            m.setAccessible(true);
            m.invoke(u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(u.getCreatedAt()).isNotNull();
        assertThat(u.getUpdatedAt()).isNotNull();
        assertThat(u.getCreatedAt()).isEqualTo(u.getUpdatedAt());
    }

    @Test
    void onUpdateActualizaUpdatedAt() throws InterruptedException {
        Usuario u = new Usuario("test2", "$2a$12$hash", "a@b.com", Rol.PACIENTE, true);
        try {
            var mCreate = BaseEntity.class.getDeclaredMethod("onCreate");
            mCreate.setAccessible(true);
            mCreate.invoke(u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LocalDateTime before = u.getUpdatedAt();
        Thread.sleep(10);
        try {
            var mUpdate = BaseEntity.class.getDeclaredMethod("onUpdate");
            mUpdate.setAccessible(true);
            mUpdate.invoke(u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(u.getUpdatedAt()).isAfterOrEqualTo(before);
    }
}
