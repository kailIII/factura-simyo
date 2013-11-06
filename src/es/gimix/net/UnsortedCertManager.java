package es.gimix.net;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

//http://stackoverflow.com/questions/4115101/apache-httpclient-on-android-producing-certpathvalidatorexception-issuername/4199518#4199518
public class UnsortedCertManager {
    public static void install() throws NoSuchAlgorithmException, KeyStoreException {
    	KeyStore ks = null;
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());  
        factory.init(ks);
        TrustManager[] trustmanagers = factory.getTrustManagers();  
        if (trustmanagers.length == 0) 
            throw new NoSuchAlgorithmException("no trust manager found");  
        final X509TrustManager standardTrustManager = (X509TrustManager) trustmanagers[0];  

        // Create a trust manager that does not validate certificate chains
        TrustManager[] manager = new TrustManager[] { new X509TrustManager() {
            /** 
             * @see javax.net.ssl.X509TrustManager#checkClientTrusted(X509Certificate[],String authType) 
             */  
            public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException 
            {  
                standardTrustManager.checkClientTrusted(certificates, authType);  
            }  

            /** 
             * @see javax.net.ssl.X509TrustManager#checkServerTrusted(X509Certificate[],String authType) 
             */  
            public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException 
            {  
                // Clean up the certificates chain and build a new one.
                // Theoretically, we shouldn't have to do this, but various web servers
                // in practice are mis-configured to have out-of-order certificates or
                // expired self-issued root certificate.
                int chainLength = certificates.length;
                if (certificates.length > 1) 
                {
                    // 1. we clean the received certificates chain.
                    // We start from the end-entity certificate, tracing down by matching
                    // the "issuer" field and "subject" field until we can't continue.
                    // This helps when the certificates are out of order or
                    // some certificates are not related to the site.
                    int currIndex;
                    for (currIndex = 0; currIndex < certificates.length; ++currIndex) 
                    {
                        boolean foundNext = false;
                        for (int nextIndex = currIndex + 1;
                                nextIndex < certificates.length;
                                ++nextIndex) 
                        {
                            if (certificates[currIndex].getIssuerDN().equals(
                                        certificates[nextIndex].getSubjectDN())) 
                            {
                                foundNext = true;
                                // Exchange certificates so that 0 through currIndex + 1 are in proper order
                                if (nextIndex != currIndex + 1) 
                                {
                                    X509Certificate tempCertificate = certificates[nextIndex];
                                    certificates[nextIndex] = certificates[currIndex + 1];
                                    certificates[currIndex + 1] = tempCertificate;
                                }
                                break;
                            }
                        }
                        if (!foundNext) break;
                    }

                    // 2. we exam if the last traced certificate is self issued and it is expired.
                    // If so, we drop it and pass the rest to checkServerTrusted(), hoping we might
                    // have a similar but unexpired trusted root.
                    chainLength = currIndex + 1;
                    X509Certificate lastCertificate = certificates[chainLength - 1];
                    Date now = new Date();
                    if (lastCertificate.getSubjectDN().equals(lastCertificate.getIssuerDN())
                            && now.after(lastCertificate.getNotAfter())) 
                    {
                        --chainLength;
                    }
                } 

                standardTrustManager.checkServerTrusted(certificates, authType);    
            }  
            public X509Certificate[] getAcceptedIssuers() {  
                return standardTrustManager.getAcceptedIssuers();  
            }
        } };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, manager, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}