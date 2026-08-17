/* =====================================================================
   SIMULATION EINER GOLFRUNDE — Nordplatz Timmendorfer Strand
   Prüft die KETTE: Rundenstart · GPS · Caddy · Eingaben · draft.json ·
   watch.json · Uhr-Gegenseite · Lochwechsel · Abschluss · Verwerfen.
   ===================================================================== */
const fs=require("fs"), vm=require("vm");
const src=fs.readFileSync("index.html","utf8");
const code=[...src.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)]
  .filter(m=>!/\bsrc=|application\/json|text\/markdown|devdocs/.test(m[1])).map(m=>m[2]).join("\n");

/* ---------- Ablage des "Repos" + Worker-Nachbau ---------- */
const REPO={};                       // Pfad -> Text
let sha=1;
const SHAS={};
function put(path, body){ REPO[path]=body; SHAS[path]="sha"+(sha++); return true; }
function workerFetch(url, opt){
  opt=opt||{};
  const u=new URL(url, "https://w.example");
  const p=u.searchParams.get("path")||"trainingsdaten.json";
  if((opt.method||"GET")==="GET"){
    if(u.searchParams.get("sha")==="1")
      return res(200,{ok:true,sha:SHAS[p]||""});
    if(u.searchParams.get("fresh")==="1"){
      if(!(p in REPO)) return res(404,{ok:false});
      return new Antwort(200, REPO[p], {"X-Repo-Sha":SHAS[p]||""});
    }
    return res(200,{ok:true,worker:"golftraining-sync v2.7"});
  }
  const pfad=(opt.headers||{})["X-Path"];
  if(!pfad) return res(400,{ok:false});
  if(["trainingsdaten.json","wissen-bilder.json","draft.json","watch.json"].indexOf(pfad)<0)
    return res(403,{ok:false,error:"path"});
  const base=(opt.headers||{})["X-Base-Sha"]||"";
  if(pfad in REPO && base && base!==SHAS[pfad]) return res(409,{ok:false});
  put(pfad, opt.body);
  return res(200,{ok:true});
}
class Antwort{
  constructor(status, text, headers){ this.status=status; this.ok=status>=200&&status<300;
    this._t=text; this._h=headers||{}; }
  text(){ return Promise.resolve(this._t); }
  json(){ return Promise.resolve(JSON.parse(this._t)); }
  get headers(){ const h=this._h; return {get:k=>h[k]||null}; }
}
function res(status,obj){ return new Antwort(status, JSON.stringify(obj)); }

/* ---------- Sandkasten ---------- */
const noop=()=>{};
const store={};
const el=new Proxy(function(){},{apply:()=>el,get:(t,k)=>{
  if(k===Symbol.toPrimitive||k==="toString") return ()=>"";
  if(k==="style"||k==="dataset"||k==="classList"||k==="parentNode") return el;
  if(k==="querySelectorAll"||k==="getElementsByTagName") return ()=>[];
  if(k==="textContent"||k==="innerHTML"||k==="value"||k==="id") return store[k]||"";
  if(k==="length") return 0; if(k==="checked"||k==="disabled") return false;
  if(k==="scrollIntoView"||k==="focus"||k==="blur"||k==="click") return noop;
  return el; }, set:(t,k,v)=>{store[k]=v;return true;}});
const LS={};
const sandbox={console,Math,Date,JSON,isFinite,isNaN,parseInt,parseFloat,String,Number,Object,Array,
  Map,Set,Promise,RegExp,Error,Boolean,Symbol,encodeURIComponent,decodeURIComponent,URL,
  /* setTimeout MUSS ausfuehren (2026-08-16): Vorher gab es nur eine Zahl
     zurueck und rief nie auf. Damit blieb jedes `await new Promise(r =>
     setTimeout(r, …))` fuer immer haengen — und die Simulation endete
     stillschweigend mitten im Ablauf, was wie „bestanden" aussah.
     Die WARTEZEIT wird ignoriert: Der Prueflauf soll nicht echte Sekunden
     verbringen, nur die Reihenfolge einhalten.
     setInterval bleibt bewusst still — sonst laufen Takte endlos weiter. */
  setTimeout:(f)=>{ if(typeof f==="function") setImmediate(f); return 0; },
  clearTimeout:noop, setInterval:()=>0, clearInterval:noop,
  fetch:(u,o)=>Promise.resolve(workerFetch(u,o)),
  localStorage:{getItem:k=>(k in LS?LS[k]:null), setItem:(k,v)=>{LS[k]=String(v);}, removeItem:k=>{delete LS[k];}},
  indexedDB:undefined,
  navigator:{onLine:true,userAgent:"node",geolocation:{watchPosition:()=>1,clearWatch:noop,getCurrentPosition:()=>{}},vibrate:noop},
  location:{href:"https://x/index.html",protocol:"https:",search:"",hash:""},
  document:{getElementById:()=>el,querySelector:()=>el,querySelectorAll:()=>[],createElement:()=>el,
    createElementNS:()=>el,addEventListener:noop,removeEventListener:noop,documentElement:el,body:el,
    visibilityState:"visible",head:el,cookie:"",title:"",referrer:"",activeElement:el},
  window:{addEventListener:noop,removeEventListener:noop,matchMedia:()=>({matches:false,addEventListener:noop}),
    innerWidth:400,innerHeight:800,location:{href:"https://x/",protocol:"https:",search:""},devicePixelRatio:2,
    scrollTo:noop,getComputedStyle:()=>({getPropertyValue:()=>""})},
  performance:{now:()=>Date.now()},crypto:{getRandomValues:a=>a},
  requestAnimationFrame:noop,alert:noop,confirm:()=>true,prompt:()=>null,
  btoa:s=>Buffer.from(s).toString("base64"),atob:s=>Buffer.from(s,"base64").toString(),
  TextEncoder,TextDecoder,Image:class{},Blob:class{},FileReader:class{},File:class{},
  screen:{width:400,height:800},history:{pushState:noop,back:noop,replaceState:noop},
  CSS:{supports:()=>false},AbortController:class{constructor(){this.signal={}}abort(){}}};
sandbox.globalThis=sandbox; sandbox.self=sandbox;
vm.createContext(sandbox);
try{ vm.runInContext(code,sandbox,{timeout:30000}); }catch(e){ console.log("LADEFEHLER:",e.message); }
const G=n=>{ try{ return vm.runInContext(n,sandbox); }catch(e){ return undefined; } };
const R=c=>{ try{ return vm.runInContext(c,sandbox); }catch(e){ return {FEHLER:e.message}; } };

let ok=0, fail=0;
function pruef(txt, bed, info){ if(bed){ ok++; console.log("  ✓ "+txt); }
  else { fail++; console.log("  ✗ "+txt+(info?"   → "+info:"")); } }
function kopf(t){ console.log("\n── "+t); }
module.exports={G,R,pruef,kopf,REPO,SHAS,sandbox,bilanz:()=>({ok,fail})};
