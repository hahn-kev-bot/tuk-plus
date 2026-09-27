# Phase 3 – Account

Status: **not started**.

## Goal

The user logs in with a phone number and an SMS code, and the app keeps the
session. Saved addresses are available for checkout.

**Done when** (PLAN.md §8): login works with the owner's real phone.

## Scope

- Phone login (api-reference §2.1):
  1. `POST magic_login {source:"sms", source_user_id, uuid}` sends a 4-digit SMS code.
  2. `GET magic_login/{id}?code=` gives the login bundle `{user, user_source, roles}`.
  3. Resend with `GET magic_login/{id}?resend=`. Email code as a second way
     (`POST email/login`).
- Session:
  - the device uuid (already made in phase 0) is written onto the user with
    `PATCH users/{id} {uuid}`;
  - the user id is the session. Store it encrypted (Android Keystore). Never log
    it (the log hashes it).
  - check the pair at start with `users/{id}?uuid=&check=true`.
- Profile: name and phone (`PATCH users/{id}`). English only, so no language setting yet.
- Saved addresses: list, add, edit, delete (`user_addresses`). Past delivery
  places from `helpers/get_past_delivery_addresses`.
- Logout (`PATCH logout/{id}`).
- Account tab: replaces the placeholder. Debug and logs stay there.
- "For you" recommendations with the user id.
- Session start log line: `logged_in` becomes true after login.

Not in scope: LINE, Facebook and Apple login (they need Tuk's OAuth app
registrations).

## Risks

- Write calls: login sends a real SMS. Only the owner tests it.
- The web app adds `+66` by default and removes a leading `0`. Copy this rule.

## Draft owner test list

1. Log in with your phone number. The SMS code arrives and works.
2. Close and open the app. You are still logged in.
3. Add, edit and delete an address. Check the same list on tukapp.co.
4. Log out and log in again.
5. Share the logs. No phone number or user id shows in clear text.
