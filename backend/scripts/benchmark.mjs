// Requires the Slangverse backend to be running. This measures end-to-end HTTP,
// not pure LLM inference time. Use generationLatencyMs for provider-side figures.
const root=(process.env.API_URL||'http://127.0.0.1:8787').replace(/\/$/,'');
const count=Math.max(1,Math.min(100,Number(process.env.RUNS||10)));
const results=[];
for(let i=0;i<count;i++){
  const start=performance.now();
  try{
    const resp=await fetch(root+'/api/sessions',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(90_000)});
    if(!resp.ok)throw new Error(`HTTP ${resp.status}`);
    const obj=await resp.json();
    results.push({ok:true,source:obj.source,httpLatencyMs:performance.now()-start,generationLatencyMs:obj.generationLatencyMs,questions:obj.questions?.length});
  }catch(error){results.push({ok:false,error:error.message,httpLatencyMs:performance.now()-start});}
}
function stats(nums){if(!nums.length)return null;const x=[...nums].sort((a,b)=>a-b);return {minMs:+x[0].toFixed(2),meanMs:+(x.reduce((a,b)=>a+b,0)/x.length).toFixed(2),p95Ms:+x[Math.ceil(x.length*.95)-1].toFixed(2),maxMs:+x[x.length-1].toFixed(2)};}
const sources={};for(const r of results)if(r.ok)sources[r.source]=(sources[r.source]||0)+1;
const success=results.filter(r=>r.ok);
console.log(JSON.stringify({type:'Slangverse exploratory technical benchmark',total:count,success:success.length,failures:count-success.length,bySource:sources,roundTripHttp:stats(success.map(r=>r.httpLatencyMs)),reportedGeneration:stats(success.map(r=>r.generationLatencyMs).filter(v=>Number.isFinite(v))),note:'Do not combine mock/fallback with real LLM generation statistics.'},null,2));
if(success.length!==count)process.exitCode=1;
