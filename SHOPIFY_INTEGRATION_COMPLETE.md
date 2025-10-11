# Shopify Webhook Integration - Setup Complete! 🎉

## What Was Done

### 1. ✅ Fixed PaymentWebhookController.java
- Updated to use `@RequestBody String rawPayload` instead of `Map<String, Object>`
- Implemented proper HMAC-SHA256 signature verification using the raw request body
- Added `@Value` annotation to inject `SHOPIFY_WEBHOOK_SECRET` from environment
- Fixed `getShopifyWebhookSecret()` to read from environment variable
- Removed broken `convertToRawJson()` method (no longer needed)

### 2. ✅ Updated docker-compose.yml
- Added `SHOPIFY_WEBHOOK_SECRET` environment variable to `xiaozhi-esp32-server-web` service
- Variable is loaded from `.env` file in the root directory

### 3. ✅ Fixed .env File
- Changed from `export SHOPIFY_WEBHOOK_SECRET="..."` to `SHOPIFY_WEBHOOK_SECRET=...`
- Docker Compose requires plain KEY=VALUE format (no export, no quotes needed)

### 4. ✅ Created Test Script
- Created `test-shopify-webhook.sh` for testing webhook integration
- Computes HMAC-SHA256 signature correctly
- Sends test payload with proper headers

---

## Next Steps to Complete Integration

### Step 1: Restart Docker Containers
The environment variable change requires a restart:

```bash
cd /home/hharis/Mangdang/xiaozhi-esp32-server-1

# Load the .env file and restart
docker-compose down
docker-compose up -d

# Check logs to verify startup
docker logs -f xiaozhi-web
```

### Step 2: Verify ngrok is Running
```bash
# Start ngrok if not already running
ngrok http 8002

# Note the HTTPS URL (e.g., https://doretha-unmediating-chun.ngrok-free.dev)
```

### Step 3: Test Webhook Locally
```bash
# Make the test script executable
chmod +x test-shopify-webhook.sh

# Run the test (update URL if your ngrok URL changed)
./test-shopify-webhook.sh https://doretha-unmediating-chun.ngrok-free.dev/xiaozhi/admin/payment/webhook/shopify
```

Expected output:
```
✅ Webhook test SUCCESSFUL!
Response: {"code":0,"msg":"success","data":"Webhook processed successfully"}
```

### Step 4: Configure Webhooks in Shopify Admin

1. Go to **Shopify Admin → Settings → Notifications → Webhooks**

2. Create these three webhooks:

   | Event | Format | URL |
   |-------|--------|-----|
   | `orders/paid` | JSON | `https://YOUR-NGROK-URL/xiaozhi/admin/payment/webhook/shopify` |
   | `orders/cancelled` | JSON | `https://YOUR-NGROK-URL/xiaozhi/admin/payment/webhook/shopify` |
   | `orders/refunded` | JSON | `https://YOUR-NGROK-URL/xiaozhi/admin/payment/webhook/shopify` |

3. **Important:** After creating each webhook, verify the webhook secret matches your `.env` file:
   - The secret you see in Shopify admin should be: `4b0915de9e5123f1daae5fa92bf52b943665d354df7a46342189a75bc60e269e`

### Step 5: Add Cart Attributes to Shopify Store

You need to attach `user_id` to orders so the webhook knows which user to activate.

#### Option A: Add to Shopify Theme (Recommended)

Add this JavaScript to your product page or cart template before checkout:

```javascript
// Get user_id from your app's session/JWT/cookie
function getUserIdFromYourApp() {
  // TODO: Implement based on your authentication system
  // Example: return getCookie('user_id') or JWT.decode(token).userId
  return "1960603563958550529"; // Replace with actual implementation
}

async function buyPremiumSubscription(planType, variantId) {
  const userId = getUserIdFromYourApp();
  
  // Add product to cart
  await fetch('/cart/add.json', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      items: [{ id: variantId, quantity: 1 }]
    })
  });

  // Set cart attributes with user mapping
  await fetch('/cart/update.json', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      attributes: {
        user_id: userId,
        subscription_type: planType
      }
    })
  });

  // Redirect to checkout
  window.location.href = '/checkout';
}

// Usage example:
// <button onclick="buyPremiumSubscription('MONTHLY', 12345678)">Buy Monthly</button>
// <button onclick="buyPremiumSubscription('YEARLY', 87654321)">Buy Yearly</button>
```

#### Option B: Use Customer Tags (via Admin API)

If you manage customers via API:
```bash
curl -X PUT "https://{api_key}:{password}@{store}.myshopify.com/admin/api/2025-07/customers/{customer_id}.json" \
  -H "Content-Type: application/json" \
  -d '{"customer": {"tags": "user_id:1960603563958550529"}}'
```

### Step 6: Test End-to-End

1. **Create a test order in Shopify**
   - Use Shopify's test payment gateway
   - Add Monthly or Yearly subscription product
   - Include cart attributes with user_id

2. **Check webhook delivery**
   - View ngrok inspector: http://127.0.0.1:4040
   - Check server logs: `docker logs xiaozhi-web`

3. **Verify subscription created**
   ```bash
   curl https://YOUR-NGROK-URL/xiaozhi/admin/payment/test/premium-status/1960603563958550529
   ```

   Expected response:
   ```json
   {
     "code": 0,
     "msg": "success",
     "data": {
       "userId": 1960603563958550529,
       "isPremium": true,
       "hasActiveSubscription": true,
       "subscriptionDetails": { ... }
     }
   }
   ```

---

## Testing Commands

### Test Database Connectivity
```bash
curl https://YOUR-NGROK-URL/xiaozhi/admin/payment/test/db-check
```

### Test Premium Status
```bash
curl https://YOUR-NGROK-URL/xiaozhi/admin/payment/test/premium-status/USER_ID
```

### List All Subscriptions
```bash
curl https://YOUR-NGROK-URL/xiaozhi/admin/payment/test/list-subscriptions
```

### Create Test Subscription Manually
```bash
curl -X POST "https://YOUR-NGROK-URL/xiaozhi/admin/payment/test/create-subscription?userId=1960603563958550529&subscriptionType=MONTHLY&amount=29.99"
```

### Test Webhook with HMAC Signature (Manual)
```bash
# Create payload file
cat > payload.json << 'EOF'
{
  "id": 123456789,
  "order_number": "1001",
  "financial_status": "paid",
  "total_price": "29.99",
  "currency": "USD",
  "note_attributes": [
    {"name": "user_id", "value": "1960603563958550529"},
    {"name": "subscription_type", "value": "MONTHLY"}
  ]
}
EOF

# Compute signature
SECRET="4b0915de9e5123f1daae5fa92bf52b943665d354df7a46342189a75bc60e269e"
SIG=$(cat payload.json | openssl dgst -sha256 -hmac "$SECRET" -binary | base64)

# Send request
curl -X POST https://YOUR-NGROK-URL/xiaozhi/admin/payment/webhook/shopify \
  -H "Content-Type: application/json" \
  -H "X-Shopify-Hmac-Sha256: $SIG" \
  -d @payload.json
```

---

## Troubleshooting

### Webhook returns "Invalid signature"
1. Verify secret in Shopify admin matches `.env` file
2. Check Docker container has env var: `docker exec xiaozhi-web printenv | grep SHOPIFY`
3. Restart containers after changing `.env`

### "No user_id found in payload"
1. Verify cart attributes are set before checkout
2. Check note_attributes in webhook payload (ngrok inspector)
3. Try customer tags as alternative

### Database connection errors
1. Check MySQL container is running: `docker ps | grep mysql`
2. Test connectivity: `curl .../admin/payment/test/db-check`
3. Check logs: `docker logs xiaozhi-esp32-server-db`

### Ngrok URL blocked by Fortinet
- Use mobile hotspot or home network to configure webhooks in Shopify admin
- Shopify servers can still reach ngrok (verified by your phone test)
- Only your corporate browser is blocked, not external access

---

## Production Deployment Checklist

Before going to production:

- [ ] Replace ngrok with permanent HTTPS domain
- [ ] Update webhook URLs in Shopify admin
- [ ] Move `SHOPIFY_WEBHOOK_SECRET` to secure secrets manager
- [ ] Implement idempotency check (prevent duplicate order processing)
- [ ] Add monitoring/alerting for webhook failures
- [ ] Implement notification service for payment failures
- [ ] Test refund and cancellation flows
- [ ] Set up backup/disaster recovery for subscription data
- [ ] Add rate limiting to webhook endpoint
- [ ] Implement webhook retry logic for failures

---

## Summary

✅ **Signature Verification:** Now uses raw request body (correct)  
✅ **Environment Variable:** Injected from `.env` file via Docker Compose  
✅ **Code Fixed:** Removed broken `@AllArgsConstructor` and outdated methods  
✅ **Test Script:** Ready to verify integration works  

**Next:** Restart Docker containers and run the test script!
