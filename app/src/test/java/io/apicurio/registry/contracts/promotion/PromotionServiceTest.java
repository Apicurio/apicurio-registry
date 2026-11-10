package io.apicurio.registry.contracts.promotion;

import io.apicurio.registry.storage.dto.ContractStatus;
import io.apicurio.registry.storage.dto.PromotionStage;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromotionServiceTest {

    private final PromotionService service = new PromotionService();

    @Test
    void testBackwardPromotionThrows() {
        assertThrows(BadRequestException.class, () -> service.validateTransition(PromotionStage.PROD,
                PromotionStage.DEV, ContractStatus.STABLE));
    }

    @Test
    void testSkipStageThrows() {
        BadRequestException e = assertThrows(BadRequestException.class, () -> service.validateTransition(
                PromotionStage.DEV, PromotionStage.PROD, ContractStatus.STABLE));
        assertEquals("Cannot skip stages. Promote to STAGE first.", e.getMessage());
    }

    @Test
    void testProdWithoutStableThrows() {
        assertThrows(BadRequestException.class, () -> service.validateTransition(PromotionStage.STAGE,
                PromotionStage.PROD, ContractStatus.DRAFT));
    }

    @Test
    void testValidForwardPromotion() {
        assertDoesNotThrow(() -> service.validateTransition(PromotionStage.DEV, PromotionStage.STAGE,
                ContractStatus.DRAFT));
    }

    @Test
    void testProdWithStable() {
        assertDoesNotThrow(() -> service.validateTransition(PromotionStage.STAGE, PromotionStage.PROD,
                ContractStatus.STABLE));
    }

    @Test
    void testFirstPromotion() {
        assertDoesNotThrow(() -> service.validateTransition(null, PromotionStage.DEV, null));
    }

    @Test
    void testFirstPromotionCannotSkipDev() {
        BadRequestException toStage = assertThrows(BadRequestException.class,
                () -> service.validateTransition(null, PromotionStage.STAGE, ContractStatus.STABLE));
        assertEquals("Cannot skip stages. Promote to DEV first.", toStage.getMessage());
        BadRequestException toProd = assertThrows(BadRequestException.class,
                () -> service.validateTransition(null, PromotionStage.PROD, ContractStatus.STABLE));
        assertEquals("Cannot skip stages. Promote to DEV first.", toProd.getMessage());
    }
}
