/*
 * Slave.cpp
 *
 *  Created on: Jul 19, 2012
 *      Author: pat2
 */

#include "Slave.h"
using namespace MPI;
using namespace std;

namespace TraceviewerServer
{

	Slave::Slave()
	{
		MPICommunication::CommandMessage Message;
		COMM_WORLD.Bcast(&Message, sizeof(Message), MPI_PACKED,
				MPICommunication::SOCKET_SERVER);
		if (Message.Command != Constants::OPEN)
			cerr << "Unexpected message command: " << Message.Command << endl;
		LocalDBOpener DBO;
		STDCL = DBO.OpenDbAndCreateSTDC(string(Message.ofile.Path));
		RunLoop();
	}
	void Slave::RunLoop()
	{
		while (true)
		{
			MPICommunication::CommandMessage Message;
			COMM_WORLD.Bcast(&Message, sizeof(Message), MPI_PACKED,
					MPICommunication::SOCKET_SERVER);
			switch (Message.Command)
			{
				case Constants::INFO:
					STDCL->SetInfo(Message.minfo.minBegTime, Message.minfo.maxEndTime,
							Message.minfo.headerSize);
					break;
				case Constants::DATA:
				{
					int LinesSent = GetData(&Message);
					MPICommunication::ResultMessage NodeFinishedMsg;
					NodeFinishedMsg.Tag = Constants::SLAVE_DONE;
					NodeFinishedMsg.Done.RankID = COMM_WORLD.Get_rank();
					NodeFinishedMsg.Done.TraceLinesSent = LinesSent;
					int SizeToSend = 3 * Constants::SIZEOF_INT;
					COMM_WORLD.Send(&NodeFinishedMsg, SizeToSend, MPI_PACKED,
							MPICommunication::SOCKET_SERVER, 0);
					break;
				}
				case Constants::DONE: //Server shutdown
					return;
			}
		}
	}

	int Slave::GetData(MPICommunication::CommandMessage* Message)
	{
		MPICommunication::get_data_command gc = Message->gdata;
		ImageTraceAttributes correspondingAttributes;

		int Truerank = COMM_WORLD.Get_rank();
		int size = COMM_WORLD.Get_size();
		//Gives us a contiguous count of ranks from 0 to size-2 regardless of which node is the socket server
		//If ss = 0, they are all mapped one less. If ss = size-1, no changes happen
		int rank = Truerank > MPICommunication::SOCKET_SERVER ? Truerank - 1 : Truerank;

		int n = gc.processEnd - gc.processStart + 1;
		int p = size;
		int mod = n % (p - 1);
		double q = ((double) n) / (p - 1);

		//If rank > n, there are more nodes than trace lines, so this node will do nothing
		if (rank > n)
			return 0;

		//If rank < (n % (p-1)) this node should compute ceil(n/(p-1)) trace lines,
		//otherwise it should compute floor(n/(p-1))

		//=MIN(F5, $D$1)*(CEILING($B$1/($B$2-1),1)) + (F5-MIN(F5, $D$1))*(FLOOR($B$1/($B$2-1),1))
		int LowerInclusiveBound = min(mod, rank) * ceil(q)
				+ (rank - min(mod, rank)) * floor(q);
		int UpperInclusiveBound = min(mod, rank + 1) * ceil(q)
				+ (rank + 1 - min(mod, rank + 1)) * floor(q) - 1;

		cout << "Rank " << Truerank << " is getting lines [" << LowerInclusiveBound << ", "
				<< UpperInclusiveBound << "]" << endl;

		correspondingAttributes.begProcess = gc.processStart;
		correspondingAttributes.endProcess = gc.processEnd;
		correspondingAttributes.numPixelsH = gc.horizontalResolution;
		//double processsamplefreq = ((double)gc.verticalResolution)/(p-1);
		//correspondingAttributes.numPixelsV = /*rank< mod*/ true ? ceil(processsamplefreq) : floor(processsamplefreq);
		correspondingAttributes.numPixelsV = gc.verticalResolution;
		double timeSpan = gc.timeEnd - gc.timeStart;
		correspondingAttributes.begTime = 0;
		correspondingAttributes.endTime = (long) timeSpan;
		correspondingAttributes.lineNum = 0;
		STDCL->Attributes = &correspondingAttributes;

		ProcessTimeline* NextTrace = STDCL->GetNextTrace(true);
		int LinesSentCount = 0;
		int waitcount = 0;
		cout << "First trace's rank: " << NextTrace->Data->Rank;
		while (NextTrace != NULL)
		{
			if ((NextTrace->Data->Rank< LowerInclusiveBound) || (NextTrace->Data->Rank> UpperInclusiveBound))
			{
				NextTrace = STDCL->GetNextTrace(true);
				waitcount++;
				continue;
			}
			if (waitcount !=0)
			{
				cout<<Truerank<< " skipped " << waitcount << " processes before actually starting work"<<endl;
				waitcount = 0;
			}
			NextTrace->ReadInData();

			vector<TimeCPID>* ActualData = &NextTrace->Data->ListCPID;
			MPICommunication::ResultMessage msg;
			msg.Tag = Constants::SLAVE_REPLY;
			msg.Data.Line = NextTrace->Line();
			int entries = ActualData->size();
			msg.Data.Size = entries;
			if (msg.Data.Size > MPICommunication::MAX_TRACE_LENGTH)
				cerr << "Trace was too big!" << endl;
			msg.Data.Begtime = (*ActualData)[0].Timestamp;
			msg.Data.Endtime = (*ActualData)[entries - 1].Timestamp;
			vector<TimeCPID>::iterator it;
			int i = 0;
			for (it = ActualData->begin(); it != ActualData->end(); ++it)
			{
				if (it->CPID == 419430400)
					cerr << "CPID: 419430400 "<< i << " TL: " << msg.Data.Line<< endl;
				msg.Data.Data[i++] = it->CPID;
			}

			// sizeof(msg) is too large because it assumes all the traces are full. It'll lead to lots of extra sending
			//										Tag, Line, Size			 Beg ts, end ts--double same size as long
			int SizeInBytes = 3 * Constants::SIZEOF_INT + 2 * Constants::SIZEOF_LONG +
			//Each entry is an int
					entries * Constants::SIZEOF_INT;
			COMM_WORLD.Send(&msg, SizeInBytes, MPI_PACKED, MPICommunication::SOCKET_SERVER,
					0);
			LinesSentCount++;
			if (LinesSentCount%100 ==0)
			cout << Truerank << " Has sent " << LinesSentCount << ". Most recent message was " << NextTrace->Line()
					<< " and contained " << entries << " entries" << endl;

			NextTrace = STDCL->GetNextTrace(true);
		}
		return LinesSentCount;
	}

	Slave::~Slave()
	{
		// TODO Auto-generated destructor stub
	}

} /* namespace TraceviewerServer */
