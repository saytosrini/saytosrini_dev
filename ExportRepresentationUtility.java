package ext.lev.viz;

import java.beans.PropertyVetoException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

import com.ptc.wvs.common.ui.VisualizationHelper;
import wt.vc.VersionControlHelper;
import wt.vc.wip.WorkInProgressHelper;
import wt.content.ApplicationData;
import wt.content.ContentHelper;
import wt.content.ContentServerHelper;
import wt.content.HolderToContent;
import wt.doc.WTDocument;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.log4j.LogR;
import wt.method.RemoteAccess;
import wt.method.RemoteMethodServer;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.representation.Representable;
import wt.representation.Representation;
import wt.util.WTException;
import wt.util.WTProperties;
import wt.util.WTPropertyVetoException;

public class ExportRepresentationUtility implements RemoteAccess, Serializable {
    private static final long serialVersionUID = 1L;
    private static final String PROPERTY_FILE_LOCATION_IN_CODEBASE = "/ext/lev/viz/representationutility.properties";
    private static final String EXPORT_PATH = getMapping("exportpath");
    private static final String csvFilePath = getMapping("ecsvpath");
    private static final String outputCsvPath = EXPORT_PATH + File.separator + "importDocRepNumbersWithPdf.csv";
    public static final String CLASSNAME = ExportRepresentationUtility.class.getName();
    private static final String PDF = "pdf";
    private static final Logger LOGGER = LogR.getLogger(ExportRepresentationUtility.class.getName());

    public static void main(String[] args) {
        try {
            RemoteMethodServer rms = RemoteMethodServer.getDefault();

            if (args.length >= 2) {
                rms.setUserName(args[0]);
                rms.setPassword(args[1]);
            } else {
                rms.setUserName("wcadmin");
                rms.setPassword("wcadmin");
            }
            
            System.out.println("CSV path information from properties: " + csvFilePath);
            System.out.println("Export path information from properties: " + EXPORT_PATH);
            Class<?>[] argClass = new Class[] { String.class };
            Object[] argsObj = new Object[] { csvFilePath };

            rms.invoke("exportRepresentations", "ext.lev.viz.ExportRepresentationUtility", null, argClass, argsObj);

            //ExportRepresentationUtility.exportRepresentations(csvFilePath);
            
            System.out.println("PDF's Downloaded Successfully!");
        } catch (Exception e) {
            System.err.println("Error during PDF export: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    public static void exportRepresentations(String csvFilePath) throws WTException {
        FileWriter csvWriter = null;
        try {
            // Create export directory if it doesn't exist
            Files.createDirectories(Paths.get(EXPORT_PATH));
            
            // Create output CSV with headers
            File outputCsvFile = new File(outputCsvPath);
            csvWriter = new FileWriter(outputCsvFile);
            csvWriter.append("DocumentNumber,PDFPath\n");
            csvWriter.flush();

            // Read document numbers from CSV
            Map<String, String> docNumbers = readCSVFile(csvFilePath);
            System.out.println("Read " + docNumbers.size() + " document numbers from CSV");

            int processedCount = 0;
            int successCount = 0;

            for (String docNumber : docNumbers.keySet()) {
                processedCount++;
                System.out.println("\nProcessing document " + processedCount + " of " + docNumbers.size() + ": " + docNumber);
                
                QuerySpec querySpec = new QuerySpec(WTDocument.class);
             // Query for the document number
                SearchCondition numberCondition = new SearchCondition(
                    WTDocument.class, WTDocument.NUMBER, SearchCondition.EQUAL, docNumber);
                querySpec.appendWhere(numberCondition);
                
                QueryResult documents = PersistenceHelper.manager.find(querySpec);
                WTDocument latestDoc = null;
                
                // Find the latest revision among all results
                while (documents.hasMoreElements()) {
                    WTDocument doc = (WTDocument) documents.nextElement();
                    
                    // Skip work in progress documents
                    if (WorkInProgressHelper.isWorkingCopy(doc)) {
                        continue;
                    }
                    
                    // Check if this is a newer revision
                    if (latestDoc == null || 
                        isNewer(doc, latestDoc)) {
                        latestDoc = doc;
                    }
                }
                
                // If we found a latest revision, get its latest iteration
                if (latestDoc != null) {
                    latestDoc = (WTDocument) VersionControlHelper.getLatestIteration(latestDoc, false);
                    
                    String pdfPath = exportRepresentationsForDocument(latestDoc);
                    
                    if (pdfPath != null && !pdfPath.isEmpty()) {
                        pdfPath = pdfPath.replace("\\", "/");
                        csvWriter.write(String.format("%s,%s%n", docNumber, pdfPath));
                        csvWriter.flush();
                        
                        successCount++;
                        System.out.println("Successfully exported PDF for document: " + docNumber);
                        System.out.println("PDF path: " + pdfPath);
                        System.out.println("Version Info: " + latestDoc.getVersionIdentifier().getValue());
                    } else {
                        System.out.println("No PDF found for document: " + docNumber);
                    }
                } else {
                    System.out.println("No document found with number: " + docNumber);
                }
            }

            System.out.println("\nExport Summary:");
            System.out.println("Total documents processed: " + processedCount);
            System.out.println("Successful PDF exports: " + successCount);
            System.out.println("Output CSV created at: " + outputCsvPath);

        } catch (Exception e) {
            LOGGER.error("Error exporting representations: " + e.getMessage(), e);
            throw new WTException("Error exporting representations: " + e.getMessage());
        } finally {
            if (csvWriter != null) {
                try {
                    csvWriter.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing CSV writer: " + e.getMessage(), e);
                }
            }
        }
    }
    private static boolean isNewer(WTDocument doc1, WTDocument doc2) throws WTException {
        try {
            String version1 = doc1.getVersionIdentifier().getValue();
            String version2 = doc2.getVersionIdentifier().getValue();
            
            // Compare the letter part of the version (e.g., "A", "B", "C")
            // Assuming versions are in format "A.1", "B.2", etc.
            char rev1 = version1.charAt(0);
            char rev2 = version2.charAt(0);
            
            return rev1 > rev2;
        } catch (Exception e) {
            LOGGER.error("Error comparing versions: " + e.getMessage(), e);
            throw new WTException(e);
        }
    }
    private static String exportRepresentationsForDocument(WTDocument document) throws WTException {
        try {
            LOGGER.debug("> downloadLatestPDFRepresentation() " + document.getDisplayIdentity());
            Map<String, Representation> representationMap = getLatestRepresentation(document);
            LOGGER.debug("> downloadLatestPDFRepresentation: Map Representation " + representationMap.size());
            
            for (Representation representation : representationMap.values()) {
                try {
                    representation = (Representation) ContentHelper.service.getContents(representation);
                    Enumeration<?> e = ContentHelper.getApplicationData(representation).elements();
                    
                    while (e.hasMoreElements()) {
                        Object appObject = e.nextElement();
                        
                        if (appObject instanceof ApplicationData) {
                            ApplicationData appData = (ApplicationData) appObject;
                            String fileName = appData.getFileName();
                            String fileExtension = wt.util.FileUtil.getExtension(fileName);
                            
                            if (fileExtension.equalsIgnoreCase(PDF)) {
                                File fileDir = new File(EXPORT_PATH);
                                createDirectory(fileDir);
                                
                                File downloadedFile = downloadContent(appData, fileDir, fileName);
                                if (downloadedFile != null && downloadedFile.exists()) {
                                    return downloadedFile.getCanonicalPath();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error processing representation: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in exportRepresentationsForDocument: " + e.getMessage(), e);
        }
        return null;
    }
    
    public static File downloadContent(final ApplicationData appData, final File fileDir, final String fileName) 
            throws WTException, WTPropertyVetoException {
        LOGGER.debug("Executing downloadContent()");
        File downloadedFile = null;
        InputStream istream = null;
        OutputStream ostream = null;
        
        try {
            Class.forName("wt.content.ContentServerHelper");
            
            downloadedFile = new File(fileDir, fileName);
            String filePath = downloadedFile.getCanonicalPath();
            HolderToContent htc = appData.getHolderLink();
            appData.setHolderLink(htc);
            
            istream = ContentServerHelper.service.findContentStream(appData);
            ostream = new FileOutputStream(filePath);
            ContentServerHelper.service.writeContentStream(appData, filePath);
            
            LOGGER.debug("downloadContent > File Downloaded: " + fileName);
            return downloadedFile;
            
        } catch (Exception e) {
            LOGGER.error("Error downloading content: " + e.getMessage(), e);
            throw new WTException(e);
        } finally {
            try {
                if (istream != null) istream.close();
                if (ostream != null) ostream.close();
            } catch (IOException e) {
                LOGGER.error("Error closing streams: " + e.getMessage(), e);
            }
        }
    }
    
    public static void createDirectory(File directory) {
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create directory: " + directory.getAbsolutePath());
            }
        }
    }

    public static Map<String, Representation> getLatestRepresentation(final WTDocument wtdocument) throws WTException {
        LOGGER.debug(" > getLatestRepresentation()" + wtdocument.getDisplayIdentity());
        final Map<String, Representation> representationMap = new HashMap<>();
        
        if (wtdocument instanceof Representable) {
            VisualizationHelper visualizationHelper = new VisualizationHelper();
            QueryResult wtReps = visualizationHelper.getRepresentations(wtdocument);
            LOGGER.debug(" > getLatestRepresentation> getRepresentations returned : " + wtReps.size());
            
            while (wtReps.hasMoreElements()) {
                final Representation representation = (Representation) wtReps.nextElement();
                LOGGER.debug(" > getLatestRepresentation> Representation : " + representation.getName() 
                    + " out of date: " + representation.isOutOfDate());
                LOGGER.debug(representation.getPersistInfo().getModifyStamp());
                if(representation.isDefaultRepresentation())
                representationMap.put("", representation);
                
            }
        }
        return representationMap;
    }
    
    private static Map<String, String> readCSVFile(String csvFilePath) throws Exception {
        Map<String, String> documentNumber = new HashMap<>();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new FileReader(csvFilePath));
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                if (line.trim().length() > 0) {
                    documentNumber.put(line.trim(), "");
                }
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing CSV reader: " + e.getMessage(), e);
                }
            }
        }

        return documentNumber;
    }

    private static Properties getPropertyFile() {
        WTProperties wtprops;
        Properties prop = null;
        FileInputStream file = null;

        try {
            wtprops = WTProperties.getServerProperties();
            String propertyFilePath = wtprops.getProperty("wt.codebase.location") + PROPERTY_FILE_LOCATION_IN_CODEBASE;
            file = new FileInputStream(propertyFilePath);
            prop = new Properties();
            prop.load(file);
        } catch (IOException e) {
            LOGGER.error("Error reading property file: " + e.getMessage(), e);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    LOGGER.error("Error closing property file: " + e.getMessage(), e);
                }
            }
        }

        return prop;
    }

    private static String getMapping(String value) {
        String mappedValue = null;
        Properties prop = getPropertyFile();

        if (prop != null) {
            mappedValue = (String) prop.get(value);
            if (mappedValue == null) {
                LOGGER.warn("No mapping found for key: " + value);
            }
        }

        return mappedValue;
    }
}