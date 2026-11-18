package com.medireport.portal.web

import android.text.TextUtils
import com.medireport.portal.data.Patient
import com.medireport.portal.data.Report
import com.medireport.portal.util.Formatters

/** Server-rendered pages of the patient portal (responsive, no external resources). */
object PortalPages {
    private fun esc(s: String) = TextUtils.htmlEncode(s)

    private const val CSS = """
:root{--p:#0f766e;--p2:#4f46e5;--bg:#f1f6f6;--card:#fff;--tx:#0f1c1c;--mu:#5b6f70;--bd:#d6e3e3}
*{box-sizing:border-box}html,body{margin:0}
body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,'Noto Sans Bengali','Noto Sans',sans-serif;background:var(--bg);color:var(--tx);min-height:100vh}
.hero{background:linear-gradient(135deg,var(--p),#0d9488 55%,var(--p2));color:#fff;padding:34px 20px 70px}
.wrap{max-width:560px;margin:0 auto}
.logo{width:54px;height:54px;border-radius:50%;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center;font-size:28px;margin-bottom:12px}
h1{margin:0 0 4px;font-size:24px}.sub{margin:0;opacity:.92;font-size:15px}
.card{background:var(--card);border-radius:20px;padding:22px;margin:-46px 0 18px;box-shadow:0 8px 28px rgba(15,60,60,.12)}
.card.flat{margin:0 0 14px;box-shadow:0 2px 8px rgba(15,60,60,.07)}
label{display:block;font-size:13px;color:var(--mu);margin:14px 0 6px}
input{width:100%;padding:13px 14px;border:1.5px solid var(--bd);border-radius:12px;font-size:16px;background:#fff;color:var(--tx)}
input:focus{outline:none;border-color:var(--p)}
button,.btn{display:inline-block;border:0;border-radius:12px;padding:13px 18px;font-size:16px;font-weight:600;cursor:pointer;text-decoration:none;text-align:center}
.primary{background:var(--p);color:#fff;width:100%;margin-top:20px}
.ghost{background:#e6f2f1;color:var(--p)}
.err{background:#fde8e8;color:#b42318;padding:11px 13px;border-radius:12px;margin-top:14px;font-size:14px}
.row{display:flex;justify-content:space-between;gap:10px;padding:7px 0;border-bottom:1px solid #eef3f3;font-size:15px}
.row:last-child{border-bottom:0}.row span:first-child{color:var(--mu)}
.top{display:flex;justify-content:space-between;align-items:center}
.top form{margin:0}.top button{background:rgba(255,255,255,.2);color:#fff;padding:8px 14px;font-size:14px}
h2{font-size:17px;margin:22px 0 10px}
.rep{display:flex;align-items:center;gap:12px}
.ic{width:44px;height:44px;border-radius:12px;background:#fde8ec;color:#e11d48;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:12px;flex:none}
.ic.img{background:#e0f2fe;color:#0284c7}
.rep .t{flex:1;min-width:0}.rep .t b{display:block;font-size:15px;overflow-wrap:anywhere}.rep .t small{color:var(--mu)}
.acts{display:flex;gap:8px;margin-top:12px}.acts .btn{flex:1;padding:10px;font-size:14px}
.dl{background:var(--p);color:#fff}
.empty{text-align:center;color:var(--mu);padding:26px 8px}
.foot{text-align:center;color:var(--mu);font-size:12px;padding:20px 0 30px}
"""

    private fun page(title: String, body: String) = """<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex">
<title>${esc(title)}</title><style>$CSS</style></head><body>$body
<div class="foot">${esc(title)} · Secure patient portal / নিরাপদ রোগী পোর্টাল</div></body></html>"""

    fun login(clinic: String, error: String? = null): String = page(
        clinic,
        """
<div class="hero"><div class="wrap"><div class="logo">+</div>
<h1>${esc(clinic)}</h1><p class="sub">Download your reports · আপনার রিপোর্ট ডাউনলোড করুন</p></div></div>
<div class="wrap" style="padding:0 16px"><div class="card">
<form method="post" action="/login" autocomplete="off">
<label>Patient ID / রোগীর আইডি</label>
<input name="id" required autofocus autocapitalize="characters" inputmode="text">
<label>Phone number (password) / ফোন নম্বর (পাসওয়ার্ড)</label>
<input name="phone" type="password" required inputmode="tel">
${if (error != null) "<div class=\"err\">${esc(error)}</div>" else ""}
<button class="primary" type="submit">Login / লগইন</button>
</form></div></div>"""
    )

    fun dashboard(clinic: String, p: Patient, reports: List<Report>): String {
        val list = if (reports.isEmpty()) {
            "<div class=\"card flat empty\">No reports are published yet.<br>এখনো কোনো রিপোর্ট প্রকাশিত হয়নি।</div>"
        } else reports.joinToString("") { r ->
            val isPdf = r.mimeType.contains("pdf")
            """<div class="card flat"><div class="rep">
<div class="ic${if (isPdf) "" else " img"}">${if (isPdf) "PDF" else "IMG"}</div>
<div class="t"><b>${esc(r.title)}</b><small>${esc(Formatters.dateTime(r.createdAt))}</small></div></div>
<div class="acts"><a class="btn ghost" href="/report/${r.id}/view" target="_blank">View / দেখুন</a>
<a class="btn dl" href="/report/${r.id}/download">Download / ডাউনলোড</a></div></div>"""
        }
        return page(
            clinic,
            """
<div class="hero"><div class="wrap"><div class="top"><div><h1>${esc(p.name)}</h1>
<p class="sub">${esc(p.patientCode)}</p></div>
<form method="post" action="/logout"><button type="submit">Logout</button></form></div></div></div>
<div class="wrap" style="padding:0 16px"><div class="card">
<div class="row"><span>Patient ID</span><b>${esc(p.patientCode)}</b></div>
<div class="row"><span>Sex / লিঙ্গ</span><b>${esc(p.sex)}</b></div>
<div class="row"><span>Age / বয়স</span><b>${esc(p.ageText)}</b></div>
<div class="row"><span>Phone / ফোন</span><b>${esc(p.phone)}</b></div></div>
<h2>My reports / আমার রিপোর্ট (${reports.size})</h2>$list</div>"""
        )
    }

    fun notFound(clinic: String) = page(
        clinic,
        """<div class="hero"><div class="wrap"><h1>Not found</h1><p class="sub">The page or report is not available.</p></div></div>
<div class="wrap" style="padding:0 16px"><div class="card"><a class="btn ghost" href="/">Back / ফিরে যান</a></div></div>"""
    )
}
