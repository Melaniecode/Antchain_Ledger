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
import com.alipay.mychain.sdk.message.query.QueryRelatedTransactionListResponse;
import com.alipay.mychain.sdk.message.query.QueryRelatedTransactionListSizeResponse;
import com.alipay.mychain.sdk.message.query.QueryTransactionResponse;
import com.alipay.mychain.sdk.message.transaction.account.RelatedDepositDataRequest;
import com.alipay.mychain.sdk.message.transaction.account.RelatedDepositDataResponse;
import com.alipay.mychain.sdk.utils.IOUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 关联存证示例
 */
public class RelatedDepositDemo {

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

        RelatedDepositDemo demo = new RelatedDepositDemo(new ChainConfig());

        // step 1: 创建SDK客户端，建立链接
        System.out.println("Step 1, Initializing SDK...");
        demo.initSdk();

        try {
            // step 2: 数据存证
            Long productId = System.currentTimeMillis();
            String message1 = "下单";
            String message2 = "出库";
            String message3 = "到达";
            String message4 = "确认收货";
            System.out.println("Step 2, Saving message...");
            demo.relatedDeposit(message1, productId);
            demo.relatedDeposit(message2, productId);
            demo.relatedDeposit(message3, productId);
            demo.relatedDeposit(message4, productId);

            // step 3: 链上查证
            System.out.println("Step 3, Querying message...");
            List<String> messages = demo.relatedQuery(productId);
            System.out.println("Messages: " + JSON.toJSONString(messages, true));
        } finally {
            // step 4: 关闭客户端
            System.out.println("Step 4, Shutting down SDK...");
            demo.shutdown();
        }

    }

    public RelatedDepositDemo(ChainConfig config) {
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
        List<InetSocketAddress> socketAddressArrayList = new ArrayList<>();
        socketAddressArrayList.add(inetSocketAddress);

        /* SSL配置 */
        InputStream skStream = getClass().getClassLoader().getResourceAsStream(config.getTlsSecretKeyFilename());
        Objects.requireNonNull(skStream, "没找到TLS私钥文件: src/resources/" + config.getTlsSecretKeyFilename());
        InputStream certStream = getClass().getClassLoader().getResourceAsStream(config.getTlsCertificateFilename());
        Objects.requireNonNull(certStream, "没找到TLS证书文件: src/resources/" + config.getTlsCertificateFilename());
        InputStream caStream = getClass().getClassLoader().getResourceAsStream(config.getTlsCaFilename());
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
        InputStream accountSkStream = RelatedDepositDemo.class.getClassLoader().getResourceAsStream(config.getAccountSecretKeyFilename());
        Objects.requireNonNull(accountSkStream, "没找到账号私钥文件: src/resources/" + config.getAccountSecretKeyFilename());
        Keypair userKeypair = pkcs8KeyOperator.load(
                IOUtil.inputStreamToByte(accountSkStream),
                config.getAccountSecretKeyPassword());
        List<SignerBase> signerBaseList = new ArrayList<>();
        SignerBase signerBase = MyCrypto.getInstance().createSigner(userKeypair);
        signerBaseList.add(signerBase);
        SignerOption signerOption = new SignerOption();
        signerOption.setSigners(signerBaseList);

        ClientEnv env = ClientEnv.build(socketAddressArrayList, sslOption, signerOption);
        env.setLogger(AbstractLoggerFactory.getInstance(RelatedDepositDemo.class));

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
     * @param message 要存证的信息
     * @return 交易哈希
     */
    private String relatedDeposit(String message, Long flag) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024 * 1024) {
            exit("depositData", "data size more than 1Mb.");
        }

        // 构建存证交易
        RelatedDepositDataRequest request = new RelatedDepositDataRequest(
                userIdentity,                               // 交易发起者
                userIdentity,                               // 交易接受者
                bytes,                                      // 存证内容
                flag,                                       // 关联标识
                BigInteger.ZERO                             // 链原生资产
        );
        RelatedDepositDataResponse response = sdk.getAccountService().relatedDepositData(request);
        if (!response.isSuccess() || response.getTransactionReceipt().getResult() != 0) {
            exit("depositData", getErrorMsg((int) response.getTransactionReceipt().getResult()));
        }

        System.out.println("depositData Done.");
        System.out.println("transaction hash: " + response.getTxHash());
        System.out.println("transaction receipt: " + getOutput(response.getTransactionReceipt()));
        return response.getTxHash().hexStrValue();
    }

    /**
     * 链上查证
     *
     * @param flag 关联标识
     */
    public List<String> relatedQuery(Long flag) {

        // 查询记录总数
        QueryRelatedTransactionListSizeResponse sizeResponse = sdk.getQueryService().queryRelatedTransactionListSize(
                userIdentity,               // 存证接受者
                flag                        // 关联标识
        );
        if (!sizeResponse.isSuccess()) {
            exit("queryRelatedTransactionListSize", sizeResponse.getErrorCode().getErrorDesc());
        }
        int pageSize = (int)sizeResponse.getSize();

        // 查询交易
        QueryRelatedTransactionListResponse response = sdk.getQueryService().queryRelatedTransactionList(
                userIdentity,               // 存证接受者
                flag,                       // 关联标识
                0,                          // 起始位置startIndex
                pageSize                    // 最大返回数量pageSize
        );

        // pageSize

        // 返回值检查
        if (!response.isSuccess()) {
            exit("queryTransaction", response.getErrorCode().getErrorDesc());
        }

        List<String> messageList = response.getTxHashes()
                .stream()
                .map(this::query)
                .map(String::new)
                .collect(Collectors.toList());

        return messageList;
    }


    /**
     * 链上查证
     *
     * @param txHash 交易哈希
     */
    @Nonnull
    private byte[] query(Hash txHash) {
        Objects.requireNonNull(txHash);

        // 查询交易
        QueryTransactionResponse queryTransactionResponse = sdk.getQueryService().queryTransaction(txHash);

        // 返回值检查
        if (!queryTransactionResponse.isSuccess()) {
            exit("queryTransaction", queryTransactionResponse.getErrorCode().getErrorDesc());
        }

        // 获取存证信息
        System.out.println("transaction: " + JSON.toJSONString(queryTransactionResponse.getTransaction(), true));
        return queryTransactionResponse.getTransaction().getData();
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

}