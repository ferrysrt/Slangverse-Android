import {readFileSync} from 'node:fs';

// Shared, versioned local asset catalogue. The LLM emits IDs, never paths or image URLs.
const catalog=JSON.parse(readFileSync(new URL('../data/profile-pictures.json',import.meta.url),'utf8'));
export const avatarIds=Object.freeze(catalog.avatars.map(a=>a.id));
const allowed=new Set(avatarIds);
const handles=new Map(catalog.avatars.filter(a=>a.handle).map(a=>[a.handle,a.id]));
const normalizeHandle=value=>typeof value==='string'?value.trim().toLowerCase():'';

export function resolveAvatarId(requestedId,handle=''){
  const normalized=normalizeHandle(handle);
  if(handles.has(normalized))return handles.get(normalized);
  if(typeof requestedId==='string'&&allowed.has(requestedId))return requestedId;
  if(!normalized)return catalog.defaultId;
  let hash=2166136261;
  for(const byte of Buffer.from(normalized,'utf8'))hash=Math.imul(hash^byte,16777619)>>>0;
  return catalog.fallbackIds[hash%catalog.fallbackIds.length];
}

/** Cosmetic normalization preserves question content, grade keys and the pack contract. */
export function withProfilePictures(items){
  const cloned=structuredClone(items);
  const actors=cloned.flatMap(q=>[q.post,...q.post.comments]);
  const requested=new Map();
  for(const actor of actors){
    const handle=normalizeHandle(actor.handle);
    if(!requested.has(handle)&&allowed.has(actor.avatarId))requested.set(handle,actor.avatarId);
  }
  for(const actor of actors)actor.avatarId=resolveAvatarId(requested.get(normalizeHandle(actor.handle)),actor.handle);
  return cloned;
}

export function avatarPromptInstructions(){
  return `Local illustrated profile pictures are available by avatarId only.
Allowed avatarId values: ${avatarIds.join(', ')}.
Add avatarId to post and each post.comments entry. Use the same avatarId every time a handle appears in the package.
For established characters use these mappings: ${[...handles].filter(([h])=>h!=='@you').map(([h,id])=>`${h}=${id}`).join(', ')}.
For new fictional handles prefer guest_01 through guest_08. Do not use player for a bot.
Never return an image URL, SVG, file path or base64 image. The Android app resolves IDs to bundled pictures offline.
Avatars express a stable fictional identity only; do not encode difficulty, answer correctness, target meaning or grading in avatar choices.`;
}
