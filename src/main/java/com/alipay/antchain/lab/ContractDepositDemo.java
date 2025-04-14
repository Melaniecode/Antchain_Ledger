/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2021 All Rights Reserved.
 */
package com.alipay.antchain.lab;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.mychain.sdk.api.MychainClient;
import com.alipay.mychain.sdk.api.env.*;
import com.alipay.mychain.sdk.api.logging.AbstractLoggerFactory;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.MyCrypto;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.crypto.hash.HashFactory;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.IHash;
import com.alipay.mychain.sdk.crypto.keyoperator.Pkcs8KeyOperator;
import com.alipay.mychain.sdk.crypto.keypair.Keypair;
import com.alipay.mychain.sdk.crypto.signer.SignerBase;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.message.Response;
import com.alipay.mychain.sdk.message.transaction.TransactionReceiptResponse;
import com.alipay.mychain.sdk.message.transaction.contract.CallContractRequest;
import com.alipay.mychain.sdk.message.transaction.contract.DeployContractRequest;
import com.alipay.mychain.sdk.utils.ByteUtils;
import com.alipay.mychain.sdk.utils.IOUtil;
import com.alipay.mychain.sdk.vm.WASMOutput;
import com.alipay.mychain.sdk.vm.WASMParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基础存证示例
 */
public class ContractDepositDemo {

    /**
     * 合约字节码文件名
     */
    private static final String CONTRACT_FILENAME = "contract/deposit.wasc";

    /**
     * 配置
     */
    private final ChainConfig config;

    /**
     * 用户账号地址
     */
    private final Identity userIdentity;

    /**
     * 部署的合约地址
     */
    private final Identity contractIdentity;

    /**
     * 蚂蚁链sdk
     */
    private MychainClient sdk;

    public static void main(String[] args) throws Exception {

        ContractDepositDemo demo = new ContractDepositDemo(new ChainConfig());

        // step 1: 创建SDK客户端，建立链接
        System.out.println("Step 1, Initializing SDK...");
        demo.initSdk();

        try {

            // step 2: 部署合约 & 初始化合约
            demo.deployContract();
            demo.initContract();

            // Step 3: 数据存证
            String message = "Hello world!";
            System.out.println("Step 2, Saving message...");
            // 存证消息，返回交易哈希
            byte[] txHash = demo.deposit(message);
            // 查询合约中存证记录总数
            BigInteger depositCount = demo.getDepositCount();
            System.out.printf("Total deposit count: %s.%n", depositCount.toString());

            // Step 4: 链上查证
            String evidence = demo.query(txHash);
            System.out.printf(
                    "message on chain: %s, %s.%n",
                    evidence,
                    message.equals(evidence) ? "查证一致" : "查证不一致");
        } finally {
            // step 4: 关闭客户端
            System.out.println("Step 4, Shutting down SDK...");
            demo.shutdown();
        }

    }

    public ContractDepositDemo(ChainConfig config) {
        this.config = config;
        /* 账户地址 */
        this.userIdentity = new Identity(hash(config.getAccount()));
        /* 合约名称随机选取 */
        String contractName = "wasm-contract" + System.currentTimeMillis();
        /* 合约地址　*/
        this.contractIdentity = new Identity(hash(contractName));
    }

    /**
     * 初始化SDK客户端
     *
     * @throws IOException
     */
    public void initSdk() throws IOException {
        ClientEnv env = initMychainEnv();
        sdk = new MychainClient();
        boolean initResult = sdk.init(env);
        if (!initResult) {
            exit("initSdk", "sdk init failed.");
        }
    }

    public ClientEnv initMychainEnv() throws IOException {
        /* 区块链节点地址 */
        InetSocketAddress inetSocketAddress = InetSocketAddress.createUnresolved(
                config.getMychainIP(),
                config.getMychianPort());
        List<InetSocketAddress> socketAddressArrayList = new ArrayList<>();
        socketAddressArrayList.add(inetSocketAddress);

        /* SSL配置 */
        InputStream skStream = ContractDepositDemo.class.getClassLoader().getResourceAsStream(config.getTlsSecretKeyFilename());
        Objects.requireNonNull(skStream, "没找到TLS私钥文件: src/resources/" + config.getTlsSecretKeyFilename());
        InputStream certStream = ContractDepositDemo.class.getClassLoader().getResourceAsStream(config.getTlsCertificateFilename());
        Objects.requireNonNull(certStream, "没找到TLS证书文件: src/resources/" + config.getTlsCertificateFilename());
        InputStream caStream = ContractDepositDemo.class.getClassLoader().getResourceAsStream(config.getTlsCaFilename());
        Objects.requireNonNull(caStream, "没找到CA文件: src/resources/" + config.getTlsCaFilename());
        ISslOption sslOption = new SslBytesOption.Builder()
                .keyBytes(IOUtil.inputStreamToByte(skStream))
                .certBytes(IOUtil.inputStreamToByte(Objects.requireNonNull(certStream)))
                .keyPassword(config.getTlsSecretKeyPassword())
                .trustStorePassword(config.getTrustStorePassword())
                .trustStoreBytes(IOUtil.inputStreamToByte(caStream))
                .build();

        /* 账户签名配置 */
        Pkcs8KeyOperator pkcs8KeyOperator = new Pkcs8KeyOperator();
        InputStream accountSkStream = ContractDepositDemo.class.getClassLoader().getResourceAsStream(config.getAccountSecretKeyFilename());
        Objects.requireNonNull(accountSkStream, "没找到账号私钥文件: src/resources/" + config.getAccountSecretKeyFilename());
        Keypair userKeypair = pkcs8KeyOperator.load(
                IOUtil.inputStreamToByte(accountSkStream),
                config.getAccountSecretKeyPassword());
        List<SignerBase> signerBaseList = new ArrayList<>();
        SignerBase signerBase = MyCrypto.getInstance().createSigner(userKeypair);
        System.out.println("signerBase:" + signerBase.getAlgo().name());
        signerBaseList.add(signerBase);
        SignerOption signerOption = new SignerOption();
        signerOption.setSigners(signerBaseList);

        ClientEnv env = ClientEnv.build(socketAddressArrayList, sslOption, signerOption);
        env.setLogger(AbstractLoggerFactory.getInstance(ContractDepositDemo.class));

        /* 摘要算法配置*/
        DigestOption digestOption = new DigestOption();
        if (config.isTeeChain() || config.isSM()) {
            digestOption.setDefaultDigestType(HashTypeEnum.SM3);
        } else {
            digestOption.setDefaultDigestType(HashTypeEnum.SHA256);
        }
        env.setDigestOption(digestOption);

        return env;
    }


    /**
     * 部署合约
     *
     * @throws InterruptedException
     */
    public void deployContract() throws InterruptedException {
        WASMParameter contractParameters = new WASMParameter();
        String path = Objects.requireNonNull(getClass().getClassLoader().getResource(CONTRACT_FILENAME)).getPath();
        byte[] contractCode = IOUtil.readFileToByteArray(path);

        // 构建交易请求
        DeployContractRequest request = new DeployContractRequest(
                userIdentity,           // 发起交易的账户地址
                contractIdentity,       // 部署的合约目标地址
                contractCode,           // 合约字节码
                VMTypeEnum.WASM,        // 合约类型
                contractParameters,     // 合约构造函数参数
                BigInteger.ZERO         // 合约交易金额, 不涉及
        );

        // 发送部署合约交易
        TransactionReceiptResponse deployContractResult = sdk.getContractService().deployContract(request);
        System.out.println("[部署合约] API结果:" + getOutput(deployContractResult));

        if (!deployContractResult.isSuccess()
                || deployContractResult.getTransactionReceipt().getResult() != 0) {
            exit("[部署合约] 交易回执结果:",
                    getErrorMsg((int) deployContractResult.getTransactionReceipt().getResult()));
        } else {
            TransactionReceipt transactionReceipt = deployContractResult.getTransactionReceipt();
            System.out.println("[部署合约] 交易回执结果:" + getErrorMsg((int) transactionReceipt.getResult()));
        }
        Thread.sleep(2000);
    }

    /**
     * 初始化合约
     */
    public void initContract() {
        // 构建请求
        WASMParameter parameters = new WASMParameter("Init");
        CallContractRequest request = new CallContractRequest(
                userIdentity,
                contractIdentity,
                parameters,
                BigInteger.ZERO,
                VMTypeEnum.WASM);

        // 执行交易，同步等待回执
        TransactionReceiptResponse callContractResult = sdk.getContractService().callContract(request);
        System.out.println("[初始化合约] API响应:" + getOutput(callContractResult));

        if (!callContractResult.isSuccess() || callContractResult.getTransactionReceipt().getResult() != 0) {
            exit("Init", getErrorMsg((int) callContractResult.getTransactionReceipt().getResult()));
        } else {
            TransactionReceipt transactionReceipt = callContractResult.getTransactionReceipt();
            System.out.println("[初始化合约] 交易回执:" + getOutput(transactionReceipt));
        }
    }

    /**
     * 存证上链
     *
     * @param message 要存证的信息
     * @return 交易哈希
     */
    public byte[] deposit(String message) {
        // 构建请求
        WASMParameter parameters = new WASMParameter("Deposit");
        parameters.addString(message);
        CallContractRequest request = new CallContractRequest(
                userIdentity,
                contractIdentity,
                parameters,
                BigInteger.ZERO,
                VMTypeEnum.WASM);

        // 执行交易，同步等待回执
        TransactionReceiptResponse callContractResult = sdk.getContractService().callContract(request);
        System.out.println("[调用Deposit] API响应:" + getOutput(callContractResult));
        Hash hash = request.getTransaction().getHash();

        if (!callContractResult.isSuccess() || callContractResult.getTransactionReceipt().getResult() != 0) {
            exit("deposit", getErrorMsg((int) callContractResult.getTransactionReceipt().getResult()));
        } else {
            TransactionReceipt transactionReceipt = callContractResult.getTransactionReceipt();
            System.out.println("[调用Deposit] 交易回执:" + getOutput(transactionReceipt));
        }

        return hash.getValue();
    }


    /**
     * 查询存证数量
     *
     * @return 交易哈希
     */
    public BigInteger getDepositCount() {
        // 构建请求
        WASMParameter parameters = new WASMParameter("GetCount");
        CallContractRequest request = new CallContractRequest(
                userIdentity,
                contractIdentity,
                parameters,
                BigInteger.ZERO,
                VMTypeEnum.WASM);
        request.setLocal();

        // 执行交易，同步等待回执
        TransactionReceiptResponse callContractResult = sdk.getContractService().callContract(request);
        System.out.println("[调用GetCount] API响应:" + getOutput(callContractResult));

        if (!callContractResult.isSuccess() || callContractResult.getTransactionReceipt().getResult() != 0) {
            exit("GetCount", getErrorMsg((int) callContractResult.getTransactionReceipt().getResult()));
        } else {
            TransactionReceipt transactionReceipt = callContractResult.getTransactionReceipt();
            System.out.println("[调用GetCount] 交易回执:" + getOutput(transactionReceipt));
        }
        byte[] output = callContractResult.getTransactionReceipt().getOutput();
        return new WASMOutput(ByteUtils.toHexString(output)).getUint64();
    }

    /**
     * 链上查证
     *
     * @param txHash 交易哈希
     */
    private String query(byte[] txHash) {
        // 构建请求
        WASMParameter parameters = new WASMParameter("QueryMessage");
        parameters.addBytes(txHash);
        CallContractRequest request = new CallContractRequest(
                userIdentity,
                contractIdentity,
                parameters,
                BigInteger.ZERO,
                VMTypeEnum.WASM);
        request.setLocal();

        // 执行交易，同步等待回执
        TransactionReceiptResponse callContractResult = sdk.getContractService().callContract(request);
        System.out.println("[调用QueryMessage] API响应:" + getOutput(callContractResult));

        if (!callContractResult.isSuccess() || callContractResult.getTransactionReceipt().getResult() != 0) {
            exit("QueryMessage", getErrorMsg((int) callContractResult.getTransactionReceipt().getResult()));
        } else {
            TransactionReceipt transactionReceipt = callContractResult.getTransactionReceipt();
            System.out.println("[调用QueryMessage] 交易回执:" + getOutput(transactionReceipt));
        }

        byte[] txOutput = callContractResult.getTransactionReceipt().getOutput();
        return new WASMOutput(ByteUtils.toHexString(txOutput)).getString();
    }

    /**
     * 关闭客户端
     */
    public void shutdown() {
        if (this.sdk != null) {
            sdk.shutDown();
        }
    }

    private static String getOutput(TransactionReceipt response) {
        JSONObject jsonObject = new JSONObject();
        response.toJson(jsonObject);
        return JSON.toJSONString(jsonObject, true);
    }

    private static String getOutput(Response response) {
        JSONObject jsonObject = new JSONObject();
        response.toJson(jsonObject);
        return JSON.toJSONString(jsonObject, true);
    }

    private static void exit(String tag, String msg) {
        exit(String.format("%s error : %s ", tag, msg));
    }

    private static void exit(String msg) {
        System.out.println(msg);
        System.exit(0);
    }

    private static String getErrorMsg(int errorCode) {
        return ErrorCode.valueOf(errorCode).getErrorDesc();
    }

    @Nonnull
    private byte[] hash(byte[] data) {
        IHash hashFunction;
        // 是否使用国密算法
        if (config.isSM()) {
            hashFunction = HashFactory.getHash(HashTypeEnum.SM3);
        } else {
            hashFunction = HashFactory.getHash(HashTypeEnum.SHA256);
        }
        return hashFunction.hash(data);
    }

    @Nonnull
    private byte[] hash(String data) {
        return hash(data.getBytes(StandardCharsets.UTF_8));
    }

}