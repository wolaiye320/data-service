package cn.dtkeys.dataservice.core.security;

public interface CredentialCodec {

    /**
     * 加密明文凭据。
     */
    String encrypt(String plainText);

    /**
     * 解密已加密凭据。
     */
    String decrypt(String cipherText);
}
