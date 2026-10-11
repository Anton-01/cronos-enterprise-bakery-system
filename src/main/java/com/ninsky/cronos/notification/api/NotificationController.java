package com.ninsky.cronos.notification.api;

import com.ninsky.cronos.application.response.core.ApiResponse;
import com.ninsky.cronos.identity.infrastructure.AuthRateLimiter;
import com.ninsky.cronos.infrastructure.util.auth.RequestContextUtil;
import com.ninsky.cronos.infrastructure.web.StrictApiContract;
import com.ninsky.cronos.infrastructure.web.paging.CatalogPage;
import com.ninsky.cronos.kitchen.shared.KitchenMessages;
import com.ninsky.cronos.notification.application.NotificationInboxService;
import com.ninsky.cronos.notification.application.NotificationSettingsService;
import com.ninsky.cronos.notification.application.PushService;
import com.ninsky.cronos.notification.domain.NotificationCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The caller's notifications (§6.2): inbox, preferences, live stream and Web Push devices. Everything is scoped to the
 * authenticated user; no permission beyond authentication. The stream (ticket) and the push renewal (old endpoint)
 * authenticate without a bearer token.
 */
@RestController
@StrictApiContract
@RequestMapping("/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "Inbox, preferences, live stream (SSE) and Web Push")
public class NotificationController {

    private final NotificationInboxService inbox;
    private final NotificationSettingsService settings;
    private final PushService push;
    private final AuthRateLimiter rateLimiter;
    private final RequestContextUtil requestContext;
    private final KitchenMessages messages;

    public NotificationController(NotificationInboxService inbox, NotificationSettingsService settings, PushService push,
                                  AuthRateLimiter rateLimiter, RequestContextUtil requestContext, KitchenMessages messages) {
        this.inbox = inbox;
        this.settings = settings;
        this.push = push;
        this.rateLimiter = rateLimiter;
        this.requestContext = requestContext;
        this.messages = messages;
    }

    // ─── inbox ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Inbox", description = "Newest first; size at most 50")
    public ResponseEntity<ApiResponse<CatalogPage<AppNotification>>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(required = false) Set<NotificationCategory> categories) {
        return ResponseEntity.ok(ApiResponse.success(null, inbox.page(page, size, unreadOnly, categories)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notifications, total and per category")
    public ResponseEntity<ApiResponse<UnreadCount>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success(null, inbox.unreadCount()));
    }

    @PostMapping("/read")
    @Operation(summary = "Mark notifications read", description = "At most 100 ids; the user's other tabs are told")
    public ResponseEntity<ApiResponse<UnreadCount>> read(@Valid @RequestBody MarkReadRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, inbox.markRead(request.ids())));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark every notification read")
    public ResponseEntity<ApiResponse<UnreadCount>> readAll() {
        return ResponseEntity.ok(ApiResponse.success(null, inbox.markAllRead()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a notification")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        inbox.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    // ─── preferences ───────────────────────────────────────────────────────────────────

    @GetMapping("/settings")
    @Operation(summary = "Notification preferences", description = "Seeded with the defaults on first read")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(null, settings.get()));
    }

    @PutMapping("/settings")
    @Operation(summary = "Change notification preferences", description = "Requires version; locked channels stay on")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> putSettings(@Valid @RequestBody NotificationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, settings.update(request)));
    }

    // ─── live stream ───────────────────────────────────────────────────────────────────

    @PostMapping("/stream/ticket")
    @Operation(summary = "Ticket for the live stream", description = "Single use, valid 60 seconds, bound to the session")
    public ResponseEntity<ApiResponse<StreamTicket>> ticket() {
        return ResponseEntity.ok(ApiResponse.success(null, inbox.ticket()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live stream (Server-Sent Events)",
            description = "Authenticated by ?ticket=; frames notification, read-state and domain; Last-Event-ID replays missed notifications")
    public ResponseEntity<SseEmitter> stream(@RequestParam(required = false) String ticket,
                                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return inbox.open(ticket, lastEventId).map(emitter -> ResponseEntity.ok().header("X-Accel-Buffering", "no").body(emitter))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    // ─── web push ──────────────────────────────────────────────────────────────────────

    @GetMapping("/push/config")
    @Operation(summary = "Web Push availability and VAPID public key")
    public ResponseEntity<ApiResponse<PushConfig>> pushConfig() {
        return ResponseEntity.ok(ApiResponse.success(null, push.config()));
    }

    @GetMapping("/push/subscriptions")
    @Operation(summary = "The caller's push devices")
    public ResponseEntity<ApiResponse<List<PushDevice>>> devices() {
        return ResponseEntity.ok(ApiResponse.success(null, push.devices()));
    }

    @PostMapping("/push/subscriptions")
    @Operation(summary = "Subscribe this browser", description = "Upsert by endpoint; at most 10 devices per user")
    public ResponseEntity<ApiResponse<PushDevice>> subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, push.subscribe(request)));
    }

    @PostMapping("/push/subscriptions/unsubscribe")
    @Operation(summary = "Unsubscribe this browser")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@Valid @RequestBody EndpointRequest request) {
        push.unsubscribe(request.endpoint());
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @DeleteMapping("/push/subscriptions/{id}")
    @Operation(summary = "Remove a device")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable UUID id) {
        push.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    @PostMapping("/push/subscriptions/renew")
    @Operation(summary = "Renew a subscription (service worker)",
            description = "Public and rate-limited per IP; replaces the subscription only when oldEndpoint is still active, else 404")
    public ResponseEntity<ApiResponse<Void>> renew(@Valid @RequestBody PushRenewRequest request) {
        rateLimiter.acquire(AuthRateLimiter.Limit.PUSH_RENEW_IP, requestContext.getClientIp());
        return push.renew(request.oldEndpoint(), request.subscription())
                ? ResponseEntity.ok(ApiResponse.success(null, null))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @PostMapping("/push/test")
    @Operation(summary = "Send a test push", description = "To one device or all; synchronous, not stored in the inbox")
    public ResponseEntity<ApiResponse<PushTestResult>> test(@RequestBody(required = false) PushTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success(null, push.test(request == null ? null : request.deviceId(),
                messages.get("notification.push.test.title"), messages.get("notification.push.test.body"))));
    }
}
