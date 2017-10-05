package edu.buffalo.cse.cse486586.simpledht;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.w3c.dom.Node;

class QueryMessage implements java.io.Serializable {
    String type;
    ConcurrentHashMap<String, String> map=new ConcurrentHashMap<String, String>();
    String origin;
}
public class SimpleDhtProvider extends ContentProvider {
    static SharedPreferences sharedPref;
    static final String TAG = SimpleDhtActivity.class.getSimpleName();
    static final String leaderAvdstr=String.valueOf(5554);
    static final String leaderPort=String.valueOf(11108);
    static ConcurrentHashMap<String, String> mymap= new ConcurrentHashMap<String, String>();
    static final int SERVER_PORT = 10000;
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    static String  receivedValue="not yet";
    static String  deletekey="not yet";
    static ConcurrentHashMap<String, String> allMap= new ConcurrentHashMap<String, String>();
    static boolean  receivedallmap=false;
    String myPort="";
    private String NodeId;
    String myAvdstr;
    String leastId="";
    private String mPredId;//my predecessors id
    private String mSuccId;// my Successor id
    private String PredecessorsPort;// Redirection port for the predecessor
    private String SuccessorsPort;// Redirection port for the successor

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if(mymap.containsKey(selection)){
            Log.v("Delete local ", "Received selection");
            mymap.remove(selection);
            //sharedPref.edit().remove(selection);
        }

        else if(selection.equals("@")){
            Log.v("Delete loca ", "Received @");
            mymap.clear();
            //sharedPref.edit().clear();
        }
        else if(selection.equals("*")){
            //return rest of the data
            Log.v("Delete all ", "Received *");
            String deleteall="DeleteAll"+"-"+myPort+"-"+selection;//forwarding to my successor
            Log.v("Delete all"," Sending delete all to successor "+deleteall);
            clientSendMessage(deleteall,SuccessorsPort);
            while(deletekey.equals("not yet")){
                //rest are deleting their maps
            }
            Log.v("Delete all","Deleted all map from all other guy" );
            deletekey="not yet";
        }
        else if (!mymap.contains(selection) && !(selection.equals("*") || selection.equals("@"))){
            Log.v("Delete selection","Deleted selection" + selection+ " from my other guy" );
            String deleteSelection="DeleteOne"+"-"+myPort+"-"+selection;//forwarding to my successor
            Log.v("Delete one"," Sending delete one to successor "+deleteSelection);
            clientSendMessage(deleteSelection,SuccessorsPort);
            while(deletekey.equals("not yet")){
                //rest are deleting their maps
            }
            Log.v("Delete all","Deleted all map from all other guy" );
            deletekey="not yet";
        }
            return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String key1=values.get("key").toString();
        String value1 = values.get("value").toString();
        insertOrPass(key1,value1);
        return uri;

    }

    public void insertOrPass(String key1,String value1){

        try {
            String hashedKey=genHash(key1);
            Log.v("insertOrPass 0 ", " key "+key1+" hashed = "+hashedKey);
//            Log.v("insertOrPass 0", "my Node id "+NodeId+" predecessor id = "+mPredId);
//            Log.v("insertOrPass 0","hashedInput.compareTo(mPredId) "+String.valueOf(hashedKey.compareTo(mPredId)));
//            Log.v("insertOrPass 0","hashedInput.compareTo(NodeId) "+String.valueOf(hashedKey.compareTo(NodeId)));

            Log.v("insertOrPass","Request to insert "+key1+" and "+value1+" to port "+myPort);
            if (isafterMyPredecessor(key1)) {

                Log.v("insertOrPass",key1+" is handled by me "+myPort);
                Context ctx = getContext();
                //Log.v("insertOrPass"," GOT CONTEXT ");
                sharedPref = ctx.getSharedPreferences("viveksinub", Context.MODE_PRIVATE);
                //Log.v("insertOrPass"," OPENED SHARED PREF ");
                SharedPreferences.Editor speditor = sharedPref.edit();
                //Log.v("insertOrPass"," OPENED SPEDITOR ");
                speditor.putString(key1, value1);
                mymap.put(key1,value1);
                Log.v("insertOrPass"," INSERTED KEY AND VALUE "+key1+" "+value1+" in hmap");
                speditor.commit();
                Log.v("insertOrPass"," INSERTED KV "+key1+" "+value1+" in shared pref of "+myPort);

            }
            else{
                Log.v("insertOrPass" , key1+"Doesnot belong to my rage"+myPort);
//        String updatemsg="UpdatePredecessor"+"-"+myPort+"-"+genHash(senderport)+"-"+mSuccId+"-"+senderport+"-"+SuccessorsPort;
                String findPositionOfKey="FindPositionOfKey"+"-"+myPort+"-"+key1+"-"+value1+"-"+hashedKey+"-"+SuccessorsPort;
                Log.v("insertOrPass" , "Sending message "+findPositionOfKey+" to "+SuccessorsPort);
                clientSendMessage(findPositionOfKey,SuccessorsPort);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (Exception e){
            Log.e("insertOrPass" , "Error in insertOrPass ");
            Log.e("insertOrPass" , "Error in insertOrPass "+e.toString());
        }
    }
    @Override
    public boolean onCreate() {

        TelephonyManager tel = (TelephonyManager)this.getContext().getSystemService(
                Context.TELEPHONY_SERVICE);
        String avdStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myAvdstr=avdStr;
        myPort = String.valueOf((Integer.parseInt(avdStr) * 2));

        String leaderport=String.valueOf((Integer.parseInt(leaderAvdstr) * 2));
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            Log.v("On create", "Created a ServerSocket");
        } catch (IOException e) {
            Log.e("On create", "Can't create a ServerSocket");

        }
        try {
            NodeId= genHash(myAvdstr); ///getting my node id
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        Log.v("onCreate "," myPort = "+ myPort);
        Log.v("onCreate "," avdStr = "+ avdStr);
        Log.v("onCreate "," ID = "+ NodeId);
        //Log.v("My unhashed id",portStr); returns 5554 for avd0
        Log.v("On create","avdStr "+avdStr+ " leaderAvdstr "+leaderAvdstr);
        if (avdStr.equals(leaderAvdstr)){
            //i am the leader
            Log.v("On create ","leader port, initializing values");
            mPredId = NodeId;//hashed Id of predecessor
            mSuccId = NodeId;//hashed Id of successor
            PredecessorsPort = myPort; //5554 is its own predecessor
            SuccessorsPort = myPort; //5554 is its own successor
            leastId=NodeId;
            Log.v("On Create","Initialized values= "+myPort+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort+"-"+myAvdstr);
        }
        else{
            //request 5554 to join
            //  sendAjoinRequest();
            mPredId = NodeId;//hashed Id of predecessor
            mSuccId = NodeId;//hashed Id of successor
            PredecessorsPort = myPort;
            SuccessorsPort = myPort;
            String msg="JOIN"+"-"+myPort+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort+"-"+myAvdstr;
            Log.v("onCreate",myPort+" avdstr= "+myAvdstr+" Sending join request to "+leaderport);
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, leaderport);
        }

        return true;
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
//            String[] DECODE = msgs[0].split("-");
//            String messageType = DECODE[0];
//            String myport = DECODE[1];
//            String msg="JOIN"+"-"+myPort+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort;

            String sendingToPort = msgs[1];
            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(sendingToPort));
                String msgToSend = msgs[0];

                ObjectOutputStream dout = new ObjectOutputStream(socket.getOutputStream());
                dout.writeObject(msgToSend);
                Log.v("ClientTask", "Sent Message "+msgs[0]+ "To "+ sendingToPort);
//                    dout.flush();
//                    dout.close();
//                    socket.close();

//                    DataInputStream inputStream = new DataInputStream(socket.getInputStream());
//                    if (inputStream.readUTF().equals("ACK")) {
//                        Log.e(TAG, "recieved ACK in client");
//                        dout.close();
//                        socket.close();
//                        inputStream.close();
//                    }
            } catch (UnknownHostException e) {
                Log.e("ClientTask", "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e("ClientTask", "ClientTask socket IOException");
            }

            return null;
        }
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            // Log.e(TAG, "Socket opened in server");

            try {

                while(true) {
                    Log.v("ServerTask","In servertask");
                    Socket s = serverSocket.accept();

                    Log.v("ServerTask", "Server accepting clients message");
                    ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
                    //decode the input string
//                    String  str = dis.readUTF();
                    Object o= ois.readObject();
                    String str="";
                    String[] DECODE={};
                    String origin="";
                    String messageType="";
                    QueryMessage message=new QueryMessage() ;
                    String senderport="";
                    if(o instanceof  String) {
                        str = o.toString();
                        DECODE = str.split("-");
                        messageType = DECODE[0];
                        senderport=DECODE[1];
                    }
                    else if(o instanceof QueryMessage){
                        message = (QueryMessage) o;
                        origin = message.origin;
                        messageType=message.type;
                        Log.v("Origin",origin);
                    }




                    // String myport = DECODE[1];
                    // if (!str.equals(null)){
                    Log.v("ServerTask","Message received "+ messageType+" from "+senderport+" to "+messageType);
                    // }
                    if(messageType.equals("JOIN")){
//              at sender String msg="JOIN"+"-"+myPort+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort+"-"+myAvdstr;
                        try {
                            joinNode(DECODE,senderport,str);
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }
                    }
                    if(messageType.equals("Joined")){
//   final String msjJoin="Joined"+"-"+myPort+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort;
                        mPredId=DECODE[2];
                        mSuccId=DECODE[3];
                        PredecessorsPort=DECODE[4];
                        SuccessorsPort=DECODE[5];
                        Log.v("ServerTask2","Joined to chord PredecessorsPort= "+ PredecessorsPort+ " SuccessorsPort = "+SuccessorsPort );
                    }
                    if(messageType.equals("UpdatePredecessor")){
                        //        String updatemsg="UpdatePredecessor"+"-"+myPort+"-"+primeSenderId+"-"+mSuccId+"-"+senderport+"-"+SuccessorsPort;
                        //use only genHash(senderport) and senderport
                        mPredId=DECODE[2];
                        PredecessorsPort=DECODE[4];
                        Log.v("ServerTask3"," Updated prdecessor ");
                        Log.v("ServerTask3"," My port "+myPort+" predecessor "+ PredecessorsPort + " Successor " +SuccessorsPort);
                    }
                    if(messageType.equals("FindPositionOfKey")){
//      String findPositionOfKey="FindPositionOfKey"+"-"+myPort+"-"+key1+"-"+value1+"-"+hashedKey+"-"+SuccessorsPort;
                        String key=DECODE[2];
                        String value=DECODE[3];
                        Log.v("ServerTask4","received key = "+key+" VALUE= "+value);
                        insertOrPass(key,value);

                    }
                    //String findthekey="FindTheKey"+"-"+myPort+"-"+selection;
                    if(messageType.equals("FindTheKey")){
                        Log.v("Servertask 5" , " Find the key from : "+ senderport+" key to look "+DECODE[2]);
//      String findthekey="FindTheKey"+"-"+myPort+"-"+selection;//forwarding to my successor
                        String keytosearch=DECODE[2];
                        if(mymap.containsKey(keytosearch)){
                            Log.v("Servertask 5.1", "for "+keytosearch+" Found the value "+ mymap.get(keytosearch)+  " sending to "+senderport);
                            String Foundvalue="FoundTheValue"+"-"+myPort+"-"+mymap.get(keytosearch);
                            clientSendMessage(Foundvalue,senderport);
                            Log.v("Servertask 5.1 "," 2 Origin is "+ senderport);
                        }
                        else if (senderport.equals(myPort)){
                            //key not found
                            receivedValue="dummy";
                        }
                        else{
                            String findthekey="FindTheKey"+"-"+senderport+"-"+keytosearch;//forwarding to my successor
                            Log.v("Servertask 5.2 "," Forwarding msg "+ findthekey+  " to "+ SuccessorsPort);
                            Log.v("Servertask 5.2  "," 2 Origin is "+ senderport);
                            clientSendMessage(findthekey,SuccessorsPort);
                        }
                    }
                    if(messageType.equals("FoundTheValue")){
                        String value= DECODE[2];
                        Log.v("Servertask 5.3 "," Found the value"+ value+ " at "+senderport);
                        receivedValue=value;
                    }
                    if(messageType.equals("GetAll")){
                        Log.v("Servertask 6.1 GetAll","Received map request from" + origin+ " myport "+ myPort);
                        if (myPort.equals(origin)){
                            Log.v("Servertask 6.1 GetAll","Received all maps " );
                            allMap.putAll(message.map);
                            receivedallmap=true;
                        }
                        else if(mymap != null){
                            Log.v("Servertask 6.2 GetAll","PAssing to succesor " +SuccessorsPort);
                            QueryMessage fillnpass = new QueryMessage();
                            fillnpass.type="GetAll";
                            ConcurrentHashMap<String, String> newjoined= new ConcurrentHashMap<String, String>();
                            newjoined.putAll(mymap);
                            newjoined.putAll(message.map);
//                            message.map.putAll(mymap);//adding mymap to received map
                            fillnpass.map.putAll(newjoined);
                            fillnpass.origin=origin;
                            clientSendQuery(fillnpass,SuccessorsPort);
                        }
                    }
                    else if(messageType.equals("DeleteAll")){
                        origin=DECODE[1];
                        Log.v("Servertask 7.1 ","Received DeleteAll request from" + origin+ " myport "+ myPort);
                       //str is the received messaeg
                        if (myPort.equals(origin)){
                            Log.v("Servertask 7.1","DeleteAll all maps " );
                            deletekey="deleted all";
                        }
                        else{
                            //delete my and pass to others
                            mymap.clear();
                            //sharedPref.edit().clear();
                            clientSendMessage(str,SuccessorsPort);
                            Log.v("Servertask"," Sent message deleteall"+str+ " to "+SuccessorsPort);
                        }

                    }
                    else if(messageType.equals("DeleteOne")){
//                        String deleteSelection="DeleteOne"+"-"+myPort+"-"+selection;//forwarding to my successor
                        origin=DECODE[1];
                        Log.v("Servertask 8.1 ","Received DeleteOne request from" + origin+ " myport "+ myPort);
                        Log.v("Servertask 8.1 ","Received message" + str);

                        //str is the received messaeg
                        String selection = DECODE[2];
                        Log.v("Servertask 8.1 ","Request To delete " + selection );

                        if (myPort.equals(origin)){

                            Log.v("Servertask 8.2","DeleteOne deleted "+ selection + " from other guy" );
                            deletekey="deleted one";
                        }
                        else if (mymap.containsKey(selection)){
                            Log.v("Servertask 8.3 ","I have the key " + selection );
                            mymap.remove(selection);
                            sharedPref.edit().remove(selection);
                            clientSendMessage(str,senderport);
                            Log.v("Servertask 8.3 ","removed the key " + selection +  " "+myPort);
                            Log.v("Servertask 8.3 ","Sent the message to origin " + senderport );
                            Log.v("Mydata ","Predecessor " + PredecessorsPort+ " me "+ myPort + " Successor "+ SuccessorsPort );

                        }
                        else{
                            Log.v("Mydata ","Predecessor " + PredecessorsPort+ " me "+ myPort + " Successor "+ SuccessorsPort );
                            Log.v("Servertask 8.4 ","I dont have the key " + selection );
                            clientSendMessage(str,SuccessorsPort);
                            Log.v("Servertask 8.4"," Sent message remove 1 "+str+ " to "+SuccessorsPort);
                        }

                    }


                }

            } catch (EOFException err) {
                Log.e("ServerTask",err.toString());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
        protected void onProgressUpdate(String...strings) {

        }
    }

    private void clientSendMessage(final String message, final String destination){
//        String msg="Joined"+"-"+senderport+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort;

        new Thread(new Runnable() {
            public void run() {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(destination));
                    String msgToSend = message;
                    ObjectOutputStream dout = new ObjectOutputStream(socket.getOutputStream());
                    dout.writeObject(msgToSend);
//                    dout.flush();
//                    dout.close();
//                    socket.close();
                    Log.v("clientSendMessage","Sent the message " + message+ "  message to "+destination);
                    Log.v("clientSendMessage"," predecessor of "+myPort+" is " +PredecessorsPort+" successor is "+SuccessorsPort);
                } catch (UnknownHostException e) {
                    Log.e("clientSendMessage", "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e("clientSendMessage", "ClientTask socket IOException");
                }

            }
        }).start();
    }
    private void clientSendQuery(final QueryMessage message, final String destination){
//        String msg="Joined"+"-"+senderport+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort;

        new Thread(new Runnable() {
            public void run() {
                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(destination));
                    QueryMessage msgToSend = message;
                    ObjectOutputStream dout = new ObjectOutputStream(socket.getOutputStream());
                    dout.writeObject(msgToSend);
//                    dout.flush();
//                    dout.close();
//                    socket.close();
                    Log.v("clientSendMessage","Sent the message " + message.type+ "  message to "+destination);
                    Log.v("clientSendMessage"," predecessor of "+myPort+" is " +PredecessorsPort+" successor is "+SuccessorsPort);
                } catch (UnknownHostException e) {
                    Log.e("clientSendJoinMessage", "ClientTask UnknownHostException");
                } catch (IOException e) {
                    Log.e("clientSendJoinMessage", "ClientTask socket IOException");
                }

            }
        }).start();
    }
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
//        try {
////            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        Log.v("Query "," Query received for "+ selection);
        String[] colums={"key","value"};
        MatrixCursor matrixCursor = new MatrixCursor(colums);
        //  MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
        //case1 : asked for local data

        if ((selection.equals("@"))){
            //return local data
            Log.v("Query 1"," case1 : asked for local data selection= "+selection);
            for (Map.Entry<String, String> entry : mymap.entrySet()) {
                Log.v("Query 1"," case1 : mymap.size= "+ mymap.size());
//                matrixCursor.addRow(new String[]{entry.getKey(), entry.getValue()});
                MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
                mrow.add("key",entry.getKey());
                mrow.add("value", entry.getValue());
                Log.v("Query 1 "," return local data "+entry.getKey()+" "+entry.getValue() );
            }

        }
        else if(selection.equals("*")){
            //return rest of the data
            Log.v("Query 2", "Received *");
            QueryMessage getall = new QueryMessage();
            getall.type="GetAll";
            getall.map.putAll(mymap);//insert my map and send
            getall.origin=myPort;
            Log.v("Query 2",getall.type+" "+getall.origin);
            clientSendQuery(getall,SuccessorsPort);
            while(!receivedallmap){
                //do nothing
            }
            Log.v("Query 2 ", "Got all map* "+allMap.size());
            for (Map.Entry<String, String> entry : allMap.entrySet()) {
                Log.v("Query 2"," case1 : mymap.size= "+ mymap.size());
                matrixCursor.addRow(new String[]{entry.getKey(), entry.getValue()});
            }
            return matrixCursor;
        }
        else{
            Log.v("Query 3 ", " not @ or * checking my and rest ");
            Log.v("Query 3  ", " mymap.containsKey(selection) "+mymap.containsKey(selection));
            //find data
            if(mymap.containsKey(selection)){
                //i have the key
                Log.v("Query 3 ","Key " + selection+" found in my port");
                MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
                mrow.add("key",selection);
                mrow.add("value", mymap.get(selection));
                Log.v("Query 3 ","Retrieved key " + selection+ " from my preference" );
                return matrixCursor;
            }
            else{
                Log.v("Query 4 ", " Checking with rest of the guys ");
                //type-origin to be replied (decode1 is the sender port)-key
                String findthekey="FindTheKey"+"-"+myPort+"-"+selection;//forwarding to my successor
                Log.v("Query 4"," Forwarding msg "+ findthekey+  " to "+ SuccessorsPort);

                clientSendMessage(findthekey,SuccessorsPort);

                while(receivedValue.equals("not yet")){
                    //to receive key
                }

                Log.v("Query 4 ","Retrieved value " + receivedValue+ " from my other guy" );
                MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
                mrow.add("key",selection);
                mrow.add("value",receivedValue);

                receivedValue="not yet";
                return matrixCursor;

            }
            //to implement
            //
        }


        return matrixCursor;
    }
//
//    private Cursor getAll(String key, ConcurrentHashMap<String, String> map, int origin,
//                          boolean wait) {
//        MatrixCursor cursor = new MatrixCursor(new String[]{FIRST_COL_NAME, SECOND_COL_NAME});
//
//        if (key.equals("@") || mPort == mSucc) {
//            for(Entry<String, String> entry : sMap.entrySet()) {
//                cursor.addRow(new String[]{entry.getKey(), entry.getValue()});
//            }
//            return cursor;
//        } else {
//            map.putAll(sMap);
//            client(mSucc, key, NONE, origin, NO_PORT, GET_ALL, map);
//            if (wait) {
//                synchronized(mMessage) {
//                    try {
//                        mMessage.wait();
//                    } catch (InterruptedException e) {
//                        Log.e(TAG, "Get all interrupted");
//                    }
//                }
//                for(Entry<String, String> entry : mMessage.map.entrySet()) {
//                    cursor.addRow(new String[]{entry.getKey(), entry.getValue()});
//                }
//                mMessage.map = NO_MAP;
//            }
//        }
//
//        return cursor;
//    }

    //@Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }
    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    public void joinNode(String [] DECODE, String senderport, String messageReceived) throws NoSuchAlgorithmException {
//String msg="JOIN"+"-"+myPort+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort+"-"+myAvdstr;
        String leaderId=genHash(leaderAvdstr);
        String primesenderiD =DECODE[3];
        Log.v("joinNode","primesenderiD "+ primesenderiD+ " CALCULATED= ");
        Log.v("joinNode","Successor "+ SuccessorsPort+ " id of port "+myPort+" is "+mSuccId);
        Log.v("joinNode","Predecessor "+ PredecessorsPort+ " id of port "+myPort+" is "+mPredId);
        Log.v("joinNode","Leader id of port "+myPort+" is "+leaderId);
        Log.v("joinNode","Node id of port "+myPort+" is "+NodeId);
        Log.v("joinNode","SUCC ID OF SENDER DECODE[2] "+ DECODE[2] );
        Log.v("joinNode","PREDEC ID OF SENDER DECODE[3] "+ DECODE[3] );
        boolean theEnd=false;
        int successornMe=mSuccId.compareTo(NodeId);
        if(successornMe<0)theEnd=true;
        int incomingnMe = primesenderiD.compareTo(NodeId);
        int incomingnsucc = primesenderiD.compareTo(mSuccId);

        int result= NodeId.compareTo(primesenderiD);
        int result1 = mSuccId.compareTo(primesenderiD);
        int result2 = NodeId.compareTo(mSuccId);
        if(mSuccId.equals(NodeId) ){
            //ID ARE HASHED ID of avd name
            //case1 initial join
            Log.v("joinNode 1","Received initial join request from "+senderport);
            String msg="Joined"+"-"+myPort+"-"+NodeId+"-"+mSuccId+"-"+myPort+"-"+myPort;
            clientSendMessage(msg,senderport);
            Log.v("joinNode 1","To client message " +msg+ " to port "+ senderport);
            mPredId=DECODE[2];
            mSuccId=DECODE[3];
            PredecessorsPort=DECODE[4];
            SuccessorsPort=DECODE[5];

            Log.v("joinNode 1","updated PredecessorsPort= "+ PredecessorsPort+ " SuccessorsPort = "+SuccessorsPort );
            Log.v("joinNode 1","updated PredecessorsID= "+ mPredId+ " Successors ID = "+mSuccId );

        }      //else if (mSuccId.compareTo(primesenderiD)>0){
            //lies between me and my successor
         else if (result < 0 && result1 > 0 || (result>0 && result1>0 && result2 > 0) || (result<0 && result1<0 && result2 > 0) ) {
            Log.v("joinNode 3",senderport+"lies between"+myPort+" my successor "+SuccessorsPort);

            String msg="Joined"+"-"+myPort+"-"+NodeId+"-"+mSuccId+"-"+myPort+"-"+SuccessorsPort;

            Log.v("joinNode 3","Sent join request to"+ senderport+" "+ msg);
            clientSendMessage(msg,senderport);
            //tell my successor to update his predecessor to senderport .
            //send him hash of predec port and pred port
            String updatemsg="UpdatePredecessor"+"-"+myPort+"-"+primesenderiD+"-"+mSuccId+"-"+senderport+"-"+SuccessorsPort;
            clientSendMessage(updatemsg,SuccessorsPort);
            Log.v("joinNode 3","Updated PredecessorsPort= "+ PredecessorsPort+ " SuccessorsPort = "+SuccessorsPort );
            mSuccId=primesenderiD;
            SuccessorsPort=senderport;
        } else if(mSuccId.equals(leaderId) && mSuccId.compareTo(primesenderiD)<0 ){
            //   String msg="JOIN"+"-"+myPort+"-"+mPredId+"-"+mSuccId+"-"+PredecessorsPort+"-"+SuccessorsPort;

            String msg="Joined"+"-"+myPort+"-"+NodeId+"-"+mSuccId+"-"+myPort+"-"+SuccessorsPort;

            Log.v("joinNode 2","Sent join request to"+ senderport+" "+ msg);
            clientSendMessage(msg,senderport);
            String updatemsg="UpdatePredecessor"+"-"+myPort+"-"+primesenderiD+"-"+mSuccId+"-"+senderport+"-"+SuccessorsPort;
            Log.v("joinNode 2","Sent UPDATE request to"+ SuccessorsPort+" "+ updatemsg);
            clientSendMessage(updatemsg,SuccessorsPort);
            mSuccId=primesenderiD;
            SuccessorsPort=senderport;

        }
        //else if (mSuccId.compareTo(primesenderiD)<0 ){
            else {
            Log.v("joinNode 4",senderport+"lies beyond successor "+SuccessorsPort + "with id "+ mSuccId+" of "+myPort);
            Log.v("joinNode 4","Sending message "+messageReceived+" to port " + SuccessorsPort+ " repeat the same");
            Log.v("joinNode 4"," My port "+myPort+" predecessor "+ PredecessorsPort + " Successor " +SuccessorsPort);
            clientSendMessage(messageReceived,SuccessorsPort);
            //lies beyond my succesor

        }
    }

    private boolean isafterMyPredecessor (String input) throws NoSuchAlgorithmException {
        String leaderId=genHash(leaderAvdstr);
        // Log.v("isafterMyPredecessor 0","leader id = " + leaderId);
        //Log.v("isafterMyPredecessor 0","Node id = " + NodeId);
        //Log.v("isafterMyPredecessor 0","NodeId.equals(leaderId) = " + NodeId.equals(leaderId));
        String hashedInput = genHash(input);
        int result= NodeId.compareTo(hashedInput);
        int result1 = mPredId.compareTo(hashedInput);
        int result2 = NodeId.compareTo(mPredId);

        try {
            if(mSuccId.equals(NodeId) || myPort.equals(PredecessorsPort)){
                // if only 1 person
                return true;
            }
           else if (result > 0 && result1 < 0 || (result>0 && result1>0 && result2 < 0) || (result<0 && result1<0 && result2 < 0) || (result1<0 && result2<0)) {
                //between me and my predecessor
                Log.v("isafterMyPredecessor 1","Inputid = " + hashedInput +" predecessr = "+ mPredId+ " myid = "+ NodeId);
                return true;
            }
//            if (hashedInput.compareTo(mPredId) > 0 && hashedInput.compareTo(NodeId) <= 0) {
//                //between me and my predecessor
//                Log.v("isafterMyPredecessor 1","Inputid = " + hashedInput +" predecessr = "+ mPredId+ " myid = "+ NodeId);
//                return true;
//            } else if(NodeId.equals(leaderId) && hashedInput.compareTo(mPredId) > 0){
//                //Leader port
//                //predecessor of leader is the biggest
//                //if last node..next is the leader
//                Log.v("isafterMyPredecessor 2","Inputid = " + hashedInput +" predecessr = "+ mPredId+ " myid = "+ NodeId);
//                return true;
//            } else if((hashedInput.compareTo(NodeId) < 0 && hashedInput.compareTo(mPredId) < 0)  && NodeId.equals(leaderId)) {
//                Log.v("isafterMyPredecessor 3","Inputid = " + hashedInput +" predecessr = "+ mPredId+ " myid = "+ NodeId);
//                return false;
//            }  else if((hashedInput.compareTo(NodeId) < 0 && hashedInput.compareTo(mPredId) < 0)  && mPredId.equals(leaderId)) {
//                Log.v("isafterMyPredecessor 4","Inputid = " + hashedInput +" predecessr = "+ mPredId+ " myid = "+ NodeId);
//
//                return true;
//            }
            else{
                //  Log.v("isafterMyPredecessor 3","hashedInput.compareTo(mPredId) "+String.valueOf(hashedInput.compareTo(mPredId)));
                // Log.v("isafterMyPredecessor 3","hashedInput.compareTo(NodeId) "+String.valueOf(hashedInput.compareTo(NodeId)));
                Log.v("isafterMyPredecessor 5","Inputid = " + hashedInput +" predecessr = "+ mPredId+ " myid = "+ NodeId);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "" + e);
        }
        return false;
    }




}
