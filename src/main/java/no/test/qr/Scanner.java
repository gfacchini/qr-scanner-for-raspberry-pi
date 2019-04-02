package no.test.qr;


import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;


public class Scanner {

    private Logger logger = LoggerFactory.getLogger(Scanner.class);
    private static final String FILE_NAME = "/tmp/scan.png";
    private String BACKEND_ADDRESS;
    private String READER_STATUS;
    private Properties properties;

    private List<String> alreadyRead;

    private static final int CONNECTION_TIMEOUT_MS = 10 * 1000; // Timeout in millis.
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
            .setConnectTimeout(CONNECTION_TIMEOUT_MS)
            .setSocketTimeout(CONNECTION_TIMEOUT_MS)
            .build();

    private static final HttpClient httpclient = HttpClients.createDefault();

    private final QRCodeReader reader;


    public Scanner() {
        this.reader = new QRCodeReader();
        this.properties = new Properties();
        BufferedInputStream stream = null;
        try {
            stream = new BufferedInputStream(new FileInputStream("qr-scan.properties"));
            properties.load(stream);
            stream.close();
            this.BACKEND_ADDRESS = properties.getProperty("backend_address");
            this.READER_STATUS = properties.getProperty("reader_status");

            this.alreadyRead = new ArrayList<String>();

        } catch (FileNotFoundException e) {
            logger.error("Properties file not found 'qr-scan.properties'", e);
        } catch (IOException e) {
            logger.error("Could not load properties", e);
        }


    }

    public static void main(String[] args) {
        while (true) {
            new Scanner().scan();
        }
    }

    public String scan() {
        String result = "";
        Statement stmt;
        Connection c = null;
        try {
            result = reader
                    .decode(acquireBitmapFromCamera())
                    .getText();
            logger.info("Scan Decode is successful: " + result);
            if (!alreadyRead.contains(result)) {
                this.alreadyRead.add(result);
                sendResultToBackend(result);
            }
        } catch (NotFoundException e) {
            //logger.error("QR Code was not found in the image. It might have been partially detected but could not be confirmed.");
        } catch (ChecksumException e) {
            logger.error("QR Code was successfully detected and decoded, but was not returned because its checksum feature failed.");
        } catch (FormatException e) {
            logger.error("QR Code was successfully detected, but some aspect of the content did not conform to the barcode's format rules. This could have been due to a mis-detection.");
        } catch (InterruptedException e) {
            logger.error("Error acquiring bitmap", e);
        } catch (IOException e) {
            logger.error("I/O error : {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unknown Error : {}", e);
        }

        return result;
    }

    public void sendResultToBackend(String result) {
        try {

            List<NameValuePair> params = new ArrayList<NameValuePair>(2);
            params.add(new BasicNameValuePair("qr", result));
            params.add(new BasicNameValuePair("timestamp", new Date().toString()));

            HttpPost httpPost = new HttpPost("http://" + BACKEND_ADDRESS + "/" + READER_STATUS);
            httpPost.setConfig(REQUEST_CONFIG);
            httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

            HttpResponse response = httpclient.execute(httpPost);

            HttpEntity entity = response.getEntity();

            if (entity != null) {
                System.out.println("HTTP POST status : " + response.getStatusLine().getStatusCode());
                entity.getContent().close();

            }

        } catch (UnsupportedEncodingException e) {
            logger.error("Could not encode Backend URL : {}", e);
        } catch (ClientProtocolException e) {
            logger.error("Could not execute HTTP request : {}", e);
        } catch (IOException e) {
            logger.error("Could send result to Backend : {}", e);
        }
    }

    private BinaryBitmap acquireBitmapFromCamera() throws InterruptedException, IOException {

        getImageFromCamera();

        File imageFile = new File(FILE_NAME);

        logger.trace("Reading file:" + FILE_NAME + " for  QR code");
        BufferedImage image = ImageIO.read(imageFile);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        RGBLuminanceSource source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
        return new BinaryBitmap(new HybridBinarizer(source));
    }

    private void getImageFromCamera() throws IOException, InterruptedException {
        String cmd = "raspistill --timeout 5 --output " + FILE_NAME + " --width 400 --height 300 --nopreview";
        logger.trace("Executing: " + cmd);
        Process process = Runtime.getRuntime().exec(cmd);

        int code = process.waitFor();
        logger.trace("Exit code: " + code);
    }


}
