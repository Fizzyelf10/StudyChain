// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/**
 * @title StudyEscrow
 * @notice Holds a buyer's stablecoin payment for a study file until the buyer confirms it.
 *
 * The platform operator never holds user funds. Money moves only in these ways:
 *   - buyer funds the escrow                       (None      -> Funded)
 *   - buyer releases to the seller                 (Funded    -> Released)
 *   - seller refunds the buyer                     (Funded or Disputed -> Refunded)
 *   - buyer opens a dispute                        (Funded    -> Disputed)
 *   - arbiter settles a dispute either way         (Disputed  -> Released or Refunded)
 *   - seller claims after `claimDelay` has passed  (Funded    -> Released)
 *
 * Each escrow is identified by a bytes32 id that the backend derives from the order id.
 */
contract StudyEscrow is ReentrancyGuard {
    using SafeERC20 for IERC20;

    enum State { None, Funded, Released, Refunded, Disputed }

    struct Escrow {
        address buyer;
        address seller;
        uint256 amount;
        uint64 fundedAt;
        State state;
    }

    IERC20 public immutable token;
    address public immutable arbiter;
    uint64 public immutable claimDelay;

    mapping(bytes32 => Escrow) private escrows;

    event Funded(bytes32 indexed id, address indexed buyer, address indexed seller, uint256 amount);
    event Released(bytes32 indexed id, address indexed seller, uint256 amount);
    event Refunded(bytes32 indexed id, address indexed buyer, uint256 amount);
    event Disputed(bytes32 indexed id, address indexed buyer);

    constructor(IERC20 token_, address arbiter_, uint64 claimDelay_) {
        require(address(token_) != address(0), "token is zero");
        require(arbiter_ != address(0), "arbiter is zero");
        token = token_;
        arbiter = arbiter_;
        claimDelay = claimDelay_;
    }

    /// @notice Lock `amount` tokens for `seller`. The caller must have approved this contract first.
    function fund(bytes32 id, address seller, uint256 amount) external nonReentrant {
        require(escrows[id].state == State.None, "escrow exists");
        require(seller != address(0) && seller != msg.sender, "bad seller");
        require(amount > 0, "amount is zero");

        escrows[id] = Escrow({
            buyer: msg.sender,
            seller: seller,
            amount: amount,
            fundedAt: uint64(block.timestamp),
            state: State.Funded
        });
        token.safeTransferFrom(msg.sender, address(this), amount);
        emit Funded(id, msg.sender, seller, amount);
    }

    /// @notice Buyer confirms the file is fine and pays the seller.
    function release(bytes32 id) external nonReentrant {
        Escrow storage e = escrows[id];
        require(e.state == State.Funded, "not funded");
        require(msg.sender == e.buyer, "only buyer");
        _payout(id, e, e.seller, State.Released);
    }

    /// @notice Seller gives the money back to the buyer.
    function refund(bytes32 id) external nonReentrant {
        Escrow storage e = escrows[id];
        require(e.state == State.Funded || e.state == State.Disputed, "nothing to refund");
        require(msg.sender == e.seller, "only seller");
        _payout(id, e, e.buyer, State.Refunded);
    }

    /// @notice Buyer says the file does not match the listing. Funds stay locked for the arbiter.
    function dispute(bytes32 id) external {
        Escrow storage e = escrows[id];
        require(e.state == State.Funded, "not funded");
        require(msg.sender == e.buyer, "only buyer");
        e.state = State.Disputed;
        emit Disputed(id, e.buyer);
    }

    /// @notice Arbiter settles a dispute.
    function resolve(bytes32 id, bool toSeller) external nonReentrant {
        Escrow storage e = escrows[id];
        require(e.state == State.Disputed, "not disputed");
        require(msg.sender == arbiter, "only arbiter");
        if (toSeller) {
            _payout(id, e, e.seller, State.Released);
        } else {
            _payout(id, e, e.buyer, State.Refunded);
        }
    }

    /// @notice If the buyer neither confirms nor disputes for `claimDelay`, the seller may collect.
    function claimAfterTimeout(bytes32 id) external nonReentrant {
        Escrow storage e = escrows[id];
        require(e.state == State.Funded, "not funded");
        require(msg.sender == e.seller, "only seller");
        require(block.timestamp >= uint256(e.fundedAt) + claimDelay, "too early");
        _payout(id, e, e.seller, State.Released);
    }

    /// @notice Read an escrow. `state` is the numeric value of the State enum (0 = None).
    function getEscrow(bytes32 id)
        external
        view
        returns (address buyer, address seller, uint256 amount, uint8 state)
    {
        Escrow storage e = escrows[id];
        return (e.buyer, e.seller, e.amount, uint8(e.state));
    }

    function _payout(bytes32 id, Escrow storage e, address to, State newState) private {
        uint256 amount = e.amount;
        e.state = newState;
        token.safeTransfer(to, amount);
        if (newState == State.Released) {
            emit Released(id, to, amount);
        } else {
            emit Refunded(id, to, amount);
        }
    }
}
