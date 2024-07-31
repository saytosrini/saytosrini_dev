package ext.sni.wvs;

import java.awt.Color;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.Logger;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.ptc.core.components.beans.ObjectBean;
import com.ptc.core.components.forms.DefaultObjectFormProcessor;
import com.ptc.core.components.forms.DynamicRefreshInfo;
import com.ptc.core.components.forms.FormProcessingStatus;
import com.ptc.core.components.forms.FormResult;
import com.ptc.core.components.util.FeedbackMessage;
import com.ptc.core.ui.resources.FeedbackType;
import com.ptc.netmarkets.model.NmObjectHelper;
import com.ptc.netmarkets.model.NmOid;
import com.ptc.netmarkets.util.beans.NmCommandBean;
import com.ptc.wvs.common.ui.VisualizationHelper;

import ext.sni.helper.SNNFileHelper;
import wt.content.ApplicationData;
import wt.content.ContentHelper;
import wt.content.ContentServerHelper;
import wt.epm.EPMDocument;
import wt.fc.QueryResult;
import wt.log4j.LogR;
import wt.representation.Representable;
import wt.representation.Representation;
import wt.util.WTException;


public class PDFWatermarkDownload extends DefaultObjectFormProcessor {

    private static final String PDF = "pdf";
    private static final Logger LOGGER = LogR.getLogger(PDFWatermarkDownload.class.getName());
    private static final String CLASS_NAME = PDFWatermarkDownload.class.getName();
    public static final String FONT_NAME = "/fonts/arial.ttf";
	public static final Font FOOTER_TEXT_FONT = FontFactory.getFont(FONT_NAME, BaseFont.WINANSI, 9, Font.BOLD, Color.BLACK);
    public static final String FINAL_FOLDER = "FinalPDF";

   	@Override
    public FormResult doOperation(NmCommandBean commandBean, java.util.List<ObjectBean> paramList) throws WTException 
	{
        System.out.println("entering doOperation");
		FormResult formResult = new FormResult(FormProcessingStatus.SUCCESS);
        formResult.addDynamicRefreshInfo(new DynamicRefreshInfo());

        //wthome = WTProperties.getLocalProperties().getProperty("wt.home"); TEST
        
        ArrayList<NmOid> nmOids = null;
        nmOids = commandBean.getActionOidsWithWizard();
        File downloadedFile = null;
        File watermarkFile=null;
        for (NmOid nmOid : nmOids) 
		{
            Object object = nmOid.getRefObject();

            if (object instanceof EPMDocument) 
			{
                EPMDocument epmdocument = (EPMDocument) object;
                if (epmdocument != null) {
                    try 
					{
                        File fileDir = new File("C:\\ptc\\Windchill_12.0\\Windchill\\temp"); // Specify the directory to save the file
                        downloadedFile = downloadLatestPDFRepresentation(epmdocument, fileDir);
                        if (downloadedFile != null) {
                            // Do something with the downloaded file, if needed
                            System.out.println("Downloaded file: " + downloadedFile.getAbsolutePath());
                            String watermarkPDFPath = addFooterToExistingPDF(downloadedFile,"C:\\ptc\\Windchill_12.0\\Windchill\\temp\\WATERMARKED",downloadedFile.getName(),epmdocument.getLifeCycleState().getDisplay());
                            watermarkFile = new File(watermarkPDFPath); 
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
        if (watermarkFile != null) 
        {
        	String urlString = getExportURL(watermarkFile.getAbsolutePath());
        	LOGGER.debug("servlet url :" + urlString);
		 	FeedbackMessage message = new FeedbackMessage(FeedbackType.SUCCESS, null,
				"Downloading PDF", null,
				"Download Success");
		 		Map<String, String> extraData = new HashMap<>();
		 		extraData.put("fileURL", urlString);
		 		formResult.setExtraData(extraData);
		 			// Pass JavaScript back to client.
		 		formResult.setStatus(FormProcessingStatus.SUCCESS);
		 		formResult.addFeedbackMessage(message);
        }
        return formResult;
    }
    public static String getExportURL(String fileURLLocation) 
    {
		URL localURL = null;
		File excelFile = new File(fileURLLocation);
		try {
			localURL = NmObjectHelper.constructOutputURL(excelFile, excelFile.getName());
			return localURL.toString();
		} catch (WTException e) {
			e.printStackTrace();
		}
		return null;
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
 

    public static String addFooterToExistingPDF(File file, String pdfPath, String fileName, String state) 
    {
        LOGGER.debug(CLASS_NAME + ".addFooterToExistingPDF(): Start.");
        String finalPDFPath = "";
        try {
            Document document = new Document();
            finalPDFPath = pdfPath + File.separator + FINAL_FOLDER + File.separator + fileName;
            LOGGER.debug(CLASS_NAME + ".addFooterToExistingPDF(): Final PDF Path = " + finalPDFPath);
            LOGGER.debug(CLASS_NAME + ".addFooterToExistingPDF(): PDF To Read Path = " + pdfPath);

            // Store Final PDFs in the FinalPDF folder
            final File finalPdfFolderPath = new File(pdfPath + File.separator + FINAL_FOLDER);
			if (!finalPdfFolderPath.exists()) {
				finalPdfFolderPath.mkdir();
			}

            // Final PDF to be generated
            PdfCopy copy = new PdfCopy(document, new FileOutputStream(new File(finalPDFPath)));
            document.open();

            // Representation to be manipulated
            PdfReader reader = new PdfReader(file.getAbsolutePath());
            int numberOfPages = reader.getNumberOfPages();

            PdfImportedPage page;
            PdfCopy.PageStamp stamp;

            for (int currentPage = 0; currentPage < numberOfPages;) {
                page = copy.getImportedPage(reader, ++currentPage);
                stamp = copy.createPageStamp(page);

                Rectangle pagesize = reader.getPageSizeWithRotation(currentPage);
                LOGGER.debug(CLASS_NAME + ".addFooterToExistingPDF(): Page Number = " + currentPage);
                LOGGER.debug(CLASS_NAME + ".addFooterToExistingPDF(): Page Width = " + pagesize.getWidth());
                LOGGER.debug(CLASS_NAME + ".addFooterToExistingPDF(): Page Height = " + pagesize.getHeight());

                // Object Info = Number, Revision, State, Type. 
                
                Phrase objectState = new Phrase(String.valueOf("State: " + state), FOOTER_TEXT_FONT);
                ColumnText.showTextAligned(stamp.getUnderContent(), Element.ALIGN_CENTER, objectState, pagesize.getWidth()/2, 16, 0);
                stamp.alterContents(); 
                copy.addPage(page);
            }

            document.close();
            reader.close();
        } catch (Exception e) {
            LOGGER.error(CLASS_NAME + ".addFooterToExistingPDF(): Error while adding footer = " + e.getLocalizedMessage());
        }

        return finalPDFPath;
    }
}
