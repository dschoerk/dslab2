to update the java security provider for bouncy castle add the following line to java.security
(start the text editor with admin privilegs)
file location: jre/lib/security/java.security

security.provider.1=org.bouncycastle.jce.provider.BouncyCastleProvider

