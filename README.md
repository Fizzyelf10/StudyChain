# StudyChain

A marketplace where university students sell and buy study material (past papers, notes, summaries) and pay in crypto. Payments sit in an **escrow smart contract** until the buyer confirms the file, so the platform never holds user funds.

- **Backend:** Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL (H2 for development)
- **Blockchain:** a Solidity escrow contract (`contracts/`), wallet-ownership proof by signature (web3j, verified in Java)
- **Frontend:** plain HTML, CSS and JavaScript served by Spring Boot (`src/main/resources/static`)

## How it works

```
 Seller                      Backend (Spring Boot)                    Blockchain
   | upload PDF + details ------> validate PDF, hash, preview, save
   |                                       |
 Buyer                                     |
   | create order ---------------> order AWAITING_PAYMENT
   | fund escrow (own wallet) ---------------------------------------> escrow holds the USDC
   | "sync" ----------------------> reads escrow state <--------------- getEscrow(id)
   |                                order FUNDED -> buyer may download
   | confirm (own wallet) -------------------------------------------> seller is paid
   | "sync" ----------------------> order COMPLETED
```

Key ideas (good interview talking points):

- **The backend only reads the chain.** Users fund, release and dispute from their own wallets. The server holds no keys and no money.
- **Wallet ownership is proven, not claimed.** The user signs a one-time message; the server recovers the signer's address from the signature (`SignatureVerifier`).
- **Paywall:** a file can be downloaded only by its seller, an admin, or a buyer whose order is `FUNDED`, `COMPLETED` or `DISPUTED`.
- **Order state mirrors the escrow.** `POST /api/orders/{id}/sync` is idempotent and checks that the on-chain buyer, seller and amount match the order before trusting it.
- **Content rules:** sellers declare the source and confirm ownership. Identical files (SHA-256) cannot be listed twice, which also stops a removed file from coming back. Three reports hide a listing automatically until an admin decides.
- **Two chain modes:** `mock` (escrow simulated in memory, runs anywhere) and `rpc` (the real contract on a testnet).

## Run it (demo mode, no blockchain needed)

Requirements: JDK 21 or newer, and IntelliJ IDEA (or Maven).

1. Open this folder in IntelliJ (**File → Open**, choose the folder with `pom.xml`). Wait for Maven to load.
2. Run `ApiApplication` (the green arrow), or in a terminal: `mvn spring-boot:run`.
3. Open <http://localhost:8080>.

If you want the Maven wrapper (`mvnw.cmd`), copy `mvnw`, `mvnw.cmd` and the `.mvn` folder from the project you generated at start.spring.io into this folder.

### Try the whole flow

1. **Sign up** as `seller@example.com`, open **Wallet**, click *Generate demo address*, then *Link this address*.
2. **Sell**: upload `samples/sample-exam.pdf`, fill in the form, publish.
3. Log out. **Sign up** as a second user, link a wallet the same way.
4. Open the listing, click **Buy with escrow**, then **Pay into escrow**, then **Download file**, then **I checked it. Pay the seller**.
5. Moderation: register with `admin@studychain.local` (the address in `application.yml`, **change it before any real use**) and open **Moderation**.

Data lives in memory and disappears when the app stops. Uploaded files are kept in `./data/files`.

### Use PostgreSQL

```
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Run the tests

```
mvn test                          # Java: unit tests + an end-to-end test of the whole story
cd contracts && npm install && npm test    # Solidity escrow contract (11 tests)
```

## Use the real escrow contract on a testnet

1. `cd contracts && npm install`
2. Get a testnet RPC URL (Infura or Alchemy, Sepolia network) and a **throwaway** wallet with a little test ETH. Never use a wallet that holds real money.
3. Deploy (PowerShell):
   ```
   $env:RPC_URL="https://sepolia.infura.io/v3/<key>"; $env:PRIVATE_KEY="0x<throwaway key>"; npm run deploy
   ```
   This deploys a `MockUSDC` test token and the `StudyEscrow` contract and prints the settings to use.
4. Start the backend with those settings, for example:
   ```
   $env:STUDYCHAIN_CHAIN_MODE="rpc"; $env:STUDYCHAIN_CHAIN_RPCURL="..."; $env:STUDYCHAIN_CHAIN_CHAINID="11155111"
   $env:STUDYCHAIN_CHAIN_ESCROWCONTRACT="0x..."; $env:STUDYCHAIN_CHAIN_TOKENCONTRACT="0x..."
   mvn spring-boot:run
   ```
5. In the browser use MetaMask on the Sepolia network. Mint test tokens to your buyer wallet by calling `mint` on the MockUSDC contract (Etherscan "Write contract" works). The UI then handles approve and fund through your wallet.

## API overview

| Method | Path | Who | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | public | get a JWT |
| GET | `/api/auth/me` | user | current user |
| POST | `/api/wallet/challenge`, `/api/wallet/link` | user | prove wallet ownership by signature |
| GET | `/api/listings?q=&kind=&page=&size=` | public | search active listings |
| GET | `/api/listings/{id}` | public | one listing |
| POST | `/api/listings` (multipart: `meta` JSON + `file` PDF) | seller with wallet | create listing |
| DELETE | `/api/listings/{id}` | seller or admin | remove |
| GET | `/api/listings/{id}/preview` | public | page 1 image |
| GET | `/api/listings/{id}/download` | paid buyer, seller, admin | the PDF |
| POST | `/api/listings/{id}/reports` | user | report a listing |
| POST | `/api/orders` | user | create or reuse an order |
| GET | `/api/orders/purchases`, `/sales`, `/{id}` | participants | list and read orders |
| POST | `/api/orders/{id}/sync` | participants | refresh order from the escrow |
| GET, POST | `/api/admin/reports`, `/api/admin/reports/{id}/resolve`, `/api/admin/orders` | admin | moderation |
| POST | `/api/dev/...` | user (mock mode only) | simulate wallet actions |

## Project layout

```
src/main/java/com/studychain/api/
  security/   JWT creation and checking, who may call what
  user/       registration, login, passwords (BCrypt)
  wallet/     challenge and signature verification
  listing/    listings, search, upload, paywalled download
  storage/    PDF validation, hashing, preview image
  order/      orders and their lifecycle
  chain/      reading the escrow: mock and real (JSON-RPC) implementations
  report/     reports, auto-hide, admin review
  common/     errors, paging, health, public config
src/main/resources/db/migration/   Flyway SQL migrations V1..V4
src/main/resources/static/         the web UI
contracts/                         Solidity contract, tests, deploy script
```

## Known limits (be honest about these in interviews)

- **Not audited.** The contract is small and tested, but it has not had a security audit. Use testnets only.
- **Disputes depend on an arbiter.** One trusted address settles them. A production version would use a multisig or a DAO-style panel, and add a timeout for stuck disputes.
- **Files are stored on local disk.** `FileStorageService` is the one class to replace with S3 or similar.
- **No rate limiting, email verification or password reset.**
- **Legal:** operating a crypto payment service in the EU can fall under MiCA and payment rules. The non-custodial design helps but is not legal advice. Selling other people's copyrighted material is not allowed, which is why sellers must declare the source and anyone can report a listing.
- **Mock mode** exists for demos. It simulates the chain in memory and must never be used for real money.
