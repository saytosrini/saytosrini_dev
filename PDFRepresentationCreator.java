package ext.lev.viz;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.ptc.wvs.server.ui.RepHelper;
import com.ptc.wvs.server.util.PublishUtils;

import wt.doc.WTDocument;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.fc.ReferenceFactory;
import wt.log4j.LogR;
import wt.method.RemoteAccess;
import wt.method.RemoteMethodServer;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.representation.Representation;
import wt.util.WTException;
import wt.util.WTProperties;
import wt.vc.VersionControlHelper;
import wt.vc.wip.WorkInProgressHelper;

public class PDFRepresentationCreator implements RemoteAccess {
    private static final String CLASSNAME = PDFRepresentationCreator.class.getName();
    private static final Logger LOGGER = LogR.getLogger(CLASSNAME);
    private static final String PROPERTY_FILE_LOCATION_IN_CODEBASE = "/ext/lev/viz/representationutility.properties";
    private static final String csvFilePath = getMapping("icsvpath"); // Fetching CSV path from properties

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
            //String csvFilePath = getMapping("icsvpath"); // Fetching CSV path from properties
            System.out.println("csv path information from properties : "+csvFilePath);
            Class<?>[] argClass = new Class[] { String.class };
            Object[] argsObj = new Object[] { csvFilePath };

            rms.invoke("createPDFRepresentations", "ext.lev.viz.PDFRepresentationCreator", null, argClass, argsObj);
                    
            LOGGER.info("PDF Representation Creation Process Completed");
        } catch (Exception e) {
            System.err.println("Error during PDF Representation Creation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void createPDFRepresentations(String csvFilePath) throws Exception {
        LOGGER.debug("Starting PDF Representation Creation Process");
        System.out.println("Starting PDF Representation Creation Process");
        Map<String, String> docNumberToPdfMap = readCSVFile(csvFilePath);

        LOGGER.debug("Found " + docNumberToPdfMap.size() + " documents to process");
        System.out.println("Found " + docNumberToPdfMap.size() + " documents to process");
        for (Map.Entry<String, String> entry : docNumberToPdfMap.entrySet()) {
            String docNumber = entry.getKey();
            String pdfPath = entry.getValue();
            LOGGER.debug("Processing document number: " + docNumber);
            System.out.println("Processing document number: " + docNumber);
            try {
                WTDocument document = findWTDocument(docNumber);
                if (document != null) {
                    createDocumentRepresentation(document, pdfPath);
                } else {
                    LOGGER.warn("Document not found for number: " + docNumber);
                    System.out.println("Document not found for number: " + docNumber);
                }
            } catch (Exception e) {
                LOGGER.error("Error processing document: " + docNumber, e);
                System.out.println("Error processing document: " + docNumber);
            }
        }
        LOGGER.debug("PDF Creation Process Exited");
    }

    private static Map<String, String> readCSVFile(String csvFilePath) throws Exception {
        Map<String, String> docNumberToPdfMap = new HashMap<>();
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

                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String docNumber = parts[0].trim();
                    String pdfPath = parts[1].trim();
                    docNumberToPdfMap.put(docNumber, pdfPath);
                }
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }

        return docNumberToPdfMap;
    }

    @SuppressWarnings("deprecation")
	private static WTDocument findWTDocument(String docNumber) throws WTException {
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
        }
        return latestDoc;
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
    private static void createDocumentRepresentation(WTDocument document, String pdfPath) throws Exception {
        LOGGER.debug("Creating representation for document: " + document.getNumber());
        // First check if representation already exists
        QueryResult existingReps = PublishUtils.getRepresentations(document);
        if (existingReps != null && existingReps.hasMoreElements()) {
            LOGGER.info("Representation already exists for document: " + document.getNumber() + ". Skipping creation.");
            return;
        }

        LOGGER.debug("No existing representation found. Creating new representation for document: " + document.getNumber());

        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            throw new Exception("PDF file not found: " + pdfPath);
        }

        // Create temporary working directory
        String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "doc_rep_" + System.currentTimeMillis();
        File tempDirFile = new File(tempDir);
        if (!tempDirFile.mkdirs()) {
            throw new Exception("Failed to create temporary directory: " + tempDir);
        }

        try {
            // Copy PDF to temp directory using Java IO
            File tempPdfFile = new File(tempDirFile, pdfFile.getName());
            copyFile(pdfFile, tempPdfFile);

            // Create representation
            ReferenceFactory refFactory = new ReferenceFactory();
            boolean repCreated = createRepresentation(tempDirFile.getPath(), refFactory.getReferenceString(document), tempPdfFile.getName());

            if (repCreated) {
                LOGGER.debug("Successfully created representation for document: " + document.getNumber());
                
                // Find the newly created representation using PublishUtils
                QueryResult reps = PublishUtils.getRepresentations(document);
                while (reps.hasMoreElements()) {
                    Representation rep = (Representation) reps.nextElement();
                    // Create thumbnails for the representation
                    DocumentThumbnailCreator.createDocumentThumbnail(document, rep);
                    break; // Process only the most recent representation
                }
            } else {
                LOGGER.error("Failed to create representation for document: " + document.getNumber());
            }
        } finally {
            // Clean up temp directory using Java IO
            deleteDirectory(tempDirFile);
        }
    }
    // Utility method to copy file
    private static void copyFile(File source, File dest) throws IOException {
        java.io.FileInputStream fis = null;
        java.io.FileOutputStream fos = null;
        try {
            fis = new java.io.FileInputStream(source);
            fos = new java.io.FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    LOGGER.warn("Error closing input stream", e);
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    LOGGER.warn("Error closing output stream", e);
                }
            }
        }
    }

    // Utility method to delete directory
    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private static boolean createRepresentation(String directory, String objectRef, String fileName) throws Exception {
        try {
            String pdfFilePath = directory + File.separator + fileName;

            LOGGER.debug("Creating representation for object reference: " + objectRef);
            LOGGER.debug("Using PDF file: " + pdfFilePath);

            boolean result = RepHelper.loadRepresentation(
                directory,               // Directory where the PDF is located
                objectRef,               // Reference to the WTDocument
                false,                   // Last parameter may depend on specific requirements
                "default",     // Name of the representation
                "PDF Document Representation", // Description of the representation
                false,                   // Some flag, depending on your logic
                false,                   // Some flag, depending on your logic
                true                     // Flag indicating that this is a PDF representation
            );

            return result; // Return the result of the representation creation
        } catch (Exception e) {
            LOGGER.error("Error creating representation for object reference: " + objectRef, e);
            throw e; // Rethrow the exception for further handling
        }
    }

    private static Properties getPropertyFile() {
        WTProperties wtprops;
        Properties prop = null;
        FileInputStream file = null;

        try {
            wtprops = WTProperties.getServerProperties();
            file = new FileInputStream(
                wtprops.getProperty("wt.codebase.location") + PROPERTY_FILE_LOCATION_IN_CODEBASE);
            prop = new Properties();
            prop.load(file);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    e.printStackTrace();
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
        }

        return mappedValue;
    }
}