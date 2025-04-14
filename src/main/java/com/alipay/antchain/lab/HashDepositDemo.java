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
import com.alipay.mychain.sdk.message.query.QueryTransactionResponse;
import com.alipay.mychain.sdk.message.transaction.account.DepositDataRequest;
import com.alipay.mychain.sdk.message.transaction.account.DepositDataResponse;
import com.alipay.mychain.sdk.utils.IOUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 哈希存证示例
 */
public class HashDepositDemo {

    /**
     * 配置
     */
    private final ChainConfig config;

    /**
     * 用户账号地址
     */
    private final Identity userIdentity;

    /**
     * 蚂蚁链sdk
     */
    private MychainClient sdk;

    public static void main(String[] args) throws Exception {

        HashDepositDemo demo = new HashDepositDemo(new ChainConfig());

        // step 1: 创建SDK客户端，建立链接
        System.out.println("Step 1, Initializing SDK...");
        demo.initSdk();
        String filename = "img.png";
        try {

            // step 2: 数据存证
            System.out.println("Step 2, Saving...");
            String txHash = demo.deposit(filename);

            // step 3: 链上查证
            System.out.println("Step 3, Verifying...");
            boolean result = demo.verify(filename, txHash);
            System.out.printf(
                    "result: %s%n",
                    result ? "查证一致" : "查证不一致");
        } finally {
            // step 4: 关闭客户端
            System.out.println("Step 4, Shutting down SDK...");
            demo.shutdown();
        }
    }

    public HashDepositDemo(ChainConfig config) {
        this.config = config;
        /* 账户地址 */
        this.userIdentity = new Identity(hash(config.getAccount()));
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
        List<InetSocketAddress> socketAddressArrayList = new ArrayList<InetSocketAddress>();
        socketAddressArrayList.add(inetSocketAddress);

        /* SSL配置 */
        InputStream skStream = HashDepositDemo.class.getClassLoader().getResourceAsStream(config.getTlsSecretKeyFilename());
        Objects.requireNonNull(skStream, "没找到TLS私钥文件: src/resources/" + config.getTlsSecretKeyFilename());
        InputStream certStream = HashDepositDemo.class.getClassLoader().getResourceAsStream(config.getTlsCertificateFilename());
        Objects.requireNonNull(certStream, "没找到TLS证书文件: src/resources/" + config.getTlsCertificateFilename());
        InputStream caStream = HashDepositDemo.class.getClassLoader().getResourceAsStream(config.getTlsCaFilename());
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
        InputStream accountSkStream = HashDepositDemo.class.getClassLoader().getResourceAsStream(config.getAccountSecretKeyFilename());
        Objects.requireNonNull(accountSkStream, "没找到账号私钥文件: src/resources/" + config.getAccountSecretKeyFilename());
        Keypair userKeypair = pkcs8KeyOperator.load(
                IOUtil.inputStreamToByte(accountSkStream),
                config.getAccountSecretKeyPassword());
        List<SignerBase> signerBaseList = new ArrayList<SignerBase>();
        SignerBase signerBase = MyCrypto.getInstance().createSigner(userKeypair);
        System.out.println("signerBase:" + signerBase.getAlgo().name());
        signerBaseList.add(signerBase);
        SignerOption signerOption = new SignerOption();
        signerOption.setSigners(signerBaseList);

        ClientEnv env = ClientEnv.build(socketAddressArrayList, sslOption, signerOption);
        env.setLogger(AbstractLoggerFactory.getInstance(HashDepositDemo.class));

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
     * 存证上链
     *
     * @param filename 要存证的文件
     * @return 交易哈希
     */
    public String deposit(String filename) throws IOException {
        String fileContentHash = getFileHash(filename);

        // 文件内容hash上链
        FileHashObject fileHashObject = new FileHashObject(filename, fileContentHash);
        DepositDataRequest request = new DepositDataRequest(
                userIdentity,                                                               // 交易发起者
                userIdentity,                                                               // 交易接受者
                fileHashObject.toJsonString().getBytes(StandardCharsets.UTF_8),                // 存证内容
                BigInteger.ZERO                                                             // 链原生资产
        );
        DepositDataResponse response = sdk.getAccountService().depositData(request);
        if (!response.isSuccess() || response.getTransactionReceipt().getResult() != 0) {
            exit("depositData", getErrorMsg((int) response.getTransactionReceipt().getResult()));
        }

        System.out.println("depositData Done.");
        System.out.println("transaction hash: " + response.getTxHash());
        System.out.println("transaction receipt: " + getOutput(response.getTransactionReceipt()));
        return response.getTxHash().hexStrValue();
    }

    /**
     * 链上哈希查证
     * @param filename 存证的文件名
     * @param txHash 存证交易哈希
     * @return 是否一致
     */
    public boolean verify(String filename, String txHash) throws IOException {
        // 获取文件内容
        String fileContentHash = getFileHash(filename);

        // 查询链上存证
        byte[] evidenceData = query(txHash);
        FileHashObject fileHashObject = JSONObject.parseObject(evidenceData, FileHashObject.class);

        // 验证哈希一致
        return fileContentHash.equals(fileHashObject.getFileContentHash());
    }


    /**
     * 查询上链交易
     * @param txHash 交易哈希
     */
    @Nonnull
    private byte[] query(String txHash) {
        Objects.requireNonNull(txHash, "参数txHash不能为空");

        // 查询交易
        QueryTransactionResponse queryTransactionResponse = sdk.getQueryService().queryTransaction(new Hash(txHash));

        // 返回值检查
        if (!queryTransactionResponse.isSuccess()) {
            exit("queryTransaction", queryTransactionResponse.getErrorCode().getErrorDesc());
        }

        // 获取存证信息
        System.out.println("transaction: " + JSON.toJSONString(queryTransactionResponse.getTransaction(), true));
        return queryTransactionResponse.getTransaction().getData();
    }


    /**
     * 计算文件哈希值
     * @param filename 存证的文件名
     * @return 文件哈希值
     * @throws IOException
     */
    private String getFileHash(String filename) throws IOException {
        // 获取文件内容
        InputStream fileInStream = HashDepositDemo.class.getClassLoader().getResourceAsStream(filename);
        Objects.requireNonNull(fileInStream, "要存证的文件没有找到: src/main/resources/" + filename);
        byte[] fileContent = IOUtil.inputStreamToByte(fileInStream);

        // 计算文件内容的哈希值
        String fileContentHash = new String(hash(fileContent));
        return fileContentHash;
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


    public static class FileHashObject {
        /**
         * 存证的原始文件路径和文件名
         */
        private String fileName;
        /**
         * 存证的时间
         */
        private LocalDateTime createTime;
        /**
         * 存证的文件内容哈希
         */
        private String fileContentHash;

        public FileHashObject() {};

        public FileHashObject(String fileName, String fileContentHash) {
            this.fileName = fileName;
            this.fileContentHash = fileContentHash;
            this.createTime = LocalDateTime.now();
        }

        public String toJsonString() {
            return JSON.toJSONString(this);
        }

        public String getFileName(){
            return this.fileName;
        }

        public String getFileContentHash(){
            return this.fileContentHash;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public void setFileContentHash(String fileContentHash) {
            this.fileContentHash = fileContentHash;
        }
    }


}