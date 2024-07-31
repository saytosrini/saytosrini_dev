package ext.sni.wvs;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.Logger;

import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.DynamicRefreshInfo;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.netmarkets.model.NmOid;
import com.ptc.netmarkets.util.beans.NmCommandBean;
import com.ptc.wvs.common.ui.VisualizationHelper;

import wt.util.WTException;
import wt.content.ApplicationData;
import wt.content.ContentHelper;
import wt.content.ContentServerHelper;
import wt.epm.EPMDocument;
import wt.fc.QueryResult;
import wt.fc.WTObject;
import wt.log4j.LogR;
import wt.representation.Representable;
import wt.representation.Representation;
import wt.representation.RepresentationHelper;
import ext.sni.helper.SNNFileHelper;

public class PDFWatermarkDownload extends DefaultObjectFormProcessor {

    private static final String PDF = "pdf";
    private static final Logger LOGGER = LogR.getLogger(PDFWatermarkDownload.class.getName());
   // private static String wthome;
    @Override
    public FormResult doOperation(NmCommandBean commandBean, java.util.List<ObjectBean> paramList) throws WTException 
	{
        System.out.println("entering doOperation");
		FormResult formResult = new FormResult(FormProcessingStatus.SUCCESS);
        formResult.addDynamicRefreshInfo(new DynamicRefreshInfo());

        //wthome = WTProperties.getLocalProperties().getProperty("wt.home"); TEST
        
        ArrayList<NmOid> nmOids = null;
        nmOids = commandBean.getActionOidsWithWizard();

        for (NmOid nmOid : nmOids) 
		{
            Object object = nmOid.getRefObject();

            if (object instanceof EPMDocument) 
			{
                EPMDocument epmdocument = (EPMDocument) object;
                if (epmdocument != null) {
                    try 
					{
                        System.out.println("Found EPM DOC" + epmdocument);
                        File fileDir = new File("C:\\ptc\\Windchill_12.0\\Windchill\\temp"); // Specify the directory to save the file
                        System.out.println("Path " + fileDir);
                        File downloadedFile = downloadLatestPDFRepresentation(epmdocument, fileDir);
                        if (downloadedFile != null) {
                            // Do something with the downloaded file, if needed
                            System.out.println("Downloaded file: " + downloadedFile.getAbsolutePath());
                        }
                    } 
					catch (Exception e) 
					{
                        formResult = new FormResult(FormProcessingStatus.FAILURE);
                        e.printStackTrace();
                    }
                }
            }
        }

        return formResult;
    }
    public static Map<String, Representation> getLatestRepresentation(final EPMDocument epmdocument) throws WTException 
	{
		LOGGER.debug(" > getLatestRepresentation()" + epmdocument.getDisplayIdentity());
		final Map<String, Representation> representationMap = new HashMap<>();
		if (epmdocument instanceof Representable) 
		{
			
			VisualizationHelper visualizationHelper = new VisualizationHelper();
			QueryResult epmReps = visualizationHelper.getRepresentations(epmdocument);
			LOGGER.debug(" > getLatestRepresentation> getRepresentations returned : " + epmReps.size());
			while (epmReps.hasMoreElements()) 
			{
				final Representation representation = (Representation) epmReps.nextElement();
				LOGGER.debug(" > getLatestRepresentation> Represntation : " + representation.getName()
						+ " out of date: " + representation.isOutOfDate());
				LOGGER.debug(representation.getPersistInfo().getModifyStamp());
				System.out.println("method getlatest representation out " + representation);
				representationMap.put("", representation);
			}
		}
		return representationMap;
	}
    public static File downloadContent(final ApplicationData appData, final File fileDir, final String fileName) throws WTException 
	{
		LOGGER.debug("Executing downloadContent()");
		File downloadedFile = null;
		try 
		{
			downloadedFile = new File(fileDir, fileName);
			ContentServerHelper.service.writeContentStream(appData, downloadedFile.getCanonicalPath());
			LOGGER.debug("downloadContent > File Downloaded: " + fileName);
			System.out.println("fileName "+ fileName);
			System.out.println("fileDir "+ fileDir);
		}
		catch (final WTException | IOException wte) 
		{
			LOGGER.error(wte.getMessage(), wte);
			throw new WTException(wte);
		}
		LOGGER.debug("Returning downloadContent() : " + downloadedFile);
		return downloadedFile;
	}
    public static File downloadLatestPDFRepresentation(final EPMDocument epmdocument, final File fileDir) throws WTException, PropertyVetoException 
    {
        LOGGER.debug("> downloadLatestPDFRepresentation() " + epmdocument.getDisplayIdentity());
        Map<String, Representation> representationMap = getLatestRepresentation(epmdocument);
        LOGGER.debug("> downloadLatestPDFRepresentation: Map Representation " + representationMap.size());
        File downloadedFile = null;

        for (Representation representation : representationMap.values()) 
        {
        	System.out.println("For Inside  ");
        	representation = (Representation) ContentHelper.service.getContents(representation);
        	Enumeration<?> e = ContentHelper.getApplicationData(representation).elements();
        	
            while (e.hasMoreElements()) {
            	System.out.println("Inside enumeration while ");
            	Object appObject = e.nextElement();
                System.out.println("Class of AppObject "+ appObject.getClass());
                
                if (appObject instanceof ApplicationData) 
                {
                    ApplicationData appData = (ApplicationData) appObject;
                    String fileName = appData.getFileName();
                    String fileExtension = wt.util.FileUtil.getExtension(fileName);
                    System.out.println("Filename  "+ fileName);
                    
                    if (fileExtension.equalsIgnoreCase(PDF)) 
                    {
                        LOGGER.debug("Downloading application data: " + fileName + " since content is PDF");
                        SNNFileHelper.createDirectory(fileDir);
                        try {
                            downloadedFile = downloadContent(appData, fileDir, fileName);
                            if (downloadedFile != null) {
                                LOGGER.debug("File downloaded successfully: " + downloadedFile.getAbsolutePath());
                                return downloadedFile; // Exit the method after successfully downloading the file
                            } else {
                                LOGGER.debug("Failed to download file.");
                            }
                        } catch (Exception ex) {
                            LOGGER.error("Error downloading file: " + ex.getMessage());
                        }
                    }
                }
            }
        }

        LOGGER.debug("Returning downloadLatestPDFRepresentation() : " + (downloadedFile != null ? downloadedFile.getAbsolutePath() : "No pdf file downloaded"));
        return downloadedFile;
    }
 

    // Other methods remain the same as before
}
