/* =========================================================================
   Golftraining – Sync-Worker (Cloudflare Worker, Module-Syntax)

   Schreibt Dateien ins GitHub-Repo. Abwärtskompatibel zum bisherigen Sync:
   Ein POST { data: ... } OHNE "path" schreibt weiterhin trainingsdaten.json.
   Mit { path:"wissen-bilder.json", data:{...} } werden die Wissen-Bilder
   synchronisiert.

   >>> AUF DEINE VORHANDENEN WORKER-VARIABLEN ABGESTIMMT <<<
   Du musst KEINE Variable umbenennen oder neu anlegen. Es werden genau
   diese (bereits vorhandenen) Variablen genutzt:

       WRITE_KEY      (Secret)    – dein Schreib-Passwort (= App-Passwort)
       GITHUB_TOKEN   (Secret)    – GitHub Token, Contents: Read and write
       GH_OWNER       (Plaintext) – z. B. golftraining-lars
       GH_REPO        (Plaintext) – z. B. Golftraining
       ALLOW_ORIGIN   (Plaintext) – z. B. *   (für CORS)

   Optional (nur falls dein GitHub Pages NICHT von "main" ausgeliefert wird):
       GH_BRANCH      (Plaintext) – Branch-Name; ohne diese Variable = "main"

   Einrichtung: Im Cloudflare-Dashboard deinen Worker öffnen -> Edit code ->
   diesen kompletten Inhalt einfügen -> Save and deploy. Fertig.
   ========================================================================= */

const ALLOWED_FILES = new Set(["trainingsdaten.json", "wissen-bilder.json"]);

function corsHeaders(env) {
  return {
    "Access-Control-Allow-Origin": env.ALLOW_ORIGIN || "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, X-Write-Key",
  };
}

export default {
  async fetch(request, env) {
    const CORS = corsHeaders(env);
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    if (request.method !== "POST") return json({ error: "POST only" }, 405, CORS);

    // Auth
    if ((request.headers.get("X-Write-Key") || "") !== env.WRITE_KEY)
      return json({ error: "unauthorized" }, 401, CORS);

    // Body
    let body;
    try { body = await request.json(); }
    catch (e) { return json({ error: "invalid json" }, 400, CORS); }

    const path = body.path || "trainingsdaten.json";
    if (!ALLOWED_FILES.has(path)) return json({ error: "file not allowed: " + path }, 400, CORS);
    if (body.data === undefined) return json({ error: "no data" }, 400, CORS);

    const branch = env.GH_BRANCH || "main";
    const text = JSON.stringify(body.data);
    const content = base64Utf8(text);

    const api = `https://api.github.com/repos/${env.GH_OWNER}/${env.GH_REPO}/contents/${encodeURIComponent(path)}`;
    const ghHeaders = {
      "Authorization": `Bearer ${env.GITHUB_TOKEN}`,
      "Accept": "application/vnd.github+json",
      "User-Agent": "golf-sync-worker",
    };

    // aktuelle sha holen (falls die Datei schon existiert)
    let sha;
    try {
      const cur = await fetch(`${api}?ref=${encodeURIComponent(branch)}`, { headers: ghHeaders });
      if (cur.ok) { const j = await cur.json(); sha = j.sha; }
    } catch (e) { /* Datei existiert noch nicht -> neu anlegen */ }

    // schreiben (create/update)
    const put = await fetch(api, {
      method: "PUT",
      headers: ghHeaders,
      body: JSON.stringify({
        message: `update ${path} via app (${new Date().toISOString()})`,
        content,
        branch,
        ...(sha ? { sha } : {}),
      }),
    });

    if (!put.ok) {
      const t = await put.text().catch(() => "");
      return json({ error: "github", status: put.status, detail: t.slice(0, 300) }, 502, CORS);
    }
    return json({ ok: true, path, bytes: text.length }, 200, CORS);
  },
};

function json(obj, status, CORS) {
  return new Response(JSON.stringify(obj), {
    status: status || 200,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

// UTF-8-sichere Base64-Kodierung (btoa allein kann keine Umlaute)
function base64Utf8(str) {
  const bytes = new TextEncoder().encode(str);
  let bin = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    bin += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
  }
  return btoa(bin);
}
