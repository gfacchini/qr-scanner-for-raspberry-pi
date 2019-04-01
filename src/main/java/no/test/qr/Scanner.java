package no.test.qr;


import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;


public class Scanner {

    private Logger logger = LoggerFactory.getLogger(Scanner.class);
    private static final String FILE_NAME = "/tmp/scan.png";
    private String DB_ADDRESS;
    private String DB_PASSWORD;
    private String DB_TABLE;
    private String READER_NUMBER;
    private Properties properties;


    private final QRCodeReader reader;


    public Scanner() {
        this.reader = new QRCodeReader();
        this.properties = new Properties();
        BufferedInputStream stream = null;
        try {
            stream = new BufferedInputStream(new FileInputStream("qr-scan.properties"));
            properties.load(stream);
            stream.close();
            this.DB_ADDRESS = properties.getProperty("db_address");
            this.DB_PASSWORD = properties.getProperty("db_password");
            this.DB_TABLE = properties.getProperty("db_table");
            this.READER_NUMBER = properties.getProperty("reader_number");

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
        String result = null;
        Statement stmt;
        Connection c = null;
        try {
            result = reader
                    .decode(acquireBitmapFromCamera())
                    .getText();
            logger.info("Scan Decode is successful: " + result);

            System.out.println(result);


            Calendar calendar = Calendar.getInstance();
            int hours = calendar.get(Calendar.HOUR_OF_DAY);
            int minutes = (calendar.get(Calendar.MINUTE) / 5) * 5;

            Class.forName("org.postgresql.Driver");
            DriverManager.getConnection("jdbc:postgresql://" + DB_ADDRESS + ":5432/postgres",
                    "postgres", DB_PASSWORD);


            stmt = c.createStatement();
            String sql = "INSERT INTO " + DB_TABLE + " (qr, c" + READER_NUMBER + ")" +
                    "VALUES (" + result + "," + hours + ":" + minutes + ")";

            stmt.executeUpdate(sql);
            stmt.close();
            c.close();

        } catch (NotFoundException e) {
            //logger.error("QR Code was not found in the image. It might have been partially detected but could not be confirmed.");
        } catch (ChecksumException e) {
            logger.error("QR Code was successfully detected and decoded, but was not returned because its checksum feature failed.");
        } catch (FormatException e) {
            logger.error("QR Code was successfully detected, but some aspect of the content did not conform to the barcode's format rules. This could have been due to a mis-detection.");
        } catch (InterruptedException e) {
            logger.error("Error acquiring bitmap", e);
        } catch (IOException e) {
            logger.error("I/O error acquiring bitmap: {}", e.getMessage());
        } catch (SQLException e) {
            logger.error("I/O error connection to database", e.getMessage());
        } catch (Exception e) {
            logger.error("Unknown Error", e);
        }

        return result;
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