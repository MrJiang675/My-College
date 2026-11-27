package com.argus.rag.auth.controller;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.auth.config.AuthProperties;
import com.argus.rag.auth.security.AuthCookieSupport;
import com.argus.rag.auth.service.AuthService;
import com.argus.rag.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
class DemoGuestControllerTest {
    @Test void disabledEndpointDoesNotCreateAccounts() {
        AuthService service = mock(AuthService.class);
        AuthController controller = new AuthController(service, mock(AuthCookieSupport.class),
                mock(CurrentUserService.class), new AuthProperties());
        assertThrows(BusinessException.class, () -> controller.guest(new MockHttpServletResponse()));
        verifyNoInteractions(service);
    }

    @Test void guestCreationIsRateLimited(){
        AuthService service=mock(AuthService.class);
        var controller=new AuthController(service,mock(AuthCookieSupport.class),mock(CurrentUserService.class),new AuthProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(controller,"demoGuestEnabled",true);
        var count=(java.util.concurrent.atomic.AtomicInteger)org.springframework.test.util.ReflectionTestUtils.getField(controller,"guestCount");
        count.set(30);
        assertThrows(BusinessException.class,()->controller.guest(new MockHttpServletResponse()));
        verifyNoInteractions(service);
    }
}
