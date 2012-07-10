/*
 * MergeDataFiles.cpp
 *
 *  Created on: Jul 9, 2012
 *      Author: pat2
 */

#include "MergeDataFiles.h"
#include <algorithm>
#include <vector>
#include <iterator>
#include "boost/iterator.hpp"
#include "boost/algorithm/string.hpp"
#include "boost/algorithm/string/classification.hpp"
#include "boost/filesystem.hpp"
#include <iostream>
#include <fstream>
#include "Constants.h"
using namespace std;
using namespace boost::filesystem;

namespace TraceviewerServer {
	MergeDataAttribute MergeDataFiles::merge(path Directory, string GlobInputFile, path OutputFile)
	{
		const int Last_dot = GlobInputFile.find_last_of('.');
		const string Suffix = GlobInputFile.substr(Last_dot);

		FILE* fout = fopen(OutputFile.string().c_str(), "r");
		// check if the file already exists
		if (fout != NULL)
		{
			if (IsMergedFileCorrect(&OutputFile))
				return SUCCESS_ALREADY_CREATED;
			// the file exists but corrupted. In this case, we have to remove and create a new one
			fclose(fout);
			remove(OutputFile.string().c_str());
		}

		// check if the files in glob patterns is correct
		path Glob(GlobInputFile);

		if (!AtLeastOneValidFile(Glob))
		{
			return FAIL_NO_DATA;
		}

		DataOutputStream dos(OutputFile.string().c_str());

	//-----------------------------------------------------
	// 1. write the header:
	//  int type (0: unknown, 1: mpi, 2: openmp, 3: hybrid, ...
	//	int num_files
	//-----------------------------------------------------

		int type = 0;
		dos.WriteInt(type);
		path::iterator it;
		vector<path> AllPaths;
		// on linux, we have to sort the files
		//To sort them, we need a random access iterator, which means we need to load all of them into a vector
		copy(directory_iterator(Glob), directory_iterator(), back_inserter(AllPaths));//http://www.boost.org/doc/libs/1_49_0/libs/filesystem/v3/example/tut4.cpp
		sort(AllPaths.begin(), AllPaths.end());

		dos.WriteInt(AllPaths.size());
		const long num_metric_header = 2 * Constants::SIZEOF_INT; // type of app (4 bytes) + num procs (4 bytes)
		const long num_metric_index  = AllPaths.size() * (Constants::SIZEOF_LONG + 2 * Constants::SIZEOF_INT );
		long offset = num_metric_header + num_metric_index;

		int name_format = 0; // FIXME hack:some hpcprof revisions have different format name !!
		//-----------------------------------------------------
		// 2. Record the process ID, thread ID and the offset
		//   It will also detect if the application is mp, mt, or hybrid
		//	 no accelator is supported
		//  for all files:
		//		int proc-id, int thread-id, long offset
		//-----------------------------------------------------
		vector<path>::iterator it2;
		for (it2 = AllPaths.begin(); it2 < AllPaths.end(); it2++)
		{
			path i = *it2;
			const string Filename = i.string();
			const int last_pos_basic_name = Filename.length()-Suffix.length();
			const string Basic_name = Filename.substr(0, last_pos_basic_name);
			vector<string> tokens;

			boost::split(tokens, Basic_name, boost::is_any_of("-"));

			const int num_tokens = tokens.size();
			if (num_tokens < PROC_POS)
				// if it is wrong file with the right extension, we skip
				continue;
			int proc;
			string Token_To_Parse = tokens[name_format + num_tokens - PROC_POS];
			proc = atoi(Token_To_Parse.c_str());
			if ((proc == 0) && (!StringActuallyZero(Token_To_Parse)))//catch (NumberFormatException e)
			{
				// old version of name format
				name_format = 1;
				string Token_To_Parse = tokens[name_format + num_tokens - PROC_POS];
				proc = atoi(Token_To_Parse.c_str());
			}
			dos.WriteInt(proc);
			if (proc != 0)
				type |= Constants::MULTI_PROCESSES;
			const int Thread = atoi(tokens[name_format + num_tokens - THREAD_POS].c_str());
			dos.WriteInt(Thread);
			if (Thread != 0)
				type |= Constants::MULTI_THREADING;
			dos.WriteLong(offset);
			offset+= file_size(i);
		}
		//-----------------------------------------------------
		// 3. Copy all data from the multiple files into one file
		//-----------------------------------------------------
		for (it2 = AllPaths.begin(); it2 < AllPaths.end(); it2++) {
			path i = *it2;

			ifstream dis(i.string().c_str(), ios_base::binary | ios_base::in);
			char data[PAGE_SIZE_GUESS];
			dis.read(data, PAGE_SIZE_GUESS);
			int NumRead = dis.gcount();
			while (NumRead > 0)
			{
				dos.write(data, NumRead);
				dis.read(data, PAGE_SIZE_GUESS);
				NumRead = dis.gcount();
			}
			dis.close();
		}
		InsertMarker(&dos);
		dos.close();
		//-----------------------------------------------------
		// 4. FIXME: write the type of the application
		//  	the type of the application is computed in step 2
		//		Ideally, this step has to be in the beginning !
		//-----------------------------------------------------
		DataOutputStream f(OutputFile.string().c_str());
		f.WriteInt(type);
		f.close();

		//-----------------------------------------------------
		// 5. remove old files
		//-----------------------------------------------------
		RemoveFiles(AllPaths);
		return SUCCESS_MERGED;
	}


		 bool MergeDataFiles::StringActuallyZero (string ToTest)
		{
			for (int var = 0; var < ToTest.length(); var++) {
				if (ToTest[var] != '0')
					return false;
			}
			return true;
		}

		 void MergeDataFiles::InsertMarker(DataOutputStream* dos)
		{
			dos->WriteLong(MARKER_END_MERGED_FILE);
		}
		 bool MergeDataFiles::IsMergedFileCorrect(path* filename)
		{
			ifstream f(filename->string().c_str(), ios_base::binary|ios_base::in);
			bool IsCorrect = false;
			const long pos = boost::filesystem::file_size(*filename)-Constants::SIZEOF_LONG;
			if (pos>0){
				f.seekg(pos, ios_base::beg);
				char buffer[8];
				f.read(buffer, 8);
				const long Marker = ((long)buffer[0]<<56)| ((long)buffer[1]<<48)| ((long)buffer[2]<<40)| ((long)buffer[3]<<32)|
						(buffer[4]<<24)| (buffer[5]<<16)| (buffer[6]<<8)| (buffer[7]<<0);
				IsCorrect = (Marker == MARKER_END_MERGED_FILE);
			}
			f.close();
			return IsCorrect;
		}
		 bool MergeDataFiles::RemoveFiles(vector<path> vect)
		{
			bool success = true;
			vector<path>::iterator it;
			for (it = vect.begin(); it != vect.end(); ++it)
			{
				success &= boost::filesystem::remove(*it);
			}
			return success;
		}
		 bool MergeDataFiles::AtLeastOneValidFile(path dir)
		 {
			 path::iterator it;
			for (it = dir.begin(); it != dir.end(); ++it)
			{
				string filename = (*it).string();
				int l = filename.length();
				//if it ends with ".hpctrace", we are good.
				string ending = ".hpctrace";
				if (l < ending.length())
					continue;
				string supposedext = filename.substr(l - ending.length(), l);

				if (ending.compare(supposedext)==0)
					return true;
			}
			return false;
		 }
} /* namespace TraceviewerServer */
