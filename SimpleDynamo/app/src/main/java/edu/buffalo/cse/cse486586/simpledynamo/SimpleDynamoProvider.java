package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import static android.content.ContentValues.TAG;

class QueryMessage implements java.io.Serializable {
	String type;
	ConcurrentHashMap<String, String> map=new ConcurrentHashMap<String, String>();
	String origin;
	String mapType="";
}

public class SimpleDynamoProvider extends ContentProvider {
	ConcurrentHashMap<String, NodeDetails> ringmap=new ConcurrentHashMap<String, NodeDetails>();
	ConcurrentHashMap<String, String> Cmap=new ConcurrentHashMap<String, String>();
	ConcurrentHashMap<String, String> Pmap=new ConcurrentHashMap<String, String>();
	ConcurrentHashMap<String, String> Gpmap=new ConcurrentHashMap<String, String>();
	ConcurrentHashMap<String, String> excess=new ConcurrentHashMap<String, String>();
	ConcurrentHashMap<String, String> AllMap=new ConcurrentHashMap<String, String>();
	ConcurrentHashMap<String, String> AllMapGet=new ConcurrentHashMap<String, String>();
	static final int SERVER_PORT = 10000;
	Context context;
	String allKv="";
	ArrayList<NodeDetails> allnodelist = new ArrayList<NodeDetails>();
	String myPort,myNodeId,myAvdstr="";
	boolean receivedallmap=false;
	boolean failed=false;
	String receivedValue="not yet";
	String receivedKey="dummy";
	int totalReceived,killedAvd=0;
	boolean recieved = false;
	boolean ACKreceived=false;
	public synchronized void deleteKey(String selection){
		if(Cmap.containsKey(selection)){
			Log.v("DeleteKey "+myAvdstr, "Delete from Cmap "+selection);
			Cmap.remove(selection);}
		String DeleteOnePmap=messageBuilder("DeleteOne",myAvdstr,"Pmap",selection);//forwarding to my successor
		String SuccessorPort=String.valueOf(Integer.parseInt(ringmap.get(myAvdstr).getSport()) * 2);
		String DeleteOneGpmap=messageBuilder("DeleteOne",myAvdstr,"Gpmap",selection);//forwarding to my successor
		String SSuccessorPort=String.valueOf(Integer.parseInt(ringmap.get(myAvdstr).getsecondSPort()) * 2);
		Log.v("Delete one"," Sending delete one to  "+SuccessorPort+
				" "+DeleteOnePmap+" "+SSuccessorPort+" "+DeleteOneGpmap);
		clientSendMessage(DeleteOnePmap,SuccessorPort);
		clientSendMessage(DeleteOneGpmap,SSuccessorPort);

	}

	public boolean hadFailed() {
		boolean hadfailed = false;
		File file = context.getFileStreamPath("failuretest");
		hadfailed=file.exists();
		if (!hadfailed) {
			try {
				FileOutputStream stream = context.openFileOutput("failuretest", Context.MODE_WORLD_WRITEABLE);
				stream.write("failuretest".getBytes());
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return hadfailed;
	}
	@Override
	public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
		String cordinatorId=getCordinator(selection);
		if(excess.containsKey(selection))excess.remove(selection);
		if(cordinatorId.equals(myAvdstr)){
			deleteKey(selection);
		}
		else if(selection.equals("@")){
			Log.v("Delete local ", " Received @");
			Cmap.clear();Pmap.clear();Gpmap.clear();excess.clear();
		}
		else if(selection.equals("*")){
			//return rest of the data
			totalReceived=0;
			int dead=0;
			Cmap.clear();Pmap.clear();Gpmap.clear();excess.clear();
			Log.v("Delete all ", "Received *");
			String message=messageBuilder("DeleteAll",myAvdstr);
			for(NodeDetails Objnd : allnodelist){
				if(!Objnd.getnodeId().equals(myAvdstr)){
					String toport=String.valueOf((Integer.parseInt(Objnd.getnodeId()) * 2));
					//int failed=pingAck(toport);
					int failed=0;
					if(dead < failed) dead = failed; //if 1 failed, it will be stored
					Log.v("Query 2"+myAvdstr, "GetAll->Received * Asking map from port "+toport);
					clientSendMessage(message,toport);
				}
			}
			Log.v("in delete"+myAvdstr,"Total dead "+dead+" totalReceived "+totalReceived);
//			while(!(totalReceived>=4-dead)){
//				//do nstill deleting
//			}
			Log.v("Delete all","Deleted all map from all other guy" );
		}
		else if (!Cmap.contains(selection) && !cordinatorId.equals(myAvdstr)&& !(selection.equals("*") || selection.equals("@"))){
			String DeleteOne=messageBuilder("DeleteOne",myAvdstr,"Cmap",selection);//forwarding to my successor
			String cordinatorPort=String.valueOf(Integer.parseInt(cordinatorId) * 2);
			Log.v("Delete one"," Sending delete one to  "+cordinatorPort+" "+DeleteOne);
			clientSendMessage(DeleteOne,cordinatorPort);
		}
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}
	public String getCordinator(String key){
		String hashedKey= null;
		String cordinator="";
		hashedKey = getHash(key);
		Log.v("getCordinator ", " key "+key+" hashed = "+hashedKey);
		int flag=0;
		for(NodeDetails nd : allnodelist){
			String NodeId=getHash(nd.getnodeId());
			String mPredId=getHash(nd.getPPort());
			int nodeandkey= NodeId.compareTo(hashedKey);//test node
			int predcandkey = mPredId.compareTo(hashedKey);//predecessor of test node
			if (nodeandkey>0 && predcandkey<0) {
				cordinator=nd.getnodeId();
				flag=1;
				break;
			}
		}
		if(flag==0){
			cordinator="5562";
		}
		Log.v("getCordinator"," key, cordinator "+key+" "+cordinator);
		return  cordinator;
	}
	@Override
	public synchronized Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub

		String key1 = values.get("key").toString();
		String value1 = values.get("value").toString();
		excess.put(key1,value1);
		String cordinator = getCordinator(key1);//get the cordinator of the key
		NodeDetails objcordinator = ringmap.get(cordinator);//get the object of the cordinator
		String successorPort=String.valueOf((Integer.parseInt(objcordinator.getSport()) * 2));
		String cordinatorPort=String.valueOf((Integer.parseInt(cordinator) * 2));
		String secondSuccessorPort= String.valueOf((Integer.parseInt(objcordinator.getsecondSPort()) * 2));
		String timeStamp = "3";
		//Type+Cordinator+Timestamp+MaptoStore
		//Pmap-Predecessor, Gpmap grand Predecessor,Cmap..Cordinator Map
		String message1 = messageBuilder("Insert", myAvdstr, key1, value1, timeStamp, "Pmap");
		Log.v("Insert "+myAvdstr," Sending Insert "+key1+" Pmap "+ successorPort);
		clientSendMessage(message1,successorPort );
		String message2 = messageBuilder("Insert", myAvdstr, key1, value1, timeStamp, "Gpmap");
		Log.v("Insert "+myAvdstr," Sending Insert "+key1+" Gpmap "+ secondSuccessorPort );
		clientSendMessage(message2,secondSuccessorPort);
		//synchronized (Cmap) {
			if (cordinator.equals(myAvdstr)) {
				Log.v("Insert " + myAvdstr, "Cmap-I am the cordinator " + cordinator + " key " + key1);
				Cmap.put(key1, value1);
			} else {
				String message = messageBuilder("Insert", cordinator, key1, value1, timeStamp, "Cmap");
				Log.v("Insert "+myAvdstr," Sending Insert "+key1+" Cmap "+ cordinator);
				clientSendMessage(message,cordinatorPort);
			}
		///}
		return uri;
	}


	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		Log.v("onCreate "+myAvdstr," Created Init");
		TelephonyManager tel = (TelephonyManager)this.getContext().getSystemService(
				Context.TELEPHONY_SERVICE);
		String avdStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myAvdstr=avdStr;
		Log.v("onCreate","My avd id = "+myAvdstr);
		myPort = String.valueOf((Integer.parseInt(avdStr) * 2));
		myNodeId=getHash(myPort);
		context=getContext();


		//(predecessor,successor,second predecessor,second successor,status,myport)
		NodeDetails obj5562=new NodeDetails("5560","5556","5558","5554","alive","5562");
		NodeDetails obj5556=new NodeDetails("5562","5554","5560","5558","alive","5556");
		NodeDetails obj5554=new NodeDetails("5556","5558","5562","5560","alive","5554");
		NodeDetails obj5558=new NodeDetails("5554","5560","5556","5562","alive","5558");
		NodeDetails obj5560=new NodeDetails("5558","5562","5554","5556","alive","5560");
		allnodelist.add(obj5562);allnodelist.add(obj5556);allnodelist.add(obj5554);
		allnodelist.add(obj5558);allnodelist.add(obj5560);
		ringmap.put("5562",obj5562);ringmap.put("5556",obj5556);ringmap.put("5554",obj5554);
		ringmap.put("5558",obj5558);ringmap.put("5560",obj5560);
		fillmyMaps();

		try {
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
			Log.v("On create", "Created a ServerSocket");
		} catch (IOException e) {
			Log.e("On create", "Can't create a ServerSocket");
		}
		return false;
	}
	public synchronized void fillmyMaps(){

		String sport=String.valueOf(Integer.valueOf(ringmap.get(myAvdstr).getSport())*2);
		String ssport=String.valueOf(Integer.valueOf(ringmap.get(myAvdstr).getsecondSPort())*2);
		String pport=String.valueOf(Integer.valueOf(ringmap.get(myAvdstr).getPPort())*2);
		String spport=String.valueOf(Integer.valueOf(ringmap.get(myAvdstr).getSecondPPort())*2);
		//get my keys from my replicas
		//ask successor for my keys

		String message1=messageBuilder("GiveMeMap",myAvdstr,"Pmap");
		clientSendMessage(message1,sport);
		String message2=messageBuilder("GiveMeMap",myAvdstr,"Gpmap");
		clientSendMessage(message2,sport);
		String message3=messageBuilder("GiveMeMap",myAvdstr,"Gpmap");
		clientSendMessage(message3,ssport);
		String message4=messageBuilder("GiveMeMap",myAvdstr,"Cmap");
		clientSendMessage(message4,spport);
		String message5=messageBuilder("GiveMeMap",myAvdstr,"Cmap");
		clientSendMessage(message5,pport);
		String message6=messageBuilder("GiveMeMap",myAvdstr,"Pmap");
		clientSendMessage(message6,pport);
	}
	public  String messageBuilder(String... args){
		String message="";
		int i;
		for(i = 0; i < args.length-1; i++){
			message=message+args[i]+"-";
		}
		message=message+args[i];
		Log.v("messageBuilder "+myAvdstr," Made message "+message);
		return message;
	}
//	public MatrixCursor sendLocal(String selection){
//		Log.v("sendLocal 1", " case1 : asked for local data selection= " + selection);
//		String[] colums={"key","value"};
//		MatrixCursor matrixCursor = new MatrixCursor(colums);
//		Log.v("Query 1", " case1 : asked for local data selection= " + selection);
//		int c=0;
//		for (Map.Entry<String, String> entry : Cmap.entrySet()) {
//			Log.v("Query 1", " case1 : mymap.size= " + Cmap.size());
//			MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
//			c++;
//			mrow.add("key", entry.getKey());
//			mrow.add("value", entry.getValue());
//			Log.v("Query 1 ", " Cmap return local data " + entry.getKey() + " " + entry.getValue()+
//					" "+" count "+ c);
//		}
//		for (Map.Entry<String, String> entry : Pmap.entrySet()) {
//			Log.v("Query 1", " case1 : mymap.size= " + Pmap.size());
//			MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
//			c++;
//			mrow.add("key", entry.getKey());
//			mrow.add("value", entry.getValue());
//			Log.v("Query 1 ", "Pmap return local data " + entry.getKey() + " " + entry.getValue()+
//					" "+" count "+ c);
//		}
//		for (Map.Entry<String, String> entry : Gpmap.entrySet()) {
//			Log.v("Query 1", " case1 : mymap.size= " + Gpmap.size());
//			MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
//			c++;
//			mrow.add("key", entry.getKey());
//			mrow.add("value", entry.getValue());
//			Log.v("Query 1 ", "Gpmap return local data " + entry.getKey() + " " + entry.getValue()+
//					" "+" count "+ c);
//		}
//		Log.v("sendLocal ","Exit");
//		return matrixCursor;
//
//	}
public MatrixCursor sendLocal(String selection){
	Log.v("sendLocal 1", " case1 : asked for local data selection= " + selection);
	String[] colums={"key","value"};
	MatrixCursor matrixCursor = new MatrixCursor(colums);
	Log.v("Query 1", " case1 : asked for local data selection= " + selection);
	Map<String,String> temp = new ConcurrentHashMap<String, String>();
	if(!Cmap.isEmpty())temp.putAll(Cmap);
	if(!Pmap.isEmpty())temp.putAll(Pmap);
	if(!Gpmap.isEmpty())temp.putAll(Gpmap);
	int c=0;
	for (Map.Entry<String, String> entry : temp.entrySet()) {
		Log.v("Query 1", " case1 : mymap.size= " + temp.size());
		MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
		c++;
		mrow.add("key", entry.getKey());
		mrow.add("value", entry.getValue());
		Log.v("Query 1", "@ returning "+entry.getKey()+" "+entry.getValue());
		Log.v("Query 1 ", " temp return local data " + entry.getKey() + " " + entry.getValue()+
				" "+" count "+ c);
	}
	Log.v("sendLocal ","Exit");
	return matrixCursor;

}
	@Override
	public synchronized Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
						String sortOrder) {

		Log.v("Query "+myAvdstr," Query received for "+ selection);
		String[] colums={"key","value"};
		MatrixCursor matrixCursor = new MatrixCursor(colums);
		killedAvd=0;
		Log.v("Query "+myAvdstr," Ready to go inside sync "+selection);
		ConcurrentHashMap<String,String> temp = new ConcurrentHashMap<String, String>();

		synchronized(excess){
			if(!Cmap.isEmpty())temp.putAll(Cmap);
			if(!Pmap.isEmpty())temp.putAll(Pmap);
			if(!Gpmap.isEmpty())temp.putAll(Gpmap);
			//if(!excess.isEmpty())temp.putAll(excess);
			Log.v("Query"+myAvdstr,"In sync "+selection);
			if ((selection.equals("@"))) {
				matrixCursor=sendLocal(selection);
//				return matrixCursor;
			}else if(selection.equals("*")){
				Log.v("Query 2", "GetAll -> inserted my map "+Cmap.size());
				String message=messageBuilder("GetAll",myAvdstr);
				int dead=0;
				for(NodeDetails Objnd : allnodelist){
					if(!Objnd.getnodeId().equals(myAvdstr)){
						String toport=String.valueOf((Integer.parseInt(Objnd.getnodeId()) * 2));
						boolean isDead=pingAck(toport);
						if(isDead){
							dead=1;
							Log.v("Query *"+myAvdstr,"Dead avd "+toport);
						}else{
							Log.v("Query 2"+myAvdstr, "GetAll->Alive Received * Asking map from port "+toport);
							clientSendMessage(message,toport);
						}

					}
				}
				Log.v("Query "+myAvdstr,"Killed avd = "+dead+" totalReceived "+totalReceived);
					while(!(totalReceived>=4-dead)){
						//Log.v("Queryloop"," loop ");
					}
				Log.v("Query 2 "+myAvdstr, "Total received "+totalReceived);
				String[] DECODE={};
				String[] kv={};
				String CmapString="";
				Log.v("Query "+myAvdstr,"Adding my map Killed avd = "+dead+" totalReceived "+totalReceived);
				for (Map.Entry<String, String> entry : Cmap.entrySet()) {
					CmapString=CmapString+entry.getKey()+"="+entry.getValue()+"#";
				}
				allKv=allKv+CmapString;
				allKv=allKv.substring(0, allKv.length()-1);//removing last #
				MatrixCursor mc2 = new MatrixCursor(colums);
				DECODE = allKv.split("#");
				for(int i=0;i<DECODE.length;i++){
					kv=DECODE[i].split("=");
					MatrixCursor.RowBuilder mc2row = mc2.newRow();
					mc2row.add("key", kv[0]);
					mc2row.add("value", kv[1]);
				}
				Log.v("Query 2 "+myAvdstr, "Before clearing Total received "+totalReceived+" "+
						"killed avd "+dead);
				totalReceived=0;
				return mc2;
			}
// 				else if(Cmap.containsKey(selection)){
//					Log.v("Query 3 ","Key " + selection+" found in my Cmap");
//					MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
//					String val=Cmap.get(selection);
//					mrow.add("key",selection);mrow.add("value", val);
//					Log.v("Query 3 ","Retrieved key " + selection+ " from my Cmap" );
//					return matrixCursor;
//			}else if(Pmap.containsKey(selection)){
//					Log.v("Query 3 ","Key " + selection+" found in my Pmap");
//					MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
//					String val=Pmap.get(selection);
//					mrow.add("key",selection);mrow.add("value", val);
//					Log.v("Query 3 ","Retrieved key " + selection+ " "+val+ " from my Pmap" );
//					return matrixCursor;
//			}else if(Gpmap.containsKey(selection)){
//					Log.v("Query 3 ","Key " + selection+" found in my Gpmap");
//					MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
//					String val=Gpmap.get(selection);
//					mrow.add("key",selection);mrow.add("value", val);
//					Log.v("Query 3 ","Retrieved key " + selection+ " "+val+" from my Gpmap" );
//					return matrixCursor;
//			}else if(excess.containsKey(selection)){
//				Log.v("Query 3 ","Key " + selection+" found in my excess");
//				MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
//				String val=excess.get(selection);
//				mrow.add("key",selection);mrow.add("value", val);
//				Log.v("Query 3 ","Retrieved key " + selection+ " "+val+ " from my excess" );
//				return matrixCursor;
//			}
			else if(temp.containsKey(selection)){
				MatrixCursor mc3 = new MatrixCursor(colums);
				MatrixCursor.RowBuilder mc2row = mc3.newRow();
				mc2row.add("key", selection);
				mc2row.add("value", temp.get(selection));
				Log.v("Query 3 "+myAvdstr, " IN temp -> "+selection+" "+temp.get(selection));
				return mc3;
			}else{
					Log.v("Query 4"+myAvdstr,"Find the key "+selection);
					String message=messageBuilder("FindTheKey",myAvdstr,selection);
					String cordinator=getCordinator(selection);
					String cport=String.valueOf((Integer.parseInt(cordinator) * 2));
					String sport=String.valueOf((Integer.parseInt(ringmap.get(cordinator).getSport()) * 2));
					String ssport=String.valueOf((Integer.parseInt(ringmap.get(cordinator).getsecondSPort()) * 2));
					receivedValue="not yet";
					receivedKey="dummy";
					clientSendMessage(message,cport);
					clientSendMessage(message,sport);
					clientSendMessage(message,ssport);
					Log.v("Query 4 "+myAvdstr, " Checking "+selection+" with "+cport);
				//	Log.v("Query 4 "+myAvdstr, " Checking "+selection+" with "+sport);
				//	Log.v("Query 4 "+myAvdstr, " Checking "+selection+" with "+ssport);
					long start_time = System.currentTimeMillis();
					long wait_time = 100;
					long end_time = start_time + wait_time;

					while(!receivedKey.equals(selection)||(System.currentTimeMillis() < end_time)){
						//to receive key
					}
					if(!receivedKey.equals(selection) && excess.containsKey(selection))receivedValue=excess.get(selection);
					Log.v("Query 4 "+myAvdstr,"Retrieved value " + receivedValue+ " for key " +selection);
					MatrixCursor.RowBuilder mrow = matrixCursor.newRow();
					mrow.add("key",selection);	mrow.add("value",receivedValue);
					return matrixCursor;
				}
		}
		Log.v("Query "+myAvdstr,"Exit query");
		return matrixCursor;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
					  String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}
	private String getHash(String input){
		String hash="";
		try {
			hash= genHash(input);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return hash;
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

	public boolean pingAck(String destination){
		boolean failed=false;

		try {
			Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
					Integer.parseInt(destination));
			String msgToSend = "Ping-"+myAvdstr;
			DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
			dout.writeUTF(msgToSend);

			Log.i("Client","Message sent to port: "+destination);
			//dout.flush();
			socket.setSoTimeout(100);
			Thread.sleep(1000);
			DataInputStream in = new DataInputStream(socket.getInputStream());
			String ack = in.readUTF();

			if(ack.equalsIgnoreCase("ACK-1")){
				Log.v("In PINACK ","Port is alive");
				failed=false;
				dout.close();
				socket.close();
			}else{
				Log.v("In PINACK ","Port is dead "+ack);
				failed = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.i("PINGACK","AVD FAILED : " + destination);
			failed = true;
		}
		return failed;

	}
	public  String MaptoString(){
		String maptostring="";
		Map<String,String>temp=new HashMap<String,String>();
		temp.putAll(Cmap);temp.putAll(Pmap);temp.putAll(Gpmap);
		for (Map.Entry<String, String> entry : temp.entrySet()) {
			maptostring=maptostring+entry.getKey()+"="+entry.getValue()+"#";
		}

		//return maptostring.substring(0, maptostring.length()-1);
		return maptostring;
	}
	public static Map StringtoMap(String maptostring){
		String[] DECODE={};
		String[] kv={};
		Map<String,String> newMap=new ConcurrentHashMap<String, String>();
		maptostring=maptostring.substring(0, maptostring.length()-1);
		DECODE = maptostring.split("#");
		for(int i=0;i<DECODE.length;i++){
			kv=DECODE[i].split("=");
			newMap.put(kv[0],kv[1]);
		}
		return newMap;
	}

	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
		protected synchronized Void doInBackground(ServerSocket... sockets) {
			Socket s = null;
			ServerSocket serverSocket = sockets[0];

			try {
				while (true) {
					String str,origin,messageType="";String[] DECODE={};
					Log.v("ServerTask","In servertask");
					 s = serverSocket.accept();
					Log.v("ServerTask", "Server accepting clients message");
//					ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
//					Object o= ois.readObject();
//					QueryMessage message=new QueryMessage();
					str="";
					DataInputStream dis = new DataInputStream(s.getInputStream());
					str=dis.readUTF();
					String senderport="";
					String senderavd="";
					DECODE = str.split("-");messageType = DECODE[0];senderavd=DECODE[1];
						senderport=String.valueOf((Integer.parseInt(senderavd) * 2));

//					if(o instanceof  String) {
//						//Type+Cordinator or Senderport+....
//						str = o.toString();
//						Log.v("Server Task "+myAvdstr," Init message received "+str);
//						DECODE = str.split("-");messageType = DECODE[0];senderavd=DECODE[1];
//						senderport=String.valueOf((Integer.parseInt(senderavd) * 2));
//					}
//
//					else if(o instanceof QueryMessage){
//						message = (QueryMessage) o;	origin = message.origin;messageType=message.type;
//						senderport=String.valueOf((Integer.parseInt(origin) * 2));
//						senderavd=origin;
//					}

					//InsertMessage="Insert"+"-"+cordinator+"-"+key1+"-"+value1+"-"+timeStamp+"-"+"Cmap";
					Log.v("ServerTask","Message received " + str +" before check "+ messageType+" from "+senderport
					+" "+senderavd);

					if(messageType.equals("Insert")) {
						Log.v("Server Task "+myAvdstr,"Received Insert "+DECODE[2]+ " "+DECODE[5]);
						//Type+Cordinator or Senderport+Key+value+Timestamp+MaptoStore
						excess.put(DECODE[2],DECODE[3]);
						if(DECODE[5].equals("Cmap")){
							Log.v("Server Task "+myAvdstr,"Inserted in Cmap "+DECODE[2]+" from "+senderavd+" "+senderport);
							Cmap.put(DECODE[2],DECODE[3]);
						}else if (DECODE[5].equals("Pmap")){
							Log.v("Server Task "+myAvdstr,"Inserted in Pmap "+DECODE[2]+" from "+senderavd+" "+senderport);
							Pmap.put(DECODE[2],DECODE[3]);
						}else if (DECODE[5].equals("Gpmap")){
							Log.v("Server Task "+myAvdstr,"Inserted in Gpmap "+DECODE[2]+" from "+senderavd+" "+senderport);
							Gpmap.put(DECODE[2],DECODE[3]);
						}
					}
					if(messageType.equals("GetAll")) {
//						QueryMessage fillnpass = new QueryMessage();
//						fillnpass.type="MyAll";
						String myKv=MaptoString();
						String message1="MyAll-"+myAvdstr+"-"+myKv;
						Log.v("Server Task"+myAvdstr,"GetAll "+message1);
						clientSendMessage(message1,senderport);
//						fillnpass.map.putAll(Cmap);fillnpass.map.putAll(Pmap);fillnpass.map.putAll(Gpmap);
//						fillnpass.origin=myAvdstr;
//						Log.v("Server Task"+myAvdstr,"GetAll Sending my map "+Cmap.size()+" to "+senderport);
//						clientSendQuery(fillnpass,senderport);
					}
					if(messageType.equals("MyAll")) {
						Log.v("MyAll "+myAvdstr," in myall ");
						allKv=allKv+DECODE[2];
						totalReceived++;
						Log.v("MyAll "+myAvdstr," in myall "+totalReceived);
					}
					if(messageType.equals("FindTheKey")) {
						//"FindTheKey",myAvdstr,selection
						String key=DECODE[2];
						Log.v("Server Task "+myAvdstr,"FindTheKey->Cmap.containsKey "+DECODE[2]+" "+Cmap.containsKey(DECODE[2]));
						Log.v("Server Task "+myAvdstr,"FindTheKey->Pmap.containsKey "+DECODE[2]+" "+Pmap.containsKey(DECODE[2]));
						Log.v("Server Task "+myAvdstr,"FindTheKey->Gpmap.containsKey "+DECODE[2]+" "+Gpmap.containsKey(DECODE[2]));
						//Log.v("Server Task "+myAvdstr,"FindTheKey->receivedValue "+receivedValue+" "+DECODE[2]);
						if(Cmap.containsKey(DECODE[2])){
							Log.v("Server Task "+myAvdstr,"FindTheKey -> "+DECODE[2]+" in if(Cmap.containsKey(DECODE[2]))");
							String val=Cmap.get(DECODE[2]);
							Log.v("Server Task "+myAvdstr,"FindTheKey -> "+DECODE[2]+" val = "+val);
							//String message1=messageBuilder("FoundTheKey",myAvdstr,val);
							String message1="FoundTheKey-"+myAvdstr+"-"+val+"-"+DECODE[2];
							Log.v("Server Task "+myAvdstr,"FoundTheKey->Sent "+message1+" "+" to "+senderport);
							clientSendMessage(message1,senderport);

						}
						else if(Pmap.containsKey(DECODE[2])){
							Log.v("Server Task "+myAvdstr,"FindTheKey -> "+DECODE[2]+" in if(Pmap.containsKey(DECODE[2]))");
							String val=Pmap.get(DECODE[2]);
							Log.v("Server Task "+myAvdstr,"FindTheKey -> "+DECODE[2]+" val = "+val);
							//String message1=messageBuilder("FoundTheKey",myAvdstr,val);
							String message2="FoundTheKey-"+myAvdstr+"-"+val+"-"+DECODE[2];
							Log.v("Server Task "+myAvdstr,"FoundTheKey->Sent "+message2+" "+" to "+senderport);
							clientSendMessage(message2,senderport);

						}
						else if(Gpmap.containsKey(DECODE[2])){
							Log.v("Server Task "+myAvdstr,"FindTheKey -> "+DECODE[2]+" in if(Gpmap.containsKey(DECODE[2]))");
							String val=Gpmap.get(DECODE[2]);
							Log.v("Server Task "+myAvdstr,"FindTheKey -> "+DECODE[2]+" val = "+val);
							//String message1=messageBuilder("FoundTheKey",myAvdstr,val);
							String message3="FoundTheKey-"+myAvdstr+"-"+val+"-"+DECODE[2];
							Log.v("Server Task "+myAvdstr,"FoundTheKey->Sent "+message3+" "+" to "+senderport);
							clientSendMessage(message3,senderport);

						}
					}
					if(messageType.equals("FoundTheKey")) {
							Log.v("Server Task "+myAvdstr,"FoundTheKey->Recieved  "+DECODE[2]+" from "+senderport+ " "+DECODE[3]);
							receivedValue=DECODE[2];
							receivedKey=DECODE[3];
							recieved = true;
					}if(messageType.equals("DeleteAll")) {
						Cmap.clear();Pmap.clear();Gpmap.clear();
						Log.v("Server Task "+myAvdstr,"DeleteAll->Recieved from "+senderport);
						String message1=messageBuilder("DeletedAll",myAvdstr);
						clientSendMessage(message1,senderport);
					}if(messageType.equals("DeletedAll")) {
						Log.v("Server Task "+myAvdstr,"DeletedAll from "+senderport);
						//totalReceived++;
					}if(messageType.equals("DeleteOne")) {
						String delType=DECODE[2];
						String selection=DECODE[3];
						Log.v("Server Task "+myAvdstr,"Delete one from "+senderport+" type = "+delType+" key "+selection);
						if(delType.equals("Cmap")){
							Log.v("Servertask Del 1"+myAvdstr," Cmap delete "+selection+" sending to successors" );
							deleteKey(selection);
							Log.v("Servertask Del 1"+myAvdstr," Cmap delete "+selection+
									" Cmap.containsKey(selection ) " +Cmap.containsKey(selection));

						}
						else if (delType.equals("Pmap")){
							Pmap.remove(selection);
							Log.v("Servertask Del 1"+myAvdstr," Removed "+selection+" Pmap.containsKey(selection ) "
									+Pmap.containsKey(selection));
						}
						else if (delType.equals("Gpmap")){
							Gpmap.remove(selection);
							Log.v("Servertask Del 1"+myAvdstr," Removed "+selection+" Gpmap.containsKey(selection ) "
									+Gpmap.containsKey(selection));
						}
					}if(messageType.equals("Ping")) {
						Log.v("Servertask "+myAvdstr," Ping from "+senderavd+" "+senderport);
						//String temp="ACK-"+myAvdstr+"-"+myPort;
						String temp="ACK-1";
						DataOutputStream out = new DataOutputStream(s.getOutputStream());
						out.writeUTF(temp);
						out.flush();
						Log.v("Servertask "+myAvdstr," Ack from "+myAvdstr+" to "+senderport+" "+temp);
						// 	clientSendMessage("ACK-1",senderport);

					}if(messageType.equals("GiveMeMap")) {
						//"GiveMeMap",myAvdstr,"Pmap"
						Log.v("Server Task "+myAvdstr," GiveMeMap -> from avd "+senderavd+ " type "+DECODE[2]);
						if(DECODE[2].equals("Cmap")&& !Cmap.isEmpty()){
							String CmapString="";
							for (Map.Entry<String, String> entry : Cmap.entrySet()) {
								CmapString=CmapString+entry.getKey()+"="+entry.getValue()+"#";
							}
							Log.v("Server Task "+myAvdstr," GiveMeMap -> sendind my Cmap to "+senderavd);
							String msgtosend="MapRequested"+"-"+myAvdstr+"-"+"Cmap"+"-"+CmapString;
							clientSendMessage(msgtosend,senderport);
						}
						else if(DECODE[2].equals("Pmap") && !Pmap.isEmpty()){
							String PmapString="";
							for (Map.Entry<String, String> entry : Pmap.entrySet()) {
								PmapString=PmapString+entry.getKey()+"="+entry.getValue()+"#";
							}
							Log.v("Server Task "+myAvdstr," GiveMeMap -> sendind my Pmap to "+senderavd);
							String msgtosend="MapRequested"+"-"+myAvdstr+"-"+"Pmap"+"-"+PmapString;
							clientSendMessage(msgtosend,senderport);
						}
						else if(DECODE[2].equals("Gpmap") && !Gpmap.isEmpty()){
							String GpmapString="";
							for (Map.Entry<String, String> entry : Gpmap.entrySet()) {
								GpmapString=GpmapString+entry.getKey()+"="+entry.getValue()+"#";
							}
							Log.v("Server Task "+myAvdstr," GiveMeMap -> sendind my Gpmap to "+senderavd);
							String msgtosend="MapRequested"+"-"+myAvdstr+"-"+"Gpmap"+"-"+GpmapString;
							clientSendMessage(msgtosend,senderport);
						}
					}if(messageType.equals("MapRequested")) {
						String Sport=ringmap.get(myAvdstr).getSport();
						String SSport=ringmap.get(myAvdstr).getsecondSPort();
						String Pport=ringmap.get(myAvdstr).getPPort();
						String GPport=ringmap.get(myAvdstr).getSecondPPort();
						String mapType=DECODE[2];
						Map<String,String> tempMap;
						tempMap=StringtoMap(DECODE[3]);
						excess.putAll(tempMap);
						Log.v("Server Task "+myAvdstr," MapRequested -> mapType "+ mapType);
						Log.v("Server Task "+myAvdstr," MapRequested -> received message "+
								" "+senderport+" "+senderavd+" "+mapType);
						if(senderavd.equals(Sport)&& mapType.equals("Pmap")){
							Log.v("Server Task "+myAvdstr,"Pmap From Successor Putting in Cmap ");
							Cmap.putAll(tempMap);
						}
						if(senderavd.equals(Sport)&& mapType.equals("Gpmap")) {
							Log.v("Server Task "+myAvdstr,"Gpmap From Successor Putting in Pmap ");
							Pmap.putAll(tempMap);
						}
						if(senderavd.equals(SSport)&& mapType.equals("Gpmap")){
							Log.v("Server Task "+myAvdstr,"GpmapSecond Successor Putting in Cmap ");
							Cmap.putAll(tempMap);
						}
						if(senderavd.equals(Pport) && mapType.equals("Pmap")){
							Log.v("Server Task "+myAvdstr,"Pmap From Predecessor Putting in Gpmap ");
							Gpmap.putAll(tempMap);
						}
						if(senderavd.equals(Pport) && mapType.equals("Cmap")) {
							Log.v("Server Task "+myAvdstr,"Cmap From Predecessor Putting in Pmap ");
							Pmap.putAll(tempMap);
						}
						if(senderavd.equals(GPport) && mapType.equals("Cmap")){
							Log.v("Server Task "+myAvdstr,"Cmap From Grand Predecessor Putting in Gpmap ");
							Gpmap.putAll(tempMap);
						}

					}if(messageType.equals("ACK")){
						Log.v("Servertask "+myAvdstr," ACK received from "+senderport);
						ACKreceived=true;
					}

				}
			} catch(EOFException e){
				Log.e("Servertask "+myAvdstr,"EOF ");
				e.printStackTrace();
			} catch (Exception err) {
				Log.e("ServerTask","Error in Servertask "+err.toString());
				err.printStackTrace();
			}
			return null;
		}
	}
	private void clientSendMessage( String message,  String destination){
		new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message, destination);

	}

	private class ClientTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {
			String destination = msgs[1];
			String message=msgs[0];
			Log.i("In client","Message to send : "+message);
			Log.i("In client","Port on which to send : "+destination);
			try {
				Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
						Integer.parseInt(destination));
				String msgToSend = message;
				DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
				dout.writeUTF(msgToSend);
				//socket.setSoTimeout(100);

//                    dout.flush();
//                    dout.close();
//                    socket.close();
				Log.v("clientSendMessage","Sent the message " + message+ "  message to "+destination);
			} catch (UnknownHostException e) {
				Log.e("clientSendMessage", "ClientTask UnknownHostException");
			} catch (IOException e) {
				Log.e("clientSendMessage", "ClientTask socket IOException");
			}catch(Exception e){
				e.printStackTrace();
			}

			return null;
		}
	}




//	private void clientSendQuery(final QueryMessage message, final String destination){
//		new Thread(new Runnable() {
//			public void run() {
//				try {
//					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
//							Integer.parseInt(destination));
//					QueryMessage msgToSend = message;
//					ObjectOutputStream dout = new ObjectOutputStream(socket.getOutputStream());
//					dout.writeObject(msgToSend);
//                    dout.flush();
//                    dout.close();
//                    socket.close();
//					Log.v("clientSendMessage Alpha","Sent the message " + message.type+ "  message to "+destination);
//				} catch (UnknownHostException e) {
//					Log.e("clientSendJoinMessage", "ClientTask UnknownHostException");
//				} catch (IOException e) {
//					Log.e("clientSendJoinMessage", "ClientTask socket IOException");
//				}
//
//			}
//		}).start();
//	}

//	private void sendAck(final String message, final String destination){
//		new Thread(new Runnable() {
//			public void run() {
//				try {
//					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
//							Integer.parseInt(destination));
//					String msgToSend = message;
////						out.writeUTF("ACK-1");
//					//out.flush();
//					DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
//					dout.writeUTF(msgToSend);
//
////                    dout.flush();
////                    dout.close();
////                    socket.close();
//					Log.v("sendAck","Sent the message " + message+ "  message to "+destination);
////					Log.v("clientSendMessage"," predecessor of "+myPort+" is " +PredecessorsPort+" successor is "+SuccessorsPort);
//				} catch (UnknownHostException e) {
//					Log.e("sendAck", "ClientTask UnknownHostException");
//				} catch (IOException e) {
//					Log.e("sendAck", "ClientTask socket IOException");
//				}
//
//			}
//		}).start();
//	}
}
