<%@ include file="/netmarkets/jsp/components/beginWizard.jspf"%>
<jca:wizard title="Downlaod Watermark PDF" buttonList="DefaultWizardButtons">
	<jca:wizardStep action="WatermarkPDFDownload_step1" label="Downlaod Watermark PDF" type="onDemandWaterMark" />
	<script>
		// Namespace for objects used by these tables
PTC.DownloadExample = {};

PTC.DownloadExample.doDownload = function (formResult)
{
    // Extract the URL of the file to be downloaded from the FormResult
    var fileURL = formResult.extraData.fileURL;

    // Download the indicated file to the user's web browser
    window.opener.PTC.util.downloadUrl (fileURL);
    window.close();

    return true;
};

// Set up the 'doDownload' function to be invoked when the "Download
// File Wizard" wizard is closed.
PTC.action.on ('objectsAffected', PTC.DownloadExample.doDownload); 
</script>
</jca:wizard>
	

<%@ include file="/netmarkets/jsp/util/end.jspf"%>
