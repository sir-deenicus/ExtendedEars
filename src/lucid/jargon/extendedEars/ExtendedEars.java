package lucid.jargon.extendedEars;

import android.app.Activity;
import android.content.Context;
import android.media.*;
import android.os.Bundle;
import java.net.*;
import java.util.Enumeration;

import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

//lightly modified to allow playing input; from http://stackoverflow.com/questions/15955958/android-audiorecord-to-server-over-udp-playback-issues
public class ExtendedEars extends Activity {

    private static String TAG = "AudioClient";

    private String SERVER = "xx.xx.xx.xx";
    private static final int PORT = 8008;

    // the audio recording options
    private static final int RECORDING_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private EditText ipbox;

    // the audio recorder
    private AudioRecord recorder;
    private PowerManager.WakeLock wakeLock;
    private AudioTrack SoundPlay; 	
    private DatagramSocket socket = null;

    // the minimum buffer size needed for audio recording
    private static int BUFFER_SIZE = AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL, FORMAT);

    // are we currently sending audio data
    private boolean currentlySendingAudio = false, currentlyPlayingAudio = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Log.i(TAG, "Creating the Audio Client with minimum buffer of "
                + BUFFER_SIZE + " bytes");

        // set up the button
        ipbox = (EditText) findViewById(R.id.ipBox);

        PowerManager mgr = (PowerManager) getSystemService(Context.POWER_SERVICE) ;
        wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");

        Button sendAudioButton = (Button) findViewById(R.id.buttonConnect);
        sendAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SERVER = ipbox.getText().toString()  ;
                startStreamingAudio();
            }
        });

        TextView ipv = (TextView) findViewById(R.id.textViewIP);
        ipv.setText("Your IP: " + getLocalIpAddress());

        Button stop = (Button) findViewById(R.id.buttonStop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               stopStreamingAudio();
            }
        });

        Button play = (Button) findViewById(R.id.buttonStartSound);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
			  if(!currentlyPlayingAudio){
                currentlyPlayingAudio = true;
                SoundPlay.play();
                startSpeakStream();
                Log.d(TAG,"now playing.");}}});

        (findViewById(R.id.buttonStopSounds))
          .setOnClickListener(new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                  currentlyPlayingAudio = false;
                  if(socket != null)
                      socket.close();

                  SoundPlay.stop();
              }
          });

        SoundPlay = new AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_MONO , AudioFormat.ENCODING_PCM_16BIT , BUFFER_SIZE * 4, AudioTrack.MODE_STREAM);
    }

    private void startStreamingAudio() {
	  if(!currentlySendingAudio){
        Log.i(TAG, "Starting the audio stream");
        wakeLock.acquire();
        currentlySendingAudio = true;
        startStreaming();
	  }
    }

    private void stopStreamingAudio() {
	  if(currentlySendingAudio){
        Log.i(TAG, "Stopping the audio stream");
        currentlySendingAudio = false;
        recorder.release();
        wakeLock.release();
	  }
    }

    private void startSpeakStream(){
        Thread streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new DatagramSocket(PORT);
                    socket.setSoTimeout(5000);
                    DatagramPacket packet ;

                    byte [] buff = new byte[BUFFER_SIZE * 4];
                    packet = new DatagramPacket(buff,buff.length);

                    Log.d(TAG, "created buffer of size: " + (BUFFER_SIZE * 4));
                    while(currentlyPlayingAudio) {
                        // send the packet
                        packet.setLength(buff.length);
                        socket.receive(packet);
                        SoundPlay.write(packet.getData(),0,packet.getLength());
                    }
                }
                catch (Exception e) {
                    Log.e(TAG, "Exception: " + e);
                    if(socket != null) socket.close();
                }
            }
        });
        streamThread.start();
    }

    private void startStreaming() {

        Log.i(TAG, "Starting the background thread to stream the audio data");

        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    Log.d(TAG, "Creating the datagram socket");
                    DatagramSocket socket = new DatagramSocket();

                    Log.d(TAG, "Creating the buffer of size " + BUFFER_SIZE);
                    byte[] buffer = new byte[BUFFER_SIZE];

                    Log.d(TAG, "Connecting to " + SERVER + ":" + PORT);
                    final InetAddress serverAddress = InetAddress.getByName(SERVER);
                    Log.d(TAG, "Connected to " + SERVER + ":" + PORT);

                    Log.d(TAG, "Creating the reuseable DatagramPacket");
                    DatagramPacket packet;

                    Log.d(TAG, "Creating the AudioRecord");
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 10);

                    Log.d(TAG, "AudioRecord recording...");
                    recorder.startRecording();

                    while (currentlySendingAudio) {
                        // read the data into the buffer
                        int read = recorder.read(buffer, 0, buffer.length);

                        // place contents of buffer into the packet
                        packet = new DatagramPacket(buffer, read, serverAddress, PORT);
                        // send the packet
                        socket.send(packet);
                    }
                    Log.d(TAG, "AudioRecord finished recording");

                } catch (Exception e) {
                    Log.e(TAG, "Exception: " + e);
                }
            }
        });

        // start the thread
        streamThread.start();
    }
    //http://android-er.blogspot.com/2014/02/android-sercerclient-example-server.html
    private String getLocalIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();

                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip = inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip = "Something Wrong! " + e.toString() + "\n";
        }
        return ip;
    }
}

