import test from 'node:test';
import assert from 'node:assert/strict';
import {collectDocuments,validateDiscoveries,extractCandidates,SEARCH_URL} from '../dataset/refresh.mjs';
const docs=[{url:'https://example.org/words',title:'Slang example',
 markdown:'People say no cap when sharing a true story. It appears in comments.'}];
test('Firecrawl v2 returns nested data.web with source URLs',async()=>{
 let requestUrl,body;
 const fetchImpl=async(url,opts)=>{requestUrl=url;body=JSON.parse(opts.body);return {ok:true,json:async()=>({data:{web:docs}})}};
 const found=await collectDocuments({key:'fake',fetchImpl,query:'recent slang'});
 assert.equal(requestUrl,SEARCH_URL);
 assert.equal(body.scrapeOptions.formats[0].type,'markdown');
 assert.equal(found.length,1);
});
test('evidence and source URL cannot be fabricated',()=>{
 const good={term:'no cap',definition:'used when telling a truthful story',sourceUrl:docs[0].url,evidenceSnippet:'People say no cap'};
 const result=validateDiscoveries([good,
   {...good,term:'madeup'},
   {...good,sourceUrl:'https://example.com/unseen'},
   {...good,evidenceSnippet:'this quote is imaginary'}
 ],docs);
 assert.equal(result.entries.length,1);
 assert.equal(result.rejected.length,3);
 assert.equal(result.entries[0].reviewStatus,'unreviewed');
});
test('LLM normalizer output remains unreviewed',async()=>{
 const row={term:'no cap',definition:'used when telling a truthful story',sourceUrl:docs[0].url,evidenceSnippet:'People say no cap'};
 const fetchImpl=async()=>({ok:true,json:async()=>({choices:[{message:{content:JSON.stringify({items:[row]})}}]})});
 const x=await extractCandidates({docs,key:'mock',model:'mock',fetchImpl});
 assert.equal(x.entries.length,1);
 assert.equal(x.entries[0].qualityClaim,'source-evidence-only-not-expert-validated');
});
