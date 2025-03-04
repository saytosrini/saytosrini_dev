package ext.lev.viz;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import wt.util.WTProperties;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.*;
class Logger {
    private static final String LOG_FILE_NAME = "document_extractor.log";
    private static PrintWriter logWriter;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public static void initialize(String xmlFolderPath) {
        try {
            File logFile = new File(xmlFolderPath, LOG_FILE_NAME);
            logWriter = new PrintWriter(new FileWriter(logFile, true), true);
            log("Logger initialized at: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void log(String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logMessage = String.format("[%s] %s", timestamp, message);
        System.out.println(logMessage);
        if (logWriter != null) {
            logWriter.println(logMessage);
        }
    }
    
    public static void error(String message, Throwable e) {
        log("ERROR: " + message);
        if (e != null) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log(sw.toString());
        }
    }
    
    public static void close() {
        if (logWriter != null) {
            logWriter.close();
        }
    }
}

class ManifestReader {
    private static final String LAST_KNOWN_REPOSITORY = "lastKnownRepository";
    private Map<String, String> manifestEntries;
    
    public ManifestReader() {
        this.manifestEntries = new HashMap<>();
    }
    
    public void processManifestFile(String manifestPath) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(manifestPath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(LAST_KNOWN_REPOSITORY)) {
                    processManifestEntry(line);
                }
            }
            Logger.log("Processed manifest file: " + manifestPath);
        } catch (IOException e) {
            Logger.error("Error reading manifest file", e);
        }
    }

    private void processManifestEntry(String line) {
        String[] parts = line.split("=", 2);
        if (parts.length == 2) {
            String key = parts[0].trim();
            String value = extractRepositoryValue(parts[1].trim());
            manifestEntries.put(key, value);
            Logger.log("Processed manifest entry: " + key + " = " + value);
        }
    }
    
    private String extractRepositoryValue(String value) {
        String lastPart = value;
        int lastSlashIndex = value.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex < value.length() - 1) {
            lastPart = value.substring(lastSlashIndex + 1);
        }
        
        int pipeIndex = lastPart.indexOf('|');
        if (pipeIndex != -1 && pipeIndex < lastPart.length() - 1) {
            lastPart = lastPart.substring(pipeIndex + 1);
        }
        
        String trimmedValue = lastPart.trim();
        Logger.log("Original value: " + value);
        Logger.log("Extracted repository value: " + trimmedValue);
        return trimmedValue;
    }
    
    public String getRepositoryValue(String key) {
        return manifestEntries.get(key);
    }
    
    public Map<String, String> getAllRepositoryValues() {
        return new HashMap<>(manifestEntries);
    }
}

public class DocumentNumberExtractor {
    private static final String PROPERTIES_FILE = "C:\\ptc\\Windchill_12.0\\Windchill\\codebase\\ext\\lev\\viz\\representationutility.properties";
    private static final String MAPPING_FILE = "C:\\ptc\\Windchill_12.0\\Windchill\\temp\\RepresentationExport\\cabinetMapping.properties";
    private static final String NUMBER_TAG = "number";
    private static final String OBJECT_CONTAINER_PATH_TAG = "objectContainerPath";
    private static final String OUTPUT_FILE = "docnumberfromxml.csv";
    private static final String FILE_FILTER = "WTDocument";
    private static final String LAST_KNOWN_REPOSITORY = "lastKnownRepository";
    
    private Properties mappingProperties;
    private ManifestReader manifestReader;
    
    public DocumentNumberExtractor() {
    	Logger.log("Initializing DocumentNumberExtractor");
    	this.manifestReader = new ManifestReader();
        this.mappingProperties = loadMappingPropertiesWithEncoding();
    }
    private Properties loadMappingPropertiesWithEncoding() {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(MAPPING_FILE), StandardCharsets.UTF_8)) {
            properties.load(reader);
            Logger.log("Loaded mapping properties file with " + properties.size() + " entries");
            logRussianEntries(properties);
        } catch (IOException e) {
            Logger.error("Error loading mapping properties file", e);
        }
        return properties;
    }
    
    private void logRussianEntries(Properties properties) {
        Logger.log("\nChecking for Russian entries in properties:");
        properties.forEach((key, value) -> {
            if (containsRussianCharacters(key.toString()) || 
                containsRussianCharacters(value.toString())) {
                Logger.log("Found Russian entry - Key: " + key + ", Value: " + value);
            }
        });
    }
    
    private boolean containsRussianCharacters(String text) {
        return text != null && text.matches(".*[а-яА-ЯёЁ].*");
    }
    
    private Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(PROPERTIES_FILE), StandardCharsets.UTF_8)) {
            properties.load(reader);
            Logger.log("Loaded properties file successfully");
        } catch (IOException e) {
            Logger.error("Error loading properties file", e);
        }
        return properties;
    }
    
    private boolean isPathInMappingProperties(String path, String repository) {
        if (repository == null || repository.isEmpty() || path == null || path.isEmpty()) {
            Logger.log("Invalid path or repository: path=" + path + ", repository=" + repository);
            return false;
        }

        String targetPaths = getMapping(repository);
        if (targetPaths == null || targetPaths.isEmpty()) {
            Logger.log("No mapping found for repository: " + repository);
            return false;
        }

        List<String> pathsList = Arrays.asList(targetPaths.split(","));
        boolean exists = pathsList.contains(path);
        
        Logger.log("Checking path: " + path);
        Logger.log("For repository: " + repository);
        Logger.log("Target paths: " + targetPaths);
        Logger.log("Path exists in mapping: " + exists);
        
        return exists;
    }
    
    public void processXMLFiles() {
        try {
            Properties properties = loadProperties();
            String xmlFolderPath = properties.getProperty("xml.folder.path");
            String manifestPath = properties.getProperty("manifest.file.path");
            
            if (xmlFolderPath == null || xmlFolderPath.trim().isEmpty()) {
                throw new IllegalArgumentException("XML folder path not specified in properties file");
            }
            
            // Initialize logger with XML folder path
            Logger.initialize(xmlFolderPath);
            Logger.log("Starting XML file processing");

            if (manifestPath != null && !manifestPath.trim().isEmpty()) {
                manifestReader.processManifestFile(manifestPath);
            }

            File folder = new File(xmlFolderPath);
            File outputFile = new File(folder, OUTPUT_FILE);
          
            if (outputFile.exists()) {
                Logger.log("Deleting existing CSV file: " + outputFile.getAbsolutePath());
                if (!outputFile.delete()) {
                    Logger.log("Warning: Could not delete existing CSV file");
                }
            }
            
            // Use Set instead of List to prevent duplicates
            Set<String> uniqueDocNumbers = new LinkedHashSet<>();
            File[] xmlFiles = folder.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".xml") && 
                name.contains(FILE_FILTER)
            );
            
            if (xmlFiles == null || xmlFiles.length == 0) {
                Logger.log("No WTDocument XML files found in directory: " + xmlFolderPath);
                return;
            }
            
            Logger.log("Starting fresh processing of " + xmlFiles.length + " WTDocument XML files...");
            
            int totalProcessed = 0;
            int totalMatched = 0;
            int duplicatesSkipped = 0;
            
            for (File xmlFile : xmlFiles) {
                Logger.log("Processing file: " + xmlFile.getName());
                ProcessingResult result = processXMLFileToSet(xmlFile, uniqueDocNumbers);
                totalProcessed++;
                totalMatched += result.matchedCount;
                duplicatesSkipped += result.duplicatesSkipped;
                Logger.log(String.format("File %s processed. Matched entries: %d, Duplicates skipped: %d", 
                    xmlFile.getName(), result.matchedCount, result.duplicatesSkipped));
            }
            
            // Convert Set to List<String[]> for CSV writing
            List<String[]> entries = uniqueDocNumbers.stream()
                .map(num -> new String[]{num})
                .collect(Collectors.toList());
                
            writeCSVWithEncoding(outputFile, entries);
            Logger.log(String.format("Processing completed. Total files: %d, Total matching entries: %d, Total duplicates skipped: %d", 
                totalProcessed, totalMatched, duplicatesSkipped));
                
        } catch (Exception e) {
            Logger.error("Error in processXMLFiles", e);
        }
    }

    // Helper class to track processing results
    private static class ProcessingResult {
        int matchedCount;
        int duplicatesSkipped;
        
        ProcessingResult(int matchedCount, int duplicatesSkipped) {
            this.matchedCount = matchedCount;
            this.duplicatesSkipped = duplicatesSkipped;
        }
    }

    private ProcessingResult processXMLFileToSet(File xmlFile, Set<String> uniqueDocNumbers) {
        int matchedEntries = 0;
        int duplicatesSkipped = 0;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            
            Document document = builder.parse(xmlFile);
            document.getDocumentElement().normalize();
            
            NodeList numberNodes = document.getElementsByTagName(NUMBER_TAG);
            NodeList objectContainerPathNodes = document.getElementsByTagName(OBJECT_CONTAINER_PATH_TAG);
            
            Map<Integer, String> numbers = new HashMap<>();
            Map<Integer, String> containerPaths = new HashMap<>();
            
            for (int i = 0; i < numberNodes.getLength(); i++) {
                Node node = numberNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    numbers.put(i, element.getTextContent().trim());
                }
            }
            
            for (int i = 0; i < objectContainerPathNodes.getLength(); i++) {
                Node node = objectContainerPathNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String fullPath = element.getTextContent().trim();
                    String trimmedPath = extractValueAfterEquals(fullPath);
                    containerPaths.put(i, trimmedPath);
                }
            }
            
            String repository = manifestReader.getRepositoryValue(LAST_KNOWN_REPOSITORY);
            Logger.log("Processing with repository: " + repository);
            
            int maxEntries = Math.max(numbers.size(), containerPaths.size());
            for (int i = 0; i < maxEntries; i++) {
                String number = numbers.getOrDefault(i, "");
                String containerPath = containerPaths.getOrDefault(i, "");
                boolean existsInMapping = !containerPath.isEmpty() && 
                                       isPathInMappingProperties(containerPath, repository);
                
                // Log all entries
                Logger.log(String.format(
                    "Document: %s, Path: %s, Exists in mapping: %s, Repository: %s",
                    number,
                    containerPath,
                    existsInMapping,
                    repository != null ? repository : ""
                ));
                
                // Only add to set if path exists in mapping and number is not empty
                if (existsInMapping && !number.isEmpty()) {
                    boolean wasAdded = uniqueDocNumbers.add(number);
                    if (wasAdded) {
                        matchedEntries++;
                    } else {
                        duplicatesSkipped++;
                        Logger.log("Skipped duplicate document number: " + number);
                    }
                }
            }
            
        } catch (Exception e) {
            Logger.error("Error processing file " + xmlFile.getName(), e);
        }
        return new ProcessingResult(matchedEntries, duplicatesSkipped);
    }
    
    private void writeCSVWithEncoding(File outputFile, List<String[]> entries) {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            // Write BOM for Excel compatibility
            writer.write('\ufeff');
            
            // Write header
            //writer.write("DocumentNumber,ObjectContainerPath,SourceFile,ExistsInMapping,Repository\n");
            writer.write("DocumentNumber\n");

            // Write only the filtered entries
            for (String[] entry : entries) {
                writer.write(String.join(",", Arrays.stream(entry)
                    .map(this::handleCSVValue)
                    .toArray(String[]::new)));
                writer.write("\n");
            }
            
            Logger.log("CSV file written successfully: " + outputFile.getAbsolutePath());
            Logger.log("Total entries written: " + entries.size());
        } catch (IOException e) {
            Logger.error("Error writing CSV file", e);
        }
    }
    
    private String extractValueAfterEquals(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int equalsIndex = path.lastIndexOf('=');
        if (equalsIndex != -1 && equalsIndex < path.length() - 1) {
            return path.substring(equalsIndex + 1).trim();
        }
        return path.trim();
    }
    
    private String handleCSVValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static Properties getPropertyFile() {
        Properties prop = null;
        try {
            WTProperties.getServerProperties();
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(MAPPING_FILE), StandardCharsets.UTF_8)) {
                prop = new Properties();
                prop.load(reader);
            }
        } catch (IOException e) {
            Logger.error("Error loading property file", e);
        }
        return prop;
    }
    
    private static String getMapping(String repository) {
        Properties prop = getPropertyFile();
        if (prop != null) {
            String mappedValue = prop.getProperty(repository);
            Logger.log("Retrieved mapping for repository " + repository + ": " + mappedValue);
            if (mappedValue != null && mappedValue.matches(".*[а-яА-ЯёЁ].*")) {
                System.out.println("Found Russian characters in mapping");
            }
            return mappedValue;
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            DocumentNumberExtractor extractor = new DocumentNumberExtractor();
            extractor.processXMLFiles();
        } catch (Exception e) {
            Logger.error("Unexpected error in main", e);
        } finally {
            Logger.log("Closing DocumentNumberExtractor application");
            Logger.close();
        }
    }
}