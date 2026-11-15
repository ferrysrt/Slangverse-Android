# Local profile pictures

22 illustrated portraits from **Open Peeps by Pablo Stanley**, rendered locally using **DiceBear 9.4.2**. The artwork is **CC0 1.0**. DiceBear's renderer code is MIT licensed. The upstream notices are retained in `LICENSE-OPEN-PEEPS.txt` and `LICENSE-DICEBEAR-CORE.txt`.

- Original artwork: https://www.openpeeps.com/
- Artwork licence: https://creativecommons.org/publicdomain/zero/1.0/
- Renderer source: https://github.com/dicebear/dicebear
- Style documentation: https://www.dicebear.com/styles/open-peeps/

These are fictional illustrations, not photographs of people or evidence about a character's background. Appearance is unrelated to question difficulty, correct answers, XP, or mastery.

## Files

- `preview.png`: labelled contact sheet for reviewing the set.
- `catalog.json`: canonical IDs, handle bindings, deterministic seeds/options, source metadata, paths and PNG SHA-256 checksums.
- `source/`: editable/reusable SVG outputs.
- `android-res/drawable-nodpi/`: 256×256 PNGs bundled by Android's resource source set. No network request or SVG dependency is required in the app.
- Generated companions: `android/app/src/main/java/id/slangverse/app/ProfilePictureCatalog.kt` and `backend/data/profile-pictures.json`. The lightweight backend catalogue is deployable with `backend/` alone; the server does not load picture files at runtime.

## How the LLM uses pictures

The model selects an existing `avatarId`; it cannot call a local file or fetch a picture itself. The generation prompt supplies the allowed IDs and established character mappings. For example:

```json
{
  "displayName": "A fictional account",
  "handle": "@newcharacter",
  "avatarId": "guest_03",
  "comments": [{"handle": "@anothercharacter", "avatarId": "guest_07", "text": "An indirect contextual reply"}]
}
```

Available IDs: `mira`, `campus`, `ari`, `matchday`, `ben`, `lila`, `dea`, `jo`, `townloop`, `naya`, `sam`, `kai`, `guest_01` through `guest_08`, `player`, `default`.

Established cast handles always use their canonical face, even if a generated ID differs. New handles use the first valid requested ID within a package. Invalid or missing IDs fall back to a deterministic guest portrait; empty identities use `default`. Backend and Android share the same UTF-8 FNV-1a fallback. The LLM is instructed to avoid `player` for bots. New generated identities are consistent within a pack, not persisted user accounts across sessions.

The backend normalizes metadata after question validation, so a bad cosmetic ID does not trigger an extra paid generation attempt. Existing exports without `avatarId` still render using handle-based fallbacks. Both Live Backend and GitHub GET parse the optional field. No image URLs, remote files, base64 images, or paths are evaluated by the Android resolver.

## Reproduce or extend

From the repository root (Node 20.9+):

```sh
npm ci --prefix tools/profile-pictures
npm --prefix tools/profile-pictures run generate
npm --prefix tools/profile-pictures run check
```

In a restricted cloud shell, use a writable npm cache, e.g. `NPM_CONFIG_CACHE=/tmp/slangverse-npm-cache`. The generator uses a writable temporary font cache when no XDG cache is configured. Dependencies and integrity hashes are pinned in the builder's lockfile. They are build-time tools, not backend runtime dependencies.

To add or change a portrait, edit `rows` and render options in `tools/profile-pictures/generate.mjs`, regenerate, review the contact sheet, and commit the SVG/PNG, catalogue and generated registries together. Android CI checks catalogue freshness and PNG integrity before building the APK.
