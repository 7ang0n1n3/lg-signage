// Author: 7ANG0N1N3 — https://github.com/7ang0n1n3/lg-signage
package com.signage.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;

import fi.iki.elonen.NanoHTTPD;

public class WebServerService extends Service {

    private static final String TAG  = "WebServerService";
    private static final int    PORT = 8080;
    private static final String SERVE_DIR = "/sdcard/signage/";

    private SignageServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, buildNotification());
        File dir = new File(SERVE_DIR);
        if (!dir.exists()) dir.mkdirs();
        try {
            server = new SignageServer(PORT, dir, this);
            server.start();
            Log.i(TAG, "Server started on port " + PORT);
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server", e);
        }
    }

    @Override public int     onStartCommand(Intent i, int f, int s) { return START_STICKY; }
    @Override public IBinder onBind(Intent i)                        { return null; }

    @Override
    public void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    private Notification buildNotification() {
        String channelId = "signage_server";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                channelId, "Signage Server", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            return new Notification.Builder(this, channelId)
                .setContentTitle("Signage")
                .setContentText("Admin: http://<device-ip>:" + PORT + "/admin")
                .setSmallIcon(android.R.drawable.ic_menu_upload_you_tube)
                .build();
        } else {
            return new Notification.Builder(this)
                .setContentTitle("Signage")
                .setContentText("Web server running on port " + PORT)
                .setSmallIcon(android.R.drawable.ic_menu_upload_you_tube)
                .build();
        }
    }

    // -------------------------------------------------------------------------

    static class SignageServer extends NanoHTTPD {

        private final File    serveDir;
        private final Context ctx;
        private static final String PREFS = "prefs";

        SignageServer(int port, File serveDir, Context ctx) {
            super(port);
            this.serveDir = serveDir;
            this.ctx      = ctx;
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri    = session.getUri();
            Method method = session.getMethod();

            // --- Admin UI ---
            if (method == Method.GET && uri.equals("/admin")) {
                return serveAdmin();
            }

            // --- Settings API ---
            if (uri.equals("/api/settings")) {
                if (method == Method.GET)  return handleGetSettings();
                if (method == Method.POST) return handleSetSettings(session);
            }

            // --- Schedule API ---
            if (uri.equals("/api/schedule")) {
                if (method == Method.GET)  return handleGetSchedule();
                if (method == Method.POST) return handleSetSchedule(session);
            }

            // --- Existing routes ---
            if (method == Method.POST && uri.equals("/download")) {
                return handleDownload(session.getParms());
            }
            if (method == Method.DELETE && uri.startsWith("/files/")) {
                return handleDelete(uri.substring(7));
            }
            if (method == Method.GET && uri.equals("/files")) {
                return handleList();
            }

            return serveFile(uri);
        }

        // ---- Admin page (embedded) ------------------------------------------
        // Note: schedule card rendering uses DOM methods (createElement/textContent)
        // to avoid setting innerHTML with user-supplied schedule data.

        private Response serveAdmin() {
            String html = buildAdminHtml();
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
        }

        private String buildAdminHtml() {
            // CSS
            String css =
                "*{box-sizing:border-box;margin:0;padding:0}" +
                "body{background:#111;color:#fff;font-family:sans-serif;padding:20px;max-width:580px;margin:0 auto}" +
                ".hd{font-size:1.5em;color:#a78bfa;margin-bottom:4px}" +
                ".sub{color:#666;font-size:12px;margin-bottom:24px}" +
                ".lbl{font-size:10px;letter-spacing:2px;color:#666;margin:20px 0 8px}" +
                ".row{background:#1e1e1e;padding:16px 20px;cursor:pointer;display:flex;align-items:center;border-top:1px solid #2a2a2a}" +
                ".row:first-child{border-top:none}" +
                ".row.on{background:#1e1e30}" +
                ".name{flex:1;font-size:15px}" +
                ".ck{color:#a78bfa;display:none}" +
                ".row.on .ck{display:block}" +
                "#pnl{background:#181828;padding:16px;display:none}" +
                ".fld{display:block;font-size:10px;letter-spacing:1px;color:#888;margin:14px 0 4px}" +
                "input[type=text],input[type=time]{width:100%;background:#242435;color:#fff;border:none;padding:10px 12px;font-size:15px;outline:none}" +
                ".clrs{display:flex;gap:5px;margin-top:2px}" +
                ".clr{flex:1;height:40px;border:2px solid transparent;cursor:pointer;opacity:.45;transition:opacity .15s}" +
                ".clr.on{border-color:#fff;opacity:1}" +
                ".btns{display:flex;gap:6px;margin-top:2px;flex-wrap:wrap}" +
                ".ob{flex:1;padding:9px 4px;background:#2a2a2a;color:#888;border:none;cursor:pointer;font-size:12px;min-width:60px}" +
                ".ob.on{background:#2d3a5a;color:#a78bfa}" +
                ".swr{display:flex;gap:20px;margin-top:14px}" +
                ".swi{flex:1}" +
                ".swi span{font-size:12px;color:#ccc;display:block;margin-bottom:4px}" +
                "input[type=checkbox]{width:22px;height:22px;cursor:pointer}" +
                "#ab{display:block;width:100%;padding:15px;background:#a78bfa;color:#111;font-size:16px;font-weight:bold;border:none;cursor:pointer;margin-top:28px}" +
                "#ab:hover{background:#c4b5fd}" +
                "#toast{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#222;color:#fff;padding:12px 28px;border-radius:6px;font-size:14px;display:none;border:1px solid #a78bfa;z-index:9999}" +
                // Schedule styles
                ".sc-card{background:#1a1a2e;border:1px solid #2a2a4a;padding:14px 16px;margin-bottom:8px;border-radius:4px}" +
                ".sc-head{display:flex;align-items:center;gap:10px;margin-bottom:10px}" +
                ".sc-time{font-size:22px;font-weight:bold;color:#a78bfa;min-width:60px}" +
                ".sc-days{font-size:11px;color:#888;flex:1}" +
                ".sc-mode{font-size:12px;color:#ccc;background:#222;padding:3px 8px;border-radius:3px}" +
                ".sc-btns{display:flex;gap:6px}" +
                ".sc-edit{flex:1;padding:7px;background:#2d3a5a;color:#a78bfa;border:none;cursor:pointer;font-size:12px}" +
                ".sc-del{padding:7px 12px;background:#3a1a1a;color:#f87171;border:none;cursor:pointer;font-size:12px}" +
                ".sc-tog{padding:7px 14px;background:#1e2e1e;color:#4ade80;border:none;cursor:pointer;font-size:12px;font-weight:bold}" +
                ".sc-tog.off{background:#2e1e1e;color:#666}" +
                "#addsc{display:block;width:100%;padding:13px;background:#1a2a1a;color:#4ade80;font-size:14px;font-weight:bold;border:1px dashed #2a4a2a;cursor:pointer;margin-top:6px}" +
                "#savesc{display:block;width:100%;padding:15px;background:#22c55e;color:#000;font-size:16px;font-weight:bold;border:none;cursor:pointer;margin-top:12px}" +
                "#savesc:hover{background:#4ade80}" +
                // Modal
                ".modal{display:none;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,.85);z-index:1000;overflow-y:auto}" +
                ".modal-box{background:#1a1a2e;margin:20px auto;padding:24px;max-width:540px;border:1px solid #3a3a6a}" +
                ".modal-hd{display:flex;align-items:center;margin-bottom:20px}" +
                ".modal-title{flex:1;font-size:1.1em;color:#a78bfa}" +
                ".modal-close{background:none;border:none;color:#888;font-size:22px;cursor:pointer;padding:0 4px}" +
                ".day-btns{display:flex;gap:4px;margin-top:4px}" +
                ".day-btn{flex:1;padding:8px 2px;background:#242435;color:#888;border:none;cursor:pointer;font-size:11px;font-weight:bold}" +
                ".day-btn.on{background:#2d3a5a;color:#a78bfa}" +
                ".mrow{background:#1e1e1e;padding:12px 16px;cursor:pointer;display:flex;align-items:center;border-top:1px solid #2a2a2a}" +
                ".mrow.on{background:#1e1e30}" +
                ".mrow .ck{color:#a78bfa;display:none;margin-left:8px}" +
                ".mrow.on .ck{display:block}" +
                "#modal-apply{display:block;width:100%;padding:14px;background:#a78bfa;color:#111;font-size:15px;font-weight:bold;border:none;cursor:pointer;margin-top:20px}";

            // Static HTML body
            String body =
                "<div class='hd'>Signage Admin</div>" +
                "<div class='sub' id='inf'>Connecting...</div>" +
                // Current mode
                "<div class='lbl'>DISPLAY MODE</div>" +
                "<div id='modes'>" +
                "<div class='row' data-m='worldclock'><span class='name'>World Clock</span><span class='ck'>&#10003;</span></div>" +
                "<div class='row' data-m='lightning'><span class='name'>Lightning</span><span class='ck'>&#10003;</span></div>" +
                "<div class='row' data-m='fire'><span class='name'>Fire</span><span class='ck'>&#10003;</span></div>" +
                "<div class='row' data-m='marquee'><span class='name'>Marquee Scroll</span><span class='ck'>&#10003;</span></div>" +
                "<div class='row' data-m='crime_eyes'><span class='name'>Crime Eyes</span><span class='ck'>&#10003;</span></div>" +
                "<div class='row' data-m='blank'><span class='name'>Blank (Dark)</span><span class='ck'>&#10003;</span></div>" +
                "</div>" +
                "<div id='pce' style='background:#181010;padding:16px;display:none'>" +
                "<label class='fld'>SELECT IMAGE</label>" +
                "<div class='btns' id='ceg'>" +
                "<button class='ob' data-v='evil-eye-1.gif'>EYE 1</button>" +
                "<button class='ob' data-v='evil-eye-2.gif'>EYE 2</button>" +
                "<button class='ob' data-v='evil-eye-3.gif'>EYE 3</button>" +
                "</div></div>" +
                // Marquee panel
                "<div id='pnl'>" +
                "<label class='fld'>TEXT</label><input type='text' id='mt' placeholder='Enter message...'>" +
                "<label class='fld'>TEXT COLOR</label><div class='clrs' id='mc'></div>" +
                "<label class='fld'>SPEED</label><div class='btns' id='ms'>" +
                "<button class='ob' data-v='1'>SLOW</button><button class='ob' data-v='3'>MED</button><button class='ob' data-v='7'>FAST</button></div>" +
                "<label class='fld'>DIRECTION</label><div class='btns' id='md'>" +
                "<button class='ob' data-v='left'>&#8592; LEFT</button><button class='ob' data-v='right'>RIGHT &#8594;</button>" +
                "<button class='ob' data-v='up'>&#8593; UP</button><button class='ob' data-v='down'>DOWN &#8595;</button></div>" +
                "<div class='swr'><div class='swi'><span>Blink Text</span><input type='checkbox' id='mb'></div></div>" +
                "<label class='fld'>BORDER STYLE</label><div class='btns' id='bst'>" +
                "<button class='ob' data-v='off'>OFF</button><button class='ob' data-v='on'>ON</button>" +
                "<button class='ob' data-v='dotted'>DOTTED</button><button class='ob' data-v='snake'>SNAKE</button></div>" +
                "<label class='fld'>BORDER COLOR</label><div class='clrs' id='bc'></div>" +
                "<div style='margin-top:6px'><button class='ob' id='brb' style='width:100%'>&#127752; RAINBOW</button></div>" +
                "<label class='fld'>BORDER SHAPE</label><div class='btns' id='bsh'>" +
                "<button class='ob' data-v='dot'>DOT</button><button class='ob' data-v='star'>STAR</button><button class='ob' data-v='square'>SQUARE</button></div>" +
                "</div>" +
                // General
                "<div class='lbl'>GENERAL</div>" +
                "<div style='background:#1e1e1e;padding:16px 20px;display:flex;align-items:center;gap:20px'>" +
                "<span style='flex:1;font-size:15px'>Auto-start on boot</span>" +
                "<input type='checkbox' id='ma' style='width:22px;height:22px'></div>" +
                "<button id='ab'>Apply Settings</button>" +
                // Schedule section
                "<div class='lbl' style='margin-top:32px'>SCHEDULE</div>" +
                "<div id='sc-list'></div>" +
                "<button id='addsc'>+ Add Schedule Entry</button>" +
                "<button id='savesc'>Save Schedule</button>" +
                // Modal
                "<div class='modal' id='modal'><div class='modal-box'>" +
                "<div class='modal-hd'><span class='modal-title' id='modal-title'>Schedule Entry</span>" +
                "<button class='modal-close' id='modal-close'>&#10005;</button></div>" +
                "<label class='fld'>TIME</label><input type='time' id='m-time' value='08:00'>" +
                "<label class='fld'>DAYS (leave all off = every day)</label>" +
                "<div class='day-btns' id='m-days'>" +
                "<button class='day-btn' data-d='0'>SUN</button><button class='day-btn' data-d='1'>MON</button>" +
                "<button class='day-btn' data-d='2'>TUE</button><button class='day-btn' data-d='3'>WED</button>" +
                "<button class='day-btn' data-d='4'>THU</button><button class='day-btn' data-d='5'>FRI</button>" +
                "<button class='day-btn' data-d='6'>SAT</button></div>" +
                "<label class='fld'>MODE</label><div id='m-modes'>" +
                "<div class='mrow' data-m='worldclock'><span style='flex:1'>World Clock</span><span class='ck'>&#10003;</span></div>" +
                "<div class='mrow' data-m='lightning'><span style='flex:1'>Lightning</span><span class='ck'>&#10003;</span></div>" +
                "<div class='mrow' data-m='fire'><span style='flex:1'>Fire</span><span class='ck'>&#10003;</span></div>" +
                "<div class='mrow' data-m='marquee'><span style='flex:1'>Marquee Scroll</span><span class='ck'>&#10003;</span></div>" +
                "<div class='mrow' data-m='crime_eyes'><span style='flex:1'>Crime Eyes</span><span class='ck'>&#10003;</span></div>" +
                "<div class='mrow' data-m='blank'><span style='flex:1'>Blank (Dark)</span><span class='ck'>&#10003;</span></div>" +
                "</div>" +
                // Modal marquee sub-panel
                "<div id='m-pnl' style='display:none'>" +
                "<label class='fld'>TEXT</label><input type='text' id='m-mt' placeholder='Enter message...'>" +
                "<label class='fld'>TEXT COLOR</label><div class='clrs' id='m-mc'></div>" +
                "<label class='fld'>SPEED</label><div class='btns' id='m-ms'>" +
                "<button class='ob' data-v='1'>SLOW</button><button class='ob' data-v='3'>MED</button><button class='ob' data-v='7'>FAST</button></div>" +
                "<label class='fld'>DIRECTION</label><div class='btns' id='m-md'>" +
                "<button class='ob' data-v='left'>&#8592; LEFT</button><button class='ob' data-v='right'>RIGHT &#8594;</button>" +
                "<button class='ob' data-v='up'>&#8593; UP</button><button class='ob' data-v='down'>DOWN &#8595;</button></div>" +
                "<div class='swr'><div class='swi'><span>Blink</span><input type='checkbox' id='m-mb'></div></div>" +
                "<label class='fld'>BORDER STYLE</label><div class='btns' id='m-bst'>" +
                "<button class='ob' data-v='off'>OFF</button><button class='ob' data-v='on'>ON</button>" +
                "<button class='ob' data-v='dotted'>DOTTED</button><button class='ob' data-v='snake'>SNAKE</button></div>" +
                "<label class='fld'>BORDER COLOR</label><div class='clrs' id='m-bc'></div>" +
                "<div style='margin-top:6px'><button class='ob' id='m-brb' style='width:100%'>&#127752; RAINBOW</button></div>" +
                "<label class='fld'>BORDER SHAPE</label><div class='btns' id='m-bsh'>" +
                "<button class='ob' data-v='dot'>DOT</button><button class='ob' data-v='star'>STAR</button><button class='ob' data-v='square'>SQUARE</button></div>" +
                "</div>" +
                // Modal crime eyes sub-panel
                "<div id='m-pce' style='display:none'>" +
                "<label class='fld'>SELECT IMAGE</label><div class='btns' id='m-ceg'>" +
                "<button class='ob' data-v='evil-eye-1.gif'>EYE 1</button>" +
                "<button class='ob' data-v='evil-eye-2.gif'>EYE 2</button>" +
                "<button class='ob' data-v='evil-eye-3.gif'>EYE 3</button></div></div>" +
                "<button id='modal-apply'>Save Entry</button>" +
                "</div></div>" +
                "<div id='toast'></div>";

            // JavaScript — schedule card rendering uses DOM methods, not innerHTML, for user data
            String js =
                "const C=['#FF0000','#FF8800','#FFFF00','#00FF00','#00FFFF','#0088FF','#FFFFFF'];" +
                "const DAYS=['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];" +
                "const MNAMES={worldclock:'World Clock',lightning:'Lightning',fire:'Fire',marquee:'Marquee',crime_eyes:'Crime Eyes',blank:'Blank'};" +
                "let cfg={};let schedule=[];let editIdx=-1;" +

                // Main text color palette
                "(()=>{const el=document.getElementById('mc');C.forEach(c=>{" +
                "  const d=document.createElement('div');d.className='clr';d.style.background=c;d.dataset.c=c;" +
                "  d.onclick=()=>{document.querySelectorAll('#mc .clr').forEach(x=>x.classList.remove('on'));d.classList.add('on');cfg.marquee_color_hex=c};" +
                "  el.appendChild(d)})})();" +

                // Main border color palette
                "(()=>{const el=document.getElementById('bc');C.forEach(c=>{" +
                "  const d=document.createElement('div');d.className='clr';d.style.background=c;d.dataset.bc=c;" +
                "  d.onclick=()=>{document.querySelectorAll('#bc .clr').forEach(x=>x.classList.remove('on'));" +
                "  document.getElementById('brb').classList.remove('on');d.classList.add('on');cfg.border_color_hex=c;cfg.border_color_mode='single'};" +
                "  el.appendChild(d)})})();" +
                "document.getElementById('brb').onclick=()=>{cfg.border_color_mode='rainbow';" +
                "  document.querySelectorAll('#bc .clr').forEach(x=>x.classList.remove('on'));document.getElementById('brb').classList.add('on')};" +

                // Main mode rows
                "document.querySelectorAll('#modes .row').forEach(r=>{r.onclick=()=>{" +
                "  document.querySelectorAll('#modes .row').forEach(x=>x.classList.remove('on'));" +
                "  r.classList.add('on');cfg.display_mode=r.dataset.m;" +
                "  document.getElementById('pnl').style.display=r.dataset.m==='marquee'?'block':'none';" +
                "  document.getElementById('pce').style.display=r.dataset.m==='crime_eyes'?'block':'none'" +
                "}});" +

                // Button group helper
                "function grp(id,key,num){document.querySelectorAll('#'+id+' .ob').forEach(b=>{b.onclick=()=>{" +
                "  document.querySelectorAll('#'+id+' .ob').forEach(x=>x.classList.remove('on'));" +
                "  b.classList.add('on');cfg[key]=num?parseInt(b.dataset.v):b.dataset.v}})}" +
                "grp('ms','marquee_speed',true);grp('md','marquee_direction',false);" +
                "grp('bst','border_style',false);grp('bsh','border_shape',false);grp('ceg','crime_eyes_gif',false);" +

                // Load and apply current settings
                "fetch('/api/settings').then(r=>r.json()).then(s=>{" +
                "  cfg={...s};document.getElementById('inf').textContent='Device: '+location.host;" +
                "  document.querySelectorAll('#modes .row').forEach(r=>{if(r.dataset.m===s.display_mode)r.classList.add('on')});" +
                "  if(s.display_mode==='marquee')document.getElementById('pnl').style.display='block';" +
                "  if(s.display_mode==='crime_eyes')document.getElementById('pce').style.display='block';" +
                "  document.querySelectorAll('#ceg .ob').forEach(b=>{if(b.dataset.v===s.crime_eyes_gif)b.classList.add('on')});" +
                "  document.getElementById('mt').value=s.marquee_text||'';" +
                "  document.getElementById('mb').checked=!!s.marquee_blink;" +
                "  document.getElementById('ma').checked=!!s.autostart;" +
                "  const ch=(s.marquee_color_hex||'#FF0000').toUpperCase();" +
                "  document.querySelectorAll('#mc .clr').forEach(d=>{if(d.dataset.c.toUpperCase()===ch)d.classList.add('on')});" +
                "  document.querySelectorAll('#ms .ob').forEach(b=>{if(parseInt(b.dataset.v)===s.marquee_speed)b.classList.add('on')});" +
                "  document.querySelectorAll('#md .ob').forEach(b=>{if(b.dataset.v===s.marquee_direction)b.classList.add('on')});" +
                "  document.querySelectorAll('#bst .ob').forEach(b=>{if(b.dataset.v===s.border_style)b.classList.add('on')});" +
                "  if(s.border_color_mode==='rainbow'){document.getElementById('brb').classList.add('on')}" +
                "  else{const bch=(s.border_color_hex||'#FF0000').toUpperCase();" +
                "    document.querySelectorAll('#bc .clr').forEach(d=>{if(d.dataset.bc.toUpperCase()===bch)d.classList.add('on')})};" +
                "  document.querySelectorAll('#bsh .ob').forEach(b=>{if(b.dataset.v===s.border_shape)b.classList.add('on')})" +
                "}).catch(()=>{document.getElementById('inf').textContent='Connection failed'});" +

                // Apply settings button
                "document.getElementById('ab').onclick=()=>{" +
                "  cfg.marquee_text=document.getElementById('mt').value;" +
                "  cfg.marquee_blink=document.getElementById('mb').checked;" +
                "  cfg.autostart=document.getElementById('ma').checked;" +
                "  fetch('/api/settings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(cfg)})" +
                "  .then(r=>r.text()).then(m=>toast(m)).catch(e=>toast('Error: '+e))};" +

                // ---- SCHEDULE ----
                // Modal palettes
                "(()=>{const el=document.getElementById('m-mc');C.forEach(c=>{" +
                "  const d=document.createElement('div');d.className='clr';d.style.background=c;d.dataset.mc=c;" +
                "  d.onclick=()=>{document.querySelectorAll('#m-mc .clr').forEach(x=>x.classList.remove('on'));d.classList.add('on')};" +
                "  el.appendChild(d)})})();" +
                "(()=>{const el=document.getElementById('m-bc');C.forEach(c=>{" +
                "  const d=document.createElement('div');d.className='clr';d.style.background=c;d.dataset.bmc=c;" +
                "  d.onclick=()=>{document.querySelectorAll('#m-bc .clr').forEach(x=>x.classList.remove('on'));" +
                "    document.getElementById('m-brb').classList.remove('on');d.classList.add('on')};" +
                "  el.appendChild(d)})})();" +
                "document.getElementById('m-brb').onclick=()=>{" +
                "  document.querySelectorAll('#m-bc .clr').forEach(x=>x.classList.remove('on'));document.getElementById('m-brb').classList.add('on')};" +

                // Modal mode rows
                "document.querySelectorAll('#m-modes .mrow').forEach(r=>{r.onclick=()=>{" +
                "  document.querySelectorAll('#m-modes .mrow').forEach(x=>x.classList.remove('on'));r.classList.add('on');" +
                "  document.getElementById('m-pnl').style.display=r.dataset.m==='marquee'?'block':'none';" +
                "  document.getElementById('m-pce').style.display=r.dataset.m==='crime_eyes'?'block':'none'" +
                "}});" +

                // Day buttons toggle
                "document.querySelectorAll('#m-days .day-btn').forEach(b=>{b.onclick=()=>b.classList.toggle('on')});" +

                // Modal button groups (read-only selection, no cfg key needed)
                "function mgrp(id){document.querySelectorAll('#'+id+' .ob').forEach(b=>{b.onclick=()=>{" +
                "  document.querySelectorAll('#'+id+' .ob').forEach(x=>x.classList.remove('on'));b.classList.add('on')}})}" +
                "mgrp('m-ms');mgrp('m-md');mgrp('m-bst');mgrp('m-bsh');mgrp('m-ceg');" +

                // Open/close modal — uses only textContent/DOM for user data
                "function openModal(idx){" +
                "  editIdx=idx;" +
                "  const e=idx===-1?{time:'08:00',days:[],mode:'worldclock',enabled:true}:schedule[idx];" +
                "  document.getElementById('modal-title').textContent=idx===-1?'New Entry':'Edit Entry';" +
                "  document.getElementById('m-time').value=e.time||'08:00';" +
                "  document.querySelectorAll('#m-days .day-btn').forEach(b=>b.classList.remove('on'));" +
                "  (e.days||[]).forEach(d=>{const b=document.querySelector('#m-days .day-btn[data-d=\"'+d+'\"]');if(b)b.classList.add('on')});" +
                "  document.querySelectorAll('#m-modes .mrow').forEach(r=>r.classList.remove('on'));" +
                "  const mr=document.querySelector('#m-modes .mrow[data-m=\"'+(e.mode||'worldclock')+'\"]');if(mr)mr.classList.add('on');" +
                "  document.getElementById('m-pnl').style.display=e.mode==='marquee'?'block':'none';" +
                "  document.getElementById('m-pce').style.display=e.mode==='crime_eyes'?'block':'none';" +
                "  document.getElementById('m-mt').value=e.marquee_text||'';" +
                "  document.getElementById('m-mb').checked=!!e.marquee_blink;" +
                "  ['m-ms','m-md','m-bst','m-bsh','m-ceg'].forEach(id=>document.querySelectorAll('#'+id+' .ob').forEach(b=>b.classList.remove('on')));" +
                "  document.querySelectorAll('#m-ms .ob').forEach(b=>{if(parseInt(b.dataset.v)===(e.marquee_speed||3))b.classList.add('on')});" +
                "  document.querySelectorAll('#m-md .ob').forEach(b=>{if(b.dataset.v===(e.marquee_direction||'left'))b.classList.add('on')});" +
                "  document.querySelectorAll('#m-bst .ob').forEach(b=>{if(b.dataset.v===(e.border_style||'snake'))b.classList.add('on')});" +
                "  document.querySelectorAll('#m-bsh .ob').forEach(b=>{if(b.dataset.v===(e.border_shape||'dot'))b.classList.add('on')});" +
                "  document.querySelectorAll('#m-ceg .ob').forEach(b=>{if(b.dataset.v===(e.crime_eyes_gif||'evil-eye-1.gif'))b.classList.add('on')});" +
                "  document.querySelectorAll('#m-mc .clr').forEach(d=>d.classList.remove('on'));" +
                "  document.querySelectorAll('#m-bc .clr').forEach(d=>d.classList.remove('on'));" +
                "  document.getElementById('m-brb').classList.remove('on');" +
                "  const mch=(e.marquee_color_hex||'#FF0000').toUpperCase();" +
                "  document.querySelectorAll('#m-mc .clr').forEach(d=>{if(d.dataset.mc&&d.dataset.mc.toUpperCase()===mch)d.classList.add('on')});" +
                "  if(e.border_color_mode==='rainbow'){document.getElementById('m-brb').classList.add('on')}" +
                "  else{const bch=(e.border_color_hex||'#FF0000').toUpperCase();" +
                "    document.querySelectorAll('#m-bc .clr').forEach(d=>{if(d.dataset.bmc&&d.dataset.bmc.toUpperCase()===bch)d.classList.add('on')})};" +
                "  document.getElementById('modal').style.display='block'" +
                "}" +
                "function closeModal(){document.getElementById('modal').style.display='none'}" +
                "document.getElementById('modal-close').onclick=closeModal;" +
                "document.getElementById('modal').addEventListener('click',function(ev){if(ev.target===this)closeModal()});" +

                // Read entry from modal
                "function readEntry(){" +
                "  const mr=document.querySelector('#m-modes .mrow.on');const mode=mr?mr.dataset.m:'worldclock';" +
                "  const days=[];document.querySelectorAll('#m-days .day-btn.on').forEach(b=>days.push(parseInt(b.dataset.d)));" +
                "  const e={id:editIdx===-1?(Date.now()+''):schedule[editIdx].id," +
                "    enabled:editIdx===-1?true:schedule[editIdx].enabled," +
                "    time:document.getElementById('m-time').value,days:days,mode:mode};" +
                "  if(mode==='marquee'){" +
                "    e.marquee_text=document.getElementById('m-mt').value;" +
                "    e.marquee_blink=document.getElementById('m-mb').checked;" +
                "    const spd=document.querySelector('#m-ms .ob.on');if(spd)e.marquee_speed=parseInt(spd.dataset.v);" +
                "    const dir=document.querySelector('#m-md .ob.on');if(dir)e.marquee_direction=dir.dataset.v;" +
                "    const bst=document.querySelector('#m-bst .ob.on');if(bst)e.border_style=bst.dataset.v;" +
                "    const bsh=document.querySelector('#m-bsh .ob.on');if(bsh)e.border_shape=bsh.dataset.v;" +
                "    const mce=document.querySelector('#m-mc .clr.on');if(mce)e.marquee_color_hex=mce.dataset.mc;" +
                "    if(document.getElementById('m-brb').classList.contains('on')){e.border_color_mode='rainbow'}" +
                "    else{const bce=document.querySelector('#m-bc .clr.on');if(bce){e.border_color_hex=bce.dataset.bmc;e.border_color_mode='single'}}" +
                "  } else if(mode==='crime_eyes'){" +
                "    const gif=document.querySelector('#m-ceg .ob.on');if(gif)e.crime_eyes_gif=gif.dataset.v}" +
                "  return e}" +

                // Modal apply
                "document.getElementById('modal-apply').onclick=()=>{" +
                "  const e=readEntry();if(editIdx===-1)schedule.push(e);else schedule[editIdx]=e;" +
                "  closeModal();renderSchedule()};" +

                // Render schedule — uses createElement + textContent, never innerHTML with user data
                "function renderSchedule(){" +
                "  const el=document.getElementById('sc-list');" +
                "  while(el.firstChild)el.removeChild(el.firstChild);" +
                "  if(!schedule.length){" +
                "    const d=document.createElement('div');" +
                "    d.style.cssText='color:#444;padding:16px;text-align:center';" +
                "    d.textContent='No schedule entries';el.appendChild(d);return}" +
                "  const sorted=[...schedule].map((e,i)=>Object.assign({},e,{_i:i})).sort((a,b)=>a.time.localeCompare(b.time));" +
                "  sorted.forEach(function(entry){" +
                "    const card=document.createElement('div');card.className='sc-card';" +
                "    if(!entry.enabled)card.style.opacity='0.45';" +
                "    const head=document.createElement('div');head.className='sc-head';" +
                "    const tEl=document.createElement('span');tEl.className='sc-time';tEl.textContent=entry.time;" +
                "    const dEl=document.createElement('span');dEl.className='sc-days';" +
                "    dEl.textContent=(entry.days&&entry.days.length)?entry.days.map(function(d){return DAYS[d]}).join(' '):'Every day';" +
                "    const mEl=document.createElement('span');mEl.className='sc-mode';" +
                "    mEl.textContent=MNAMES[entry.mode]||entry.mode;" +
                "    head.appendChild(tEl);head.appendChild(dEl);head.appendChild(mEl);" +
                "    const btns=document.createElement('div');btns.className='sc-btns';" +
                "    const editBtn=document.createElement('button');editBtn.className='sc-edit';editBtn.textContent='Edit';" +
                "    editBtn.onclick=(function(i){return function(){openModal(i)}})(entry._i);" +
                "    const togBtn=document.createElement('button');" +
                "    togBtn.className='sc-tog'+(entry.enabled?'':' off');togBtn.textContent=entry.enabled?'ON':'OFF';" +
                "    togBtn.onclick=(function(i){return function(){schedule[i].enabled=!schedule[i].enabled;renderSchedule()}})(entry._i);" +
                "    const delBtn=document.createElement('button');delBtn.className='sc-del';delBtn.textContent='Delete';" +
                "    delBtn.onclick=(function(i){return function(){if(confirm('Delete this entry?')){schedule.splice(i,1);renderSchedule()}}})(entry._i);" +
                "    btns.appendChild(editBtn);btns.appendChild(togBtn);btns.appendChild(delBtn);" +
                "    card.appendChild(head);card.appendChild(btns);el.appendChild(card)})}" +

                // Load schedule
                "fetch('/api/schedule').then(function(r){return r.json()}).then(function(s){schedule=s||[];renderSchedule()}).catch(function(){});" +

                // Add / save schedule
                "document.getElementById('addsc').onclick=function(){openModal(-1)};" +
                "document.getElementById('savesc').onclick=function(){" +
                "  fetch('/api/schedule',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(schedule)})" +
                "  .then(function(r){return r.text()}).then(function(m){toast(m)}).catch(function(e){toast('Error: '+e)})};" +

                "function toast(m){const t=document.getElementById('toast');" +
                "  t.textContent=m;t.style.display='block';setTimeout(function(){t.style.display='none'},3000)}";

            return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<title>Signage Admin</title>" +
                "<style>" + css + "</style>" +
                "</head><body>" + body +
                "<script>" + js + "<" + "/script></body></html>";
        }

        // ---- GET /api/settings ---------------------------------------------

        private Response handleGetSettings() {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String mode        = prefs.getString ("display_mode",      "worldclock");
            String text        = prefs.getString ("marquee_text",      "HELLO WORLD").replace("\\", "\\\\").replace("\"", "\\\"");
            int    colorInt    = prefs.getInt    ("marquee_color",     0xFFFF0000);
            String colorHex    = String.format("#%06X", colorInt & 0xFFFFFF);
            int    speed       = prefs.getInt    ("marquee_speed",     3);
            String dir         = prefs.getString ("marquee_direction", "left");
            boolean blink      = prefs.getBoolean("marquee_blink",     false);
            String bstyle      = prefs.getString ("border_style",      "snake");
            String bcolormode  = prefs.getString ("border_color_mode", "single");
            int    bcolorInt   = prefs.getInt    ("border_color",      0xFFFF0000);
            String bcolorHex   = String.format("#%06X", bcolorInt & 0xFFFFFF);
            String bshape      = prefs.getString ("border_shape",      "dot");
            String eyeGif      = prefs.getString ("crime_eyes_gif",    "evil-eye-1.gif");

            ComponentName receiver = new ComponentName(ctx, BootReceiver.class);
            int state = ctx.getPackageManager().getComponentEnabledSetting(receiver);
            boolean autostart = state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

            String json = "{" +
                "\"display_mode\":\""      + mode       + "\"," +
                "\"marquee_text\":\""      + text       + "\"," +
                "\"marquee_color_hex\":\"" + colorHex   + "\"," +
                "\"marquee_speed\":"       + speed      + ","   +
                "\"marquee_direction\":\"" + dir        + "\"," +
                "\"marquee_blink\":"       + blink      + ","   +
                "\"border_style\":\""      + bstyle     + "\"," +
                "\"border_color_mode\":\"" + bcolormode + "\"," +
                "\"border_color_hex\":\"" + bcolorHex  + "\"," +
                "\"border_shape\":\""      + bshape     + "\"," +
                "\"crime_eyes_gif\":\""    + eyeGif     + "\"," +
                "\"autostart\":"           + autostart  +
                "}";

            Response r = newFixedLengthResponse(Response.Status.OK, "application/json", json);
            r.addHeader("Access-Control-Allow-Origin", "*");
            return r;
        }

        // ---- POST /api/settings --------------------------------------------

        private Response handleSetSettings(IHTTPSession session) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String body = files.get("postData");
                if (body == null || body.isEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Empty body");
                }

                JSONObject json = new JSONObject(body);
                SharedPreferences.Editor ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();

                if (json.has("display_mode"))      ed.putString ("display_mode",      json.getString ("display_mode"));
                if (json.has("marquee_text"))      ed.putString ("marquee_text",      json.getString ("marquee_text"));
                if (json.has("marquee_speed"))     ed.putInt    ("marquee_speed",     json.getInt    ("marquee_speed"));
                if (json.has("marquee_direction")) ed.putString ("marquee_direction", json.getString ("marquee_direction"));
                if (json.has("marquee_blink"))     ed.putBoolean("marquee_blink",     json.getBoolean("marquee_blink"));
                if (json.has("border_style"))      ed.putString ("border_style",      json.getString ("border_style"));
                if (json.has("border_color_mode")) ed.putString ("border_color_mode", json.getString ("border_color_mode"));
                if (json.has("border_shape"))      ed.putString ("border_shape",      json.getString ("border_shape"));
                if (json.has("crime_eyes_gif"))    ed.putString ("crime_eyes_gif",    json.getString ("crime_eyes_gif"));

                if (json.has("marquee_color_hex")) {
                    String hex = json.getString("marquee_color_hex").replace("#", "");
                    ed.putInt("marquee_color", (int)(0xFF000000L | Long.parseLong(hex, 16)));
                }
                if (json.has("border_color_hex")) {
                    String hex = json.getString("border_color_hex").replace("#", "");
                    ed.putInt("border_color", (int)(0xFF000000L | Long.parseLong(hex, 16)));
                }

                ed.apply();

                // Autostart
                if (json.has("autostart")) {
                    ComponentName receiver = new ComponentName(ctx, BootReceiver.class);
                    int newState = json.getBoolean("autostart")
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    ctx.getPackageManager().setComponentEnabledSetting(
                        receiver, newState, PackageManager.DONT_KILL_APP);
                }

                // Tell MainActivity to reload immediately
                ctx.sendBroadcast(new Intent("com.signage.app.SETTINGS_CHANGED"));

                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Settings applied");

            } catch (Exception e) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }

        // ---- GET /api/schedule ---------------------------------------------

        private Response handleGetSchedule() {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String json = prefs.getString("schedule", "[]");
            Response r = newFixedLengthResponse(Response.Status.OK, "application/json", json);
            r.addHeader("Access-Control-Allow-Origin", "*");
            r.addHeader("Cache-Control", "no-store");
            return r;
        }

        // ---- POST /api/schedule --------------------------------------------

        private Response handleSetSchedule(IHTTPSession session) {
            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String body = files.get("postData");
                if (body == null || body.isEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Empty body");
                }
                // Validate it's a JSON array
                new JSONArray(body);
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("schedule", body)
                    .putString("schedule_applied_id", "") // reset so scheduler re-evaluates
                    .apply();
                Response r = newFixedLengthResponse(Response.Status.OK, "text/plain", "Schedule saved");
                r.addHeader("Access-Control-Allow-Origin", "*");
                return r;
            } catch (Exception e) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }

        // ---- Existing handlers ---------------------------------------------

        private Response serveFile(String uri) {
            if (uri.equals("/")) uri = "/index.html";
            File f = new File(serveDir, uri);
            if (!f.exists() && uri.equals("/index.html")) {
                return newFixedLengthResponse(Response.Status.OK, "text/html",
                    "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                    "<style>*{margin:0;padding:0}body{background:#000;color:#fff;" +
                    "font-family:sans-serif;display:flex;align-items:center;" +
                    "justify-content:center;height:100vh;flex-direction:column;gap:16px}" +
                    "h1{font-size:3em}p{opacity:.6}a{color:#a78bfa}</style></head><body>" +
                    "<h1>Signage</h1>" +
                    "<p><a href='/admin'>Open Admin Panel</a></p>" +
                    "<p>Or drop index.html into /sdcard/signage/</p>" +
                    "</body></html>");
            }
            if (!f.exists()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found: " + uri);
            }
            try {
                return newFixedLengthResponse(Response.Status.OK, getMimeType(f.getName()),
                    new FileInputStream(f), f.length());
            } catch (IOException e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
            }
        }

        private Response handleDownload(Map<String, String> params) {
            String urlStr = params.get("url");
            if (urlStr == null || urlStr.isEmpty()) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing 'url' param");
            }
            String fileName = params.get("file");
            if (fileName == null || fileName.isEmpty()) {
                fileName = urlStr.substring(urlStr.lastIndexOf('/') + 1);
            }
            if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf('?'));
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "SignageApp/1.0");
                File outFile = new File(serveDir, fileName);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK: " + fileName);
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: " + e.getMessage());
            }
        }

        private Response handleDelete(String fileName) {
            if (fileName.contains("..") || fileName.contains("/")) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Forbidden");
            }
            File f = new File(serveDir, fileName);
            if (f.exists() && f.delete()) {
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Deleted: " + fileName);
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found: " + fileName);
        }

        private Response handleList() {
            File[] files = serveDir.listFiles();
            StringBuilder sb = new StringBuilder("[");
            if (files != null) {
                boolean first = true;
                for (File f : files) {
                    if (!first) sb.append(",");
                    sb.append("{\"name\":\"").append(f.getName())
                      .append("\",\"size\":").append(f.length()).append("}");
                    first = false;
                }
            }
            sb.append("]");
            return newFixedLengthResponse(Response.Status.OK, "application/json", sb.toString());
        }

        private String getMimeType(String name) {
            String n = name.toLowerCase();
            if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
            if (n.endsWith(".js"))   return "application/javascript";
            if (n.endsWith(".css"))  return "text/css";
            if (n.endsWith(".json")) return "application/json";
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
            if (n.endsWith(".png"))  return "image/png";
            if (n.endsWith(".gif"))  return "image/gif";
            if (n.endsWith(".svg"))  return "image/svg+xml";
            if (n.endsWith(".webp")) return "image/webp";
            if (n.endsWith(".mp4"))  return "video/mp4";
            if (n.endsWith(".webm")) return "video/webm";
            if (n.endsWith(".mp3"))  return "audio/mpeg";
            if (n.endsWith(".pdf"))  return "application/pdf";
            return "application/octet-stream";
        }
    }
}
