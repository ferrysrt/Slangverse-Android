# Play Slangverse on a real Android phone with cloud GPT-6 Luna

**Scope:** Research/dev prototype only. The Android APK does **not** contain an OpenRouter key. The GitHub Actions build is not a server. This guide does not claim cloud deployment or phone play has already been tested.

## Step 1 — Confirm the GitHub pull request and Android artifact

- Merge the cloud-play PR after both the **Mock backend checks** and **Android debug build** CI jobs succeed.
- Open GitHub -> Actions -> **Android debug build (no Codex)** -> successful workflow -> download the `slangverse-debug-apk` ZIP -> extract/install `app-debug.apk`.
- For a physical phone, the emulator-only `http://10.0.2.2:8787` address will not work.

## Step 2 — Deploy the HTTPS backend (Render hobby free tier)

1. Sign in to [Render](https://dashboard.render.com/), connect GitHub with permission to read the **private** `ferrysrt/Slangverse-Android` repository.
2. Choose **New -> Blueprint**, select this repository and the branch containing `render.yaml`. Render config is in the repo root, but the service works in `backend/`.
3. During initial Blueprint creation, enter the two **secret** values that Render requests:
   - `LLM_API_KEY`: your OpenRouter API key, **server side only**.
   - `SLANGVERSE_CLIENT_TOKEN`: a **different, random** 24+ character passphrase for your phone, not the OpenRouter API key. Keep it out of the repo and chat.
4. For a local 32-byte client token, PowerShell can create one:
   ```powershell
   $bytes = New-Object byte[] 32
   [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
   [Convert]::ToBase64String($bytes)
   ```
   Save the value in a password manager. You will enter it into the Android app when connecting.
5. Deploy the service. The Blueprint uses the **free** plan, Singapore region, HTTPS endpoint, `LLM_PROVIDER=openai-compatible`, `LLM_MODEL=openai/gpt-6-luna` (not `:batch`), no automatic deployments, and a required backend token. If you edit the Blueprint after service creation, Render does not automatically request new `sync: false` values—set them manually in Environment.
6. Check `https://YOUR-SERVICE.onrender.com/api/health` in a browser. This endpoint is intentionally public for health checking; it returns `mode: openai-compatible` and `providerConfigured: true` if the model credentials are present. It does **not** prove a successful LLM call.

   PowerShell protected catalog check (replace placeholders locally):
   ```powershell
   Invoke-RestMethod -Uri "https://YOUR-SERVICE.onrender.com/api/catalog" -Headers @{ Authorization = "Bearer YOUR_SEPARATE_CLIENT_TOKEN" }
   ```

**Free tier limitations:** Render can spin down a free service after 15 minutes without requests; the next request may take around a minute. The initial Android catalog request allows 90 seconds for cold starts, and session creation allows 150 seconds. Sessions are only in backend memory, so spin-downs, restarts or redeploys **erase active sessions**. Free hosting does not make OpenRouter calls free. Monitor spending and set a provider-level usage limit.

## Step 3 — Play on Android

1. Install a debug APK containing the cloud-play PR.
2. Open Slangverse -> **Feed -> PLAY API SESSION**.
3. Enter **Backend base URL**: the full `https://YOUR-SERVICE.onrender.com` URL (no trailing `/api`).
4. Enter the **Backend access token** from Step 2. **Do not** enter the OpenRouter API key.
5. Tap **LOAD AVAILABLE SLANG**, select a provisional slang sense and **START 10-CONTEXT SESSION**.
6. Verify the post screen actually displays `QUESTION SOURCE: LLM`. `MOCK` means mock mode. `FALLBACK` means the backend could not obtain a structurally valid LLM package and used mock questions instead.
7. Play and grade answers. Score and correct options come from the Node backend, not the APK.

## Security and cost guardrails

- The cloud Blueprint has `SLANGVERSE_REQUIRE_TOKEN=true`; a missing/short `SLANGVERSE_CLIENT_TOKEN` stops backend startup rather than running publicly without protection.
- All game endpoints other than the public health check require `Authorization: Bearer <client token>`. Android's token input is masked, kept in Compose memory during the screen's lifetime, and never hardcoded into the APK.
- One active LLM generation at a time is permitted. `SLANGVERSE_MAX_LLM_SESSIONS=10` bounds *attempted sessions per process*, and returns HTTP 429 afterward. **This is not a durable spending cap**: the counter resets on restart, and each generation may make external requests. Set an independent limit at the OpenRouter billing/account level.
- This is a shared-secret prototype—not a complete account/auth/rate-limited production architecture. Do not share the token publicly or distribute the API/phone configuration to untrusted users. Revoke/rotate the token if exposed.
- Do not publish on plain HTTP. HTTP is only for **trusted private LAN testing** in debug builds; always use HTTPS for the cloud.
- Session data has no permanent database, audit log or durable research dataset. Back up research metrics separately without leaking questions, answer keys, credentials, or participant details.
- Valid JSON and 10 distinct context IDs are **not** linguistic or educational validation.

## Local Wi-Fi alternative (no cloud account)

On your own laptop in `backend/`, run:

```powershell
$env:HOST = "0.0.0.0"
npm start
```

For live GPT-6 Luna rather than the default mock mode, make your private `backend/.env` with `LLM_PROVIDER=openai-compatible`, `LLM_API_KEY`, `LLM_MODEL=openai/gpt-6-luna`, `LLM_TIMEOUT_MS=120000`, `LLM_MAX_TOKENS=8000`, and `LLM_RETRIES=0` then run `npm run start:env`. Include `HOST=0.0.0.0`. Phone and laptop must be on the same trusted Wi-Fi; use `http://LAPTOP-WIFI-IP:8787` in the APK. Set an optional separate `SLANGVERSE_CLIENT_TOKEN` for local bearer protection and enter it on the phone. Do not expose the local server to the internet.

**Validation checklist:** backend CI ✅/❌; Android CI ✅/❌; Render deployed ✅/❌; `/api/health` responsive ✅/❌; protected catalog accessible ✅/❌; a session reports `LLM` ✅/❌; answers score correctly ✅/❌; 10 questions complete ✅/❌. Do not treat earlier CI smoke tests as a real-device result.
