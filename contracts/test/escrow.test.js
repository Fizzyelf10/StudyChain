// Tests run against an in-process local blockchain (ganache). Run: npm test
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");
const ganache = require("ganache");
const { ethers } = require("ethers");

const State = { None: 0n, Funded: 1n, Released: 2n, Refunded: 3n, Disputed: 4n };
const USDC = (n) => BigInt(Math.round(n * 1e6));
const DAY = 86400;

function artifact(name) {
  return JSON.parse(fs.readFileSync(path.join(__dirname, "..", "build", name + ".json"), "utf8"));
}

let idCounter = 0;
const newId = () => ethers.zeroPadValue(ethers.toBeHex(++idCounter), 32);

// Fresh chain + contracts for every test, so tests cannot affect each other
async function setup() {
  const rawProvider = ganache.provider({ logging: { quiet: true }, wallet: { totalAccounts: 5 }, chain: { vmErrorsOnRPCResponse: true } });
  const provider = new ethers.BrowserProvider(rawProvider);
  const [deployer, buyer, seller, arbiter, stranger] = await Promise.all(
    [0, 1, 2, 3, 4].map((i) => provider.getSigner(i))
  );

  const tokenArt = artifact("MockUSDC");
  const token = await new ethers.ContractFactory(tokenArt.abi, tokenArt.bytecode, deployer).deploy();
  await token.waitForDeployment();

  const escrowArt = artifact("StudyEscrow");
  const escrow = await new ethers.ContractFactory(escrowArt.abi, escrowArt.bytecode, deployer).deploy(
    await token.getAddress(),
    await arbiter.getAddress(),
    7 * DAY
  );
  await escrow.waitForDeployment();

  await (await token.mint(await buyer.getAddress(), USDC(100))).wait();
  await (await token.connect(buyer).approve(await escrow.getAddress(), USDC(100))).wait();

  const addr = {
    buyer: await buyer.getAddress(),
    seller: await seller.getAddress(),
  };
  return { rawProvider, provider, token, escrow, buyer, seller, arbiter, stranger, addr };
}

async function funded(ctx, amount = 5) {
  const id = newId();
  await (await ctx.escrow.connect(ctx.buyer).fund(id, ctx.addr.seller, USDC(amount))).wait();
  return id;
}

async function rejects(promise, fragment) {
  await assert.rejects(promise, (err) => {
    const text = String(err.shortMessage || "") + String(err.message || "") + String(err.reason || "");
    assert.ok(text.includes(fragment), `expected "${fragment}" in: ${text}`);
    return true;
  });
}

test("fund locks the buyer's tokens in the contract", async () => {
  const ctx = await setup();
  const id = await funded(ctx, 5);
  assert.equal(await ctx.token.balanceOf(ctx.addr.buyer), USDC(95));
  assert.equal(await ctx.token.balanceOf(await ctx.escrow.getAddress()), USDC(5));
  const [buyer, seller, amount, state] = await ctx.escrow.getEscrow(id);
  assert.equal(buyer, ctx.addr.buyer);
  assert.equal(seller, ctx.addr.seller);
  assert.equal(amount, USDC(5));
  assert.equal(state, State.Funded);
});

test("an unknown escrow reads as state None", async () => {
  const ctx = await setup();
  const [, , , state] = await ctx.escrow.getEscrow(newId());
  assert.equal(state, State.None);
});

test("fund rejects duplicates, self-sales and zero amounts", async () => {
  const ctx = await setup();
  const id = await funded(ctx);
  await rejects(ctx.escrow.connect(ctx.buyer).fund.staticCall(id, ctx.addr.seller, USDC(1)), "escrow exists");
  await rejects(ctx.escrow.connect(ctx.buyer).fund.staticCall(newId(), ctx.addr.buyer, USDC(1)), "bad seller");
  await rejects(ctx.escrow.connect(ctx.buyer).fund.staticCall(newId(), ctx.addr.seller, 0n), "amount is zero");
});

test("buyer release pays the seller", async () => {
  const ctx = await setup();
  const id = await funded(ctx, 5);
  await (await ctx.escrow.connect(ctx.buyer).release(id)).wait();
  assert.equal(await ctx.token.balanceOf(ctx.addr.seller), USDC(5));
  assert.equal(await ctx.token.balanceOf(await ctx.escrow.getAddress()), 0n);
  const [, , , state] = await ctx.escrow.getEscrow(id);
  assert.equal(state, State.Released);
});

test("only the buyer can release, and only once", async () => {
  const ctx = await setup();
  const id = await funded(ctx);
  await rejects(ctx.escrow.connect(ctx.seller).release.staticCall(id), "only buyer");
  await rejects(ctx.escrow.connect(ctx.stranger).release.staticCall(id), "only buyer");
  await (await ctx.escrow.connect(ctx.buyer).release(id)).wait();
  await rejects(ctx.escrow.connect(ctx.buyer).release.staticCall(id), "not funded");
});

test("seller refund returns the money to the buyer", async () => {
  const ctx = await setup();
  const id = await funded(ctx, 5);
  await rejects(ctx.escrow.connect(ctx.buyer).refund.staticCall(id), "only seller");
  await (await ctx.escrow.connect(ctx.seller).refund(id)).wait();
  assert.equal(await ctx.token.balanceOf(ctx.addr.buyer), USDC(100));
  const [, , , state] = await ctx.escrow.getEscrow(id);
  assert.equal(state, State.Refunded);
});

test("dispute freezes funds until the arbiter decides (refund path)", async () => {
  const ctx = await setup();
  const id = await funded(ctx, 5);
  await rejects(ctx.escrow.connect(ctx.seller).dispute.staticCall(id), "only buyer");
  await (await ctx.escrow.connect(ctx.buyer).dispute(id)).wait();
  let [, , , state] = await ctx.escrow.getEscrow(id);
  assert.equal(state, State.Disputed);

  await rejects(ctx.escrow.connect(ctx.buyer).release.staticCall(id), "not funded");
  await rejects(ctx.escrow.connect(ctx.stranger).resolve.staticCall(id, true), "only arbiter");
  await (await ctx.escrow.connect(ctx.arbiter).resolve(id, false)).wait();
  assert.equal(await ctx.token.balanceOf(ctx.addr.buyer), USDC(100));
  [, , , state] = await ctx.escrow.getEscrow(id);
  assert.equal(state, State.Refunded);
});

test("arbiter can settle a dispute in the seller's favour", async () => {
  const ctx = await setup();
  const id = await funded(ctx, 5);
  await (await ctx.escrow.connect(ctx.buyer).dispute(id)).wait();
  await (await ctx.escrow.connect(ctx.arbiter).resolve(id, true)).wait();
  assert.equal(await ctx.token.balanceOf(ctx.addr.seller), USDC(5));
  const [, , , state] = await ctx.escrow.getEscrow(id);
  assert.equal(state, State.Released);
});

test("resolve only works on disputed escrows", async () => {
  const ctx = await setup();
  const id = await funded(ctx);
  await rejects(ctx.escrow.connect(ctx.arbiter).resolve.staticCall(id, true), "not disputed");
});

test("seller can claim only after the delay, and not while disputed", async () => {
  const ctx = await setup();
  const id = await funded(ctx, 5);
  await rejects(ctx.escrow.connect(ctx.seller).claimAfterTimeout.staticCall(id), "too early");
  await rejects(ctx.escrow.connect(ctx.buyer).claimAfterTimeout.staticCall(id), "only seller");

  await ctx.rawProvider.request({ method: "evm_increaseTime", params: [8 * DAY] });
  await ctx.rawProvider.request({ method: "evm_mine", params: [] });
  await (await ctx.escrow.connect(ctx.seller).claimAfterTimeout(id)).wait();
  assert.equal(await ctx.token.balanceOf(ctx.addr.seller), USDC(5));

  const second = await funded(ctx, 2);
  await (await ctx.escrow.connect(ctx.buyer).dispute(second)).wait();
  await ctx.rawProvider.request({ method: "evm_increaseTime", params: [8 * DAY] });
  await ctx.rawProvider.request({ method: "evm_mine", params: [] });
  await rejects(ctx.escrow.connect(ctx.seller).claimAfterTimeout.staticCall(second), "not funded");
});

test("fund fails without an allowance", async () => {
  const ctx = await setup();
  await (await ctx.token.connect(ctx.buyer).approve(await ctx.escrow.getAddress(), 0n)).wait();
  await assert.rejects(ctx.escrow.connect(ctx.buyer).fund(newId(), ctx.addr.seller, USDC(1)));
});
