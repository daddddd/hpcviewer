/*
 * LargeByteBuffer.cpp
 *
 *  Created on: Jul 10, 2012
 *      Author: pat2
 */
#include "LargeByteBuffer.h"

using namespace std;

namespace TraceviewerServer
{

	ULong FileSize;
	LargeByteBuffer::LargeByteBuffer(string SPath)
	{
		//string SPath = Path.string();

		int MapFlags = MAP_PRIVATE;
		int MapProt = PROT_READ;

		struct stat Finfo;
		stat(SPath.c_str(), &Finfo);

		FileSize = Finfo.st_size;

//		if (PAGE_SIZE % mm::mapped_file::alignment() != 0)
//			cerr<< "PAGE_SIZE isn't a multiple of the OS granularity!!";
//		long FileSize = fs::file_size(Path);
		int FullPages = FileSize / PAGE_SIZE;
		int PartialPageSize = FileSize % PAGE_SIZE;
		NumPages = FullPages + (PartialPageSize == 0 ? 0 : 1);

		typedef int FileDescriptor;
		FileDescriptor fd = open(SPath.c_str(), O_RDONLY);

		ULong SizeRemaining = FileSize;

		//MasterBuffer = new mm::mapped_file*[NumPages];
		MasterBuffer = new char*[NumPages];
		for (int i = 0; i < NumPages; i++)
		{

			//MasterBuffer[i] = new mm::mapped_file(Path, mm::mapped_file::readonly, PAGE_SIZE, PAGE_SIZE*i);
			//This is done to make the Blue Gene Q easier
			void* AllocatedRegion = mmap(0, min((ULong) PAGE_SIZE, SizeRemaining), MapProt,
					MapFlags, fd, PAGE_SIZE * i);
			if (AllocatedRegion == MAP_FAILED)
			{
				cerr << "Mapping returned error " << strerror(errno) << endl;
			}

			char* temp = (char*) AllocatedRegion;
			MasterBuffer[i] = temp;
			cout << "Allocated a page: " << AllocatedRegion << endl;
			SizeRemaining -= min((ULong) PAGE_SIZE, SizeRemaining);
		}

	}

	int LargeByteBuffer::GetInt(ULong pos)
	{
		int Page = pos / PAGE_SIZE;
		int loc = pos % PAGE_SIZE;
		char* p2D = MasterBuffer[Page] + loc;
		int val = ByteUtilities::ReadInt(p2D);
		return val;
	}
	Long LargeByteBuffer::GetLong(ULong pos)
	{
		int Page = pos / PAGE_SIZE;
		int loc = pos % PAGE_SIZE;
		char* p2D = MasterBuffer[Page] + loc;
		Long val = ByteUtilities::ReadLong(p2D);
		return val;

	}
	ULong LargeByteBuffer::Size()
	{
		return Length;
	}
	LargeByteBuffer::~LargeByteBuffer()
	{
		for (int i = 0; i < NumPages; i++)
		{
			munmap(MasterBuffer[i], i + 1 == NumPages ? FileSize % PAGE_SIZE : PAGE_SIZE);
			delete (MasterBuffer[i]);
		}
		delete (MasterBuffer);
	}
}

