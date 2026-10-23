package com.kimkinam.camerashit

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * 폰 자기 자신의 무선 디버깅(adbd)에 접속하는 클라이언트. 키/인증서는 앱 전용 저장소에 한 번만 만들고
 * 재사용한다. 한 번 페어링하면 이후에는 코드 없이 다시 연결할 수 있다.
 */
class AdbManager private constructor(context: Context) : AbsAdbConnectionManager() {
    private val key: PrivateKey
    private val cert: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val keyFile = File(context.filesDir, "adb_private.key")
        val certFile = File(context.filesDir, "adb_cert.der")
        val loadedKey = if (keyFile.exists() && certFile.exists()) {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        } else {
            null
        }
        if (loadedKey != null) {
            key = loadedKey
            cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            val pair = generator.generateKeyPair()
            key = pair.private
            cert = createCertificate(pair.public, key)
            keyFile.writeBytes(key.encoded)
            certFile.writeBytes(cert.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = key

    override fun getCertificate(): Certificate = cert

    override fun getDeviceName(): String = "CameraShit"

    private fun createCertificate(publicKey: java.security.PublicKey, privateKey: PrivateKey): Certificate {
        val algorithm = "SHA512withRSA"
        val notBefore = Date()
        val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000)
        val extensions = CertificateExtensions()
        extensions.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier)
        )
        extensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
        val name = X500Name("CN=CameraShit")
        val info = X509CertInfo()
        info.set("version", CertificateVersion(2))
        info.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
        info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
        info.set("subject", CertificateSubjectName(name))
        info.set("key", CertificateX509Key(publicKey))
        info.set("validity", CertificateValidity(notBefore, notAfter))
        info.set("issuer", CertificateIssuerName(name))
        info.set("extensions", extensions)
        val certificate = X509CertImpl(info)
        certificate.sign(privateKey, algorithm)
        return certificate
    }

    companion object {
        @Volatile
        private var instance: AdbManager? = null

        /** 이미 만들어진 인스턴스만 돌려준다(없으면 null). 키 생성 같은 무거운 작업을 피해야 할 때 쓴다. */
        fun peek(): AdbManager? = instance

        fun get(context: Context): AdbManager =
            instance ?: synchronized(this) {
                instance ?: AdbManager(context.applicationContext).also { instance = it }
            }
    }
}
