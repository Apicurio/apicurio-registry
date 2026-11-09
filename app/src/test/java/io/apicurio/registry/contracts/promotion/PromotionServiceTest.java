package io.apicurio.registry.contracts.promotion;

import io.apicurio.registry.storage.dto.PromotionStage;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromotionServiceTest {

    @Test
    void testBackwardPromotionThrows() {
        assertThrows(BadRequestException.class, () -> {
            callValidate(PromotionStage.PROD, PromotionStage.DEV, "STABLE");
        });
    }

    @Test
    void testSkipStageThrows() {
        assertThrows(BadRequestException.class, () -> {
            callValidate(PromotionStage.DEV, PromotionStage.PROD, "STABLE");
        });
    }

    @Test
    void testProdWithoutStableThrows() {
        assertThrows(BadRequestException.class, () -> {
            callValidate(PromotionStage.STAGE, PromotionStage.PROD, "DRAFT");
        });
    }

    @Test
    void testValidForwardPromotion() {
        assertDoesNotThrow(() -> callValidate(PromotionStage.DEV, PromotionStage.STAGE, "DRAFT"));
    }

    @Test
    void testProdWithStable() {
        assertDoesNotThrow(() -> callValidate(PromotionStage.STAGE, PromotionStage.PROD, "STABLE"));
    }

    @Test
    void testFirstPromotion() {
        assertDoesNotThrow(() -> callValidate(null, PromotionStage.DEV, null));
    }

    private void callValidate(PromotionStage current, PromotionStage target,
            String status) {
        try {
            var method = PromotionService.class.getDeclaredMethod(
                    "validateTransition", PromotionStage.class, PromotionStage.class,
                    java.util.Map.class, String.class);
            method.setAccessible(true);
            java.util.Map<String, String> labels = new java.util.HashMap<>();
            String prefix = "contract.test.";
            if (status != null) {
                labels.put(prefix + "status", status);
            }
            method.invoke(new PromotionService(), current, target, labels, prefix);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof BadRequestException) {
                throw (BadRequestException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
