package com.mytv.home;

import android.content.Context;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Tiny web page on the home Wi-Fi (http://TV-IP:8080) to add or remove the family photos from a phone.
 * Photos stay only on the TV, never on the internet.
 */
public final class PhotoServer {
    public static final int PORT = 8080;
    private static boolean started;

    /** 4-digit code shown on the TV; the phone page needs it before it can see or change photos. */
    public static String pin(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("photos", Context.MODE_PRIVATE);
        String p = sp.getString("pin", null);
        if (p == null) {
            p = String.format(Locale.US, "%04d", new java.security.SecureRandom().nextInt(10000));
            sp.edit().putString("pin", p).apply();
        }
        return p;
    }

    /** Family names typed on the phone page, kept only on the TV: {dad, mom, kids[]} */
    public static org.json.JSONObject family(Context c) {
        try {
            return new org.json.JSONObject(c.getSharedPreferences("photos", Context.MODE_PRIVATE).getString("family", "{}"));
        } catch (Exception e) {
            return new org.json.JSONObject();
        }
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        d.mkdirs();
        return d;
    }

    public static File[] photos(Context c) {
        File[] f = dir(c).listFiles((d, n) -> n.endsWith(".jpg"));
        if (f == null) return new File[0];
        Arrays.sort(f);
        return f;
    }

    public static synchronized void start(Context ctx) {
        if (started) return;
        started = true;
        Context app = ctx.getApplicationContext();
        Thread t = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(PORT)) {
                while (true) {
                    Socket s = ss.accept();
                    new Thread(() -> handle(app, s)).start();
                }
            } catch (Exception e) {
                started = false;
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public static String address() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return "http://" + a.getHostAddress() + ":" + PORT;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static void handle(Context c, Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(30000);
            InputStream in = sock.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null) return;
            int length = 0;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                if (line.toLowerCase(Locale.US).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
            }
            String[] parts = requestLine.split(" ");
            String method = parts[0], path = parts.length > 1 ? parts[1] : "/";
            String query = "";
            int q = path.indexOf('?');
            if (q >= 0) { query = path.substring(q + 1); path = path.substring(0, q); }
            OutputStream out = new BufferedOutputStream(sock.getOutputStream());
            boolean page = path.equals("/") || path.equals("/index.html");
            if (!page && !pin(c).equals(param(query, "pin"))) { send(out, 403, "text/plain", "pin".getBytes()); return; }

            if (method.equals("POST") && path.equals("/upload")) {
                if (length <= 0 || length > 15_000_000) { send(out, 413, "text/plain", "too big".getBytes()); return; }
                byte[] body = new byte[length];
                int got = 0;
                while (got < length) { int r = in.read(body, got, length - got); if (r < 0) break; got += r; }
                File f = new File(dir(c), System.currentTimeMillis() + ".jpg");
                try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(body, 0, got); }
                send(out, 200, "text/plain", "ok".getBytes());
            } else if (method.equals("POST") && path.equals("/delete")) {
                String name = new File(URLDecoder.decode(param(query, "name"), "UTF-8")).getName();
                new File(dir(c), name).delete();
                send(out, 200, "text/plain", "ok".getBytes());
            } else if (method.equals("POST") && path.equals("/family")) {
                byte[] body = new byte[Math.min(Math.max(length, 0), 20000)];
                int got = 0;
                while (got < body.length) { int r = in.read(body, got, body.length - got); if (r < 0) break; got += r; }
                org.json.JSONObject j = new org.json.JSONObject(new String(body, 0, got, StandardCharsets.UTF_8));
                c.getSharedPreferences("photos", Context.MODE_PRIVATE).edit().putString("family", j.toString()).apply();
                send(out, 200, "text/plain", "ok".getBytes());
            } else if (path.equals("/family")) {
                send(out, 200, "application/json; charset=utf-8", family(c).toString().getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/list")) {
                StringBuilder sb = new StringBuilder("[");
                for (File f : photos(c)) sb.append(sb.length() > 1 ? "," : "").append('"').append(f.getName()).append('"');
                send(out, 200, "application/json", sb.append("]").toString().getBytes());
            } else if (path.equals("/photo")) {
                File f = new File(dir(c), new File(param(query, "name")).getName());
                if (!f.exists()) { send(out, 404, "text/plain", "no".getBytes()); return; }
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                try (FileInputStream fi = new FileInputStream(f)) { byte[] buf = new byte[8192]; int r; while ((r = fi.read(buf)) > 0) b.write(buf, 0, r); }
                send(out, 200, "image/jpeg", b.toByteArray());
            } else {
                send(out, 200, "text/html; charset=utf-8", PAGE.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }

    private static String param(String query, String key) {
        for (String kv : query.split("&")) if (kv.startsWith(key + "=")) return kv.substring(key.length() + 1);
        return "";
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = in.read()) >= 0) {
            if (ch == '\n') return sb.toString();
            if (ch != '\r') sb.append((char) ch);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws Exception {
        String head = "HTTP/1.1 " + code + " OK\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static final String PAGE = "<!doctype html><html lang=ar dir=rtl><head><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'><title>صور شاشتي</title><style>"
            + "body{margin:0;font-family:-apple-system,sans-serif;background:#0f2a2a;color:#f5f1e6;padding:20px}"
            + "h1{font-size:24px;margin:0 0 6px}p{color:#b9c4bc;margin:0 0 18px}"
            + "fieldset{border:1px solid #e8c35a55;border-radius:14px;margin:22px 0;padding:14px}legend{color:#e8c35a;font-weight:bold;padding:0 6px}"
            + "fieldset input{display:block;width:100%;box-sizing:border-box;margin:6px 0 12px;padding:12px;border-radius:10px;border:0;font-size:17px}"
            + "fieldset small{color:#b9c4bc}#sv{background:#e8c35a;color:#10261f;border:0;border-radius:12px;padding:12px 26px;font-size:17px;font-weight:bold}"
            + "label{display:block;background:#e8c35a;color:#10261f;text-align:center;padding:18px;border-radius:14px;font-size:20px;font-weight:bold}"
            + "input{display:none}#st{margin:14px 0;min-height:22px;color:#e8c35a}"
            + "#g{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}"
            + ".c{position:relative}.c img{width:100%;aspect-ratio:1;object-fit:cover;border-radius:10px}"
            + ".c button{position:absolute;top:6px;left:6px;border:0;border-radius:50%;width:32px;height:32px;background:#000a;color:#fff;font-size:16px}"
            + "</style></head><body><h1>صور البيت 📷</h1><p>آخر صورتين بتضيفهم بيظهروا ثابتين على التليفزيون في دواير. والصور بتفضل محفوظة على التليفزيون بس.</p>"
            + "<label>➕ ضيف صور<input id=f type=file accept='image/*' multiple></label><div id=st></div><div id=g></div>"
            + "<fieldset><legend>🏡 العيلة</legend><small>الأسامي دي بتظهر على الشاشة في الأدعية، ومحفوظة على التليفزيون بس.</small>"
            + "<input id=dad placeholder='اسم الأب'><input id=mom placeholder='اسم الأم'>"
            + "<input id=kids placeholder='أسامي الأولاد، وبينهم فاصلة: سارة، مريم'>"
            + "<label style='background:none;color:#f5f1e6;text-align:start;padding:0 0 12px;font-size:16px;font-weight:normal'>"
            + "<input id=girls type=checkbox style='display:inline;width:auto;margin:0 0 0 8px'>كلهم بنات</label>"
            + "<button id=sv>حفظ</button> <span id=fs></span></fieldset><script>"
            + "const st=document.getElementById('st'),g=document.getElementById('g');"
            + "let pin=localStorage.getItem('pin')||'';"
            + "function q(u){return u+(u.includes('?')?'&':'?')+'pin='+pin}"
            + "async function ask(){pin=prompt('اكتب الرقم السري اللي ظاهر على التليفزيون')||'';localStorage.setItem('pin',pin)}"
            + "async function load(){let r=await fetch(q('/list'));while(r.status==403){await ask();r=await fetch(q('/list'))}"
            + "const l=await r.json();g.innerHTML='';"
            + "l.reverse().forEach(n=>{const d=document.createElement('div');d.className='c';"
            + "d.innerHTML='<img src=\"'+q('/photo?name='+n)+'\"><button>✕</button>';"
            + "d.querySelector('button').onclick=async()=>{if(confirm('أمسح الصورة دي؟')){await fetch(q('/delete?name='+n),{method:'POST'});load()}};g.appendChild(d)});}"
            + "function shrink(file){return new Promise((ok,bad)=>{const i=new Image();i.onload=()=>{const m=1600,s=Math.min(1,m/Math.max(i.width,i.height));"
            + "const c=document.createElement('canvas');c.width=i.width*s;c.height=i.height*s;c.getContext('2d').drawImage(i,0,0,c.width,c.height);"
            + "c.toBlob(b=>ok(b),'image/jpeg',0.88)};i.onerror=bad;i.src=URL.createObjectURL(file)})}"
            + "document.getElementById('f').onchange=async e=>{const fs=[...e.target.files];let k=0;"
            + "for(const f of fs){st.textContent='بيرفع '+(++k)+' من '+fs.length+'…';"
            + "try{const b=await shrink(f);await fetch(q('/upload'),{method:'POST',body:b})}catch(x){}}"
            + "st.textContent='تم ✅';e.target.value='';load()};"
            + "async function fam(){const j=await (await fetch(q('/family'))).json();dad.value=j.dad||'';mom.value=j.mom||'';kids.value=(j.kids||[]).join('، ');girls.checked=!!j.girls}"
            + "sv.onclick=async()=>{const k=kids.value.split(/[,،]/).map(x=>x.trim()).filter(x=>x);"
            + "await fetch(q('/family'),{method:'POST',body:JSON.stringify({dad:dad.value.trim(),mom:mom.value.trim(),kids:k,girls:girls.checked})});"
            + "document.getElementById('fs').textContent='اتحفظ ✅'};"
            + "load().then(fam);"
            + "</script></body></html>";
}
