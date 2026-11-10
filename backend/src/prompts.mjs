export const PROMPT_VERSION = 'slangverse-v2.2';
export const CONTEXTS = [
  'campus','gaming','office','travel','sports',
  'technology','finance','event','exam','application'
];
export function createGenerationPrompt({term='cooked',meaning='in serious trouble'}={}){
  return `Generate an EDUCATIONAL, FICTIONAL Internet-slang question package, JSON only. No markdown.
Term: ${JSON.stringify(term)}.
Approved reference meaning: ${JSON.stringify(meaning)}.
Provide exactly 10 distinct fictional English social posts, one context in each of these EXACT contextId values IN ORDER: ${CONTEXTS.join(', ')}.
Difficulty sequence: easy,easy,easy,easy,easy,medium,medium,medium,hard,hard. These are nominal design labels, not empirically measured difficulty.
The JSON root MUST be an object with an items array of exactly ten elements, nothing else required.
Each item MUST include these fields and types:
{
 "id":"${term}-1", "slang":"${term}", "targetMeaning":${JSON.stringify(meaning)},
 "difficulty":"easy", "contextId":"campus",
 "post":{"id":"campus","displayName":"Campus Account","handle":"@fictionalhandle","hashtag":"#CampusChaos","content":"A natural fictional post containing ${term}","comments":[{"handle":"@fictionalreply","text":"an indirect context clue"}]},
 "question":"What does '${term}' mean in this post?",
 "options":["wrong1",${JSON.stringify(meaning)},"wrong2","wrong3"], "correctIndex":1,
 "explanation":"A short explanation that fits the approved meaning"
}
Rules:
- Use the approved reference meaning verbatim as exactly one of the four options; correctIndex MUST point to it (zero-based). Rotate the correctIndex position across items.
- Four distinct options per question, with three plausible but incorrect distractors. Use straightforward language.
- Post content must use the exact term naturally in the intended figurative sense; one consistent meaning per package. Different real situations and narrative angles.
- Each post has 2 or 3 fictional user replies. Replies are subtle reactions, implications, or jokes, NEVER dictionary definitions.
- No comment may include the literal approved meaning or any whole option text. Don't reveal an answer letter or the correct option.
- Keep reply strings short, platform-style, and non-violent. No real personal data, brands, links, hate, or sexual content.
- Unique IDs from '${term}-1' to '${term}-10'; unique context IDs. Return JSON object only.
- This is fictional educational material and is NOT linguistically certified.`;
}
