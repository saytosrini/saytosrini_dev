package com.ptc.wvs.server.publish;

import wt.epm.EPMDocument;
import wt.fc.Persistable;
import wt.fc.QueryResult;
import wt.representation.Representation;

import java.io.File;
import java.util.Properties;

/**
  * This class will set the Dedicated Worker for each JOB 
  * 
  */
public class PTCWVSPublishFilters
{

/**
  *     publish.publishqueue.priorities.filtermethod
  *
  *     This method is called when a publish job is being submitted and
  *     allows the priority, queue set and
  *     representation name and descrition to be set
  *     return [0] = priority H, M L
  *            [1] = queue set name which should have been confgured by 
  *                  "publish.publishqueue.setnames"
  *            [2] = new name for representation
  *            [3] = new description for representation
  *     Any of the return strings can be null, in which case the
  *     existing value is unchanged.
  *     Controlled by property
  *       publish.publishqueue.priorities.filtermethod=
  *       com.ptc.wvs.server.publish.
  *       PTCWVSPublishFilters/publishqueueFiltermethod
  *
  **/
public static String[] publishqueueFiltermethod(Persistable p, 
              Integer requestType, Integer requestSource,
              String requestQueuePriority, String requestQueueSet,
              String repName, String repDesc)
{
    String[] ret = {requestQueuePriority, requestQueueSet, repName, repDesc};


if( p instanceof EPMDocument ) {
		EPMDocument epmdoc = (EPMDocument)p;

if( epmdoc.getAuthoringApplication().toString().toUpperCase().equals("PROE") )
{
	
ret[1] = "PROQ";

}

if( epmdoc.getAuthoringApplication().toString().toUpperCase().equals("SOLIDWORKS") )
{
ret[1] = "SWQ";
}

if( epmdoc.getAuthoringApplication().toString().toUpperCase().equals("UG") )
{
ret[1] = "UGQ";
}

	System.out.println("publishqueueFiltermethod outputs:");
	System.out.println("                       Queue Set: " + ret[1]);
	System.out.println("                             Priority: " + ret[0]);
	System.out.println("                         RepName: " + ret[2]);
	System.out.println("                         RepDesc: " + ret[3]);
}
    return ret;
}


/**
  *     publish.publishqueue.usesetworkers.filtermethod
  *
  *   This method will be called when a conversion job is being 
  *   submitted to the CADAgent representation name and description
  *   to be set return the worker queue set the the
  *   CADAGent should use, ie this job will be able to use a
  *   worker that has the "queueset" property that includes the 
  *   return value. There is not reason that this has to be the 
  *   same as the actual publsihing queue set that is being used,
  *   for example you could have dedicated worker for assemblies 
  *   and parts from the same set.
  *   Controlled by property
  *      publish.publishqueue.usesetworkers.filtermethod=
  *      com.ptc.wvs.server.publish.MyPublishFilters/
  *      publishUsesetworkersFiltermethod
  *
  **/
public static String publishUsesetworkersFiltermethod(Persistable p, 
                                String workerType, 
                                String cadType, String fileName,
                                String requestQueuePriority,
                                String requestQueueSet)
{
    System.out.println("publishqueueUsesetworkersFiltermethod : " + p + " " + 
workerType + " " + cadType +
 " " + fileName + " " + requestQueuePriority + " " + requestQueueSet);

    String ret = "DEFAULT";
    if( p instanceof EPMDocument ) {
		EPMDocument epmdoc = (EPMDocument)p;

		System.out.println( "Input docType: " + epmdoc.getDocType().toString() );
		System.out.println( "Input AuthApp: " + epmdoc.getAuthoringApplication().toString().toUpperCase() );

		if( epmdoc.getAuthoringApplication().toString().toUpperCase().equals("PROE") )
		{
		
		ret="PROQ";
		
		}
		
		if( epmdoc.getAuthoringApplication().toString().toUpperCase().equals("SOLIDWORKS") )
		{
		
		ret="SWQ";
		
		}
		
		if( epmdoc.getAuthoringApplication().toString().toUpperCase().equals("UG") )
		{
		
		ret="UGQ";
		
		}
		
	
	}

	System.out.println("WorkerSet: " + ret);

    return ret;
}


public static void main(String args[])
   {
	System.out.println("hook " + args[0]);
	System.out.println("EXITSTATUS:1");
   }
}