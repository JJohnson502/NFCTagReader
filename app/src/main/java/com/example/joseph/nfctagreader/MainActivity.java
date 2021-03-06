package com.example.joseph.nfctagreader;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.ndeftools.*;
import org.w3c.dom.Text;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {

    public static final String ERROR_DETECTED = "No NFC tag detected";
    public static final String WRITE_SUCCESS = "Text written to the NFC tag successfully!";
    public static final String WRITE_ERROR = "Error during writing, is the NFC tag close enough to your device?";
    NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;
    IntentFilter writeTagFilters[];
    boolean writeMode;
    Tag myTag;
    Context context;

    TextView tvNFCContent;
    TextView message;
    Button btnWrite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        tvNFCContent = (TextView) findViewById(R.id.nfc_contents);
        message = (TextView) findViewById(R.id.edit_message);
        btnWrite = (Button) findViewById(R.id.button);

        btnWrite.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                try{
                    if(myTag == null){
                        Toast.makeText(context, ERROR_DETECTED, Toast.LENGTH_LONG).show();
                    }
                    else{
                        write(message.getText().toString(), myTag);
                        Toast.makeText(context, WRITE_SUCCESS, Toast.LENGTH_LONG).show();
                    }
                }
                catch(IOException e){
                    Toast.makeText(context, WRITE_ERROR, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
                catch(FormatException e) {
                    Toast.makeText(context, WRITE_ERROR, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if(nfcAdapter == null){
            //Stop here we need NFC
            Toast.makeText(this, "this device doesnt support NFC.", Toast.LENGTH_LONG).show();
            finish();
        }
        readFromIntent(getIntent());

        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writeTagFilters = new IntentFilter[]{ tagDetected };
    }


    /**
     * Tag Reader Methods
     */
    private void readFromIntent(Intent intent){
        String action = intent.getAction();
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action) || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)){
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs = null;
            if(rawMsgs != null){
                msgs = new NdefMessage[rawMsgs.length];
                for(int i = 0; i < rawMsgs.length; i++){
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }
            }
            buildTagViews(msgs);
        }
    }

    private void buildTagViews(NdefMessage[] msgs){
        if(msgs == null || msgs.length == 0) return;

        String text = "";
        byte[] payload = msgs[0].getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16"; //Get Text Encoding
        int languageCodeLength = payload[0] & 0063; //Get language Code, eg. en
        try{
            text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        }
        catch(UnsupportedEncodingException e){
            Log.e("UnsupportedEncoding", e.toString());
        }
        tvNFCContent.setText("NFC Content: " +text);
    }

    /**
     * Tag Writer Methods
     */

    private void write(String text, Tag tag) throws IOException, FormatException{
        NdefRecord[] records = { createRecord(text)};
        NdefMessage message = new NdefMessage(records);
        //get instance of Ndef for the tag
        Ndef ndef = Ndef.get(tag);
        //enable IO
        ndef.connect();
        //Write Message
        ndef.writeNdefMessage(message);
        //close connection
        ndef.close();
    }
    private  NdefRecord createRecord(String text) throws UnsupportedEncodingException{
        String lang         ="en";
        byte[] textBytes    = text.getBytes();
        byte[] langBytes    = lang.getBytes("US-ASCII");
        int    langLength   = langBytes.length;
        int    textLength   = textBytes.length;
        byte[] payload      = new byte[1 + langLength + textLength];

        //set status byte
        payload[0] = (byte) langLength;

        //copy langbytes and textbytes into payload
        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, 1+ langLength, textLength);

        NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);

        return recordNFC;
    }

    @Override
    protected void onNewIntent(Intent intent){
        setIntent(intent);
        readFromIntent(intent);
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
    }

    @Override
    public void onPause(){
        super.onPause();
        WriteModeOff();
    }

    @Override
    public void onResume(){
        super.onResume();
        WriteModeOn();
    }

    private void WriteModeOn(){
        writeMode = true;
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null);
    }

    private void WriteModeOff(){
        writeMode = false;
        nfcAdapter.disableForegroundDispatch(this);
    }

}
