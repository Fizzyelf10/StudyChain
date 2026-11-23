// Deploys MockUSDC (optional) and StudyEscrow to a network.
// Usage (Sepolia testnet example, PowerShell):
//   $env:RPC_URL="https://..."; $env:PRIVATE_KEY="0x..."; npm run deploy
// Env vars:
//   RPC_URL      JSON-RPC endpoint of the network
//   PRIVATE_KEY  key of a TEST wallet that pays gas (never use a wallet that holds real money)
//   TOKEN        optional: address of an existing ERC-20 (e.g. USDC). If missing, MockUSDC is deployed.
//   ARBITER      optional: address that settles disputes. Defaults to the deployer.
//   CLAIM_DAYS   optional: days before a seller may claim an unconfirmed escrow. Default 7.
const fs = require("fs");
const path = require("path");
const { ethers } = require("ethers");

function artifact(name) {
  return JSON.parse(fs.readFileSync(path.join(__dirname, "..", "build", name + ".json"), "utf8"));
}

async function main() {
  const { RPC_URL, PRIVATE_KEY } = process.env;
  if (!RPC_URL || !PRIVATE_KEY) throw new Error("Set RPC_URL and PRIVATE_KEY first");

  const provider = new ethers.JsonRpcProvider(RPC_URL);
  const wallet = new ethers.Wallet(PRIVATE_KEY, provider);
  const network = await provider.getNetwork();
  console.log("Deployer:", wallet.address, "chainId:", network.chainId.toString());

  let tokenAddress = process.env.TOKEN;
  if (!tokenAddress) {
    const { abi, bytecode } = artifact("MockUSDC");
    const token = await new ethers.ContractFactory(abi, bytecode, wallet).deploy();
    await token.waitForDeployment();
    tokenAddress = await token.getAddress();
    console.log("MockUSDC deployed:", tokenAddress);
  }

  const arbiter = process.env.ARBITER || wallet.address;
  const claimDelay = BigInt(process.env.CLAIM_DAYS || "7") * 86400n;
  const { abi, bytecode } = artifact("StudyEscrow");
  const escrow = await new ethers.ContractFactory(abi, bytecode, wallet).deploy(tokenAddress, arbiter, claimDelay);
  await escrow.waitForDeployment();
  const escrowAddress = await escrow.getAddress();
  console.log("StudyEscrow deployed:", escrowAddress);

  console.log("\nPut this in the backend (application.yml or environment variables):");
  console.log("  studychain.chain.mode: rpc");
  console.log("  studychain.chain.rpc-url:", RPC_URL);
  console.log("  studychain.chain.chain-id:", network.chainId.toString());
  console.log("  studychain.chain.escrow-contract:", escrowAddress);
  console.log("  studychain.chain.token-contract:", tokenAddress);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
