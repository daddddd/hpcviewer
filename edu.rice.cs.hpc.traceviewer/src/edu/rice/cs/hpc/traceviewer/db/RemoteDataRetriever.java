package edu.rice.cs.hpc.traceviewer.db;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;

import edu.rice.cs.hpc.traceviewer.db.TimeCPID;
import edu.rice.cs.hpc.traceviewer.spaceTimeData.CallPath;
import edu.rice.cs.hpc.traceviewer.timeline.ProcessTimeline;

/**
 * Handles communication with the remote server, including asking for data and
 * parsing data, but not opening the connection or closing the connection. It
 * assumes the connection has already been opened by RemoteDBOpener and can be
 * retrieved from SpaceTimeDataControllerRemote.
 * 
 * @author Philip Taffet
 * 
 */
public class RemoteDataRetriever {
	private final Socket socket;
	DataInputStream receiver;
	DataOutputStream sender;
	public final int Height;
	public RemoteDataRetriever(Socket _serverConnection) throws IOException {
		socket = _serverConnection;
		//TODO:Wrap in GZip stream
		receiver = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		sender = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
		
		//Check for DBOK
		int Message = waitAndReadInt(receiver);
		if (Message == 0x44424F4B)//DBOK
		{
			Height = receiver.readInt();
		}
		else if (Message == 0x4E4F4442)//NODB
		{
			//Tell the user
			Height = -1;
		}
		else
		{
			Height = -1;
		}
	}
	//TODO: Inclusive or exclusive?
	/**
	 * Issues a command to the remote server for the data requested, and waits for a response.
	 * @param P0 The lower bound of the ranks to get
	 * @param Pn The upper bound of the ranks to get
	 * @param t0 The lower bound for the time to get
	 * @param tn The upper bound for the time to get
	 * @param vertRes The number of pixels in the vertical direction (process axis). This is used to compute a stride so that not every rank is included
	 * @param horizRes The number of pixels in the horizontal direction (time axis). This is used to compute a delta t that controls how many samples are returned per rank
	 * @return
	 * @throws IOException 
	 */
	public ProcessTimeline[] getData(int P0, int Pn, double t0, double tn, int vertRes, int horizRes, HashMap<Integer, CallPath> _scopeMap) throws IOException
	{
		//Make the call
		//Check to make sure the server is sending back data
		//Wait/Receive/Parse:
				//			Make into TimeCPID[]
				//			Make into DataByRank
				//			Make into ProcessTimeline
				//			Put into appropriate place in array
		//When all are done, return the array
		
		System.out.println("getData called");
		requestData(P0, Pn, t0, tn, vertRes, horizRes);
		System.out.println("Data request finished");
		
		int ResponseCommand = waitAndReadInt(receiver);
		if (ResponseCommand != 0x48455245)//"HERE" in ASCII
			throw new IOException("The server did not send back data");
		System.out.println("Data receive begin");
		int RanksReceived = 0;
		int RanksExpected = Math.min(Pn-P0, vertRes);
		ProcessTimeline[] timelines = new ProcessTimeline[RanksExpected];
		while (RanksReceived < RanksExpected)
		{
			int RankNumber = receiver.readInt();
			int Length = receiver.readInt();//Number of CPID's
			TimeCPID[] ranksData = readTimeCPIDArray(Length, t0, tn);
			TraceDataByRankRemote dataAsTraceDBR = new TraceDataByRankRemote(ranksData);
			
			
			int lineNumber;
			if (false)//if (Pn-P0 > vertRes)
				lineNumber = (int)Math.round((RankNumber-P0)*(double)vertRes/(Pn-P0));//Its like a line: P0 -> 0, the slope is number of pixels/number of ranks
			else
				lineNumber = RankNumber-P0;
			ProcessTimeline PTl = new ProcessTimeline(dataAsTraceDBR, _scopeMap, lineNumber, RanksExpected, tn-t0, t0);
			timelines[RankNumber]= PTl;//RankNumber or RankNumber-P0??
			RanksReceived++;
			if (RanksReceived%100==0|| RanksReceived>500)
				System.out.println(RanksReceived+ "/" + RanksExpected);
		}
		System.out.println("Data receive end");
		return timelines;
	}

	/**
	 * Reads from the stream and creates an array of Timestamp-CPID pairs containing the data for this rank
	 * @param length The number of Timestamp-CPID pairs in this rank (not the length in bytes)
	 * @param t0 The start time
	 * @param tn The end time
	 * @return The array of data for this rank
	 * @throws IOException
	 */
	private TimeCPID[] readTimeCPIDArray(int length, double t0, double tn) throws IOException {
		TimeCPID[] ToReturn = new TimeCPID[length];
		double deltaT = (tn-t0)/length;
		for (int i = 0; i < ToReturn.length; i++) {
			ToReturn[i] = new TimeCPID(t0+i*deltaT, receiver.readInt());//Does this method of getting timestamps actually work???
		}
		return ToReturn;
	}
	private void requestData(int P0, int Pn, double t0, double tn, int vertRes,
			int horizRes) throws IOException {
		sender.writeInt(0x44415441);//"DATA" in ASCII
		sender.writeInt(32);//There will be 32 more bytes in this message
		sender.writeInt(P0);
		sender.writeInt(Pn);
		sender.writeDouble(t0);
		sender.writeDouble(tn);
		sender.writeInt(vertRes);
		sender.writeInt(horizRes);
		//That's it for the message
		sender.flush();
	}
	private static int waitAndReadInt(DataInputStream receiver)
			throws IOException {
		int nextCommand;
		// Sometime the buffer is filled with 0s for some reason. This flushes
		// them out. This is awful, but otherwise we just get 0s
		while (receiver.available() <= 4
				|| ((nextCommand = receiver.readInt()) == 0)) {

			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
		}
		if (receiver.available() < 4)// There certainly isn't a message
										// available, since every message is at
										// least 4 bytes, but the next time the
										// buffer has anything there will be a
										// message
		{
			receiver.read(new byte[receiver.available()]);// Flush the rest of
															// the buffer
			while (receiver.available() <= 0) {

				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			nextCommand = receiver.readInt();
		}
		return nextCommand;
	}
}
