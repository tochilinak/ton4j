package org.ton.java.smartcontract.integrationtests;

import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ton.java.address.Address;
import org.ton.java.smartcontract.GenerateWallet;
import org.ton.java.smartcontract.payments.PaymentChannel;
import org.ton.java.smartcontract.payments.PaymentsUtils;
import org.ton.java.smartcontract.types.*;
import org.ton.java.smartcontract.wallet.v3.WalletV3R1;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.tonlib.types.ExtMessageInfo;
import org.ton.java.tonlib.types.VerbosityLevel;
import org.ton.java.utils.Utils;

import java.math.BigInteger;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@RunWith(JUnit4.class)
public class TestPayments extends CommonTest {
    static WalletV3R1 walletA;
    static WalletV3R1 walletB;

    static Address walletAddressA;
    static Address walletAddressB;

    @BeforeClass
    public static void setUpClass() throws InterruptedException {
        // PARTIES
        // The payment channel is established between two participants A and B.
        // Each has own secret key, which he does not reveal to the other.

        walletA = GenerateWallet.random(tonlib, 6);
        walletB = GenerateWallet.random(tonlib, 6);
        walletAddressA = walletA.getAddress();
        walletAddressB = walletB.getAddress();
    }

    @Test
    public void testPayments() {

        tonlib = Tonlib.builder()
                .testnet(true)
                .ignoreCache(false)
                .verbosityLevel(VerbosityLevel.DEBUG)
                .build();

        log.info("walletA address {}", walletA.getAddress().toString(true, true, true));
        log.info("walletB address {}", walletB.getAddress().toString(true, true, true));

        //----------------------------------------------------------------------
        // PREPARE PAYMENT CHANNEL

        // The parties agree on the configuration of the payment channel.
        // They share information about the payment channel ID, their public keys, their wallet addresses for withdrawing coins, initial balances.
        // They share this information off-chain, for example via a websocket.
        ChannelInitState channelInitState = ChannelInitState.builder()
                .balanceA(Utils.toNano(1)) // A's initial balance in Toncoins. Next A will need to make a top-up for this amount
                .balanceB(Utils.toNano(2)) // B's initial balance in Toncoins. Next B will need to make a top-up for this amount
                .seqnoA(BigInteger.ZERO)
                .seqnoB(BigInteger.ZERO)
                .build();

        ChannelConfig channelConfig = ChannelConfig.builder()
                .channelId(BigInteger.valueOf(124)) // Channel ID, for each new channel there must be a new ID
                .addressA(walletAddressA) // A's funds will be withdrawn to this wallet address after the channel is closed
                .addressB(walletAddressB) // B's funds will be withdrawn to this wallet address after the channel is closed
                .initBalanceA(channelInitState.getBalanceA())
                .initBalanceB(channelInitState.getBalanceB())
                .build();

        // Each on their side creates a payment channel object with this configuration

//        Options channelOptionsA = Options.builder()
//                .channelConfig(channelConfig)
//                .isA(true)
//                .myKeyPair(walletA.getKeyPair())
//                .hisPublicKey(walletB.getKeyPair().getPublicKey())
//                .publicKey(walletA.getKeyPair().getPublicKey())
//                .secretKey(walletA.getKeyPair().getSecretKey())
//                .build();

//        PaymentChannel channelA = new Wallet(WalletVersion.payments, channelOptionsA).create();
        PaymentChannel channelA = PaymentChannel.builder()
                .channelConfig(channelConfig)
                .isA(true)
                .myKeyPair(walletA.getKeyPair())
                .hisPublicKey(walletB.getKeyPair().getPublicKey())
                .build();
        log.info("channel A address {}", channelA.getAddress().toString(true, true, true));

//        Options channelOptionsB = Options.builder()
//                .channelConfig(channelConfig)
//                .isA(false)
//                .myKeyPair(walletB.getKeyPair())
//                .hisPublicKey(walletA.getKeyPair().getPublicKey())
//                .publicKey(walletB.getKeyPair().getPublicKey())
//                .secretKey(walletB.getKeyPair().getSecretKey())
//                .build();

        PaymentChannel channelB = PaymentChannel.builder()
                .channelConfig(channelConfig)
                .isA(false)
                .myKeyPair(walletB.getKeyPair())
                .hisPublicKey(walletA.getKeyPair().getPublicKey())
//                .publicKey(walletB.getKeyPair().getPublicKey())
//                .secretKey(walletB.getKeyPair().getSecretKey())
                .build();

//        PaymentChannel channelB = new Wallet(WalletVersion.payments, channelOptionsB).create();
        log.info("channel B address {}", channelB.getAddress().toString(true, true, true));

        assertThat(channelA.getAddress().toString(true, true, true)).isEqualTo(channelB.getAddress().toString(true, true, true));

//        FromWallet fromWalletA = channelA.fromWallet(tonlib, walletA.getWallet(), walletA.getKeyPair().getSecretKey());
//        FromWallet fromWalletB = channelB.fromWallet(tonlib, walletB.getWallet(), walletB.getKeyPair().getSecretKey());

        //----------------------------------------------------------------------
        // DEPLOY PAYMENT CHANNEL FROM WALLET A

        // Wallet A must-have a balance.
        // 0.05 TON is the amount to execute this transaction on the blockchain. The unused portion will be returned.
        // After this action, a smart contract of our payment channel will be created in the blockchain.

//        FromWalletConfig config = FromWalletConfig.builder()
//                .seqno(0)
//                .amount(Utils.toNano(0.05))
//                .build();

        WalletV3Config config = WalletV3Config.builder()
                .subWalletId(42)
                .seqno(walletA.getSeqno())
                .mode(3)
                .validUntil(Instant.now().getEpochSecond() + 5 * 60L)
//                .secretKey(walletA.getKeyPair().getSecretKey())
//                .publicKey(walletA.getKeyPair().getPublicKey())
                .destination(channelA.getAddress())
                .amount(Utils.toNano(0.055))
                .stateInit(channelA.getStateInit())
                .body(PaymentsUtils.createTopUpBalance(Utils.toNano(0), Utils.toNano(0)))
                .build();

//        ExtMessageInfo extMessageInfo = fromWalletA.deploy(config);
        ExtMessageInfo extMessageInfo = walletA.sendTonCoins(config);
        assertThat(extMessageInfo.getError().getCode()).isZero();
        Utils.sleep(35, "deploying channel A");

        log.info("channel A state {}", channelA.getChannelState());
        ChannelData data = channelA.getData();
        log.info("channel A data {}", data);
        log.info("balanceA = {}", Utils.formatNanoValue(data.getBalanceA()));
        log.info("balanceB = {}", Utils.formatNanoValue(data.getBalanceB()));

        // TOP UP

        // Now each parties must send their initial balance from the wallet to the channel contract.

//        extMessageInfo = fromWalletA.topUp(channelInitState.getBalanceA(), BigInteger.ZERO,
//        channelInitState.getBalanceA().add(Utils.toNano(0.05))); // +0.05 TON to network fees
        config = WalletV3Config.builder()
                .subWalletId(42)
                .seqno(walletA.getSeqno())
                .mode(3)
                .validUntil(Instant.now().getEpochSecond() + 5 * 60L)
//                .secretKey(walletA.getKeyPair().getSecretKey())
//                .publicKey(walletA.getKeyPair().getPublicKey())
                .destination(channelA.getAddress())
                .amount(channelInitState.getBalanceA().add(Utils.toNano(0.05))) // // +0.05 TON to network fees
                .body(PaymentsUtils.createTopUpBalance(channelInitState.getBalanceA(), BigInteger.ZERO))
                .build();
        extMessageInfo = walletA.sendTonCoins(config);

        assertThat(extMessageInfo.getError().getCode()).isZero();
        Utils.sleep(30, "topping up from wallet A...");

//        extMessageInfo = fromWalletB.topUp(BigInteger.ZERO, channelInitState.getBalanceB(), channelInitState.getBalanceB().add(Utils.toNano(0.05))); // +0.05 TON to network fees
        config = WalletV3Config.builder()
                .subWalletId(42)
                .seqno(walletB.getSeqno())
                .mode(3)
                .validUntil(Instant.now().getEpochSecond() + 5 * 60L)
//                .secretKey(walletB.getKeyPair().getSecretKey())
//                .publicKey(walletB.getKeyPair().getPublicKey())
                .destination(channelB.getAddress())
                .amount(channelInitState.getBalanceB().add(Utils.toNano(0.05))) // // +0.05 TON to network fees
                .body(PaymentsUtils.createTopUpBalance(BigInteger.ZERO, channelInitState.getBalanceB()))
                .build();
        extMessageInfo = walletB.sendTonCoins(config);
        assertThat(extMessageInfo.getError().getCode()).isZero();
        Utils.sleep(40, "topping up from wallet B...");

        ChannelData channelData = channelA.getData();
        log.info("channel A state {}", channelA.getChannelState());
        log.info("channel A data {}", channelData);

        assertThat(channelData.getBalanceA()).isNotEqualTo(BigInteger.ZERO);
        assertThat(channelData.getBalanceB()).isNotEqualTo(BigInteger.ZERO);

        // to check, call the get method - the balances should change

        // INIT

        // After everyone has done top-up, we can initialize the channel from any wallet

//        extMessageInfo = fromWalletA.init(channelInitState.getBalanceA(), channelInitState.getBalanceB(), Utils.toNano(0.05));
        config = WalletV3Config.builder()
                .subWalletId(42)
                .seqno(walletA.getSeqno())
                .mode(3)
                .validUntil(Instant.now().getEpochSecond() + 5 * 60L)
//                .secretKey(walletA.getKeyPair().getSecretKey())
//                .publicKey(walletA.getKeyPair().getPublicKey())
                .destination(channelA.getAddress())
                .amount(Utils.toNano(0.05)) // +0.05 TON to network fees
                .body(channelA.createInitChannel(channelInitState.getBalanceA(), channelInitState.getBalanceB()))
                .build();
        extMessageInfo = walletA.sendTonCoins(config);
        assertThat(extMessageInfo.getError().getCode()).isZero();
        Utils.sleep(45, "initializing channel...");

        // to test call the get method - `state` should change to `TonWeb.payments.PaymentChannel.STATE_OPEN`

        BigInteger channelAState = channelA.getChannelState();
        log.info("channel A state {}", channelAState);
        log.info("channel A data {}", channelA.getData());

        assertThat(channelAState).isNotEqualTo(BigInteger.ZERO);

        //----------------------------------------------------------------------
        // FIRST OFF-CHAIN TRANSFER - A sends 0.1 TON to B

        // A creates new state - subtracts 0.1 from A's balance, adds 0.1 to B's balance, increases A's seqno by 1
        ChannelState channelState1 = ChannelState.builder()
                .balanceA(Utils.toNano(0.9))
                .balanceB(Utils.toNano(2.1))
                .seqnoA(BigInteger.ONE)
                .seqnoB(BigInteger.ZERO)
                .build();

        // A signs this state and sends signed state to B (e.g. via websocket)
        byte[] signatureA1 = channelA.signState(channelState1);

        // B checks that the state is changed according to the rules, signs this state, send signed state to A (e.g. via websocket)
        if (!channelB.verifyState(channelState1, signatureA1)) {
            throw new Error("Invalid A signature");
        }

        byte[] signatureB1 = channelB.signState(channelState1);
        log.info("signatureB1 {}", Utils.bytesToHex(signatureB1));

        //----------------------------------------------------------------------
        // SECOND OFFCHAIN TRANSFER - A sends 0.2 TON to B

        // A creates new state - subtracts 0.2 from A's balance, adds 0.2 to B's balance, increases A's seqno by 1
        ChannelState channelState2 = ChannelState.builder()
                .balanceA(Utils.toNano(0.7))
                .balanceB(Utils.toNano(2.3))
                .seqnoA(BigInteger.TWO)
                .seqnoB(BigInteger.ZERO)
                .build();

        byte[] signatureA2 = channelA.signState(channelState2);

        // B checks that the state is changed according to the rules, signs this state, send signed state to A (e.g. via websocket)
        if (!channelB.verifyState(channelState2, signatureA2)) {
            throw new Error("Invalid A signature");
        }

        byte[] signatureB2 = channelB.signState(channelState2);
        log.info("signatureB2 {}", Utils.bytesToHex(signatureB2));
        //----------------------------------------------------------------------
        // THIRD OFFCHAIN TRANSFER - B sends 1.1 TON TO A

        // B creates new state - subtracts 1.1 from B's balance, adds 1.1 to A's balance, increases B's seqno by 1
        ChannelState channelState3 = ChannelState.builder()
                .balanceA(Utils.toNano(1.8))
                .balanceB(Utils.toNano(1.2))
                .seqnoA(BigInteger.TWO)
                .seqnoB(BigInteger.ONE)
                .build();

        byte[] signatureB3 = channelB.signState(channelState3);

        // B checks that the state is changed according to the rules, signs this state, send signed state to A (e.g. via websocket)
        if (!channelA.verifyState(channelState3, signatureB3)) {
            throw new Error("Invalid B signature");
        }

        byte[] signatureA3 = channelA.signState(channelState3);
        log.info("signatureA3 {}", Utils.bytesToHex(signatureA3));

        //----------------------------------------------------------------------
        // So they can do this endlessly.
        // Note that a party can make its transfers (from itself to another) asynchronously without waiting for the action of the other side.
        // Party must increase its seqno by 1 for each of its transfers and indicate the last seqno and balance of the other party that it knows.

        //----------------------------------------------------------------------
        // CLOSE PAYMENT CHANNEL

        // The parties decide to end the transfer session.
        // If one of the parties disagrees or is not available, then the payment channel can be emergency terminated using the last signed state.
        // That is why the parties send signed states to each other off-chain.
        // But in our case, they do it by mutual agreement.

        // First B signs closing message with last state, B sends it to A (e.g. via websocket)

        byte[] signatureCloseB = channelB.signClose(channelState3);


        // A verifies and signs this closing message and include B's signature

        // A sends closing message to blockchain, payments channel smart contract
        // Payment channel smart contract will send funds to participants according to the balances of the sent state.

        if (!channelA.verifyClose(channelState3, signatureCloseB)) {
            throw new Error("Invalid B signature");
        }

//        extMessageInfo = fromWalletA.close(channelState3, signatureCloseB, Utils.toNano(0.05));
        config = WalletV3Config.builder()
                .subWalletId(42)
                .seqno(walletA.getSeqno())
                .destination(channelA.getAddress())
                .amount(Utils.toNano(0.05))
                .body(channelA.createCooperativeCloseChannel(signatureCloseB, channelState3).getCell())
                .build();
        extMessageInfo = walletA.sendTonCoins(config);
        log.info("channel B address {}", channelB.getAddress().toString(true, true, true));
        assertThat(extMessageInfo.getError().getCode()).isZero();
    }
}
