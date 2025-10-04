package xiaozhi.modules.sys.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import xiaozhi.common.utils.Result;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.sys.dto.UserPremiumSubscriptionDTO;
import xiaozhi.modules.sys.entity.SysUserEntity;
import xiaozhi.modules.sys.service.UserPremiumSubscriptionService;
import xiaozhi.modules.sys.service.SysUserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 支付回调处理控制器
 * 用于处理来自Shopify支付服务的webhook通知
 */
@RestController
@RequestMapping("/admin/payment")
@Tag(name = "Shopify Payment Webhook Management")
@AllArgsConstructor
@Slf4j
public class PaymentWebhookController {
    
    private final UserPremiumSubscriptionService premiumSubscriptionService;
    private final SysUserService sysUserService;
    
    @PostMapping("webhook/shopify")
    @Operation(summary = "Handle Shopify webhook notifications")
    public Result<String> handleShopifyWebhook(@RequestBody Map<String, Object> payload,
                                               @RequestHeader("X-Shopify-Hmac-Sha256") String signature) {
        try {
            log.info("Received Shopify webhook: {}", payload.get("id"));
            
            // Verify webhook signature (implement signature verification as needed)
            if (!verifyShopifySignature(payload, signature)) {
                log.warn("Invalid Shopify webhook signature");
                return new Result<String>().error("Invalid signature");
            }
            
            // Check if this is a successful order
            String financialStatus = (String) payload.get("financial_status");
            if ("paid".equals(financialStatus)) {
                handleSuccessfulPayment(payload, "SHOPIFY");
            } else if ("refunded".equals(financialStatus) || "cancelled".equals(financialStatus)) {
                handleFailedPayment(payload, "SHOPIFY");
            }
            
            return new Result<String>().ok("Webhook processed successfully");
        } catch (Exception e) {
            log.error("Error processing Shopify webhook: {}", e.getMessage(), e);
            return new Result<String>().error("Webhook processing failed: " + e.getMessage());
        }
    }

    @PostMapping("subscription/create")
    @Operation(summary = "Manually create premium subscription (for testing)")
    public Result<String> createTestSubscription(@RequestParam Long userId,
                                                 @RequestParam String subscriptionType,
                                                 @RequestParam BigDecimal amount) {
        try {
            UserPremiumSubscriptionDTO subscription = new UserPremiumSubscriptionDTO();
            subscription.setUserId(userId);
            subscription.setSubscriptionType(subscriptionType);
            subscription.setPaymentAmount(amount);
            subscription.setPaymentCurrency("USD");
            subscription.setPaymentMethod("SHOPIFY");
            subscription.setPaymentTransactionId("test_" + System.currentTimeMillis());
            subscription.setSubscriptionStartDate(new Date());
            
            // Set end date based on subscription type
            LocalDateTime endDate = LocalDateTime.now();
            if ("MONTHLY".equals(subscriptionType)) {
                endDate = endDate.plusMonths(1);
            } else if ("YEARLY".equals(subscriptionType)) {
                endDate = endDate.plusYears(1);
            } else {
                endDate = endDate.plusDays(30); // Default to 30 days
            }
            subscription.setSubscriptionEndDate(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
            
            premiumSubscriptionService.createSubscription(subscription);
            
            return new Result<String>().ok("Test subscription created successfully");
        } catch (Exception e) {
            log.error("Error creating test subscription: {}", e.getMessage(), e);
            return new Result<String>().error("Failed to create subscription: " + e.getMessage());
        }
    }

    /**
     * Test endpoint for premium status checking without authentication
     * This endpoint is for testing purposes only
     */
    @GetMapping("/test/premium-status/{userId}")
    @Operation(summary = "Test Premium Status (No Auth Required)")
    public Result<Map<String, Object>> testPremiumStatus(@PathVariable Long userId) {
        try {
            log.info("=== TESTING PREMIUM STATUS ===");
            log.info("Checking premium status for user: {}", userId);
            
            boolean isPremium = premiumSubscriptionService.isUserPremium(userId);
            
            // Get additional info for debugging
            UserPremiumSubscriptionDTO activeSubscription = premiumSubscriptionService.getActiveSubscriptionByUserId(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("isPremium", isPremium);
            result.put("hasActiveSubscription", activeSubscription != null);
            result.put("subscriptionDetails", activeSubscription);
            result.put("timestamp", new Date());
            
            log.info("Premium status result: isPremium={}, hasActiveSubscription={}", 
                    isPremium, activeSubscription != null);
            
            return new Result<Map<String, Object>>().ok(result);
        } catch (Exception e) {
            log.error("Error checking premium status for user {}: {}", userId, e.getMessage(), e);
            return new Result<Map<String, Object>>().error("Error checking premium status: " + e.getMessage());
        }
    }

    /**
     * Test endpoint to create a sample premium subscription
     */
    @PostMapping("/test/create-subscription")
    @Operation(summary = "Create Test Subscription (No Auth Required)")
    public Result<String> createTestSubscriptionV2(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "MONTHLY") String subscriptionType,
            @RequestParam(defaultValue = "29.99") String amount) {
        try {
            log.info("=== CREATING TEST SUBSCRIPTION ===");
            log.info("Creating test subscription for user: {} type: {} amount: {}", userId, subscriptionType, amount);
            
            UserPremiumSubscriptionDTO dto = new UserPremiumSubscriptionDTO();
            dto.setUserId(userId);
            dto.setSubscriptionType(subscriptionType);
            dto.setPaymentAmount(new BigDecimal(amount));
            dto.setPaymentCurrency("USD");
            dto.setStatus("ACTIVE");
            dto.setPaymentMethod("SHOPIFY");
            dto.setPaymentTransactionId("TEST-" + System.currentTimeMillis());
            
            // Set subscription dates
            Date now = new Date();
            dto.setSubscriptionStartDate(now);
            
            // Calculate end date based on subscription type
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            if ("MONTHLY".equals(subscriptionType)) {
                cal.add(Calendar.MONTH, 1);
            } else if ("YEARLY".equals(subscriptionType)) {
                cal.add(Calendar.YEAR, 1);
            } else {
                cal.add(Calendar.MONTH, 1); // Default to monthly
            }
            dto.setSubscriptionEndDate(cal.getTime());
            
            premiumSubscriptionService.createSubscription(dto);
            
            log.info("Test subscription created successfully for user: {}", userId);
            return new Result<String>().ok("Test subscription created successfully");
        } catch (Exception e) {
            log.error("Error creating test subscription for user {}: {}", userId, e.getMessage(), e);
            return new Result<String>().error("Error creating test subscription: " + e.getMessage());
        }
    }

    /**
     * Test endpoint to list all subscriptions for debugging
     */
    @GetMapping("/test/list-subscriptions")
    @Operation(summary = "List All Subscriptions (No Auth Required)")
    public Result<List<Map<String, Object>>> listAllSubscriptions() {
        try {
            log.info("=== LISTING ALL SUBSCRIPTIONS ===");
            
            Map<String, Object> params = new HashMap<>();
            PageData<UserPremiumSubscriptionDTO> pageData = premiumSubscriptionService.page(params);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (UserPremiumSubscriptionDTO dto : pageData.getList()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", dto.getId());
                item.put("userId", dto.getUserId());
                item.put("subscriptionType", dto.getSubscriptionType());
                item.put("status", dto.getStatus());
                item.put("paymentAmount", dto.getPaymentAmount());
                item.put("paymentCurrency", dto.getPaymentCurrency());
                item.put("paymentMethod", dto.getPaymentMethod());
                item.put("paymentTransactionId", dto.getPaymentTransactionId());
                item.put("subscriptionStartDate", dto.getSubscriptionStartDate());
                item.put("subscriptionEndDate", dto.getSubscriptionEndDate());
                item.put("createDate", dto.getCreateDate());
                result.add(item);
            }
            
            log.info("Found {} subscriptions", result.size());
            return new Result<List<Map<String, Object>>>().ok(result);
        } catch (Exception e) {
            log.error("Error listing subscriptions: {}", e.getMessage(), e);
            return new Result<List<Map<String, Object>>>().error("Error listing subscriptions: " + e.getMessage());
        }
    }

    /**
     * Test endpoint to check database connectivity
     */
    @GetMapping("/test/db-check")
    @Operation(summary = "Database Connectivity Check (No Auth Required)")
    public Result<Map<String, Object>> testDatabaseConnectivity() {
        try {
            log.info("=== TESTING DATABASE CONNECTIVITY ===");
            
            Map<String, Object> result = new HashMap<>();
            
            // Test user table access
            SysUserEntity testUser = sysUserService.selectById(1960603563958550529L);
            result.put("userTableAccess", testUser != null);
            result.put("testUserId", 1960603563958550529L);
            result.put("userExists", testUser != null);
            if (testUser != null) {
                result.put("username", testUser.getUsername());
                result.put("userPremiumStatus", testUser.getIsPremium());
                result.put("premiumExpiresAt", testUser.getPremiumExpiresAt());
            }
            
            // Test subscription table access
            UserPremiumSubscriptionDTO activeSubscription = premiumSubscriptionService.getActiveSubscriptionByUserId(1960603563958550529L);
            result.put("subscriptionTableAccess", true);
            result.put("hasActiveSubscription", activeSubscription != null);
            
            // Test premium service functionality
            boolean isPremium = premiumSubscriptionService.isUserPremium(1960603563958550529L);
            result.put("premiumServiceWorking", true);
            result.put("isPremiumResult", isPremium);
            
            result.put("timestamp", new Date());
            result.put("status", "All checks passed");
            
            log.info("Database connectivity test completed successfully");
            return new Result<Map<String, Object>>().ok(result);
        } catch (Exception e) {
            log.error("Database connectivity test failed: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "Failed");
            errorResult.put("error", e.getMessage());
            errorResult.put("timestamp", new Date());
            return new Result<Map<String, Object>>().error("Database test failed: " + e.getMessage());
        }
    }
    
    private void handleSuccessfulPayment(Map<String, Object> payload, String paymentMethod) {
        try {
            // Extract payment information from Shopify order payload
            Long orderId = ((Number) payload.get("id")).longValue();
            String orderNumber = (String) payload.get("order_number");
            String transactionId = String.valueOf(orderId);
            
            // Extract total price
            String totalPriceStr = (String) payload.get("total_price");
            if (totalPriceStr == null) {
                log.warn("Missing total_price in Shopify webhook payload");
                return;
            }
            
            BigDecimal paymentAmount = new BigDecimal(totalPriceStr);
            
            // Extract currency
            String currency = (String) payload.get("currency");
            if (currency == null) currency = "USD";
            
            // Extract user information using reusable method
            String userIdStr = extractUserIdFromPayload(payload);
            if (userIdStr == null) {
                log.warn("No user_id found in Shopify webhook payload");
                return;
            }
            
            Long userId = Long.parseLong(userIdStr);
            
            // Extract subscription type from note_attributes
            String subscriptionType = "MONTHLY"; // Default
            Object noteAttributesObj = payload.get("note_attributes");
            if (noteAttributesObj instanceof java.util.List) {
                java.util.List<Map<String, Object>> noteAttributes = (java.util.List<Map<String, Object>>) noteAttributesObj;
                for (Map<String, Object> attr : noteAttributes) {
                    String name = (String) attr.get("name");
                    String value = (String) attr.get("value");
                    if ("subscription_type".equals(name)) {
                        subscriptionType = value;
                        break;
                    }
                }
            }
            
            // Create premium subscription
            UserPremiumSubscriptionDTO subscription = new UserPremiumSubscriptionDTO();
            subscription.setUserId(userId);
            subscription.setSubscriptionType(subscriptionType);
            subscription.setPaymentAmount(paymentAmount);
            subscription.setPaymentCurrency(currency);
            subscription.setPaymentMethod(paymentMethod);
            subscription.setPaymentTransactionId(transactionId);
            subscription.setSubscriptionStartDate(new Date());
            
            // Set end date based on subscription type
            LocalDateTime endDate = LocalDateTime.now();
            if ("YEARLY".equals(subscription.getSubscriptionType())) {
                endDate = endDate.plusYears(1);
            } else {
                endDate = endDate.plusMonths(1);
            }
            subscription.setSubscriptionEndDate(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
            
            premiumSubscriptionService.createSubscription(subscription);
            
            log.info("Created premium subscription for user {} with Shopify order {} (transaction {})", userId, orderNumber, transactionId);
            
        } catch (Exception e) {
            log.error("Error handling successful payment: {}", e.getMessage(), e);
        }
    }
    
    private void handleFailedPayment(Map<String, Object> payload, String paymentMethod) {
        try {
            // Handle failed payments - mark subscriptions as failed, send notifications, etc.
            log.info("Processing failed payment for method: {}", paymentMethod);
            
            if ("SHOPIFY".equals(paymentMethod)) {
                Long orderId = ((Number) payload.get("id")).longValue();
                String orderNumber = (String) payload.get("order_number");
                String financialStatus = (String) payload.get("financial_status");
                String transactionId = String.valueOf(orderId);
                
                log.info("Shopify order {} (#{}) failed with status: {}", orderId, orderNumber, financialStatus);
                
                // Extract user information to identify affected subscription
                String userIdStr = extractUserIdFromPayload(payload);
                if (userIdStr == null) {
                    log.warn("Cannot process failed payment - no user_id found in payload");
                    return;
                }
                
                Long userId = Long.parseLong(userIdStr);
                
                // 1. Mark existing subscriptions as cancelled if this was a refund
                if ("refunded".equals(financialStatus)) {
                    handleRefundedSubscription(userId, transactionId, orderId, orderNumber);
                } else if ("cancelled".equals(financialStatus)) {
                    handleCancelledSubscription(userId, transactionId, orderId, orderNumber);
                } else {
                    handleGeneralPaymentFailure(userId, transactionId, orderId, orderNumber, financialStatus);
                }
                
                // 2. Send notifications to users
                sendFailureNotificationToUser(userId, financialStatus, orderNumber);
                
                log.info("Completed processing failed payment for user {} with order {}", userId, orderNumber);
            }
        } catch (Exception e) {
            log.error("Error handling failed payment: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Extract user ID from Shopify payload (reusable method)
     */
    private String extractUserIdFromPayload(Map<String, Object> payload) {
        String userIdStr = null;
        
        // Try to get user_id from note_attributes
        Object noteAttributesObj = payload.get("note_attributes");
        if (noteAttributesObj instanceof java.util.List) {
            java.util.List<Map<String, Object>> noteAttributes = (java.util.List<Map<String, Object>>) noteAttributesObj;
            for (Map<String, Object> attr : noteAttributes) {
                String name = (String) attr.get("name");
                String value = (String) attr.get("value");
                if ("user_id".equals(name)) {
                    userIdStr = value;
                    break;
                }
            }
        }
        
        // If not found in note_attributes, try customer data
        if (userIdStr == null) {
            Map<String, Object> customer = (Map<String, Object>) payload.get("customer");
            if (customer != null) {
                Object tagsObj = customer.get("tags");
                if (tagsObj instanceof String) {
                    String tags = (String) tagsObj;
                    String[] tagArray = tags.split(",");
                    for (String tag : tagArray) {
                        if (tag.trim().startsWith("user_id:")) {
                            userIdStr = tag.trim().substring(8);
                            break;
                        }
                    }
                }
            }
        }
        
        return userIdStr;
    }
    
    /**
     * Handle refunded subscription - mark as refunded and cancel premium access
     */
    private void handleRefundedSubscription(Long userId, String transactionId, Long orderId, String orderNumber) {
        try {
            // Find subscription by transaction ID
            var existingSubscription = premiumSubscriptionService.findByTransactionId(transactionId);
            if (existingSubscription != null) {
                // Update subscription status to REFUNDED
                existingSubscription.setStatus("REFUNDED");
                existingSubscription.setUpdateDate(new Date());
                premiumSubscriptionService.updateSubscription(existingSubscription);
                
                log.info("Marked subscription {} as REFUNDED for user {} due to Shopify refund order {} ({})", 
                    existingSubscription.getId(), userId, orderId, orderNumber);
            } else {
                log.warn("No subscription found with transaction ID {} for refund processing", transactionId);
            }
        } catch (Exception e) {
            log.error("Error handling refunded subscription for user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Handle cancelled subscription - mark as cancelled
     */
    private void handleCancelledSubscription(Long userId, String transactionId, Long orderId, String orderNumber) {
        try {
            // Find subscription by transaction ID
            var existingSubscription = premiumSubscriptionService.findByTransactionId(transactionId);
            if (existingSubscription != null) {
                // Update subscription status to CANCELLED
                existingSubscription.setStatus("CANCELLED");
                existingSubscription.setUpdateDate(new Date());
                premiumSubscriptionService.updateSubscription(existingSubscription);
                
                log.info("Marked subscription {} as CANCELLED for user {} due to Shopify cancellation order {} ({})", 
                    existingSubscription.getId(), userId, orderId, orderNumber);
            } else {
                log.warn("No subscription found with transaction ID {} for cancellation processing", transactionId);
            }
        } catch (Exception e) {
            log.error("Error handling cancelled subscription for user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Handle general payment failure - log and potentially mark as failed
     */
    private void handleGeneralPaymentFailure(Long userId, String transactionId, Long orderId, String orderNumber, String financialStatus) {
        try {
            // For other failure types, we might want to mark as EXPIRED or FAILED depending on business logic
            var existingSubscription = premiumSubscriptionService.findByTransactionId(transactionId);
            if (existingSubscription != null) {
                // For payment failures, we might want to give users a grace period
                log.info("Payment failure detected for subscription {} (user {}), status: {}. Consider implementing grace period logic.", 
                    existingSubscription.getId(), userId, financialStatus);
                
                // Optionally mark as failed after grace period
                // existingSubscription.setStatus("FAILED");
                // premiumSubscriptionService.updateSubscription(existingSubscription);
            } else {
                log.warn("No subscription found with transaction ID {} for failure processing", transactionId);
            }
        } catch (Exception e) {
            log.error("Error handling payment failure for user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Send notification to user about payment failure
     */
    private void sendFailureNotificationToUser(Long userId, String financialStatus, String orderNumber) {
        try {
            // TODO: Implement notification service (email, SMS, in-app notification)
            // This could integrate with your existing notification system
            
            String notificationMessage = createFailureNotificationMessage(financialStatus, orderNumber);
            
            log.info("Sending failure notification to user {}: {}", userId, notificationMessage);
            
            // Example implementations:
            // notificationService.sendEmail(userId, "Payment Issue", notificationMessage);
            // notificationService.sendPushNotification(userId, notificationMessage);
            // notificationService.createInAppNotification(userId, "PAYMENT_FAILURE", notificationMessage);
            
        } catch (Exception e) {
            log.error("Error sending failure notification to user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Create appropriate notification message based on failure type
     */
    private String createFailureNotificationMessage(String financialStatus, String orderNumber) {
        switch (financialStatus) {
            case "refunded":
                return String.format("Your premium subscription payment has been refunded (Order #%s). Your premium access will be removed shortly.", orderNumber);
            case "cancelled":
                return String.format("Your premium subscription order has been cancelled (Order #%s). Please contact support if this was unexpected.", orderNumber);
            default:
                return String.format("There was an issue with your premium subscription payment (Order #%s). Please check your payment method or contact support.", orderNumber);
        }
    }
    
    /**
     * Verify Shopify webhook signature using HMAC-SHA256
     * @param payload The webhook payload
     * @param signature The HMAC signature from Shopify
     * @return true if signature is valid
     */
    private boolean verifyShopifySignature(Map<String, Object> payload, String signature) {
        try {
            // TODO: Get webhook secret from configuration/environment
            String webhookSecret = getShopifyWebhookSecret();
            
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                log.warn("Shopify webhook secret not configured - skipping signature verification");
                return true; // Allow in development, but log warning
            }
            
            // Calculate HMAC-SHA256 signature
            String calculatedSignature = calculateHmacSha256(payload, webhookSecret);
            
            // Compare signatures (use constant-time comparison to prevent timing attacks)
            boolean isValid = constantTimeEquals(signature, calculatedSignature);
            
            if (!isValid) {
                log.error("Invalid Shopify webhook signature. Expected: {}, Got: {}", calculatedSignature, signature);
            } else {
                log.debug("Shopify webhook signature verified successfully");
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying Shopify signature: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get Shopify webhook secret from configuration
     * In production, this should come from environment variables or secure configuration
     */
    private String getShopifyWebhookSecret() {
        // TODO: Implement configuration retrieval
        // return System.getenv("SHOPIFY_WEBHOOK_SECRET");
        // or from application.yml/properties
        return null; // Currently not configured
    }
    
    /**
     * Calculate HMAC-SHA256 signature for webhook verification
     */
    private String calculateHmacSha256(Map<String, Object> payload, String secret) {
        try {
            // Convert payload to raw JSON string (order matters!)
            String rawPayload = convertToRawJson(payload);
            
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(rawPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error calculating HMAC-SHA256: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate webhook signature", e);
        }
    }
    
    /**
     * Convert payload back to raw JSON string for signature verification
     * Note: This is simplified - in production you'd need the exact raw request body
     */
    private String convertToRawJson(Map<String, Object> payload) {
        // TODO: In production, capture and use the raw request body
        // For now, use a basic JSON conversion
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Error converting payload to JSON: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
