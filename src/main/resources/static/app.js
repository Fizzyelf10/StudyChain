// StudyChain web UI: plain JavaScript, no build step. One file, read top to bottom:
//   1. helpers  2. API calls  3. wallet and escrow actions  4. one function per page  5. router
"use strict";

/* ---------- 1. helpers ---------- */

const state = { token: localStorage.getItem("token"), user: null, config: null };
const KIND = { PAST_PAPER: "Past paper", NOTES: "Lecture notes", SUMMARY: "Summary" };
const SOURCE = { OWN_WORK: "Own work", UNIVERSITY_PUBLISHED: "Published by university" };
const STATUS_TEXT = {
  AWAITING_PAYMENT: "Awaiting payment", FUNDED: "Paid, in escrow", COMPLETED: "Completed",
  DISPUTED: "In dispute", REFUNDED: "Refunded",
};
const REASONS = {
  COPIED_MATERIAL: "Copied from a textbook or course reader", LEAKED_EXAM: "Leaked or unreleased exam",
  NOT_AS_DESCRIBED: "Not the material described", OTHER: "Something else",
};

const $ = (selector, root = document) => root.querySelector(selector);
// Every piece of user text goes through esc() before it is put into HTML, so nobody can inject scripts.
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const money = (n) => Number(n).toFixed(2) + " USDC";
const view = (html) => { $("#app").innerHTML = html; window.scrollTo(0, 0); };

let toastTimer;
function toast(message, isError = false) {
  const el = $("#toast");
  el.textContent = message;
  el.className = "toast show" + (isError ? " err" : "");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.className = "toast"), 4500);
}

/* Runs an action and shows any error as a toast. Disables the button while it works. */
async function guarded(button, action) {
  if (button) button.disabled = true;
  try {
    await action();
  } catch (e) {
    toast(e.message || String(e), true);
  } finally {
    if (button) button.disabled = false;
  }
}

/* ---------- 2. API calls ---------- */

async function api(path, opts = {}) {
  const headers = {};
  if (state.token) headers.Authorization = "Bearer " + state.token;
  let body;
  if (opts.form) {
    body = opts.form; // the browser sets the multipart Content-Type itself
  } else if (opts.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(opts.body);
  }
  const res = await fetch(path, { method: opts.method || "GET", headers, body });
  if (!res.ok) {
    let message = res.status + " " + res.statusText;
    try {
      const e = await res.json();
      message = e.message || message;
      const fields = e.fieldErrors && Object.entries(e.fieldErrors);
      if (fields && fields.length) message += ": " + fields.map(([k, v]) => k + " " + v).join(", ");
    } catch (ignored) { /* body was not JSON */ }
    if (res.status === 401 && state.token) logout(false);
    throw new Error(message);
  }
  if (opts.blob) return res.blob();
  if (res.status === 204) return null;
  return res.json();
}

function setSession(auth) {
  state.token = auth.token;
  state.user = auth.user;
  localStorage.setItem("token", auth.token);
}

function logout(navigate = true) {
  state.token = null;
  state.user = null;
  localStorage.removeItem("token");
  renderNav();
  if (navigate) location.hash = "#/";
}

async function loadSession() {
  state.config = await api("/api/config");
  if (state.token) {
    try { state.user = await api("/api/auth/me"); } catch (e) { logout(false); }
  }
}

function requireLogin() {
  if (state.user) return true;
  location.hash = "#/login";
  return false;
}

async function downloadFile(listingId) {
  const blob = await api(`/api/listings/${listingId}/download`, { blob: true });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "studychain-" + listingId + ".pdf";
  a.click();
  URL.revokeObjectURL(url);
}

/* ---------- 3. wallet and escrow actions ---------- */

const ESCROW_ABI = [
  "function fund(bytes32 id, address seller, uint256 amount)",
  "function release(bytes32 id)",
  "function refund(bytes32 id)",
  "function dispute(bytes32 id)",
  "function claimAfterTimeout(bytes32 id)",
];
const ERC20_ABI = [
  "function approve(address spender, uint256 amount) returns (bool)",
  "function allowance(address owner, address spender) view returns (uint256)",
];

async function getSigner() {
  if (!window.ethereum || !window.ethers) throw new Error("Install a wallet such as MetaMask to continue");
  const provider = new ethers.BrowserProvider(window.ethereum);
  await provider.send("eth_requestAccounts", []);
  const wanted = state.config.chainId;
  const network = await provider.getNetwork();
  if (Number(network.chainId) !== wanted) {
    await window.ethereum.request({ method: "wallet_switchEthereumChain", params: [{ chainId: "0x" + wanted.toString(16) }] });
  }
  return new ethers.BrowserProvider(window.ethereum).getSigner();
}

/* Does one escrow action. In mock mode the backend pretends to be the chain; in rpc mode it is a real transaction. */
async function escrowAction(order, action, extra = "") {
  if (state.config.chainMode === "mock") {
    await api(`/api/dev/escrow/${order.id}/${action}${extra}`, { method: "POST" });
  } else {
    const signer = await getSigner();
    const me = (await signer.getAddress()).toLowerCase();
    const expected = (action === "refund" || action === "claim") ? order.sellerWallet : order.buyerWallet;
    if (me !== expected.toLowerCase()) throw new Error("Switch your wallet to " + expected + " for this step");
    const escrow = new ethers.Contract(order.escrow.escrowContract, ESCROW_ABI, signer);
    const id = order.escrow.escrowId;
    if (action === "fund") {
      const amount = BigInt(order.escrow.amountUnits);
      const token = new ethers.Contract(order.escrow.tokenContract, ERC20_ABI, signer);
      if ((await token.allowance(me, order.escrow.escrowContract)) < amount) {
        toast("Step 1 of 2: approve the payment in your wallet");
        await (await token.approve(order.escrow.escrowContract, amount)).wait();
      }
      toast("Step 2 of 2: lock the payment in escrow");
      await (await escrow.fund(id, order.sellerWallet, amount)).wait();
    } else if (action === "release") {
      await (await escrow.release(id)).wait();
    } else if (action === "refund") {
      await (await escrow.refund(id)).wait();
    } else if (action === "dispute") {
      await (await escrow.dispute(id)).wait();
    } else if (action === "claim") {
      await (await escrow.claimAfterTimeout(id)).wait();
    }
  }
  await api(`/api/orders/${order.id}/sync`, { method: "POST" }); // refresh the order from the escrow
}

/* ---------- 4. pages ---------- */

const browseState = { q: "", kind: "", page: 0 };

function listingCard(l) {
  const published = l.source === "UNIVERSITY_PUBLISHED";
  return `<a class="card item" href="#/listing/${esc(l.id)}">
    <div class="thumb">${l.hasPreview ? `<img src="/api/listings/${esc(l.id)}/preview" alt="" loading="lazy">` : "No preview"}</div>
    <div class="body">
      <div class="badges"><span class="badge">${esc(l.courseCode)}</span>
        <span class="badge ${published ? "good" : ""}">${esc(SOURCE[l.source])}</span></div>
      <h3>${esc(l.title)}</h3>
      <div class="muted">${esc(KIND[l.kind])} · ${l.pageCount} pages · ${esc(l.university)}</div>
      <div class="price">${money(l.priceUsdc)}</div>
    </div></a>`;
}

async function browsePage() {
  view(`<h1>Study material from your own university</h1>
    <p class="muted">Pay with crypto. Your payment waits in escrow until you have checked the file.</p>
    <div class="toolbar">
      <input id="q" class="search" type="search" placeholder="Search by title, course code or university" aria-label="Search" value="${esc(browseState.q)}">
      <div class="chips" id="kinds"></div>
    </div>
    <div id="results"></div>`);
  const kinds = [["", "All"], ["PAST_PAPER", "Past papers"], ["NOTES", "Notes"], ["SUMMARY", "Summaries"]];
  $("#kinds").innerHTML = kinds.map(([v, label]) =>
    `<button class="chip ${browseState.kind === v ? "on" : ""}" data-kind="${v}">${label}</button>`).join("");
  $("#kinds").addEventListener("click", (e) => {
    const b = e.target.closest("[data-kind]");
    if (!b) return;
    browseState.kind = b.dataset.kind; browseState.page = 0; browsePage();
  });
  let timer;
  $("#q").addEventListener("input", (e) => {
    clearTimeout(timer);
    timer = setTimeout(() => { browseState.q = e.target.value; browseState.page = 0; loadResults(); }, 300);
  });
  await loadResults();
}

async function loadResults() {
  const params = new URLSearchParams({ q: browseState.q, page: browseState.page, size: 12 });
  if (browseState.kind) params.set("kind", browseState.kind);
  const res = await api("/api/listings?" + params);
  const box = $("#results");
  if (!box) return;
  if (res.items.length === 0) {
    box.innerHTML = `<p class="notice">Nothing matches yet. <a href="#/sell">List your own material.</a></p>`;
    return;
  }
  box.innerHTML = `<p class="muted">${res.totalItems} listing${res.totalItems === 1 ? "" : "s"}</p>
    <div class="grid">${res.items.map(listingCard).join("")}</div>
    <div class="pager">
      <button class="btn" id="prev" ${res.page === 0 ? "disabled" : ""}>Previous</button>
      <span class="muted">Page ${res.page + 1} of ${Math.max(res.totalPages, 1)}</span>
      <button class="btn" id="next" ${res.page + 1 >= res.totalPages ? "disabled" : ""}>Next</button>
    </div>`;
  $("#prev").onclick = () => { browseState.page--; loadResults(); };
  $("#next").onclick = () => { browseState.page++; loadResults(); };
}

async function listingPage(id) {
  const l = await api("/api/listings/" + id);
  const mine = state.user && state.user.id === l.sellerId;
  const published = l.source === "UNIVERSITY_PUBLISHED";
  const hidden = l.status !== "ACTIVE";
  view(`<div class="split">
    <div class="main">
      <div class="badges"><span class="badge">${esc(l.courseCode)}</span>
        <span class="badge ${published ? "good" : ""}">${esc(SOURCE[l.source])}</span>
        ${hidden ? `<span class="badge warn">${esc(l.status)}</span>` : ""}</div>
      <h1>${esc(l.title)}</h1>
      <p class="muted">${esc(KIND[l.kind])} · ${l.pageCount} pages · ${esc(l.university)} · listed by ${esc(l.sellerName)}</p>
      ${l.hasPreview ? `<img class="preview" src="/api/listings/${esc(l.id)}/preview" alt="Preview of page 1">` : ""}
      ${l.description ? `<h2>About this file</h2><p>${esc(l.description)}</p>` : ""}
      <div class="card"><strong>Source declared by the seller</strong><p>${esc(l.sourceNote)}</p></div>
      <div id="report"></div>
    </div>
    <aside class="side card">
      <div class="price" style="font-size:32px">${money(l.priceUsdc)}</div>
      <p class="muted">Paid from your own wallet and locked in escrow. The seller is paid only after you confirm.</p>
      <div id="actions" class="row"></div>
    </aside></div>`);

  const actions = $("#actions");
  if (mine) {
    actions.innerHTML = `<button class="btn" id="dl">Download my file</button>
      <button class="btn danger" id="rm" ${l.status === "REMOVED" ? "disabled" : ""}>Remove listing</button>`;
    $("#dl").onclick = (e) => guarded(e.target, () => downloadFile(l.id));
    $("#rm").onclick = (e) => guarded(e.target, async () => {
      if (!confirm("Remove this listing?")) return;
      await api("/api/listings/" + l.id, { method: "DELETE" });
      toast("Listing removed"); location.hash = "#/mine";
    });
  } else {
    actions.innerHTML = `<button class="btn primary" id="buy" style="width:100%">Buy with escrow</button>`;
    $("#buy").onclick = (e) => guarded(e.target, async () => {
      if (!requireLogin()) return;
      if (!state.user.walletAddress) { toast("Link a wallet first"); location.hash = "#/wallet"; return; }
      const order = await api("/api/orders", { method: "POST", body: { listingId: l.id } });
      location.hash = "#/order/" + order.id;
    });
    renderReportBox(l);
  }
}

function renderReportBox(l) {
  const box = $("#report");
  box.innerHTML = `<p><a href="#" id="open-report">Report this listing</a></p>`;
  $("#open-report").onclick = (e) => {
    e.preventDefault();
    if (!requireLogin()) return;
    box.innerHTML = `<form class="card form" id="report-form">
      <strong>Why are you reporting this?</strong>
      ${Object.entries(REASONS).map(([v, label], i) =>
        `<label class="check"><input type="radio" name="reason" value="${v}" ${i === 0 ? "checked" : ""}> ${esc(label)}</label>`).join("")}
      <label>Details (optional)<textarea name="details" rows="2" maxlength="1000"></textarea></label>
      <div class="row"><button class="btn primary" type="submit">Send report</button>
        <button class="btn" type="button" id="cancel-report">Cancel</button></div></form>`;
    $("#cancel-report").onclick = () => renderReportBox(l);
    $("#report-form").onsubmit = (ev) => {
      ev.preventDefault();
      const f = new FormData(ev.target);
      guarded(ev.submitter, async () => {
        await api(`/api/listings/${l.id}/reports`, { method: "POST", body: { reason: f.get("reason"), details: f.get("details") } });
        box.innerHTML = `<div class="notice good">Thanks. A reviewer will check this listing and may take it down.</div>`;
      });
    };
  };
}

function loginPage() {
  view(`<h1>Log in</h1><form class="form" id="f">
    <label>Email<input name="email" type="email" autocomplete="email" required></label>
    <label>Password<input name="password" type="password" autocomplete="current-password" required></label>
    <button class="btn primary" type="submit">Log in</button>
    <p class="muted">New here? <a href="#/register">Create an account</a></p></form>`);
  $("#f").onsubmit = (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    guarded(e.submitter, async () => {
      setSession(await api("/api/auth/login", { method: "POST", body: { email: f.get("email"), password: f.get("password") } }));
      location.hash = "#/";
    });
  };
}

function registerPage() {
  view(`<h1>Create an account</h1><form class="form" id="f">
    <label>Display name<input name="displayName" required minlength="2" maxlength="60"></label>
    <label>Email<input name="email" type="email" autocomplete="email" required></label>
    <label>Password (8 or more characters)<input name="password" type="password" autocomplete="new-password" required minlength="8" maxlength="72"></label>
    <button class="btn primary" type="submit">Sign up</button>
    <p class="muted">Already registered? <a href="#/login">Log in</a></p></form>`);
  $("#f").onsubmit = (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    guarded(e.submitter, async () => {
      setSession(await api("/api/auth/register", { method: "POST", body: Object.fromEntries(f) }));
      location.hash = "#/wallet";
    });
  };
}

function walletPage() {
  if (!requireLogin()) return;
  const mock = state.config.chainMode === "mock";
  const current = state.user.walletAddress;
  view(`<h1>Your wallet</h1>
    ${current ? `<div class="notice good">Linked wallet: <span class="mono">${esc(current)}</span></div>`
              : `<div class="notice warn">No wallet linked yet. You need one to buy or sell.</div>`}
    <h2>${current ? "Change wallet" : "Link a wallet"}</h2>
    <div class="form">
      <button class="btn primary" id="connect">Connect wallet and sign</button>
      <p class="muted">You sign a short message to prove you own the address. Nothing is spent.</p>
      ${mock ? `<h2>Demo mode</h2>
        <p class="muted">The backend runs in mock mode. Enter any address, or generate one, to try the app without a wallet.</p>
        <label>Address<input id="demo-address" placeholder="0x..." class="mono"></label>
        <div class="row"><button class="btn" id="gen">Generate demo address</button>
          <button class="btn" id="demo-link">Link this address</button></div>` : ""}
    </div>`);
  $("#connect").onclick = (e) => guarded(e.target, async () => {
    if (!window.ethereum || !window.ethers) throw new Error("Install a wallet such as MetaMask first");
    const provider = new ethers.BrowserProvider(window.ethereum);
    const signer = await provider.getSigner();
    const address = await signer.getAddress();
    const challenge = await api("/api/wallet/challenge", { method: "POST", body: { address } });
    const signature = await signer.signMessage(challenge.message);
    state.user = await api("/api/wallet/link", { method: "POST", body: { address, signature } });
    toast("Wallet linked"); walletPage();
  });
  if (mock) {
    $("#gen").onclick = () => {
      const bytes = crypto.getRandomValues(new Uint8Array(20));
      $("#demo-address").value = "0x" + Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    };
    $("#demo-link").onclick = (e) => guarded(e.target, async () => {
      state.user = await api("/api/dev/wallet", { method: "POST", body: { address: $("#demo-address").value.trim() } });
      toast("Wallet linked"); walletPage();
    });
  }
}

function sellPage() {
  if (!requireLogin()) return;
  if (!state.user.walletAddress) {
    view(`<h1>Sell material</h1><div class="notice warn">Link a wallet first so buyers can pay you. <a href="#/wallet">Link wallet</a></div>`);
    return;
  }
  view(`<h1>Sell material</h1>
    <div class="split"><form class="main form" id="f">
      <label>Title<input name="title" required maxlength="200" placeholder="e.g. Linear Algebra I, exam paper 2023"></label>
      <div class="cols">
        <label>Course code<input name="courseCode" required maxlength="20" placeholder="MATH101"></label>
        <label>University<input name="university" required maxlength="100"></label>
      </div>
      <div class="cols">
        <label>Type<select name="kind">${Object.entries(KIND).map(([v, t]) => `<option value="${v}">${t}</option>`).join("")}</select></label>
        <label>Source<select name="source">${Object.entries(SOURCE).map(([v, t]) => `<option value="${v}">${t}</option>`).join("")}</select></label>
        <label>Price (USDC)<input name="priceUsdc" inputmode="decimal" required placeholder="3.00"></label>
      </div>
      <label>Where does this file come from?<textarea name="sourceNote" rows="2" required minlength="10" maxlength="500" placeholder="I wrote these notes myself, or: published by the maths department at ..."></textarea></label>
      <label>Description (optional)<textarea name="description" rows="3" maxlength="2000"></textarea></label>
      <label>PDF file (max 25 MB)<input name="file" type="file" accept="application/pdf,.pdf" required></label>
      <label class="check"><input type="checkbox" name="ownershipConfirmed" required>
        <span>I wrote this myself, or the university has published it. It is not a leaked exam, a scanned textbook or someone else's course reader.</span></label>
      <button class="btn primary" type="submit">Publish listing</button>
    </form>
    <aside class="side card"><strong>What you can sell</strong>
      <p><b>Yes:</b> your own notes and summaries, and past papers your university has published for students.</p>
      <p><b>No:</b> leaked exams, scanned textbooks or slides you do not own, anything a lecturer asked you not to share.</p>
      <p class="muted">Anyone can report a listing. Listings with several reports are hidden until a moderator decides.</p></aside></div>`);
  $("#f").onsubmit = (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    const meta = {
      title: f.get("title"), description: f.get("description"), courseCode: f.get("courseCode"),
      university: f.get("university"), kind: f.get("kind"), source: f.get("source"),
      sourceNote: f.get("sourceNote"), priceUsdc: f.get("priceUsdc"),
      ownershipConfirmed: f.get("ownershipConfirmed") === "on",
    };
    const form = new FormData();
    form.append("meta", new Blob([JSON.stringify(meta)], { type: "application/json" }));
    form.append("file", f.get("file"));
    guarded(e.submitter, async () => {
      const created = await api("/api/listings", { method: "POST", form });
      toast("Listing published"); location.hash = "#/listing/" + created.id;
    });
  };
}

async function minePage() {
  if (!requireLogin()) return;
  const page = await api("/api/listings/mine");
  view(`<h1>My listings</h1><p><a class="btn primary" href="#/sell">New listing</a></p>
    ${page.items.length === 0 ? `<p class="notice">You have not listed anything yet.</p>` :
      `<div class="scroll"><table><thead><tr><th>Title</th><th>Price</th><th>Status</th></tr></thead><tbody>
      ${page.items.map((l) => `<tr><td><a href="#/listing/${esc(l.id)}">${esc(l.title)}</a></td><td>${money(l.priceUsdc)}</td>
        <td><span class="badge ${l.status === "ACTIVE" ? "good" : "warn"}">${esc(l.status)}</span></td></tr>`).join("")}
      </tbody></table></div>`}`);
}

function orderRows(orders, party) {
  if (orders.length === 0) return `<p class="notice">Nothing here yet.</p>`;
  return `<div class="scroll"><table><thead><tr><th>File</th><th>${party}</th><th>Amount</th><th>Status</th></tr></thead><tbody>
    ${orders.map((o) => `<tr><td><a href="#/order/${esc(o.id)}">${esc(o.listingTitle)}</a></td>
      <td>${esc(party === "Seller" ? o.sellerName : o.buyerName)}</td><td>${money(o.amountUsdc)}</td>
      <td><span class="badge ${o.status === "COMPLETED" ? "good" : o.status === "DISPUTED" ? "bad" : "warn"}">${esc(STATUS_TEXT[o.status])}</span></td></tr>`).join("")}
    </tbody></table></div>`;
}

async function ordersPage() {
  if (!requireLogin()) return;
  const [purchases, sales] = await Promise.all([api("/api/orders/purchases"), api("/api/orders/sales")]);
  view(`<h1>My orders</h1><h2>Purchases</h2>${orderRows(purchases, "Seller")}<h2>Sales</h2>${orderRows(sales, "Buyer")}`);
}

async function orderPage(id) {
  if (!requireLogin()) return;
  const o = await api("/api/orders/" + id);
  const me = (state.user.walletAddress || "").toLowerCase();
  const isBuyer = me === o.buyerWallet.toLowerCase();
  const isSeller = me === o.sellerWallet.toLowerCase();
  const isAdmin = state.user.role === "ADMIN";
  const mock = state.config.chainMode === "mock";
  const order = ["AWAITING_PAYMENT", "FUNDED", "COMPLETED"];
  const stage = o.status === "AWAITING_PAYMENT" ? 1 : o.status === "FUNDED" ? 2 : o.status === "DISPUTED" ? 2 : 4;
  const steps = [
    ["Lock payment in escrow", "Your USDC goes into a smart contract. The seller cannot touch it yet."],
    ["Check the file", "Download it and compare it with the listing."],
    ["Confirm", "Happy? Pay the seller. Not happy? Open a dispute."],
    ["Done", "The seller was paid, or you were refunded."],
  ];
  view(`<h1>${esc(o.listingTitle)}</h1>
    <p><span class="badge ${o.status === "COMPLETED" ? "good" : o.status === "DISPUTED" ? "bad" : "warn"}">${esc(STATUS_TEXT[o.status])}</span>
      <span class="muted"> · ${money(o.amountUsdc)} · buyer ${esc(o.buyerName)} · seller ${esc(o.sellerName)}</span></p>
    <div class="split"><div class="main">
      <ol class="steps">${steps.map(([t, d], i) => `<li class="${i + 1 < stage ? "done" : i + 1 === stage ? "now" : ""}">
        <span class="n">${i + 1}</span><span><strong>${esc(t)}</strong><br><span class="muted">${esc(d)}</span></span></li>`).join("")}</ol>
      <div id="order-actions" class="row" style="margin-top:20px"></div>
    </div>
    <aside class="side card"><strong>Escrow details</strong>
      <p class="muted">Escrow id</p><p class="mono">${esc(o.escrow.escrowId)}</p>
      <p class="muted">Buyer wallet</p><p class="mono">${esc(o.buyerWallet)}</p>
      <p class="muted">Seller wallet</p><p class="mono">${esc(o.sellerWallet)}</p>
      ${mock ? `<p class="notice">Demo mode: the escrow is simulated by the backend.</p>` : ""}
    </aside></div>`);

  const box = $("#order-actions");
  const buttons = [];
  const add = (label, cls, handler) => buttons.push({ label, cls, handler });
  const run = (action, extra, message) => async () => { await escrowAction(o, action, extra); toast(message); orderPage(id); };

  if (isBuyer && o.status === "AWAITING_PAYMENT") add("Pay " + money(o.amountUsdc) + " into escrow", "primary", run("fund", "", "Payment locked in escrow"));
  if (o.canDownload && (isBuyer || isSeller || isAdmin)) add("Download file", "", () => downloadFile(o.listingId));
  if (isBuyer && o.status === "FUNDED") {
    add("I checked it. Pay the seller", "good", run("release", "", "Seller paid"));
    add("Open a dispute", "danger", run("dispute", "", "Dispute opened"));
  }
  if (isSeller && (o.status === "FUNDED" || o.status === "DISPUTED")) add("Refund the buyer", "", run("refund", "", "Buyer refunded"));
  if (isSeller && o.status === "FUNDED" && !mock) add("Claim payment after the waiting period", "", run("claim", "", "Payment claimed"));
  if (isAdmin && o.status === "DISPUTED" && mock) {
    add("Settle: pay the seller", "good", run("resolve", "?toSeller=true", "Settled for the seller"));
    add("Settle: refund the buyer", "", run("resolve", "?toSeller=false", "Settled for the buyer"));
  }
  add("Refresh status", "", async () => { await api(`/api/orders/${o.id}/sync`, { method: "POST" }); orderPage(id); });

  box.innerHTML = buttons.map((b, i) => `<button class="btn ${b.cls}" data-i="${i}">${esc(b.label)}</button>`).join("");
  box.onclick = (e) => {
    const b = e.target.closest("[data-i]");
    if (b) guarded(b, buttons[Number(b.dataset.i)].handler);
  };
  if (isAdmin && o.status === "DISPUTED" && !mock) {
    box.insertAdjacentHTML("afterend", `<p class="notice warn">Disputes are settled on-chain by the arbiter wallet calling resolve() on the contract.</p>`);
  }
}

async function adminPage() {
  if (!requireLogin()) return;
  if (state.user.role !== "ADMIN") { view(`<h1>Admin</h1><p class="notice warn">Admins only.</p>`); return; }
  const [reports, disputes] = await Promise.all([api("/api/admin/reports?status=OPEN"), api("/api/admin/orders?status=DISPUTED")]);
  view(`<h1>Moderation</h1><h2>Open reports</h2>
    ${reports.length === 0 ? `<p class="notice good">No open reports.</p>` : reports.map((r) => `<div class="card" style="margin-bottom:12px">
      <div class="badges"><span class="badge warn">${esc(REASONS[r.reason])}</span><span class="badge">${esc(r.listingStatus)}</span></div>
      <p><a href="#/listing/${esc(r.listingId)}"><strong>${esc(r.listingTitle)}</strong></a> by ${esc(r.sellerName)}<br>
        <span class="muted">Reported by ${esc(r.reporterName)}${r.details ? ": " + esc(r.details) : ""}</span></p>
      <div class="row"><button class="btn" data-id="${esc(r.id)}" data-act="DISMISS">Dismiss report</button>
        <button class="btn danger" data-id="${esc(r.id)}" data-act="REMOVE_LISTING">Remove listing</button></div></div>`).join("")}
    <h2>Disputed orders</h2>${orderRows(disputes, "Buyer")}`);
  $("#app").onclick = (e) => {
    const b = e.target.closest("[data-act]");
    if (!b) return;
    guarded(b, async () => {
      await api(`/api/admin/reports/${b.dataset.id}/resolve`, { method: "POST", body: { action: b.dataset.act, note: "" } });
      toast("Done"); adminPage();
    });
  };
}

/* ---------- 5. navigation and router ---------- */

function renderNav() {
  const here = location.hash.replace(/^#\/?/, "").split("/")[0];
  const link = (href, label, key) => `<a href="#/${href}" class="${here === key ? "active" : ""}">${label}</a>`;
  let html = link("", "Browse", "");
  if (state.user) {
    html += link("sell", "Sell", "sell") + link("orders", "Orders", "orders") + link("mine", "My listings", "mine");
    if (state.user.role === "ADMIN") html += link("admin", "Moderation", "admin");
    html += `<span class="spacer"></span>` + link("wallet", state.user.walletAddress ? "Wallet " + esc(state.user.walletAddress.slice(0, 6) + "…" + state.user.walletAddress.slice(-4)) : "Link wallet", "wallet")
      + `<button id="logout">Log out</button>`;
  } else {
    html += `<span class="spacer"></span>` + link("login", "Log in", "login") + link("register", "Sign up", "register");
  }
  $("#nav").innerHTML = html;
  const out = $("#logout");
  if (out) out.onclick = () => logout();
}

const routes = [
  [/^$/, browsePage], [/^listing\/([\w-]+)$/, listingPage], [/^order\/([\w-]+)$/, orderPage],
  [/^login$/, loginPage], [/^register$/, registerPage], [/^sell$/, sellPage], [/^orders$/, ordersPage],
  [/^mine$/, minePage], [/^wallet$/, walletPage], [/^admin$/, adminPage],
];

async function route() {
  const path = location.hash.replace(/^#\/?/, "");
  renderNav();
  for (const [pattern, handler] of routes) {
    const match = path.match(pattern);
    if (match) {
      try { await handler(...match.slice(1)); } catch (e) { view(`<p class="error">${esc(e.message)}</p>`); }
      renderNav();
      return;
    }
  }
  view(`<h1>Page not found</h1><p><a href="#/">Back to browse</a></p>`);
}

window.addEventListener("hashchange", route);
loadSession().then(route).catch((e) => view(`<p class="error">Could not reach the server: ${esc(e.message)}</p>`));
