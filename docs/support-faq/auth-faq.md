# Authentication & Authorization FAQ

## Section 1: Common Login Errors

### 401 Unauthorized
A 401 error means the server did not accept your credentials.

**Common causes:**
- Password was changed but the session token is stale — log out fully and log in again
- Account email not verified — check your inbox for a verification email
- Two-factor authentication (2FA) code expired — generate a new one from your authenticator app
- Password reset link has expired (links expire after 1 hour) — request a new reset link
- Cookies or browser cache contain an old session — clear browser storage and retry

**Resolution steps:**
1. Go to the login page and use "Forgot password" to generate a fresh reset link
2. Open the reset link in an incognito/private browser window
3. Set a new password that meets complexity requirements (min 8 chars, 1 digit, 1 special char)
4. Log in immediately after resetting; do not use old saved passwords

---

### 403 Forbidden
A 403 error means the server recognized your identity but rejected the request due to insufficient permissions or account restrictions.

**Common causes:**
- Account is suspended due to overdue payment — update billing information to restore access
- Account has been locked due to too many failed login attempts — contact support to unlock
- Subscription plan has expired — renew your plan to regain feature access
- Resource you are accessing requires a higher-tier subscription plan
- API key is invalid or has been revoked — regenerate API credentials in account settings

**Resolution steps:**
1. Check your account status in the Account Settings page
2. If suspended: go to Billing → update payment method → wait up to 15 minutes for reactivation
3. If locked: contact support with your account email to request manual unlock
4. If subscription expired: go to Billing → Subscription → Renew

---

### 429 Too Many Requests / Rate Limited
**Cause:** Too many login attempts in a short window triggers a temporary block.

**Resolution:**
- Wait 15 minutes before attempting login again
- If you need immediate access, contact support

---

## Section 2: Password Reset

### Reset link not arriving
- Check your spam/junk folder
- Ensure you are using the exact email address registered with your account
- Email delivery may take up to 5 minutes — wait before requesting a new link
- If still no email after 10 minutes: use a different email client or check email provider filters

### Reset link says "expired" or "already used"
- Password reset links are single-use and expire after 1 hour
- Request a new reset link from the login page

---

## Section 3: Two-Factor Authentication (2FA)

### 2FA code not working
- Ensure your device clock is synchronized (TOTP codes depend on exact time)
- Use the current code shown in your authenticator app — codes refresh every 30 seconds
- If you lost access to your authenticator app: use a backup recovery code (given at 2FA setup)
- If backup codes are lost: contact support with account verification details

---

## Section 4: Account Status Explanations

### Active
Your account is fully functional. All features available per your subscription plan.

### Locked
Your account has been temporarily locked due to multiple failed login attempts (security protection).
**To unlock:** Contact support. An agent will verify your identity and unlock the account.
Support can unlock accounts with status "locked". Agents must NOT unlock "suspended" accounts without billing team approval.

### Suspended
Your account has been suspended. This typically occurs due to:
- Overdue payment (most common)
- Policy violation (less common)

**To restore:** Resolve the underlying issue (e.g. update payment method in Billing settings), then contact support.
Important: suspended accounts cannot be unlocked by front-line support alone — escalation to billing/trust team is required.

---

## Section 5: API Authentication

### API key returning 401
- Regenerate your API key in Account Settings → API Keys
- Ensure you are passing the key as `Authorization: Bearer <key>` header (not in query params)
- Check that the key has not been revoked or rotated by an admin

### OAuth token errors
- Token may have expired — refresh using your refresh token
- If refresh token is also invalid: re-authenticate the OAuth flow from scratch
- Scope mismatch: the token may not include the required permission scope — re-authorize with correct scopes

---

## Section 6: Authorization Failure Troubleshooting

### Why is my authorization failing?
Authorization failures (401/403) can have several root causes depending on account status:

**If account is SUSPENDED:**
- Most common cause: overdue payment
- The account subscription has expired or payment was declined
- All API calls and login attempts will return 403 Forbidden
- Resolution: Update payment method in Billing → Payment Methods, then wait 15 minutes

**If account is LOCKED:**
- Caused by too many failed login attempts (security lockout)
- The system automatically locks accounts after 5 consecutive failed attempts
- Resolution: Contact support for manual identity verification and account unlock
- Do NOT attempt more logins — it resets the unlock timer

**If token is expired:**
- Session tokens expire after 24 hours of inactivity
- JWT tokens have a configurable expiry (default: 1 hour)
- Resolution: Log out completely, clear browser cache, log in fresh

**If API key is revoked:**
- API keys can be rotated by admin users
- Check Account Settings → API Keys for active keys
- Generate a new key if yours has been revoked
