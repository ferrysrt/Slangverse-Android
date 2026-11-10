import http from 'node:http';
import {grade,progress,publicQuestion} from './game.mjs';
import {createSessionService} from './sessionService.mjs';
const generateSession = createSessionService();
const sessions = new Map();
const port = Number(process.env.PORT||8787);
const bindHost = process.env.HOST||'127.0.0.1';
const maxSessions = 1000;
function reply(res,status,body){
  res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'Content-Type','Access-Control-Allow-Methods':'GET,POST,OPTIONS','Cache-Control':'no-store'});
  res.end(status===204?'':JSON.stringify(body));
}
async function parseBody(req){
  const chunks=[];let size=0;
  for await(const c of req){size+=c.length;if(size>20_000){const e=new Error('payload-too-large');e.status=413;throw e;}chunks.push(c);}
  try{return JSON.parse(Buffer.concat(chunks).toString('utf8')||'{}');}
  catch{const e=new Error('invalid-json');e.status=400;throw e;}
}
function cleanup(){
  const now=Date.now();for(const [id,s] of sessions)if(s.expiresAt<=now)sessions.delete(id);
  while(sessions.size>maxSessions){const first=sessions.keys().next().value;if(!first)break;sessions.delete(first);}
}
const server = http.createServer(async(req,res)=>{
  const started=performance.now(),url=new URL(req.url||'/',`http://${bindHost}:${port}`);let source='none';
  try{
    if(req.method==='OPTIONS')return reply(res,204,{});
    if(req.method==='GET'&&url.pathname==='/api/health')return reply(res,200,{ok:true,mode:process.env.LLM_PROVIDER||'mock',llmConnected:false,providerConfigured:Boolean(process.env.LLM_API_KEY&&process.env.LLM_MODEL)});
    if(req.method==='GET'&&url.pathname==='/api/trending')return reply(res,200,{source:'curated',trending:[{tag:'#CampusChaos',category:'campus'},{tag:'#GamingMoments',category:'gaming'},{tag:'#MemeCulture',category:'internet'}]});
    if(req.method==='POST'&&url.pathname==='/api/sessions'){
      cleanup();const generated=await generateSession();const session=generated.session;source=session.source;sessions.set(session.id,session);
      if(generated.failureReason)console.warn(JSON.stringify({event:'generation-fallback',category:generated.failureReason,attempts:generated.providerCalls,promptVersion:generated.promptVersion}));
      return reply(res,201,{id:session.id,source:session.source,generationLatencyMs:generated.generationLatencyMs,questions:session.questions.map(publicQuestion)});
    }
    const match=url.pathname.match(/^\/api\/sessions\/([\w-]+)(\/answers)?$/);
    if(match){
      cleanup();const session=sessions.get(match[1]);if(!session)return reply(res,404,{error:'session-not-found'});source=session.source;
      if(req.method==='GET'&&!match[2])return reply(res,200,progress(session));
      if(req.method==='POST'&&match[2]){
        const data=await parseBody(req);if(!data||typeof data!=='object'||Array.isArray(data))return reply(res,400,{error:'invalid-body'});
        const answer=grade(session,data.questionId,data.selectedIndex);return reply(res,answer.status,answer);
      }
    }
    if(req.method==='GET'&&url.pathname==='/api/leaderboard')return reply(res,200,{source:'demo',competitive:false,players:[],message:'Competitive ranking disabled until standardized daily sessions and authentication exist.'});
    return reply(res,404,{error:'not-found'});
  }catch(e){return reply(res,e.status||500,{error:e.status?e.message:'internal-error'});}
  finally{console.log(JSON.stringify({event:'http',method:req.method,path:url.pathname,elapsedMs:Math.round((performance.now()-started)*100)/100,provider:'none',source,at:new Date().toISOString()}));}
});
if(process.env.NODE_ENV!=='test'&&process.env.SLANGVERSE_NO_LISTEN!=='1')server.listen(port,bindHost,()=>console.log(`Slangverse MOCK API at http://${bindHost}:${port}`));
export default server;
