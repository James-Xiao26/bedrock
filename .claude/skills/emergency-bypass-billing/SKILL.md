---
name: emergency-bypass-billing
description: How the user pays for Bedrock's $1 emergency bypass. Google Play Billing handles the money end-to-end; the app never stores or sees payment info. Use when implementing, reviewing, or reasoning about the paid bypass, the IAP catalog entry, the purchase flow, or refund/acknowledgement behavior.
---

# Paying for the emergency bypass

## The big answer

**Google Play handles the payment. Bedrock never stores, sees, or touches payment info.**

The user's card / PayPal / Google Play balance already lives in their Google account, managed by Google Play. When they buy a bypass, Google shows its own purchase sheet (fingerprint / PIN / password), charges the account on file, and hands the app back only a signed *proof of purchase* - never card data.

This is not a choice, it's mandatory. Play policy requires Google Play Billing for in-app digital purchases; you may not bolt on Stripe/PayPal/your own processor for this, and there is no card field to build. So there is nothing to store, no PCI scope, no backend required. Bedrock stays local-only.

## Product setup (once, in Play Console)

- One in-app product, type **Consumable** (a "one-time product" that can be rebought).
- Fixed price ~$1 (Google localizes it).
- ID e.g. `emergency_bypass`. This is the *only* IAP - matches [[bedrock-product-decisions]] ("single $1 consumable IAP").

Consumable (not a subscription, not a non-consumable) because the user buys a bypass repeatedly. Each purchase must be *consumed* before it can be bought again.

## The purchase flow (Kotlin, client-only - no server)

Billing lives in native per project architecture (`billing/` under `android/app/src/main/kotlin/app/bedrock/`), driven by the engine, exposed to Dart over the method channel. Never do billing in Dart.

1. `BillingClient` connected at startup; `queryProductDetails` for `emergency_bypass`.
2. User taps "Pay $1 to unlock" → `launchBillingFlow()` (must run on UI thread). Google's sheet takes over; Google authenticates and charges.
3. Result arrives in `onPurchasesUpdated()`. On `OK` + `PURCHASED`, verify the purchase signature, then **grant the bypass** (end the lockdown).
4. **`consumeAsync(purchaseToken)`** - this both acknowledges the purchase (required within 3 days or Google auto-refunds and revokes it) *and* frees the product to be bought again next time.

`consumeAsync` alone satisfies acknowledgement; do **not** also call `acknowledgePurchase` for a consumable.

## Gotchas that bite

- **Acknowledge/consume within 3 days** or Google auto-refunds and the bypass silently reverses. For a bypass you consume within seconds of granting, so this is only a risk if the grant→consume path can crash between steps. Consume right after granting; on next startup, `queryPurchases` and consume any owned-but-unconsumed token (crash recovery).
- **Grant on the signed purchase, then consume - in that order.** If you consume first and crash before granting, the user paid and got nothing.
- **No backend = weaker fraud protection.** Acceptable at $1; Google's own guidance says client-only `consumeAsync` is a valid pattern. Don't build a server for this. `// ponytail: client-only billing; add server verification only if $1 fraud ever shows up in the numbers`
- **Test with license testers** (Play Console → license testing) so purchases are free and instantly refundable; real charges need an internal/closed test track.
- **Billing Library version floor:** new apps/updates must ship **v8+ by Aug 31 2026** (extension to Nov 1 2026 on request). Start on the current major, not an old one.

## Policy watch (load-bearing)

The paid unlock brushes Google's ransomware policy - see [[play-policy-risk-analysis]]. The safety valve that keeps it compliant: **the user is never actually locked out** (Settings/Play Store never blocked, force-stop/uninstall always work), so the $1 is a convenience shortcut, not the only way to regain control. Keep that true and obvious in copy. The payment mechanism itself (Google Play Billing) is standard and uncontroversial; the *optics of paying to unlock* are the only risk, not the plumbing.

## Sources

- [One-time products](https://developer.android.com/google/play/billing/one-time-products)
- [One-time purchase lifecycle](https://developer.android.com/google/play/billing/lifecycle/one-time)
- [Integrate the Play Billing Library](https://developer.android.com/google/play/billing/integrate)
- [Google Play's billing system](https://developer.android.com/distribute/play-billing)
- [Billing Library release notes (version deadlines)](https://developer.android.com/google/play/billing/release-notes)
