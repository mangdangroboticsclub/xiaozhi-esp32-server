# Shopify Payment Integration Guide

## Overview

This guide outlines how to integrate the xiaozhi-esp32-server premium subscription system with Shopify webhooks for payment processing.

## Implementation Summary

### ✅ Completed Changes

1. **PaymentWebhookController.java** - Updated to handle only Shopify webhooks
   - Removed Stripe and PayPal webhook endpoints
   - Added `/admin/payment/webhook/shopify` endpoint
   - Implemented Shopify order payload parsing
   - Added signature verification placeholder

2. **Entity & DTO Updates** - Added SHOPIFY as supported payment method
   - `UserPremiumSubscriptionEntity.java` - Updated payment method comments
   - `UserPremiumSubscriptionDTO.java` - Updated schema documentation
   - Test subscription creation now defaults to SHOPIFY

## Shopify Webhook Configuration

### 1. Webhook Endpoint Setup

Your webhook endpoint is now: `https://your-domain.com/admin/payment/webhook/shopify`

### 2. Required Webhooks in Shopify

Configure these webhooks in your Shopify admin panel:

- **Order Payment** - `orders/paid`
- **Order Cancelled** - `orders/cancelled` 
- **Order Refunded** - `orders/refunded`

### 3. Webhook Payload Structure

The controller expects Shopify order webhooks with this structure:

```json
{
  "id": 123456789,
  "order_number": 1001,
  "total_price": "29.99",
  "currency": "USD",
  "financial_status": "paid",
  "note_attributes": [
    {"name": "user_id", "value": "123"},
    {"name": "subscription_type", "value": "MONTHLY"}
  ],
  "customer": {
    "id": 987654321,
    "tags": "user_id:123,premium_user"
  }
}
```

## User ID Mapping Strategy

The system supports two methods to link Shopify orders to users:

### Method 1: Note Attributes (Recommended)
Add custom attributes during checkout:
```javascript
// In your Shopify checkout process
fetch('/cart/update.json', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({
    attributes: {
      'user_id': currentUserId,
      'subscription_type': selectedPlan
    }
  })
});
```

### Method 2: Customer Tags
Tag customers with their user ID:
```javascript
// When creating/updating customer
{
  "customer": {
    "tags": "user_id:123,premium_subscriber"
  }
}
```

## Product Setup in Shopify

### 1. Premium Subscription Products

Create products in your Shopify store:

- **Premium Monthly** - $9.99/month
- **Premium Yearly** - $99.99/year

### 2. Product Variants

Configure variants for different subscription types:
- SKU: `PREMIUM_MONTHLY`
- SKU: `PREMIUM_YEARLY`

## Frontend Integration

### 1. Redirect to Shopify Checkout

Update your subscription selection page:

```javascript
// When user selects premium plan
async function selectPlan(planType) {
  const userId = getCurrentUserId();
  
  // Add items to Shopify cart with user information
  const cartData = {
    items: [{
      id: planType === 'YEARLY' ? YEARLY_VARIANT_ID : MONTHLY_VARIANT_ID,
      quantity: 1
    }],
    attributes: {
      'user_id': userId,
      'subscription_type': planType
    }
  };
  
  const response = await fetch('/cart/add.json', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(cartData)
  });
  
  if (response.ok) {
    window.location.href = '/checkout';
  }
}
```

### 2. User Dashboard Integration

Update the user dashboard to show Shopify-based subscriptions:

```javascript
// Check subscription status
async function checkSubscriptionStatus() {
  const response = await fetch(`/admin/premium/user/${userId}`);
  const subscription = await response.json();
  
  if (subscription && subscription.paymentMethod === 'SHOPIFY') {
    displayShopifySubscription(subscription);
  }
}
```

## Security Implementation

### 1. Webhook Signature Verification

Update the signature verification method:

```java
private boolean verifyShopifySignature(String rawPayload, String signature) {
    try {
        // Get webhook secret from configuration
        String webhookSecret = shopifyWebhookSecret;
        
        // Calculate HMAC-SHA256
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
            webhookSecret.getBytes(StandardCharsets.UTF_8), 
            "HmacSHA256"
        );
        mac.init(secretKey);
        
        byte[] hash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
        String calculatedSignature = Base64.getEncoder().encodeToString(hash);
        
        return MessageDigest.isEqual(
            signature.getBytes(StandardCharsets.UTF_8),
            calculatedSignature.getBytes(StandardCharsets.UTF_8)
        );
    } catch (Exception e) {
        log.error("Error verifying signature: {}", e.getMessage());
        return false;
    }
}
```

### 2. Environment Configuration

Add to your application.properties:

```properties
# Shopify Configuration
shopify.webhook.secret=${SHOPIFY_WEBHOOK_SECRET}
shopify.api.key=${SHOPIFY_API_KEY}
shopify.api.secret=${SHOPIFY_API_SECRET}
shopify.store.domain=${SHOPIFY_STORE_DOMAIN}
```

## Database Considerations

### 1. Transaction ID Mapping

Shopify order IDs will be stored as `paymentTransactionId`:
- Format: Shopify order ID (e.g., "123456789")
- Unique constraint ensures no duplicate processing

### 2. Currency Support

The system now supports multiple currencies from Shopify:
- USD, EUR, GBP, CAD, AUD, etc.
- Currency stored in `paymentCurrency` field

## Testing Strategy

### 1. Webhook Testing

Use Shopify's webhook testing tools:

```bash
# Test webhook delivery
curl -X POST https://your-domain.com/admin/payment/webhook/shopify \
  -H "Content-Type: application/json" \
  -H "X-Shopify-Hmac-Sha256: your-test-signature" \
  -d @test-order-payload.json
```

### 2. Order Flow Testing

1. Create test products in Shopify
2. Process test orders with user_id attributes
3. Verify subscription creation in database
4. Test webhook signature verification

## Deployment Checklist

### 1. Shopify Store Setup
- [ ] Products created for premium subscriptions
- [ ] Webhooks configured for orders/paid, orders/cancelled, orders/refunded
- [ ] Webhook secret generated and stored securely

### 2. Application Configuration
- [ ] Webhook endpoint URL updated in Shopify
- [ ] Environment variables configured
- [ ] Signature verification implemented
- [ ] SSL certificate configured for webhook endpoint

### 3. Frontend Updates
- [ ] Checkout flow redirects to Shopify
- [ ] User ID mapping implemented
- [ ] Subscription dashboard updated
- [ ] Error handling for failed payments

## Monitoring & Analytics

### 1. Key Metrics to Track

- **Webhook Success Rate** - Target: >99%
- **Order Processing Time** - Target: <2 seconds
- **Subscription Conversion Rate** - Track funnel performance
- **Payment Method Distribution** - Monitor Shopify vs other methods

### 2. Logging Strategy

```java
// Enhanced logging for Shopify webhooks
log.info("Shopify webhook received - Order: {}, Amount: {}, User: {}", 
    orderNumber, paymentAmount, userId);
log.info("Premium subscription created - User: {}, Type: {}, Expires: {}", 
    userId, subscriptionType, subscription.getSubscriptionEndDate());
```

## Troubleshooting

### Common Issues

1. **Missing User ID**
   - Ensure note_attributes or customer tags contain user_id
   - Verify frontend passes user information during checkout

2. **Signature Verification Fails**
   - Check webhook secret configuration
   - Verify HMAC calculation matches Shopify's method
   - Ensure raw payload (not parsed JSON) is used for verification

3. **Duplicate Subscriptions**
   - Implement idempotency checking using order IDs
   - Check for existing subscriptions before creating new ones

4. **Currency Conversion Issues**
   - Handle different currency formats from Shopify
   - Implement currency conversion if needed

### Debug Mode

Enable debug logging for webhook processing:

```properties
logging.level.xiaozhi.modules.sys.controller.PaymentWebhookController=DEBUG
```

## Benefits of Shopify Integration

### 1. Simplified Payment Processing
- ✅ No need to handle sensitive payment data
- ✅ Shopify handles PCI compliance
- ✅ Support for 100+ payment methods globally
- ✅ Built-in fraud protection

### 2. Enhanced User Experience
- ✅ Familiar Shopify checkout experience
- ✅ Multiple payment options (cards, PayPal, Apple Pay, etc.)
- ✅ Mobile-optimized checkout
- ✅ Automatic tax calculation

### 3. Business Intelligence
- ✅ Comprehensive order analytics in Shopify
- ✅ Customer lifecycle management
- ✅ Inventory and subscription tracking
- ✅ Built-in reporting and insights

## Next Steps

1. **Immediate (Week 1)**
   - Set up Shopify store and products
   - Configure webhook endpoints
   - Test basic order processing

2. **Short Term (Week 2-3)**
   - Implement signature verification
   - Update frontend checkout flow
   - Add comprehensive error handling

3. **Medium Term (Month 2)**
   - Add subscription management features
   - Implement customer portal integration
   - Set up monitoring and analytics

4. **Long Term (Month 3+)**
   - Multi-currency support
   - Advanced subscription features
   - International market expansion

The Shopify integration provides a robust, scalable foundation for premium subscriptions while leveraging Shopify's powerful e-commerce platform and global payment infrastructure.

(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 1: Check Premium Status (Test Endpoint) ===
> '

=== TEST 1: Check Premium Status (Test Endpoint) ===

ozhi/admin/payment/test/premium-status/19606035639(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X GET "http://localhost:8002/xiaozhi/admin/payment/test/premium-status/1960603563958550529" \
>   -H "Content-Type: application/json" \
>   -H "Authorization: Bearer $TOKEN"
{"code":0,"msg":"success","data":{"subscriptionDetails":{"id":"1974363685115506689","userId":"1960603563958550529","subscriptionType":"MONTHLY","paymentAmount":29.99,"paymentCurrency":"USD","paymentMethod":"SHOPIFY","paymentTransactionId":"123456789","subscriptionStartDate":"2025-10-04 14:39:19","subscriptionEndDate":"2025-11-04 14:39:19","status":"ACTIVE","autoRenew":0,"creator":"1960603563958550529","createDate":"2025-10-04 14:39:19","updateDate":"2025-10-04 14:39:19"},"isPremium":true,"userId":"1960603563958550529","hasActiveSubscription":true,"timestamp":"2025-10-04 15:40:40"}}(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 2: List All Subscriptions (Test Endpoint) ===
> '

=== TEST 2: List All Subscriptions (Test Endpoint) ===

iaozhi/admin/payment/test/list-subscriptions" \
  (base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X GET "http://localhost:8002/xiaozhi/admin/payment/test/list-subscriptions" \
   -H "Content-Type: application/json" \
   -H "Authorization: Bearer $TOKEN"
{"code":0,"msg":"success","data":[{"subscriptionType":"MONTHLY","subscriptionEndDate":"2025-11-04 14:39:19","paymentMethod":"SHOPIFY","id":"1974363685115506689","paymentCurrency":"USD","subscriptionStartDate":"2025-10-04 14:39:19","userId":"1960603563958550529","paymentAmount":29.99,"paymentTransactionId":"123456789","status":"ACTIVE","createDate":"2025-10-04 14:39:19"}]}(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 3: Database Check (Test Endpoint) ===
> '

=== TEST 3: Database Check (Test Endpoint) ===

dmin/payment/test/db-check" \
  -H "Content-Type: (base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X GET "http://localhost:8002/xiaozhi/admin/payment/test/db-check" \
   -H "Content-Type: application/json" \
   -H "Authorization: Bearer $TOKEN"
{"code":0,"msg":"success","data":{"premiumServiceWorking":true,"userExists":true,"premiumExpiresAt":"2025-11-04 14:39:19","isPremiumResult":true,"testUserId":"1960603563958550529","subscriptionTableAccess":true,"hasActiveSubscription":true,"userPremiumStatus":1,"userTableAccess":true,"username":"coolHaris","timestamp":"2025-10-04 15:40:58","status":"All checks passed"}}(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 4: Create Test Subscription ===
> '

=== TEST 4: Create Test Subscription ===

curl -X POST "http://localhost:8002/xiaozhi/admin/(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X POST "http://localhost:8002/xiaozhi/admin/payment/test/create-subscription?userId=1964238859134115841&subscriptionType=YEARLY&amount=299.99" \
   -H "Content-Type: application/json" \
   -H "Authorization: Bearer $TOKEN"
{"code":0,"msg":"success","data":"Test subscription created successfully"}(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 5: Premium Admin Panel - Page List ===
> '

=== TEST 5: Premium Admin Panel - Page List ===

admin/premium/page?page=1&limit=10" \
  -H "Conten(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X GET "http://localhost:8002/xiaozhi/admin/premium/page?page=1&limit=10" \
>   -H "Content-Type: application/json" \
>   -H "Authorization: Bearer $TOKEN"
{"code":0,"msg":"success","data":{"total":2,"list":[{"id":"1974379245337423873","userId":"1960603563958550529","subscriptionType":"YEARLY","paymentAmount":299.99,"paymentCurrency":"USD","paymentMethod":"SHOPIFY","paymentTransactionId":"TEST-1759563668933","subscriptionStartDate":"2025-10-04 15:41:09","subscriptionEndDate":"2026-10-04 15:41:09","status":"ACTIVE","autoRenew":0,"creator":"1960603563958550529","createDate":"2025-10-04 15:41:09","updateDate":"2025-10-04 15:41:09"},{"id":"1974363685115506689","userId":"1960603563958550529","subscriptionType":"MONTHLY","paymentAmount":29.99,"paymentCurrency":"USD","paymentMethod":"SHOPIFY","paymentTransactionId":"123456789","subscriptionStartDate":"2025-10-04 14:39:19","subscriptionEndDate":"2025-11-04 14:39:19","status":"ACTIVE","autoRenew":0,"creator":"1960603563958550529","createDate":"2025-10-04 14:39:19","updateDate":"2025-10-04 14:39:19"}]}}(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 6: Check if User is Premium (Admin API) ===
> '

=== TEST 6: Check if User is Premium (Admin API) ===

ozhi/admin/premium/is-premium/1960603563958550529"(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X GET "http://localhost:8002/xiaozhi/admin/premium/is-premium/1960603563958550529" \
>   -H "Content-Type: application/json" \
>   -H "Authorization: Bearer $TOKEN"
{"code":0,"msg":"success","data":true}(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 7: Get Active Subscription for User ===
> '

=== TEST 7: Get Active Subscription for User ===

/admin/premium/user/1960603563958550529" \
  -H "C(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X GET "http://localhost:8002/xiaozhi/admin/premium/user/1960603563958550529" \
>   -H "Content-Type: application/json" \
>   -H "Authorization: Bearer $TOKEN"
{"code":0,"msg":"success","data":{"id":"1974379245337423873","userId":"1960603563958550529","subscriptionType":"YEARLY","paymentAmount":299.99,"paymentCurrency":"USD","paymentMethod":"SHOPIFY","paymentTransactionId":"TEST-1759563668933","subscriptionStartDate":"2025-10-04 15:41:09","subscriptionEndDate":"2026-10-04 15:41:09","status":"ACTIVE","autoRenew":0,"creator":"1960603563958550529","createDate":"2025-10-04 15:41:09","updateDate":"2025-10-04 15:41:09"}}(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 8: Check and Update Premium Status ===
> '

=== TEST 8: Check and Update Premium Status ===

(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X POST "http://localhost:8002/xiaozhi/admin/premium/check-status/1960603563958550529" \
>   -H "Content-Type: application/json" \
>   -H "Authorization: Bearer $TOKEN"
{"code":0,"msg":"success","data":true}(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ echo '
> === TEST 9: Test Refund Webhook (No Auth Required) ===
> '

=== TEST 9: Test Refund Webhook (No Auth Required) ===

iaozhi/admin/payment/webhook/shopify \
  -H "Conte(base) haris@xiaozhitest:~/xiaozhi-esp32-server$ curl -X POST http://localhost:8002/xiaozhi/admin/payment/webhook/shopify \
   -H "Content-Type: application/json" \
   -H "X-Shopify-Hmac-Sha256: 4b0915de9e5123f1daae5fa92bf52b943665d354df7a46342189a75bc60e269e" \
   -d '{
     "id": 123456792,
     "total_price": "29.99",
     "currency": "USD",
     "financial_status": "refunded",
     "customer": {
       "id": 1960603563958550529,
       "email": "test@example.com"
     },
     "line_items": [
       {
         "product_id": 1,
         "title": "Monthly Premium Subscription",
         "quantity": 1,
         "price": "29.99"
       }
     ],
     "refunds": [
       {
         "id": "refund_123",
         "amount": "29.99",
         "currency": "USD",
         "reason": "requested_by_customer"
       }
     ],
     "note_attributes": [
       {
         "name": "user_id",
         "value": "1960603563958550529"
       }
     ]
   }'
{"code":401,"msg":"Unauthorized","data":null}(base)