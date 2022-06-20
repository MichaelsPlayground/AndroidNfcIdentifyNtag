package de.androidcrypto.androidnfcidentifyntag;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.TextView;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    TextView nfcaContent;
    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcaContent = findViewById(R.id.tvNfcaContent);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }
    @Override
    protected void onResume() {
        super.onResume();

        if (mNfcAdapter != null) {
            Bundle options = new Bundle();
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);

            // Enable ReaderMode for all types of card and disable platform sounds
            // the option NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK is NOT set
            // to get the data of the tag afer reading
            mNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableReaderMode(this);
    }

    // This method is run in another thread when a card is discovered
    // !!!! This method cannot cannot direct interact with the UI Thread
    // Use `runOnUiThread` method to change the UI from this method
    @Override
    public void onTagDiscovered(Tag tag) {
        // Read and or write to Tag here to the appropriate Tag Technology type class
        // in this example the card should be an Ndef Technology Type

        // clear the datafields
        //clearEncryptionData();

        System.out.println("NFCA discovered");

        NfcA nfca = null;

        // Whole process is put into a big try-catch trying to catch the transceive's IOException
        try {
            nfca = NfcA.get(tag);
            if (nfca != null) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(200);
            }

            nfca.connect();
            byte[] response;
            String nfcaContentString = "Content of NFCA tag";

            // first get sak
            short sakData = nfca.getSak();
            nfcaContentString = nfcaContentString + "\n" + "read SAK";
            nfcaContentString = nfcaContentString + "\n" + "sakData: " + shortToHex(sakData);

            // then check atqa
            byte[] atqaData = nfca.getAtqa();
            nfcaContentString = nfcaContentString + "\n" + "read ATQA";
            nfcaContentString = nfcaContentString + "\n" + "atqaData: " + bytesToHex(atqaData);

            String ntagType = NfcIdentifyNtag.checkNtagType(nfca, tag.getId()); // get the NTAG-id along with other data
            nfcaContentString = nfcaContentString + "\n" + "NTAG TYPE: " + ntagType;
            if (!ntagType.equals("0")) {
                nfcaContentString = nfcaContentString + "\n" + "complete NTAG TYPE: " + NfcIdentifyNtag.getIdentifiedNtagType();
                nfcaContentString = nfcaContentString + "\n" + "NTAG pages: " + NfcIdentifyNtag.getIdentifiedNtagPages();
                nfcaContentString = nfcaContentString + "\n" + "NTAG memory bytes: " + NfcIdentifyNtag.getIdentifiedNtagMemoryBytes();
                nfcaContentString = nfcaContentString + "\n" + "NTAG ID: " + bytesToHex(NfcIdentifyNtag.getIdentifiedNtagId());
            }

            String[] techList = tag.getTechList();
            for (int i = 0; i < techList.length; i++) {
                nfcaContentString = nfcaContentString + "\n" + "techlist: " + techList[i];
            }

            String finalNfcaContentString = nfcaContentString;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //UI related things, not important for NFC
                    nfcaContent.setText(finalNfcaContentString);
                }
            });

            try {
                nfca.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            //Trying to catch any ioexception that may be thrown
            e.printStackTrace();
        } catch (Exception e) {
            //Trying to catch any exception that may be thrown
            e.printStackTrace();
        }

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    // source: https://stackoverflow.com/a/37047375/8166854 May 5, 2016 at 9:46
    // user Michael Roland
    boolean testCommand(NfcA nfcA, byte[] command) throws IOException {
        final boolean leaveConnected = nfcA.isConnected();

        boolean commandAvailable = false;

        if (!leaveConnected) {
            nfcA.connect();
        }

        try {
            byte[] result = nfcA.transceive(command);
            if ((result != null) &&
                    (result.length > 0) &&
                    !((result.length == 1) && ((result[0] & 0x00A) == 0x000))) {
                // some response received and response is not a NACK response
                commandAvailable = true;

                // You might also want to check if you received a response
                // that is plausible for the specific command before you
                // assume that the command is actualy available and what
                // you expected...
            }
        } catch (IOException e) {
            // IOException (including TagLostException) could indicate that
            // either the tag is no longer in range or that the command is
            // not supported by the tag
        }

        try {
            nfcA.close();
        } catch (Exception e) {}

        if (leaveConnected) {
            nfcA.connect();
        }

        return commandAvailable;
    }

    public static String shortToHex(short data) {
        return Integer.toHexString(data & 0xffff);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte b : bytes) result.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

}