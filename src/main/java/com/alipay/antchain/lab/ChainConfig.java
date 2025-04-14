/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2022 All Rights Reserved.
 */
package com.alipay.antchain.lab;

public class ChainConfig {

    // -----------------
    /* 区块链账号配置 */
    // -----------------

    /**
     * 链账户名
     */
    private String account = "james1017";
    /**
     * 链账户私钥文件
     */
    private String accountSecretKeyFilename = "account/user.key";
    /**
     * 链账户私钥密码
     */
    private String accountSecretKeyPassword = "Firedra414_";

    // -----------------
    /* 区块链节点连接配置 */
    // -----------------

    /**
     * 链节点IP
     */
    private String mychainIP = "106.14.91.216";
    /**
     * 链节点端口
     */
    private int mychianPort = 18130;
    /**
     * TLS证书私钥文件
     */
    private String tlsSecretKeyFilename = "mychain/client.key";
    /**
     * TLS证书私钥密码
     */
    private String tlsSecretKeyPassword = "Firedra414_";
    /**
     * TLS证书文件
     */
    private String tlsCertificateFilename = "mychain/client.crt";
    /**
     * TLS证书CA
     */
    private String tlsCaFilename = "mychain/trustCa";

    /**
     * trustCa password.
     */
    private String trustStorePassword = "mychain";

    // -----------------
    // 高级功能配置
    // -----------------

    /**
     * 是否开启隐私保护
     */
    private boolean isTeeChain = false;
    /**
     * 是否使用国家密码规范
     */
    private boolean isSM = false;



    public String getAccount() {
        return account;
    }

    public String getAccountSecretKeyFilename() {
        return accountSecretKeyFilename;
    }

    public String getAccountSecretKeyPassword() {
        return accountSecretKeyPassword;
    }

    public String getMychainIP() {
        return mychainIP;
    }

    public int getMychianPort() {
        return mychianPort;
    }

    public String getTlsSecretKeyFilename() {
        return tlsSecretKeyFilename;
    }

    public String getTlsSecretKeyPassword() {
        return tlsSecretKeyPassword;
    }

    public String getTlsCertificateFilename() {
        return tlsCertificateFilename;
    }

    public String getTlsCaFilename() {
        return tlsCaFilename;
    }

    public boolean isTeeChain() {
        return isTeeChain;
    }

    public boolean isSM() {
        return isSM;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }
}
