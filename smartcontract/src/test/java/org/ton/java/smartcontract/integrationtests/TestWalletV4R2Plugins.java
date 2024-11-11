package org.ton.java.smartcontract.integrationtests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.iwebpp.crypto.TweetNaclFast;
import java.math.BigInteger;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ton.java.address.Address;
import org.ton.java.cell.Cell;
import org.ton.java.smartcontract.faucet.TestnetFaucet;
import org.ton.java.smartcontract.types.DeployedPlugin;
import org.ton.java.smartcontract.types.NewPlugin;
import org.ton.java.smartcontract.types.WalletV4R2Config;
import org.ton.java.smartcontract.utils.MsgUtils;
import org.ton.java.smartcontract.wallet.v4.SubscriptionInfo;
import org.ton.java.smartcontract.wallet.v4.WalletV4R2;
import org.ton.java.tonlib.types.ExtMessageInfo;
import org.ton.java.utils.Utils;

@Slf4j
@RunWith(JUnit4.class)
public class TestWalletV4R2Plugins extends CommonTest {

  static String FAUCET_ADDRESS_RAW =
      "0:b52a16ba3735501df19997550e7ed4c41754ee501ded8a841088ce4278b66de4";

  @Test
  public void testWalletV4Deploy() throws InterruptedException {

    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPair();

    WalletV4R2 contract = WalletV4R2.builder().tonlib(tonlib).keyPair(keyPair).walletId(42).build();

    Address walletAddress = contract.getAddress();

    String nonBounceableAddress = walletAddress.toNonBounceable();
    String bounceableAddress = walletAddress.toBounceable();
    log.info("bounceableAddress: {}", bounceableAddress);
    log.info("pub-key {}", Utils.bytesToHex(contract.getKeyPair().getPublicKey()));
    log.info("prv-key {}", Utils.bytesToHex(contract.getKeyPair().getSecretKey()));

    BigInteger balance =
        TestnetFaucet.topUpContract(tonlib, Address.of(nonBounceableAddress), Utils.toNano(0.1));
    log.info("new wallet {} balance: {}", contract.getName(), Utils.formatNanoValue(balance));

    ExtMessageInfo extMessageInfo = contract.deploy();
    assertThat(extMessageInfo.getError().getCode()).isZero();
  }

  @Test
  public void testPlugins() throws InterruptedException {

    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPair();

    WalletV4R2 contract = WalletV4R2.builder().tonlib(tonlib).keyPair(keyPair).walletId(42).build();

    Address walletAddress = contract.getAddress();

    String nonBounceableAddress = walletAddress.toNonBounceable();
    String bounceableAddress = walletAddress.toBounceable();
    log.info("bounceableAddress: {}", bounceableAddress);
    log.info("pub-key {}", Utils.bytesToHex(contract.getKeyPair().getPublicKey()));
    log.info("prv-key {}", Utils.bytesToHex(contract.getKeyPair().getSecretKey()));

    BigInteger balance =
        TestnetFaucet.topUpContract(tonlib, Address.of(nonBounceableAddress), Utils.toNano(7));
    log.info("new wallet {} balance: {}", contract.getName(), Utils.formatNanoValue(balance));

    // deploy wallet-v4
    ExtMessageInfo extMessageInfo = contract.deploy();
    assertThat(extMessageInfo.getError().getCode()).isZero();

    contract.waitForDeployment(30);

    long walletCurrentSeqno = contract.getSeqno();
    log.info("walletV4 balance: {}", Utils.formatNanoValue(contract.getBalance()));
    log.info("seqno: {}", walletCurrentSeqno);
    log.info("walletId: {}", contract.getWalletId());
    log.info("pubKey: {}", Utils.bytesToHex(contract.getPublicKey()));
    log.info("pluginsList: {}", contract.getPluginsList());
    assertThat(contract.getPluginsList().isEmpty()).isTrue();

    // create and deploy plugin -- start

    Address beneficiaryAddress = Address.of("kf_sPxv06KagKaRmOOKxeDQwApCx3i8IQOwv507XD51JOLka");
    log.info("beneficiaryAddress: {}", beneficiaryAddress.toBounceable());

    SubscriptionInfo subscriptionInfo =
        SubscriptionInfo.builder()
            .beneficiary(beneficiaryAddress)
            .subscriptionFee(Utils.toNano(2))
            .period(60)
            .startTime(0)
            .timeOut(30)
            .lastPaymentTime(0)
            .lastRequestTime(0)
            .failedAttempts(0)
            .subscriptionId(12345)
            .build();

    Utils.sleep(30);

    log.info(
        "beneficiaryWallet balance {}",
        Utils.formatNanoValue(tonlib.getAccountBalance(beneficiaryAddress)));

    WalletV4R2Config config =
        WalletV4R2Config.builder()
            .seqno(contract.getSeqno())
            .operation(1) // deploy and install plugin
            .walletId(42)
            .newPlugin(
                NewPlugin.builder()
                    .secretKey(keyPair.getSecretKey())
                    .seqno(walletCurrentSeqno)
                    .pluginWc(contract.getWc()) // reuse wc of the wallet
                    .amount(
                        Utils.toNano(0.1)) // initial plugin balance, will be taken from wallet-v4
                    .stateInit(contract.createPluginStateInit(subscriptionInfo))
                    .body(contract.createPluginBody())
                    .build())
            .build();

    extMessageInfo = contract.send(config);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    Utils.sleep(45);

    log.info(
        "beneficiaryWallet balance {}",
        Utils.formatNanoValue(tonlib.getAccountBalance(beneficiaryAddress)));

    // create and deploy plugin -- end

    // get plugin list
    List<String> plugins = contract.getPluginsList();
    log.info("pluginsList: {}", plugins);

    assertThat(plugins.isEmpty()).isFalse();

    Address pluginAddress = Address.of(plugins.get(0));
    log.info("pluginAddress {}", pluginAddress.toString(false));

    subscriptionInfo = contract.getSubscriptionData(pluginAddress);

    log.info("{}", subscriptionInfo);
    log.info(
        "plugin hash: int {}, hex {}",
        new BigInteger(pluginAddress.hashPart),
        Utils.bytesToHex(pluginAddress.hashPart));

    log.info("plugin {} installed {}", pluginAddress, contract.isPluginInstalled(pluginAddress));

    log.info("collect fee - first time");

    Cell extMessage =
        MsgUtils.createExternalMessageWithSignedBody(
                contract.getKeyPair(), pluginAddress, null, null)
            .toCell();

    extMessageInfo = tonlib.sendRawMessage(extMessage.toBase64());
    log.info("extMessageInfo {}", extMessageInfo);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    tonlib.waitForDeployment(beneficiaryAddress, 90);
    //    ContractUtils.waitForDeployment(tonlib, beneficiaryAddress, 90); // no need?

    log.info(
        "beneficiaryWallet balance {}",
        Utils.formatNanoValue(tonlib.getAccountBalance(beneficiaryAddress)));

    Utils.sleep(30, "wait for seqno update");

    log.info("walletV4 balance: {}", Utils.formatNanoValue(contract.getBalance()));

    subscriptionInfo = contract.getSubscriptionData(pluginAddress);
    log.info("{}", subscriptionInfo);

    assertThat(subscriptionInfo.getLastPaymentTime()).isNotEqualTo(0);

    log.info("collect fee - second time");

    Utils.sleep(180, "wait for timeout");

    subscriptionInfo = contract.getSubscriptionData(pluginAddress);
    log.info("{}", subscriptionInfo);

    extMessage =
        MsgUtils.createExternalMessageWithSignedBody(
                contract.getKeyPair(), pluginAddress, null, null)
            .toCell();
    extMessageInfo = tonlib.sendRawMessage(extMessage.toBase64());
    log.info("extMessageInfo {}", extMessageInfo);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    tonlib.waitForDeployment(subscriptionInfo.getBeneficiary(), 90);

    log.info(
        "beneficiaryWallet balance {}",
        Utils.formatNanoValue(tonlib.getAccountBalance(subscriptionInfo.getBeneficiary())));

    Utils.sleep(30);

    log.info("walletV4 balance: {}", Utils.formatNanoValue(contract.getBalance()));

    subscriptionInfo = contract.getSubscriptionData(pluginAddress);
    log.info("{}", subscriptionInfo);

    // uninstall/remove plugin from the wallet -- start

    log.info("Uninstalling plugin {}", Address.of(contract.getPluginsList().get(0)));

    walletCurrentSeqno = contract.getSeqno();

    config =
        WalletV4R2Config.builder()
            .seqno(contract.getSeqno())
            .walletId(config.getWalletId())
            .operation(3) // uninstall plugin
            .deployedPlugin(
                DeployedPlugin.builder()
                    .seqno(walletCurrentSeqno)
                    .amount(Utils.toNano(0.1))
                    .pluginAddress(Address.of(contract.getPluginsList().get(0)))
                    .secretKey(keyPair.getSecretKey())
                    .queryId(0)
                    .build())
            .build();

    extMessageInfo = contract.uninstallPlugin(config);
    Utils.sleep(30, "sent uninstall request");
    assertThat(extMessageInfo.getError().getCode()).isZero();

    // uninstall plugin -- end

    Utils.sleep(60);
    List<String> list = contract.getPluginsList();
    log.info("pluginsList: {}", list);
    assertThat(list.isEmpty()).isTrue();

    config =
        WalletV4R2Config.builder()
            .operation(0)
            .walletId(contract.getWalletId())
            .seqno(contract.getSeqno())
            .destination(Address.of(FAUCET_ADDRESS_RAW))
            .amount(Utils.toNano(0.331))
            .build();

    extMessageInfo = contract.send(config);
    assertThat(extMessageInfo.getError().getCode()).isZero();
  }

  @Test
  public void testSimpleSend() throws InterruptedException {

    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPair();

    WalletV4R2 contract = WalletV4R2.builder().tonlib(tonlib).keyPair(keyPair).walletId(42).build();

    Address walletAddress = contract.getAddress();

    String nonBounceableAddress = walletAddress.toNonBounceable();
    String bounceableAddress = walletAddress.toBounceable();
    log.info("bounceableAddress: {}", bounceableAddress);
    log.info("pub-key {}", Utils.bytesToHex(contract.getKeyPair().getPublicKey()));
    log.info("prv-key {}", Utils.bytesToHex(contract.getKeyPair().getSecretKey()));

    BigInteger balance =
        TestnetFaucet.topUpContract(tonlib, Address.of(nonBounceableAddress), Utils.toNano(1));
    log.info("new wallet {} balance: {}", contract.getName(), Utils.formatNanoValue(balance));

    // deploy wallet-v4
    ExtMessageInfo extMessageInfo = contract.deploy();
    assertThat(extMessageInfo.getError().getCode()).isZero();

    contract.waitForDeployment(30);

    long walletCurrentSeqno = contract.getSeqno();
    log.info("walletV4 balance: {}", Utils.formatNanoValue(contract.getBalance()));
    log.info("seqno: {}", walletCurrentSeqno);
    log.info("walletId: {}", contract.getWalletId());
    log.info("pubKey: {}", Utils.bytesToHex(contract.getPublicKey()));
    log.info("pluginsList: {}", contract.getPluginsList());
    log.info("pluginsList: {}", contract.getPluginsList());

    WalletV4R2Config config =
        WalletV4R2Config.builder()
            .operation(0)
            .walletId(contract.getWalletId())
            .seqno(contract.getSeqno())
            .destination(Address.of(FAUCET_ADDRESS_RAW))
            .mode(3)
            .amount(Utils.toNano(0.331))
            .comment("ton4j-v4r2-simple-send")
            .build();

    contract.send(config);
  }
}
