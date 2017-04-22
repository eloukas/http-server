import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.*;
import java.lang.*;
import java.text.*;
import java.nio.file.Files;

public class clientThread extends Thread{
	public static String request, userStr,remoteAd;
	public static PrintWriter out;
	public static OutputStream data;
	
	public clientThread(String req, String usr , PrintWriter output, OutputStream dat, String address){
		request= req; 
		
		//System.out.println(Thread.currentThread().getName());
		userStr=usr;
		remoteAd= address;
		out=output;
		data=dat;
	}
	
		/*
	responseToClient gets 3 inputs:
		String request = first line of our HTTP REQUEST
		PrintWriter Out = clientSocket's output stream. We send there the HTTP RESPONSE for the protocol to work.
		OutputStream data  = another clientSocket's output stream. After the HTTP Response is sent and we can communicate to the client,
		we use it to send a File there.
		
	So, this method breaks the first line of the HTTP GET REQUEST, and calls a *specific* response method like:
		responseForError ( for 400/404/405/500 errors)
		sendFile ( if a file is requested)
		sendDirectory ( if a directory is requested)
	*/
	public void run(){
		long startTime = System.currentTimeMillis();
		//System.out.println(Thread.currentThread().getName() + "  line 38\n");
		//System.out.println(mainServer.msgQ.size());
		if (mainServer.msgQ.size() > 0 ) {
			try {
				//System.out.println("going to take from msgQ");
				request = mainServer.msgQ.take();
				//System.out.println("got from msgQ");
				//System.out.println(mainServer.msgQ.size());
			}catch(Exception E) {
				System.out.println("problem in taking from *Q* inside constructor");
			}
		}
		
		
		//various declarations
		String readFile;
		String codeStatus;
		String [] parts = null;
		String versionOfHttp= "", extensionForMime="";
		//split the request so we can get first line (Get line) part by part to check errors
		BufferedReader strRead = new BufferedReader(new StringReader(request));
		String line="";
		String reqLog = request;
		
		
		
		//get the first line of GET request,and split it word by word
		try{
			line=strRead.readLine();
			parts= line.split("\\s+");
			line="";
			strRead.close();
		}
		catch(Exception e){ // in case something bad happens
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String exceptionAsString = sw.toString();
			writeErrorLog(reqLog,exceptionAsString,remoteAd);
		}
			
		try{
			//Getting information from the request
			if (parts.length > 1 ){ //could be removed
				if (parts[1].matches("(.*)%20(.*)")){   //fix the " " character in file's name 
					parts[1]=parts[1].replaceAll("%20", " ");
				}
				// remove "/" character of C:\root\/
				parts[1] = parts[1].substring(1); 
			}
			if (parts.length == 3){   //could be removed
				//get http protocol 
				if  (parts[2].equals("HTTP/1.1") ) {
					versionOfHttp = parts[2];
				}
				else if (parts[2].equals("HTTP/1.0") ){
					versionOfHttp = parts[2];
				}
			}
				
			//Creates file object for the currently asked object
			File filepath= new File (mainServer.ROOT + parts[1]);
			
			//System.out.println("filepath = " + filepath);
		//System.out.println(Thread.currentThread().getName() + "  line 100\n");
			try{
				//if you load winehouse.mp3
				//then, extension will be .mp3 
				int index = parts[1].lastIndexOf('.');
				extensionForMime= parts[1].substring(index);
				
			}catch (Exception Ex){//in case something bad happens
			//System.out.println("180 line");
				StringWriter sw = new StringWriter();
				Ex.printStackTrace(new PrintWriter(sw));
				String exceptionAsString = sw.toString();
				writeErrorLog(reqLog,exceptionAsString,remoteAd);
			}
				//System.out.println("extensionForMime is " +extensionForMime);
				//use this for Mapping
				
			/*
			ERROR 405 Method Not allowed.
			Only HTTP GET is supported in this code.
			*/
			if(!parts[0].equals("GET")){ 
				codeStatus = "405 Method Not Allowed";
				writeAccessLog(reqLog,userStr,codeStatus);
				responseForError(codeStatus,out);
			}
			/*
			ERROR 404 Not Found.
			The file/directory you want does not exist.
			*/
			else if(!filepath.exists()){
				codeStatus = "404 File Not Found";
				writeAccessLog(reqLog,userStr,codeStatus);
				responseForError(codeStatus,out);
			}
			/*
			ERROR 400 Bad Request 
			*/
			else if((parts[2]== null ) ||															//&& parts[3]== null)
					( !(parts[2].equals("HTTP/1.1")) && !(parts[2].equals("HTTP/1.0")) ) ){
					//out.println("400 Bad Request!");
				codeStatus = "400 Bad Request";
				writeAccessLog(reqLog,userStr,codeStatus);
				responseForError(codeStatus,out);
				//return;
			}
			else{
				//200 ok
				codeStatus = "200 OK";
					
				//Create an OutputStream so we can send data/bytes there for the client
				//OutputStream data = new BufferedOutputStream( clientSocket.getOutputStream());		

				try {
					/*
					extensionForMime will now return the appropriate content-type extension
					we need for html coding.
					
					For example, ".txt" corresponds to "text/plain"
					*/
					extensionForMime = getMimeExtension(extensionForMime);
					
				}catch(Exception Exxxx){ // in case something bad happens
				//System.out.println("163 line");
					StringWriter sw = new StringWriter();
					Exxxx.printStackTrace(new PrintWriter(sw));
					String exceptionAsString = sw.toString();
					writeErrorLog(reqLog,exceptionAsString,remoteAd);
				}
					//check
					//System.out.println("extensionForMime is : " + extensionForMime); 
				
				// if it is a FILE, write to acces log and then send it
				if (filepath.isFile() ) { 
					//sendFile( String versionOfHttp,PrintWriter out, File filepath, String extensionForMime, OutputStream data);
					writeAccessLog(reqLog,userStr,codeStatus);
					sendFile( versionOfHttp, out,  filepath,  extensionForMime,  data);
				}
				// if it is a DIRECTORY, send index.htm or show the current dir
				else if (filepath.isDirectory() ) {
					//sendDirectory
					//first check index.htm, if not , build some shit
					File indexHTML;
						
					indexHTML =searchForIndexHTML(filepath);
					String extension="";
						
					try {
						extension = getMimeExtension(".html");
					}catch (Exception Ex1) { // in case something bad happens
						StringWriter sw = new StringWriter();
						Ex1.printStackTrace(new PrintWriter(sw));
						String exceptionAsString = sw.toString();
						writeErrorLog(reqLog,exceptionAsString,remoteAd);
					}
						
					if (indexHTML != null) {//if index exists , call sendFile and serve the existing index.html file
						// 		text/html
						writeAccessLog(reqLog,userStr,codeStatus);
						sendFile( versionOfHttp, out,  indexHTML,  extension,  data);
					}
					else {//else, show the existing directory -> sendDirectory
						try {
							//System.out.println("sending directory");
							writeAccessLog(reqLog,userStr,codeStatus);
							sendDirectory(filepath,out);
						}
						catch (Exception E){ // in case sth bad happens
						//System.out.println("208 line");
							StringWriter sw = new StringWriter();
							E.printStackTrace(new PrintWriter(sw));
							String exceptionAsString = sw.toString();
							writeErrorLog(reqLog,exceptionAsString,remoteAd);
						}//to create sendDirectory method
							
					}
					//System.out.println(Thread.currentThread().getName() + "  line 217\n");
				}
			}
		}
		catch (IOException e) { //in case sth bad happens
			/*
			 ERROR 500 Internal Server Error
			*/
			//System.out.println("223 line");
			codeStatus = "500 Internal Server Error";
			
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String exceptionAsString = sw.toString();
			
			writeErrorLog(reqLog,exceptionAsString,remoteAd);
			writeAccessLog(reqLog,userStr,codeStatus);
			
			responseForError(codeStatus,out);
			//System.out.println(e.getMessage());
		}	
		
		long stopTime = System.currentTimeMillis();
		long elapsedTime = stopTime - startTime;
		//COUNT the running time for each response and add it to total
		mainServer.countTime=mainServer.countTime+ (int)elapsedTime;
		//Count total connections asked from server
		mainServer.countCons=mainServer.countCons+1; //Counter for statistics (+1 after succesful connection
					
	}
	
	/*
	responseForError takes 2 inputs:
		String codeStatus
		PrintWriter out.
	According to them, sends the appropriate HTTP Response through 'out' and then shows an HTML page for it.
	*/
	private static void responseForError(String codeStatus, PrintWriter out) {
		String title, body;
		//count each error we send to client, not the internal errors
		mainServer.countErrors=mainServer.countErrors+1;
		//The body and the title of the page  are chosen in a switchcase structure.
        switch ( codeStatus )  {
            case "400 Bad Request":		//Bad Request
                title = "Bad Request";
                body = "HTTP Error 400: Bad Request";
                break;
            case "404 File Not Found":		//File not Found
                title = "File Not Found";
                body = "HTTP Error 404: File Not Found";
                break;
            case "405 Method Not Allowed":		//Method Not Allowed
                title = "Method Not Allowed";
                body = "HTTP Error 405: Method Not Allowed";
                break;
            case "500 Internal Server Error":		//Internal Server Error
                title = "Internal Server Error";
                body = "HTTP Error 500: Internal Server Error";
                break;
            default:		//Unknown
                title = "Unknown Error";
                body = "HTTP Error: Unknown Error";
		
		}
		
		
		//--------------------------------------------------------
		//Create a StringBuilder object, called "html", in which we build the html(you don't say!)
		StringBuilder html = new StringBuilder();

		//Start of HTML
        html.append( "<!DOCTYPE html>\r\n" );
        html.append( "<html>\r\n" );
        html.append( "<head>\r\n" );
		
		//Uncomment the following if you want the ERROR text to be red
       // html.append( "<style> .size, .date {padding: 0 32px} h1.header {color: red; vertical-align: middle;}</style>\r\n" );
		
		//Title and Body we chose earlier
        html.append( "<title>" + title + "</title>\r\n" );
        html.append( "<h1 class=\"header\">" + body + "</h1>\r\n" );
		
		//End of HTML
        html.append( "</head>\r\n" );
        html.append( "<body>\r\n" );
        html.append( "</body>\r\n" );
        html.append( "</html>\r\n" );
		//--------------------------------------------------------
		
        //########################inside responseForError method//########################
		
		//--------------------------------------------------------
		//HTTP RESPONSE
		//Write to PrintWriter "out" the HTTP Response
		Date date = new Date();
		out.print( "HTTP/1.1 " + codeStatus + "\r\n" ); //
        out.print( "Date: " + date + "\r\n" );
        out.print( mainServer.SERVERNAME + "\r\n" );
		//toString method returns a string representing the data in this sequence
		out.print( "Content-length: " + html.toString().length() + "\r\n" ); 
        out.print( "Connection: close\r\n" );
        out.print( "Content-type: text/html\r\n\r\n" );
		
		//Then, show the HTML on screen
		out.print( html.toString() );
		
		//Flush the toilet before you leave.
        out.flush();
		
		//PrintWriter 'out' needs close in order to be saved after flushed.
		//out.close();
		//not sure for that
		//--------------------------------------------------------
		
	}

	/*
	sendFile method 
		sends the appropriate HTTP response through 'out'
		serves the requested file from 'filepath' through 'data'
	*/
	synchronized public static void sendFile( String versionOfHttp, PrintWriter out, File filepath, String extensionForMime, OutputStream data ) throws IOException{
		
		
		Date date = new Date();
		
		//Build HTTP RESPONSE 
		//---------------------------------------------------------------------
		//might need "\r" in the end of the lines
		
		out.print(versionOfHttp + " 200 OK"); 
		out.print("\r\n");
		out.print("Date: " + date);
		out.print("\r\n");
		out.print("Server: "+ mainServer.SERVERNAME);//
		out.print("\r\n");
		out.print("Last-Modified: " + getLastModifiedDate(filepath.lastModified() )  );
		out.print("\r\n");
		out.print("Content-Length: " + filepath.length());
		out.print("\r\n");
		out.print("Connection: close ");
		out.print("\r\n");
		
		
		//mime probably , String extensionForMime (?(
		//Replace this line with getMime shit 
		//out.print("Content-Type: " + Files.probeContentType(filepath.toPath()) + "\r\n");
		out.print("Content-Type: " + extensionForMime + "\r\n");
		out.print("\r\n");
		//out.print("\r\n");
			
		//Save HTTP Response 
		out.flush();
			
		//Send Data to client through a 8 KiloBytes Buffer
		int count;
		byte[] buffer = new byte[ 8192 ];
		
		
		//########################inside sendFile method//########################
		
		//Create a FileInputStream from the file  
		FileInputStream bytesFromFile = new FileInputStream( filepath.getPath() );

		//Reads up to 8 KBs of data from this input stream into an array of bytes called "count"
		//it returns -1 if EOF
		while ( (( count = bytesFromFile.read(buffer) )  != -1) ) {
					
			//Writes the data to our OutputStream
			data.write( buffer );
			data.flush();
		}
		out.flush();
		
		bytesFromFile.close(); //close file resource 
	}

	/*
	searchForIndexHTML takes a File filepath as input
	and returns the index.htm(l), if there is any, in this directory.
	Otherwise, it returns null.
	*/
	public static File searchForIndexHTML( File filepath ){
        for ( File file : filepath.listFiles() )//enhanced iteration through list of files
        {
            if ( file.isFile()) {
				//check .htm also(if it is named such due to convention)
				if (( file.getName().equals( "index.html" )) || ( file.getName().equals( "index.htm" ) ) ){ 
					return file; //return index.htm
				}
			}
        }
		//else
        return null;
    }
	
	/*
	sendDirectory method
		sends the appropriate HTTP response
		shows an HTML page of the directory list 
	*/
	public static void sendDirectory(File filepath, PrintWriter out) throws Exception{
		
		
		//--------------------------------------------------------
		//Create a StringBuilder object, called "html", in which we build the html(you don't say!)
		StringBuilder html = new StringBuilder();

		//Start of HTML
        html.append( "<html>\r\n" );
        html.append( "<head>\r\n" );
		//Title 
        html.append( "<title>" + mainServer.SERVERNAME + "</title>\r\n" );
        html.append( "</head>\r\n" );
        html.append( "<body>\r\n" );
		//name the current directory
		html.append( "<h1>Index of " + (filepath.equals(mainServer.ROOTPATH) ? "/" : filepath.getName() )+"</h1>\r\n" );
	    html.append( "<table>\r\n" );
		//different columns for Name/Size/LastModified
        html.append( "<tr><h3><th valign=\"left\"> Name</th><th > Size </th><th>Last Modified </th></h3></tr>\r\n" );
		html.append( "<tr><th colspan=\"5\"><hr></th></tr>\r\n" );
		
		
		//if it's not root , then show BACK button
		if (!filepath.equals(mainServer.ROOTPATH)) {
			int index = mainServer.ROOTPATH.getPath().length();
			String extension="";
			//we will use substring(index) so we refer to the public path of the server
			if (filepath.getParent().substring(index).equals("") ) {
				extension="/"; //make sure in the end we always have '/' character 
			}
			// For example: For /dir1/dir2, BACK BUTTON will redirect you to /dir1 
			extension = extension + filepath.getParent().substring(index);
			//System.out.println("extension is " + extension);
			//build back button to html 
			html.append( "<tr><td valign=\"top\"><img src=\"/icons/dir.png\"></td><td class=\"link\"><a href=\"" + extension + "\">" + "Parent Directory" + "</a></td></tr>\r\n" );
		}
		
		//System.out.println("line 425");
		//then show directory of listFiles +name +size+last modified String iconspath = "C:\\icons\\";
		
		for ( File file : filepath.listFiles() ){
			int index = mainServer.ROOTPATH.getPath().length();
			String extensionForLocalHost = file.getPath().substring(index);
			html.append( "<tr><td valign=\"top\"><img src=\"/icons/"+imageFor(file)+"\"></td>");
			html.append( "<td valign=\"top\"><a href=\""+extensionForLocalHost+"\">"+file.getName()+"</a></td> ");
			html.append( "<td valign=\"top\">"+(file.isDirectory() ? "- " : getFileSize(file) )+"</td>");
			html.append( "<td valign=\"top\">"+getLastModifiedDate(file.lastModified())+"</td> </tr>\r\n ");
		}
	

		html.append( "<tr><th colspan=\"5\"><hr></th></tr>\r\n");
		
		//End of HTML
		html.append( "</table>\r\n" );
        html.append( "</body>\r\n" );
        html.append( "</html>\r\n" );

		//########################inside sendDirectory method//########################
		
		//Send HTTP RESPONSE
		Date date = new Date();
        out.print("HTTP/1.1 200 OK" + "\r\n");
        out.print( "Date: " + date + "\r\n" );
        out.print( mainServer.SERVERNAME + "\r\n" );
        out.print( "Content-length: " + html.toString().length() + "\r\n" );
        out.print( "Connection: close\r\n" );
        out.print( "Content-type: text/html\r\n\r\n" );
		
		//SEND HTML RESPONSE
        out.print( html.toString() );
		
		//flush
        out.flush();
		//close(?)
		//out.close();
		//not sure for that
	}
	
	/*
	getMimeExtension(String extensionForMime) takes a file suffix 
	as an input and returns the appropriate Content-Type for HTTP as a String
	For example : .pdf should correspond to application/pdf 
	With this way, our Server can serve files without errors.
	*/
	synchronized public static String getMimeExtension(String extensionForMime) throws Exception { //File filepath
		Properties mimeMap = new Properties();
		String extensionForUse;
		FileInputStream mime_types;
		
		//Create a FileInputStream from the mime-types .txt file
		mime_types = new FileInputStream( "mime-types.txt" );
		//load it to the "mimeMap" Properties Object 
		mimeMap.load(mime_types);
		//get its appropriate Content-Type and return it
		extensionForUse = mimeMap.getProperty(extensionForMime);
		
		return extensionForUse;
	}
	
	/*
	getLastModifiedDate gets "file.lastModified" method as input.
	It returns the Date the file was last modified 
	as a String in a format we want.
	*/
	synchronized public static String getLastModifiedDate( long timeDate )
    {
        String dateFormat = "EEE, d MMM YYYY HH:mm:ss z";
        SimpleDateFormat sdf = new SimpleDateFormat( dateFormat );
        sdf.setTimeZone( TimeZone.getTimeZone( "GMT" ) );

        return sdf.format( timeDate );
    }

	/*
	getFileSize(File file) gets a file as input and returns 
	its size in a String in corresponding bytes size.
	*/
    synchronized public static String getFileSize( File file ){
        double bytes = file.length();
        double kilobytes = ( bytes / 1024);
        double megabytes = (kilobytes / 1024);
        double gigabytes = (megabytes / 1024);

        if ( gigabytes >= 1 )
            return String.format( "%.1f GBs", gigabytes );
        else if ( megabytes >= 1 )
            return String.format( "%.1f MBs", megabytes );
        else if ( kilobytes >= 1 )
            return String.format( "%.1f KBs", kilobytes );
        else
            return String.format( "%.1f Bytes", bytes );
    }
	
	/*
	imageFor method 
		is used for assigning the corresponding icon to each type of file ( by finding its extension ) 
	*/
	synchronized public static String imageFor(File f){
		//String test="icons\\doc.png";
		//return test;
	  String icon = "";
	  String ext="";
	  try{
		  int index = f.getName().lastIndexOf('.');
		  ext=  f.getName().substring(index);
		  ext = ext.toLowerCase();
      }
	  catch(Exception e){}
	  
	  switch(ext) {
        /* doc */
        case ".doc":
        case ".docx":
        case ".odt":
          icon += "doc.png";
          break;
        /* xls */
        case ".xls":
        case ".xlsx":
        case ".ods":
          icon += "xls.png";
          break;
        /* ppt */
        case ".ppt":
        case ".pptx":
        case ".odp":
          icon += "ppt.png";
          break;
        /* pdf */
        case ".pdf":
        case ".ps":
          icon += "pdf.png";
          break;
        /* images */
        case ".png":
        case ".jpg":
        case ".jpeg":
        case ".bmp":
        case ".tiff":
        case ".svg":
        case ".pgm":
        case ".ppm":
        case ".pbm":
          icon += "img.png";
          break;
        /* video */  
        case ".mp4":
        case ".flv":
        case ".mkv":
        case ".ogv":
        case ".avi":
        case ".mov":
        case ".qt":
          icon += "video.png";
          break;
        /* audio */  
        case ".wav":
        case ".mp3":
        case ".ogg":
        case ".cda":
        case ".flac":
        case ".snd":
        case ".aa":
        case ".mka":
        case ".wma":
        case ".m4p":
        case ".mp4a":
        case ".mpa":      
          icon += "audio.png";
          break;
        /* html */
        case ".html":
        case ".htm":
          icon += "html.png";
          break;
        /* xml */
        case ".xml":
          icon += "xml.png";
          break;
        /* rss */
        case ".rss":
          icon += "rss.png";
          break;
        default:
          icon += "txt.png";      
      }
	  //if file is Directory give the correct icon
	  if(f.isDirectory()){
		  icon="dir.png";
	  }
	  
	  return icon;
	  
	}

	/*
	writeAccessLog
		writes 
			1)local ip 
			2)current date time 
			3)code status 
			4)user agent 
		in "access_log.txt" 
		
		This method is called every time before server takes any action like calling method "sendFile"
		or "sendDirectory" etc
	*/
	synchronized  public static void writeAccessLog(String request,String userStr,String codeStatus){
		
		try{
			//System.out.println(Thread.currentThread().getName() + "  line 660\n");
			//create Writer object so we can *write* in the file 
			mainServer.writerAccess  = new PrintWriter(new FileWriter(mainServer.ACCESSPATH, true));
			
			//find ip of current server
			InetAddress ip;
			try {
				ip = InetAddress.getLocalHost();
				mainServer.writerAccess.print(ip.getHostAddress()+" - ");
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			
			//insert Date and Time of the request
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
		    mainServer.writerAccess.print("["+(dateFormat.format(date)+"]  ")); //2016/11/16 12:08:43
			
			//insert request
			mainServer.writerAccess.print(request +"->");
			
			//insert codeStatus
			mainServer.writerAccess.print(codeStatus+"->");
			
			//insert USER AGENT
			mainServer.writerAccess.print(userStr);
			
			mainServer.writerAccess.print("\n");
			mainServer.writerAccess.close();
		}
		catch(Exception ex){}
	}
	
	/*
	writeErrorLog
		is called every time a thread crushes, meaning where an exception happens
		(that might cause the end of the program)
		In this log, there are specifications for the current error each time.
	*/
	synchronized public static void writeErrorLog(String header,String exception,String remoteAd){
		try{
			
			//create Writer for the file-filepath we want
			mainServer.writerError=  new PrintWriter(new FileWriter(mainServer.ERRORPATH, true));
			
			//insert REMOTE IP ADDRESS
			mainServer.writerError.print(remoteAd);
		
			//insert Date and Time of the request
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
		    mainServer.writerError.print(" -> ["+(dateFormat.format(date)+"]  -> "));
			
			//insert HTTP header
			mainServer.writerError.print(header + " -> ");
			//insert exception stack trace
			mainServer.writerError.print(exception);
			
			//close
			mainServer.writerError.print("\n");
			mainServer.writerError.close();
		}
		catch(Exception exx){
			
		}
	}
}
	
