/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2021 All Rights Reserved.
 */
package com.alipay.antchain.lab;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.mychain.sdk.api.MychainClient;
import com.alipay.mychain.sdk.api.env.ClientEnv;
import com.alipay.mychain.sdk.api.env.ISslOption;
import com.alipay.mychain.sdk.api.env.SignerOption;
import com.alipay.mychain.sdk.api.env.SslBytesOption;
import com.alipay.mychain.sdk.message.Response;
import com.alipay.mychain.sdk.message.query.QueryLastBlockHeaderResponse;
import com.alipay.mychain.sdk.utils.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 蚂蚁链连接测试
 */
public class ConnectionTest {
    /**
     * 配置
     */
    private final ChainConfig config;

    /**
     * 蚂蚁链sdk
     */
    private MychainClient sdk;

    public static void main(String[] args) throws Exception {

        ConnectionTest demo = new ConnectionTest(new ChainConfig());

        // step 1: 创建SDK客户端，建立链接
        System.out.println("Step 1, Initializing SDK...");
        demo.initSdk();

        // step 2: 查询最新区块头
        System.out.println("Step 2, Querying the latest block head...");
        demo.queryLatestBlock();

        // step 3: 关闭客户端
        System.out.println("Step 3, Shutting down SDK...");
        demo.shutdown();
    }

    public ConnectionTest(ChainConfig config) {
        this.config = config;
    }

    /**
     * 初始化SDK客户端
     * @throws IOException
     */
    public void initSdk() throws IOException {
        InetSocketAddress inetSocketAddress = InetSocketAddress.createUnresolved(
                config.getMychainIP(),
                config.getMychianPort());

        InputStream skStream = ConnectionTest.class.getClassLoader().getResourceAsStream(config.getTlsSecretKeyFilename());
        Objects.requireNonNull(skStream, "没找到TLS私钥文件: src/resources/" + config.getTlsSecretKeyFilename());

        InputStream certStream = ConnectionTest.class.getClassLoader().getResourceAsStream(config.getTlsCertificateFilename());
        Objects.requireNonNull(certStream, "没找到TLS证书文件: src/resources/" + config.getTlsCertificateFilename());

        InputStream caStream = ConnectionTest.class.getClassLoader().getResourceAsStream(config.getTlsCaFilename());
        Objects.requireNonNull(caStream, "没找到CA文件: src/resources/" + config.getTlsCaFilename());

        ISslOption sslOption = new SslBytesOption.Builder()
                .keyBytes(IOUtil.inputStreamToByte(skStream))
                .certBytes(IOUtil.inputStreamToByte(Objects.requireNonNull(certStream)))
                .keyPassword(config.getTlsSecretKeyPassword())
                .trustStorePassword(config.getTrustStorePassword())
                .trustStoreBytes(IOUtil.inputStreamToByte(caStream))
                .build();
        List<InetSocketAddress> socketAddressArrayList = new ArrayList<>();
        socketAddressArrayList.add(inetSocketAddress);

        SignerOption signerOption = new SignerOption();

        ClientEnv env = ClientEnv.build(socketAddressArrayList, sslOption, signerOption);

        sdk = new MychainClient();
        boolean initResult = sdk.init(env);
        if (!initResult) {
            throw new RuntimeException("SDK initialization failed.");
        }
        System.out.println("SDK initialization OK.");
    }

    /**
     * 关闭SDK客户端
     */
    public void shutdown() {
        if (this.sdk != null) {
            sdk.shutDown();
        }
    }

    /**
     * 查询最新区块
     */
    public void queryLatestBlock() {
        QueryLastBlockHeaderResponse response = sdk.getQueryService().queryLastBlockHeader();
        System.out.printf("Latest Block: %s\n", getOutput(response));
    }
    private static String getOutput(Response response) {
        JSONObject jsonObject = new JSONObject();
        response.toJson(jsonObject);
        return JSON.toJSONString(jsonObject, true);
    }

}
