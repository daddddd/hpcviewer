/*
 * SpaceTimeDataControllerLocal.h
 *
 *  Created on: Jul 9, 2012
 *      Author: pat2
 */

#ifndef SPACETIMEDATACONTROLLERLOCAL_H_
#define SPACETIMEDATACONTROLLERLOCAL_H_
#include "FileData.h"
#include "ImageTraceAttributes.h"
#include "BaseDataFile.h"
#include "ProcessTimeline.h"

namespace TraceviewerServer
{

	class SpaceTimeDataControllerLocal
	{
	public:
		SpaceTimeDataControllerLocal();
		SpaceTimeDataControllerLocal(FileData*);
		virtual ~SpaceTimeDataControllerLocal();
		void SetInfo(Long, Long, int);
		ProcessTimeline* GetNextTrace(bool);
		void AddNextTrace(ProcessTimeline*);
		void FillTraces(int, bool);
		ProcessTimeline* FillTrace(bool);
		void PrepareViewportPainting(bool);

		//The number of processes in the database, independent of the current display size
		int GetHeight();
		//boost::filesystem::path ExperimentXML;
		std::string GetExperimentXML();
		ImageTraceAttributes* Attributes;
		ProcessTimeline** Traces;
		int TracesLength;
	private:
		int LineToPaint(int);

		ImageTraceAttributes* OldAttributes;

		BaseDataFile* DataTrace;
		int HEADER_SIZE;

		// The minimum beginning and maximum ending time stamp across all traces (in microseconds).
		Long MaxEndTime, MinBegTime;

		ProcessTimeline* DepthTrace;

		int Height;
		string ExperimentXML;

	};

} /* namespace TraceviewerServer */
#endif /* SPACETIMEDATACONTROLLERLOCAL_H_ */
